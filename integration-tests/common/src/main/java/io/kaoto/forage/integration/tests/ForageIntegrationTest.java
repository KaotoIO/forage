package io.kaoto.forage.integration.tests;

import java.util.function.Consumer;
import org.apache.camel.tooling.model.Strings;
import org.citrusframework.TestAction;
import org.citrusframework.TestActionSupport;
import org.citrusframework.actions.camel.CamelIntegrationRunCustomizedActionBuilder;
import org.citrusframework.spi.Resource;
import org.citrusframework.spi.Resources;

/**
 * Interface required for special test cases:
 * <ul>
 *     <li>Start routes once per class - implement {@link #runBeforeAll}. See example {@code io.kaoto.forage.jdbc.MultiTest}.</li>
 * </ul>
 *
 * <p>The test class has to register extension like {@code @ExtendWith(IntegrationTestSetupExtension.class)}
 *
 */
public interface ForageIntegrationTest extends TestActionSupport {

    /**
     * Start routes once for the class lifetime.
     *
     * @return Name of the integration to be stopped. If you return null, do not forget to register a cleanup method via @{link java.lang.AutoCloseable}
     */
    default String runBeforeAll(ForageTestCaseRunner runner, Consumer<AutoCloseable> afterAll) {
        return null;
    }

    /**
     * Returns CamelContextCustomizerBuildItem configured to run forage application.
     *
     * @param processName Name of the process when running camel jbang. Used as a reference to other linked actions (like cleanup, verifications, ...)
     */
    default CamelIntegrationRunCustomizedActionBuilder<?, ?> forageRun(String processName) {
        return this.forageRun(processName, null, null);
    }

    /**
     * Returns CamelContextCustomizerBuildItem configured to run forage application.
     *
     * @param processName Name of the process when running camel jbang. Used as a reference to other linked actions (like cleanup, verifications, ...)
     * @param foragePropertiesFile Name of the resource with forage properties. Relatively from the caller class path with subfolder of caller class name.
     * @param camelRoute Name of the resource with camel (typically yaml) routes. Relatively from the caller class path with subfolder of caller class name.
     */
    default CamelIntegrationRunCustomizedActionBuilder<?, ?> forageRun(
            String processName, String foragePropertiesFile, String camelRoute) {
        CamelIntegrationRunCustomizedActionBuilder<?, ?> builder =
                camel().jbang().custom("forage", "run").processName(processName);

        if (!Strings.isNullOrEmpty(foragePropertiesFile)) {
            builder.addResource(classResource(foragePropertiesFile));
        }
        if (!Strings.isNullOrEmpty(camelRoute)) {
            builder.addResource(classResource(camelRoute));
        }
        return builder;
    }

    default Resource classResource(String resourceRelativePath) {
        return Resources.fromClasspath(getClass().getSimpleName() + "/" + resourceRelativePath, getClass());
    }

    /**
     * Registers process cleanup for an integration started in {@link #runBeforeAll}. Call this right
     * after each run action: the extension only registers cleanup for the integration name returned
     * from {@code runBeforeAll}, and only when it returns normally — an integration started before a
     * later action fails would otherwise keep running (and, on Quarkus, keep its HTTP port bound)
     * into the following tests.
     */
    default void registerIntegrationCleanup(
            ForageTestCaseRunner runner, String integrationName, Consumer<AutoCloseable> afterAll) {
        runner.run((TestAction) context -> {
            Object pidValue = context.getVariables().get(integrationName + ":pid");
            if (pidValue != null) {
                long pid = Long.parseLong(pidValue.toString());
                afterAll.accept(() -> IntegrationTestSetupExtension.destroyProcess(integrationName, pid));
            }
        });
    }

    /**
     * Sends a message to a running integration via {@code camel cmd send}, addressing it by the
     * PID of its {@code Running} process. Use this instead of {@code camel().jbang().cmd().send()},
     * which addresses by name: with exported runtimes (spring-boot, quarkus) on Camel JBang 4.20
     * the launcher's leftover {@code Terminated} entry makes the name ambiguous and the send is
     * silently dropped (exit code 0). See {@link ForageCmdSendAction}.
     */
    default ForageCmdSendAction.Builder forageCmdSend(String integrationName) {
        return new ForageCmdSendAction.Builder().integration(integrationName);
    }
}
