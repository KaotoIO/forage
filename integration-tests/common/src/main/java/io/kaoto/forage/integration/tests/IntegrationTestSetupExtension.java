package io.kaoto.forage.integration.tests;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
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
 *     <li>@Testcontainers</li>
 *     <li>@ExtendWith(IntegrationTestSetupExtension.class)</li>
 * </ul>
 * and should add args to the citrus runner similar to <pre>.withArg(System.getProperty(IntegrationTestSetupExtension.RUNTIME_PROPERTY))</pre>
 */
public class IntegrationTestSetupExtension implements BeforeEachCallback, AfterAllCallback, ParameterResolver {

    private final Logger LOG = LoggerFactory.getLogger(IntegrationTestSetupExtension.class);

    public static final String RUNTIME_PROPERTY = "INTEGRATION_TEST_RUNTIME";

    private boolean runBeforeAll = false;
    private final List<AutoCloseable> closeables = new CopyOnWriteArrayList<>();
    private TestContext previousTestContext;
    private volatile boolean shutdownHookRegistered = false;

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        if (!runBeforeAll) {
            CamelActionBuilder camel =
                    (CamelActionBuilder) TestActionBuilder.lookup("camel").get();
            runBeforeAll = true;
            internalBeforeAll(context, camel);
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

    private void destroyProcess(String integrationName, long pid) {
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

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType() == ForageTestCaseRunner.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return CitrusExtensionHelper.getTestRunner(extensionContext);
    }
}
