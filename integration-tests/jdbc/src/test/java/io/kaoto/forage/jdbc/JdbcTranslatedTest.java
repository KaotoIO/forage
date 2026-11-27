package io.kaoto.forage.jdbc;

import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import org.citrusframework.annotations.CitrusTest;
import org.citrusframework.jbang.ProcessAndOutput;
import org.citrusframework.junit.jupiter.CitrusSupport;
import org.citrusframework.spi.Resource;
import io.kaoto.forage.integration.tests.ForageIntegrationTest;
import io.kaoto.forage.integration.tests.ForageTestCaseRunner;
import io.kaoto.forage.integration.tests.IntegrationTestSetupExtension;
import io.kaoto.forage.integration.tests.PropertiesTemplateHelper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A plain datasource, without transaction or other properties the Quarkus export keeps as {@code forage.*}: on
 * Quarkus, the export translates its whole configuration to {@code quarkus.datasource.*} properties.
 */
@CitrusSupport
@ExtendWith(IntegrationTestSetupExtension.class)
public class JdbcTranslatedTest implements ForageIntegrationTest {

    private static final String INTEGRATION_NAME = "jdbc-translated";

    @Override
    public String runBeforeAll(ForageTestCaseRunner runner, Consumer<AutoCloseable> afterAll) {
        Resource dynamicProperties = PropertiesTemplateHelper.createFromTemplate(
                classResource("forage-datasource-factory.properties.template"),
                Map.of(
                        "forage\\.ds1\\.jdbc\\.url=.*",
                        Matcher.quoteReplacement("forage.ds1.jdbc.url="
                                + JdbcContainers.postgres().getJdbcUrl())),
                afterAll);

        runner.when(camel().cli()
                .custom("forage", "run")
                .processName(INTEGRATION_NAME)
                .addResource(dynamicProperties)
                .addResource(classResource("route.camel.yaml"))
                .autoRemove(false)
                .dumpIntegrationOutput(true));

        return INTEGRATION_NAME;
    }

    @Test
    @CitrusTest()
    public void translatedDataSource(ForageTestCaseRunner runner) {
        runner.then(camel().cli()
                .verify()
                .integration(INTEGRATION_NAME)
                .waitForLogMessage("from translated ds1 - [{id=1, content=postgres 1}, {id=2, content=postgres 2}]"));
        runner.then(context -> {
            if ("quarkus"
                    .equals(System.getProperty(
                            IntegrationTestSetupExtension.RUNTIME_PROPERTY,
                            System.getenv(IntegrationTestSetupExtension.RUNTIME_PROPERTY)))) {
                Long pid = context.getVariable(INTEGRATION_NAME + ":pid", Long.class);
                ProcessAndOutput process =
                        context.getVariable(INTEGRATION_NAME + ":process:" + pid, ProcessAndOutput.class);
                assertTrue(process.getOutput().contains("exported as quarkus.* configuration"), process.getOutput());
                // no startup error, no Forage Quarkus extension (features forage-*) in the exported app
                assertFalse(
                        process.getOutput()
                                .lines()
                                .anyMatch(line -> line.contains(" ERROR [") || line.startsWith("[ERROR]")),
                        process.getOutput());
                assertTrue(
                        process.getOutput()
                                .lines()
                                .anyMatch(line -> line.contains("Installed features:") && !line.contains("forage-")),
                        process.getOutput());
            }
        });
    }
}
