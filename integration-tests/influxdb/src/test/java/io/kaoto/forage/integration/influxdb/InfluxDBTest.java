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
import io.kaoto.forage.integration.tests.ForageIntegrationTest;
import io.kaoto.forage.integration.tests.ForageTestCaseRunner;
import io.kaoto.forage.integration.tests.IntegrationTestSetupExtension;
import io.kaoto.forage.integration.tests.PropertiesTemplateHelper;

import static org.awaitility.Awaitility.await;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@CitrusSupport
@ExtendWith({IntegrationTestSetupExtension.class})
public class InfluxDBTest implements ForageIntegrationTest {
    private static final String NAME = "forage-influxdb-test";
    // Citrus needs per-method test instances, but runBeforeAll initializes these only once.
    private static String url;
    private static String measurement;

    @Override
    public String runBeforeAll(ForageTestCaseRunner runner, Consumer<AutoCloseable> afterAll) {
        var container = InfluxDBContainers.v1();
        url = "http://" + container.getHost() + ":" + container.getMappedPort(8086);
        measurement = "forage_" + UUID.randomUUID().toString().replace("-", "");
        Resource properties = PropertiesTemplateHelper.createFromTemplate(
                classResource("forage-influxdb.properties.template"),
                Map.of("forage\\.influxdb\\.url=.*", Matcher.quoteReplacement("forage.influxdb.url=" + url)),
                afterAll);
        Resource route = PropertiesTemplateHelper.createFromTemplate(
                classResource("route.camel.yaml"), "\\{\\{measurement}}", measurement, afterAll);
        runner.when(forageRun(NAME).addResource(properties).addResource(route).dumpIntegrationOutput(true));
        return NAME;
    }

    @Test
    @CitrusTest
    void writesPointToDatabase(ForageTestCaseRunner runner) {
        runner.then(camel().jbang().verify().integration(NAME).waitForLogMessage("InfluxDB 1 point sent"));
        HttpClient client = HttpClient.newHttpClient();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(url + "/query?db=metrics&q=SELECT%20*%20FROM%20" + measurement))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Basic d3JpdGVyOnRlc3QtcGFzc3dvcmQ=")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains(measurement, "21");
        });
    }
}
