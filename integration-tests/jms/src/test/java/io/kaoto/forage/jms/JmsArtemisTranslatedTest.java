package io.kaoto.forage.jms;

import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import org.citrusframework.annotations.CitrusTest;
import org.citrusframework.jbang.ProcessAndOutput;
import org.citrusframework.junit.jupiter.CitrusSupport;
import org.citrusframework.spi.Resource;
import org.testcontainers.activemq.ArtemisContainer;
import io.kaoto.forage.integration.tests.ForageIntegrationTest;
import io.kaoto.forage.integration.tests.ForageTestCaseRunner;
import io.kaoto.forage.integration.tests.IntegrationTestSetupExtension;
import io.kaoto.forage.integration.tests.PropertiesTemplateHelper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A default Artemis connection factory without transactions: on Quarkus, the export translates its whole
 * configuration to {@code quarkus.artemis.*} and {@code quarkus.pooled-jms.*} properties.
 */
@CitrusSupport
@ExtendWith(IntegrationTestSetupExtension.class)
public class JmsArtemisTranslatedTest implements ForageIntegrationTest {

    private static final String INTEGRATION_NAME = "jms-translated";

    @Override
    public String runBeforeAll(ForageTestCaseRunner runner, Consumer<AutoCloseable> afterAll) {
        ArtemisContainer artemis = JmsContainers.artemis();
        String brokerUrl = "tcp://" + artemis.getHost() + ":" + artemis.getMappedPort(61616);
        Resource properties = PropertiesTemplateHelper.createFromTemplate(
                classResource("forage-connectionfactory.properties.template"),
                Map.of(
                        "forage\\.jms\\.broker\\.url=.*",
                        Matcher.quoteReplacement("forage.jms.broker.url=" + brokerUrl)),
                afterAll);

        runner.when(camel().cli()
                .custom("forage", "run")
                .processName(INTEGRATION_NAME)
                .addResource(properties)
                .addResource(classResource("route.camel.yaml"))
                .autoRemove(false)
                .dumpIntegrationOutput(true));

        return INTEGRATION_NAME;
    }

    @Test
    @CitrusTest()
    public void translatedConnectionFactory(ForageTestCaseRunner runner) {
        runner.then(
                camel().cli().verify().integration(INTEGRATION_NAME).waitForLogMessage("Received: Translated message"));
        // the pool keys need quarkus-pooled-jms, which the export adds for pool.enabled=true
        runner.then(context -> {
            Long pid = context.getVariable(INTEGRATION_NAME + ":pid", Long.class);
            ProcessAndOutput process =
                    context.getVariable(INTEGRATION_NAME + ":process:" + pid, ProcessAndOutput.class);
            assertFalse(process.getOutput().contains("Unrecognized configuration key"), process.getOutput());
            if ("quarkus"
                    .equals(System.getProperty(
                            IntegrationTestSetupExtension.RUNTIME_PROPERTY,
                            System.getenv(IntegrationTestSetupExtension.RUNTIME_PROPERTY)))) {
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
