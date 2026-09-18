package io.kaoto.forage.influxdb2;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.influxdb.InfluxDB;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.influxdb.InfluxDBBeanFactory;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.write.Point;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

class InfluxDBCoexistenceTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void bothFactoriesSendAuthenticatedWritesThroughCamel(boolean named) throws Exception {
        List<Request> requests = new CopyOnWriteArrayList<>();
        java.util.concurrent.CountDownLatch writes = new java.util.concurrent.CountDownLatch(2);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            requests.add(new Request(
                    path,
                    exchange.getRequestURI().getRawQuery(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            String response =
                    switch (path) {
                        case "/api/v2/orgs" -> "{\"orgs\":[{\"id\":\"0123456789abcdef\",\"name\":\"acme\"}]}";
                        case "/api/v2/buckets" -> "{\"buckets\":[{\"id\":\"fedcba9876543210\",\"name\":\"metrics\"}]}";
                        default -> null;
                    };
            if (response == null) {
                exchange.sendResponseHeaders(204, -1);
            } else {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                byte[] body = response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
            if (path.equals("/write") || path.equals("/api/v2/write")) {
                writes.countDown();
            }
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        String v1Prefix = named ? "forage.legacy.influxdb." : "forage.influxdb.";
        String v2Prefix = named ? "forage.modern.influxdb2." : "forage.influxdb2.";
        String v1Name = named ? "legacy" : "influxdb";
        String v2Name = named ? "modern" : "influxdb2";
        System.setProperty(v1Prefix + "url", url);
        System.setProperty(v1Prefix + "username", "writer");
        System.setProperty(v1Prefix + "password", "secret");
        System.setProperty(v2Prefix + "url", url);
        System.setProperty(v2Prefix + "token", "test-token");
        InfluxDBBeanFactory v1 = new InfluxDBBeanFactory();
        InfluxDB2BeanFactory v2 = new InfluxDB2BeanFactory();
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            v1.setCamelContext(context);
            v2.setCamelContext(context);
            v1.configure();
            v2.configure();
            assertThat(context.getRegistry().lookupByName(v1Name)).isInstanceOf(InfluxDB.class);
            assertThat(context.getRegistry().lookupByName(v2Name)).isInstanceOf(InfluxDBClient.class);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:v1").to("influxdb:" + v1Name + "?databaseName=metrics");
                    from("direct:v2")
                            .to("influxdb2:" + v2Name
                                    + "?org=acme&bucket=metrics&autoCreateOrg=false&autoCreateBucket=false");
                }
            });
            context.start();
            try (var producer = context.createProducerTemplate()) {
                producer.sendBody(
                        "direct:v1",
                        org.influxdb.dto.Point.measurement("temperature")
                                .addField("value", 21)
                                .build());
                producer.sendBody("direct:v2", Point.measurement("temperature").addField("value", 22));
            }
            assertThat(writes.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(requests).anySatisfy(request -> {
                assertThat(request.path()).isEqualTo("/write");
                assertThat(request.query()).contains("db=metrics");
                assertThat(request.authorization()).isEqualTo("Basic d3JpdGVyOnNlY3JldA==");
                assertThat(request.body()).contains("temperature", "value=21i");
            });
            assertThat(requests).anySatisfy(request -> {
                assertThat(request.path()).isEqualTo("/api/v2/write");
                assertThat(request.query()).contains("bucket=metrics", "org=acme");
                assertThat(request.authorization()).isEqualTo("Token test-token");
                assertThat(request.body()).contains("temperature", "value=22i");
            });
            context.stop();
        } finally {
            v1.stop();
            v2.stop();
            server.stop(0);
            for (String key : List.of(
                    v1Prefix + "url",
                    v1Prefix + "username",
                    v1Prefix + "password",
                    v2Prefix + "url",
                    v2Prefix + "token")) {
                System.clearProperty(key);
            }
            ConfigStore.getInstance().reload();
        }
    }

    private record Request(String path, String query, String authorization, String body) {}
}
