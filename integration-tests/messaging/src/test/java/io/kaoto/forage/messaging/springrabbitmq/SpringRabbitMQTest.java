package io.kaoto.forage.messaging.springrabbitmq;

import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import org.citrusframework.annotations.CitrusTest;
import org.citrusframework.junit.jupiter.CitrusSupport;
import org.citrusframework.spi.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;
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
public class SpringRabbitMQTest implements ForageIntegrationTest {
    private static final Logger LOG = LoggerFactory.getLogger(SpringRabbitMQTest.class);

    public static final String INTEGRATION_NAME = "spring-rabbitmq-routes";

    @Container
    static RabbitMQContainer rabbitmq =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management")).withExposedPorts(5672, 15672);

    @Override
    public String runBeforeAll(ForageTestCaseRunner runner, Consumer<AutoCloseable> afterAll) {
        // Load template properties and replace testcontainer-specific values
        Resource dynamicProperties = PropertiesTemplateHelper.createFromTemplate(
                classResource("forage-spring-rabbitmq.properties.template"),
                Map.of(
                        "forage\\.rabbitmq\\.port=.*",
                        Matcher.quoteReplacement("forage.rabbitmq.port=" + rabbitmq.getMappedPort(5672))),
                afterAll);

        // running jbang forage run with dynamically modified properties
        runner.when(camel().jbang()
                .custom("forage", "run")
                .processName(INTEGRATION_NAME)
                .addResource(dynamicProperties)
                .addResource(classResource("route.camel.yaml"))
                .dumpIntegrationOutput(true));

        return INTEGRATION_NAME;
    }

    /**
     * Test Spring RabbitMQ message routing.
     * Verifies that messages are sent from timer to RabbitMQ exchange and consumed successfully.
     */
    @Test
    @CitrusTest()
    public void springRabbitMQMessaging(ForageTestCaseRunner runner) {

        // validation of logged message from consumer
        runner.then(camel().jbang()
                .verify()
                .integration(INTEGRATION_NAME)
                .waitForLogMessage("Received: Hello Camel from producer-route"));
    }
}
