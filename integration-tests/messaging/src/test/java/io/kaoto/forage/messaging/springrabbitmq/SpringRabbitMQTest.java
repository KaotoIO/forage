package io.kaoto.forage.messaging.springrabbitmq;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.citrusframework.annotations.CitrusTest;
import org.citrusframework.junit.jupiter.CitrusSupport;
import org.citrusframework.spi.Resource;
import org.citrusframework.spi.Resources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;
import io.kaoto.forage.integration.tests.ForageIntegrationTest;
import io.kaoto.forage.integration.tests.ForageTestCaseRunner;
import io.kaoto.forage.integration.tests.IntegrationTestSetupExtension;

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
        // Load original properties file and replace testcontainer-specific values
        try {
            Resource originalProperties = classResource("forage-rabbitmq.properties");
            String original = new String(originalProperties.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            // Replace connection details with testcontainer values
            String propertiesContent = original.replaceAll(
                            "forage\\.spring\\.rabbitmq\\.host=.*", "forage.spring.rabbitmq.host=" + rabbitmq.getHost())
                    .replaceAll(
                            "forage\\.spring\\.rabbitmq\\.port=.*",
                            "forage.spring.rabbitmq.port=" + rabbitmq.getMappedPort(5672))
                    .replaceAll(
                            "forage\\.spring\\.rabbitmq\\.username=.*",
                            "forage.spring.rabbitmq.username=" + rabbitmq.getAdminUsername())
                    .replaceAll(
                            "forage\\.spring\\.rabbitmq\\.password=.*",
                            "forage.spring.rabbitmq.password=" + rabbitmq.getAdminPassword());

            // Write to temporary file (ByteArrayResource doesn't support getFile())
            Path tempPropertiesFile = Files.createTempFile("forage-rabbitmq-", ".properties");
            Files.writeString(tempPropertiesFile, propertiesContent, StandardCharsets.UTF_8);

            // Register cleanup to delete temp file
            afterAll.accept(() -> Files.deleteIfExists(tempPropertiesFile));

            Resource dynamicProperties = Resources.create(tempPropertiesFile.toFile());

            // running jbang forage run with dynamically modified properties
            runner.when(camel().jbang()
                    .custom("forage", "run")
                    .processName(INTEGRATION_NAME)
                    .addResource(dynamicProperties)
                    .addResource(classResource("route.camel.yaml"))
                    .dumpIntegrationOutput(true));

        } catch (IOException e) {
            throw new RuntimeException("Failed to prepare forage-rabbitmq.properties", e);
        }

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
