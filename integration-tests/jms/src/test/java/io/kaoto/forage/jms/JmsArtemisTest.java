package io.kaoto.forage.jms;

import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import org.citrusframework.annotations.CitrusTest;
import org.citrusframework.junit.jupiter.CitrusSupport;
import org.citrusframework.spi.Resource;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.activemq.ArtemisContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import io.kaoto.forage.integration.tests.ForageIntegrationTest;
import io.kaoto.forage.integration.tests.ForageTestCaseRunner;
import io.kaoto.forage.integration.tests.IntegrationTestSetupExtension;
import io.kaoto.forage.integration.tests.PropertiesTemplateHelper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@CitrusSupport
@Testcontainers
@ExtendWith(IntegrationTestSetupExtension.class)
public class JmsArtemisTest implements ForageIntegrationTest {
    private static final Logger LOG = LoggerFactory.getLogger(JmsArtemisTest.class);

    static final String ARTEMIS_IMAGE_NAME =
            ConfigProvider.getConfig().getValue("activemq.artemis.container.image", String.class);
    public static final String INTEGRATION_NAME = "jms-routes";

    @Container
    static ArtemisContainer artemis = new ArtemisContainer(
                    DockerImageName.parse(ARTEMIS_IMAGE_NAME).asCompatibleSubstituteFor("apache/activemq-artemis"))
            .withExposedPorts(61616, 8161)
            .withUser("artemis")
            .withPassword("artemis");

    @Override
    public String runBeforeAll(ForageTestCaseRunner runner, Consumer<AutoCloseable> afterAll) {
        // Load template properties and replace testcontainer-specific values
        String brokerUrl = "tcp://" + artemis.getHost() + ":" + artemis.getMappedPort(61616);
        Resource dynamicProperties = PropertiesTemplateHelper.createFromTemplate(
                classResource("forage-connectionfactory.properties.template"),
                Map.of(
                        "forage\\.jms\\.broker\\.url=.*",
                        Matcher.quoteReplacement("forage.jms.broker.url=" + brokerUrl)),
                afterAll);

        // running jbang forage run with dynamically modified properties
        runner.when(camel().jbang()
                .custom("forage", "run")
                .processName(INTEGRATION_NAME)
                .addResource(dynamicProperties)
                .addResource(classResource("route-artemis.camel.yaml"))
                .dumpIntegrationOutput(true));

        return INTEGRATION_NAME;
    }

    /**
     * Test based on <a href="https://github.com/megacamelus/forage-examples/tree/main/jms/transactional">JMS transactional example</a>.
     */
    @Test
    @CitrusTest()
    public void artemisTransactional(ForageTestCaseRunner runner) {

        // validation of logged message
        runner.then(camel().jbang()
                .verify()
                .integration(INTEGRATION_NAME)
                .waitForLogMessage("Successfully processed message: Transactional message"));
    }
}
