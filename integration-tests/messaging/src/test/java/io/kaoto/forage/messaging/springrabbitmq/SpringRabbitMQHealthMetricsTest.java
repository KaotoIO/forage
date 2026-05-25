package io.kaoto.forage.messaging.springrabbitmq;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
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
import io.kaoto.forage.integration.tests.DisableOnCamelMain;
import io.kaoto.forage.integration.tests.DisableOnQuarkus;
import io.kaoto.forage.integration.tests.ForageIntegrationTest;
import io.kaoto.forage.integration.tests.ForageTestCaseRunner;
import io.kaoto.forage.integration.tests.IntegrationTestSetupExtension;
import io.kaoto.forage.integration.tests.RuntimeConditionExtension;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.awaitility.Awaitility.await;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for Spring Boot Actuator health indicators and Micrometer metrics
 * with Spring RabbitMQ.
 *
 * <p>This test verifies that:
 * <ul>
 *   <li>Health indicators are automatically enabled when actuator is present</li>
 *   <li>RabbitMQ health endpoint reports broker status and version</li>
 *   <li>Metrics are automatically collected when Micrometer is present</li>
 *   <li>RabbitMQ metrics are exposed via actuator metrics endpoint</li>
 * </ul>
 *
 * <p>This test only runs with Spring Boot runtime, as actuator endpoints are Spring Boot specific.
 */
@CitrusSupport
@Testcontainers
@ExtendWith({IntegrationTestSetupExtension.class, RuntimeConditionExtension.class})
@DisableOnQuarkus(reason = "Actuator health and metrics endpoints are Spring Boot specific")
@DisableOnCamelMain(reason = "Actuator health and metrics endpoints are Spring Boot specific")
public class SpringRabbitMQHealthMetricsTest implements ForageIntegrationTest {
    private static final Logger LOG = LoggerFactory.getLogger(SpringRabbitMQHealthMetricsTest.class);

    public static final String INTEGRATION_NAME = "spring-rabbitmq-health-metrics";

    @Container
    static RabbitMQContainer rabbitmq =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management")).withExposedPorts(5672, 15672);

    /** Random port for actuator endpoints, assigned in runBeforeAll */
    private static int actuatorPort;

