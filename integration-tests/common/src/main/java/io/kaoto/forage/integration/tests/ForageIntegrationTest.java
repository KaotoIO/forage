package io.kaoto.forage.integration.tests;

import java.util.function.Consumer;
import org.apache.camel.tooling.model.Strings;
import org.citrusframework.TestActionSupport;
import org.citrusframework.actions.camel.CamelIntegrationRunCustomizedActionBuilder;
import org.citrusframework.spi.Resource;
import org.citrusframework.spi.Resources;
import io.kaoto.forage.plugin.ExportHelper;

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
        return builder.withArgs("--java-version=" + ExportHelper.getCiJavaVersion());
    }

    default Resource classResource(String resourceRelativePath) {
        return Resources.fromClasspath(getClass().getSimpleName() + "/" + resourceRelativePath, getClass());
    }
}
