package io.kaoto.forage.messaging.springrabbitmq;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.citrusframework.annotations.CitrusTest;
import org.citrusframework.junit.jupiter.CitrusSupport;
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

/**
 * Integration test for named/prefixed Spring RabbitMQ connection factory configuration.
 * Verifies that prefixed properties (e.g., {@code forage.mq1.spring.rabbitmq.host})
 * correctly create named connection factory beans.
 */
@CitrusSupport
@Testcontainers
@ExtendWith(IntegrationTestSetupExtension.class)
public class SpringRabbitMQNamedTest implements ForageIntegrationTest {
    private static final Logger LOG = LoggerFactory.getLogger(SpringRabbitMQNamedTest.class);

    public static final String INTEGRATION_NAME = "spring-rabbitmq-named-routes";

    @Container
    static RabbitMQContainer rabbitmq =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management")).withExposedPorts(5672, 15672);

    @Override
    public String runBeforeAll(ForageTestCaseRunner runner, Consumer<AutoCloseable> afterAll) {
        Map<String, String> envVars = new HashMap<>();
        envVars.put("FORAGE_MQ1_SPRING_RABBITMQ_HOST", rabbitmq.getHost());
        envVars.put("FORAGE_MQ1_SPRING_RABBITMQ_PORT", String.valueOf(rabbitmq.getMappedPort(5672)));
        envVars.put("FORAGE_MQ1_SPRING_RABBITMQ_USERNAME", rabbitmq.getAdminUsername());
        envVars.put("FORAGE_MQ1_SPRING_RABBITMQ_PASSWORD", rabbitmq.getAdminPassword());

        runner.when(forageRun(INTEGRATION_NAME, "forage-spring-rabbitmq.properties", "route.camel.yaml")
                .dumpIntegrationOutput(true)
                .withEnvs(envVars));

        return INTEGRATION_NAME;
    }

    @Test
    @CitrusTest()
    public void springRabbitMQNamedMessaging(ForageTestCaseRunner runner) {
        runner.then(camel().jbang()
                .verify()
                .integration(INTEGRATION_NAME)
                .waitForLogMessage("Received: Hello Camel from named-producer-route"));
    }
}