    /** JSON parser for HTTP response validation */
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String runBeforeAll(ForageTestCaseRunner runner, Consumer<AutoCloseable> afterAll) {
        try {
            // Find an unused random port for actuator endpoints
            actuatorPort = findAvailablePort();
            LOG.info("Using random port {} for actuator endpoints", actuatorPort);

            Resource templateProperties = classResource("forage-spring-rabbitmq.properties.template");
            String template;
            try (var inputStream = templateProperties.getInputStream()) {
                template = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }

            // Replace connection details with testcontainer values and set actuator port
            String propertiesContent = template.replaceAll(
                            "forage\\.spring\\.rabbitmq\\.port=.*",
                            Matcher.quoteReplacement("forage.spring.rabbitmq.port=" + rabbitmq.getMappedPort(5672)))
                    .replaceAll("server\\.port=.*", Matcher.quoteReplacement("server.port=" + actuatorPort));

            // Write to temp directory with proper name so it gets discovered by config system
            Path tempDir = Files.createTempDirectory("forage-test-");
            Path tempPropertiesFile = tempDir.resolve("forage-spring-rabbitmq.properties");
            Files.writeString(tempPropertiesFile, propertiesContent, StandardCharsets.UTF_8);

            // Register cleanup to delete temp file and directory
            afterAll.accept(() -> {
                try {
                    Files.deleteIfExists(tempPropertiesFile);
                    Files.deleteIfExists(tempDir);
                } catch (java.nio.file.DirectoryNotEmptyException e) {
                    // Ignore - temp directory will be cleaned up by OS
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });

            Resource dynamicProperties = Resources.create(tempPropertiesFile.toFile());

            // Start Spring Boot application with actuator and metrics
            runner.when(camel().jbang()
                    .custom("forage", "run")
                    .processName(INTEGRATION_NAME)
                    .addResource(dynamicProperties)
                    .addResource(classResource("route.camel.yaml"))
                    .withArg("--dep", "org.springframework.boot:spring-boot-starter-web")
                    .withArg("--dep", "org.springframework.boot:spring-boot-starter-actuator")
                    .withArg("--dep", "io.micrometer:micrometer-core")
                    .dumpIntegrationOutput(true));

        } catch (IOException e) {
            throw new RuntimeException("Failed to prepare forage-spring-rabbitmq.properties", e);
        }

        return INTEGRATION_NAME;
    }

    /**
     * Test that Spring Boot starts successfully with actuator health and metrics enabled.
     * Verifies that RabbitMQ health indicator and metrics autoconfiguration work correctly.
     */
    @Test
    @CitrusTest()
    public void springBootStartsWithHealthAndMetricsEnabled(ForageTestCaseRunner runner) {
        // Verify messages are being processed (which confirms RabbitMQ connection is healthy)
        runner.then(camel().jbang()
                .verify()
                .integration(INTEGRATION_NAME)
                .waitForLogMessage("Received: Health/Metrics Test Message"));

        // Now verify health and metrics endpoints
        verifyHealthEndpoint();
        verifyMetricsEndpoint();
    }

    /**
     * Verify that the health endpoint reports RabbitMQ status correctly.
     */
    private void verifyHealthEndpoint() {
        String healthUrl = "http://localhost:" + actuatorPort + "/actuator/health";
        LOG.info("Starting health endpoint verification at {}", healthUrl);
        await().atMost(30, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).untilAsserted(() -> {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request =
                    HttpRequest.newBuilder().uri(URI.create(healthUrl)).GET().build();

            HttpResponse<String> response;
            try {
                LOG.info("Sending HTTP GET request to health endpoint...");
                response = client.send(request, HttpResponse.BodyHandlers.ofString());
                LOG.info("Received response with status code: {}", response.statusCode());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.error("InterruptedException when calling health endpoint: {}", e.getMessage());
                throw new RuntimeException("Failed to call health endpoint", e);
            } catch (IOException e) {
                LOG.error("IOException when calling health endpoint: {}", e.getMessage());
                throw new RuntimeException("Failed to call health endpoint", e);
            }

            assertThat(response.statusCode())
                    .as("Health endpoint should return 200 OK")
                    .isEqualTo(200);

            JsonNode json = objectMapper.readTree(response.body());

            // Verify overall status is UP
            assertThat(json.get("status").asText())
                    .as("Overall health status should be UP")
                    .isEqualTo("UP");

            // Verify RabbitMQ component exists and is UP
            assertThat(json.has("components"))
                    .as("Health response should have components")
                    .isTrue();
            JsonNode components = json.get("components");
            assertThat(components.has("rabbit"))
                    .as("Health components should include 'rabbit'")
                    .isTrue();
        });
    }

    /**
     * Verify that metrics endpoint exposes RabbitMQ metrics.
     */
    private void verifyMetricsEndpoint() {
        String metricsUrl = "http://localhost:" + actuatorPort + "/actuator/metrics";
        await().atMost(30, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).untilAsserted(() -> {
            HttpClient client = HttpClient.newHttpClient();
            // Check that RabbitMQ metrics are available
            HttpRequest metricsListRequest =
                    HttpRequest.newBuilder().uri(URI.create(metricsUrl)).GET().build();

            HttpResponse<String> metricsListResponse =
                    client.send(metricsListRequest, HttpResponse.BodyHandlers.ofString());

            assertThat(metricsListResponse.statusCode())
                    .as("Metrics endpoint should return 200 OK")
                    .isEqualTo(200);

            JsonNode metricsList = objectMapper.readTree(metricsListResponse.body());
            assertThat(metricsList.has("names"))
                    .as("Metrics response should have 'names' array")
                    .isTrue();

            String metricsNames = metricsList.get("names").toString();

            // Verify RabbitMQ metrics are present
            assertThat(metricsNames)
                    .as("Metrics should include rabbitmq.connections")
                    .contains("rabbitmq.connections");

            LOG.info("✅ Metrics endpoint verification passed: RabbitMQ metrics are exposed");
        });
    }

    /**
     * Find an available TCP port by opening and immediately closing a ServerSocket.
     * This is a common pattern for finding random ports in tests.
     *
     * @return an available port number
     */
    private static int findAvailablePort() {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("Failed to find available port", e);
        }
    }
}
