package io.kaoto.forage.integration.tests;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.citrusframework.TestActionBuilder;
import org.citrusframework.camel.actions.CamelActionBuilder;
import org.citrusframework.context.TestContext;
import org.citrusframework.junit.jupiter.CitrusExtensionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.plugin.ExportHelper;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * JUnit5 extension, responsible for:
 * <ul>
 *   <li>Copying the test resource files into working directory.</li>
 *   <li>Starting tests for all runtimes. This is implemented by {@link org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider}.
 *       Each run puts value into system properties with key {@link IntegrationTestSetupExtension#RUNTIME_PROPERTY}.
 *       There are 3 values: null, "--runtime=spring-boot" and "--runtime=quarkus".</li>
 * </ul>
 *
 * <p>Test class should be annotated with:
 * <ul>
 *     <li>@CitrusSupport</li>
 *     <li>@ExtendWith(IntegrationTestSetupExtension.class)</li>
 * </ul>
 * and should add args to the citrus runner similar to <pre>.withArg(System.getProperty(IntegrationTestSetupExtension.RUNTIME_PROPERTY))</pre>
 */
public class IntegrationTestSetupExtension implements BeforeEachCallback, AfterAllCallback, ParameterResolver {

    private static final Logger LOG = LoggerFactory.getLogger(IntegrationTestSetupExtension.class);

    public static final String RUNTIME_PROPERTY = "INTEGRATION_TEST_RUNTIME";

    // The forage plugin installation is idempotent and JVM-wide (it touches the shared
    // camel-jbang plugin registry), so run it once per JVM rather than once per test class
    // per suite: each `camel plugin add` spawns a JBang JVM costing ~10s on CI runners (#434).
    private static final AtomicBoolean PLUGIN_INSTALLED = new AtomicBoolean(false);

    private boolean runBeforeAll = false;
    private final List<AutoCloseable> closeables = new CopyOnWriteArrayList<>();
    private TestContext previousTestContext;
    private volatile boolean shutdownHookRegistered = false;

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        if (!runBeforeAll) {
            runBeforeAll = true;
            if (PLUGIN_INSTALLED.compareAndSet(false, true)) {
                CamelActionBuilder camel =
                        (CamelActionBuilder) TestActionBuilder.lookup("camel").get();
                internalBeforeAll(context, camel);
            }
            runBeforeAll(context);
        }
        // save test context variables
        TestContext testContext = CitrusExtensionHelper.getTestContext(context);
        if (previousTestContext != null) {
            testContext.getVariables().putAll(previousTestContext.getVariables());
        } else {
            previousTestContext = testContext;
        }
    }

    private void runBeforeAll(ExtensionContext context) {

        if (context.getRequiredTestInstance() instanceof ForageIntegrationTest forageTest) {

            ForageTestCaseRunner runner = (ForageTestCaseRunner) CitrusExtensionHelper.getTestRunner(context);

            LOG.info(
                    "Running 'runBeforeAll' setup for class: {}",
                    context.getRequiredTestClass().getName());
            String integrationName = forageTest.runBeforeAll(runner, closeables::add);
            registerShutdownHook();
            if (integrationName == null) {
                LOG.warn(
                        "'runBeforeAll' method did not return name of the integration. Any required cleanup has to be registered manually.");
            } else {
                // Citrus TestContext is scoped per test method: the runner and context captured
                // here belong to the first test method's lifecycle and are invalidated once that
                // test completes. Because afterAll() executes after all test methods have
                // finished, running Citrus actions (e.g. camel.jbang().stop()) through the
                // captured runner would operate on a stale context. This may be improved in a
                // future Citrus version by providing a class-level TestContext. Until then, we
                // capture the OS process ID and destroy the process directly in afterAll().
                TestContext testContext = CitrusExtensionHelper.getTestContext(context);
                Object pidValue = testContext.getVariables().get(integrationName + ":pid");
                if (pidValue != null) {
                    long pid = Long.parseLong(pidValue.toString());
                    closeables.add(() -> destroyProcess(integrationName, pid));
                }
            }
        }
    }

    private void registerShutdownHook() {
        if (shutdownHookRegistered) {
            return;
        }
        shutdownHookRegistered = true;
        Runtime.getRuntime()
                .addShutdownHook(new Thread(
                        () -> {
                            LOG.info("JVM shutdown detected, cleaning up Camel integrations");
                            closeables.forEach(c -> {
                                try {
                                    c.close();
                                } catch (Exception e) {
                                    LOG.warn("Error during shutdown cleanup", e);
                                }
                            });
                        },
                        "forage-test-cleanup"));
    }

    /**
     * Destroys a Camel integration process tree. Public so tests that start additional
     * integrations beyond the one returned from
     * {@link ForageIntegrationTest#runBeforeAll(ForageTestCaseRunner, java.util.function.Consumer)}
     * can register their cleanup via the {@code afterAll} consumer.
     */
    public static void destroyProcess(String integrationName, long pid) {
        ProcessHandle.of(pid)
                .ifPresentOrElse(
                        handle -> {
                            LOG.info("Stopping Camel integration '{}' (pid: {})", integrationName, pid);
                            // Destroy the entire process tree. Camel JBang may spawn child processes
                            // (e.g. mvn quarkus:run → java) that hold resources such as network ports.
                            // Killing only the top-level process can leave children running.
                            handle.descendants().forEach(descendant -> {
                                LOG.info("Stopping descendant process (pid: {})", descendant.pid());
                                descendant.destroy();
                            });
                            handle.destroy();
                            try {
                                handle.onExit().get(10, TimeUnit.SECONDS);
                                LOG.info("Camel integration '{}' (pid: {}) stopped", integrationName, pid);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                LOG.warn(
                                        "Interrupted while waiting for Camel integration '{}' (pid: {}) to stop, forcing kill",
                                        integrationName,
                                        pid);
                                handle.descendants().forEach(ProcessHandle::destroyForcibly);
                                handle.destroyForcibly();
                            } catch (Exception e) {
                                LOG.warn(
                                        "Camel integration '{}' (pid: {}) did not stop gracefully, forcing kill",
                                        integrationName,
                                        pid);
                                handle.descendants().forEach(ProcessHandle::destroyForcibly);
                                handle.destroyForcibly();
                            }
                        },
                        () -> LOG.info("Camel integration '{}' (pid: {}) already stopped", integrationName, pid));
    }

    @Override
    public void afterAll(ExtensionContext context) {
        previousTestContext = null;
        LOG.info(
                "Running 'afterAll' setup for class: {}",
                context.getRequiredTestClass().getName());
        closeables.forEach(c -> {
            try {
                c.close();
            } catch (Exception e) {
                LOG.warn("Error closing test case", e);
            }
        });
        closeables.clear();
    }

    private void internalBeforeAll(ExtensionContext context, CamelActionBuilder camel) {
        ensureCamelCliVersion();
        deleteForagePlugin();

        String projectVersion = ExportHelper.getProjectVersion();
        // ensure, that forage plugin is installed
        CitrusExtensionHelper.getTestRunner(context)
                .when(camel.jbang()
                        .plugin()
                        .add()
                        .pluginName("forage")
                        .withArg("--artifactId", "camel-jbang-plugin-forage")
                        .withArg("--groupId", "io.kaoto.forage")
                        .withArg("--version", projectVersion)
                        .withArg("--gav", "io.kaoto.forage:camel-jbang-plugin-forage:" + projectVersion));
    }

    private static final Pattern CAMEL_VERSION_PATTERN = Pattern.compile("Camel (?:CLI|JBang) version:\\s*(\\S+)");

    /**
     * Validates the installed Camel CLI major.minor matches the project's expected version.
     * Auto-installs the correct version via JBang if there is a mismatch.
     */
    private void ensureCamelCliVersion() {
        String expectedVersion = ExportHelper.getCamelVersion();
        String expectedMajorMinor = majorMinor(expectedVersion);
        if (expectedMajorMinor == null) {
            LOG.warn("Could not determine expected Camel version from build properties, skipping CLI version check");
            return;
        }

        String installedVersion = getInstalledCamelVersion();
        if (installedVersion == null) {
            LOG.info("Camel CLI app is not installed — Citrus invokes JBang directly, skipping version check");
            return;
        }

        String installedMajorMinor = majorMinor(installedVersion);
        if (expectedMajorMinor.equals(installedMajorMinor)) {
            LOG.info("Camel CLI version {} matches expected major.minor {}", installedVersion, expectedMajorMinor);
            return;
        }

        LOG.warn(
                "Camel CLI version mismatch: installed {}, expected {}. Auto-installing the correct version...",
                installedVersion,
                expectedVersion);
        installCamelCli(expectedVersion);
    }

    private String getInstalledCamelVersion() {
        try {
            ProcessBuilder pb = new ProcessBuilder("camel", "version");
            pb.redirectErrorStream(true);
            Process process;
            try {
                process = pb.start();
            } catch (IOException notFound) {
                LOG.info("Camel CLI executable not found on PATH: {}", notFound.getMessage());
                return null;
            }
            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                LOG.warn("Camel CLI version check timed out");
                return null;
            }

            Matcher matcher = CAMEL_VERSION_PATTERN.matcher(output);
            if (matcher.find()) {
                return matcher.group(1);
            }
            LOG.warn("Could not parse Camel CLI version from output: {}", output);
            return "BROKEN";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Interrupted while checking Camel CLI version");
            return null;
        } catch (Exception e) {
            LOG.warn("Failed to check Camel CLI version: {}", e.getMessage());
            return null;
        }
    }

    private void installCamelCli(String version) {
        try {
            LOG.info("Running: jbang app install --force -Dcamel.jbang.version={} camel@apache/camel", version);
            ProcessBuilder pb = new ProcessBuilder(
                    "jbang", "app", "install", "--force", "-Dcamel.jbang.version=" + version, "camel@apache/camel");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }
            if (!process.waitFor(120, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("Camel CLI installation timed out after 120 seconds");
            }
            int exitCode = process.exitValue();
            if (exitCode == 0) {
                LOG.info("Camel CLI installed successfully: {}", output);
            } else {
                throw new IllegalStateException(
                        "Failed to install Camel CLI version " + version + " (exit code " + exitCode + "): " + output);
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while installing Camel CLI. Run manually: jbang app install --force -Dcamel.jbang.version="
                            + version + " camel@apache/camel",
                    e);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to install Camel CLI. Run manually: jbang app install --force -Dcamel.jbang.version="
                            + version + " camel@apache/camel",
                    e);
        }
    }

    private static String majorMinor(String version) {
        if (version == null) {
            return null;
        }
        String[] parts = version.split("\\.");
        if (parts.length >= 2) {
            return parts[0] + "." + parts[1];
        }
        return null;
    }

    /**
     * Removes any previously installed forage plugin to ensure a clean install
     * with the current build's version.
     */
    private void deleteForagePlugin() {
        try {
            ProcessBuilder pb = new ProcessBuilder("camel", "plugin", "delete", "forage");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                LOG.warn("Forage plugin delete timed out");
                return;
            }
            int exitCode = process.exitValue();
            if (exitCode == 0) {
                LOG.info("Deleted existing forage plugin: {}", output);
            } else {
                LOG.debug("No existing forage plugin to delete (exit code {})", exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.debug("Interrupted while deleting forage plugin");
        } catch (Exception e) {
            LOG.debug("Could not delete forage plugin (may not exist): {}", e.getMessage());
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType() == ForageTestCaseRunner.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return CitrusExtensionHelper.getTestRunner(extensionContext);
    }
}
