package io.kaoto.forage.integration.influxdb;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import org.citrusframework.annotations.CitrusTest;
import org.citrusframework.junit.jupiter.CitrusSupport;
import org.citrusframework.spi.Resource;
import io.kaoto.forage.integration.tests.DisableOnQuarkus;
import io.kaoto.forage.integration.tests.ForageIntegrationTest;
import io.kaoto.forage.integration.tests.ForageTestCaseRunner;
import io.kaoto.forage.integration.tests.IntegrationTestSetupExtension;
import io.kaoto.forage.integration.tests.PropertiesTemplateHelper;
import io.kaoto.forage.integration.tests.RuntimeConditionExtension;

import static org.awaitility.Awaitility.await;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@CitrusSupport
@ExtendWith({IntegrationTestSetupExtension.class, RuntimeConditionExtension.class})
@DisableOnQuarkus(reason = "Camel Quarkus has no InfluxDB 2 extension")
public class InfluxDB2Test implements ForageIntegrationTest {
    private static final String NAME = "forage-influxdb2-test";
    private String url;
    private String measurement;

    @Override
    public String runBeforeAll(ForageTestCaseRunner runner, Consumer<AutoCloseable> afterAll) {
        var container = InfluxDBContainers.v2();
        url = "http://" + container.getHost() + ":" + container.getMappedPort(8086);
        measurement = "forage_" + UUID.randomUUID().toString().replace("-", "");
        Resource properties = PropertiesTemplateHelper.createFromTemplate(
                classResource("forage-influxdb2.properties.template"),
                Map.of("forage\\.influxdb2\\.url=.*", Matcher.quoteReplacement("forage.influxdb2.url=" + url)),
                afterAll);
        Resource route = PropertiesTemplateHelper.createFromTemplate(
                classResource("route.camel.yaml"), "\\{\\{measurement}}", measurement, afterAll);
        runner.when(forageRun(NAME).addResource(properties).addResource(route).dumpIntegrationOutput(true));
        return NAME;
    }

    @Test
    @CitrusTest
    void writesPointToDatabase(ForageTestCaseRunner runner) {
        runner.then(camel().jbang().verify().integration(NAME).waitForLogMessage("InfluxDB 2 point sent"));
        HttpClient client = HttpClient.newHttpClient();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url + "/api/v2/query?org=acme"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Token forage-test-token")
                    .header("Content-Type", "application/vnd.flux")
                    .header("Accept", "application/csv")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "from(bucket: \"metrics\") |> range(start: -1h) |> filter(fn: (r) => r._measurement == \""
                                    + measurement + "\")"))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains(measurement, "21");
        });
    }
}
