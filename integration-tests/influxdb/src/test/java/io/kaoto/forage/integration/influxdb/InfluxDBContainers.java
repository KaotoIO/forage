package io.kaoto.forage.integration.influxdb;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

final class InfluxDBContainers {
    private static final GenericContainer<?> V1 = new GenericContainer<>(DockerImageName.parse("influxdb:1.8.10"))
            .withExposedPorts(8086)
            .withEnv("INFLUXDB_DB", "metrics")
            .withEnv("INFLUXDB_ADMIN_USER", "writer")
            .withEnv("INFLUXDB_ADMIN_PASSWORD", "test-password")
            .withEnv("INFLUXDB_HTTP_AUTH_ENABLED", "true")
            .waitingFor(Wait.forHttp("/ping").forStatusCode(204));
    private static final GenericContainer<?> V2 = new GenericContainer<>(DockerImageName.parse("influxdb:2.7"))
            .withExposedPorts(8086)
            .withEnv("DOCKER_INFLUXDB_INIT_MODE", "setup")
            .withEnv("DOCKER_INFLUXDB_INIT_USERNAME", "writer")
            .withEnv("DOCKER_INFLUXDB_INIT_PASSWORD", "test-password")
            .withEnv("DOCKER_INFLUXDB_INIT_ORG", "acme")
            .withEnv("DOCKER_INFLUXDB_INIT_BUCKET", "metrics")
            .withEnv("DOCKER_INFLUXDB_INIT_ADMIN_TOKEN", "forage-test-token")
            .waitingFor(Wait.forHttp("/api/v2/setup")
                    .forResponsePredicate(body -> body.replaceAll("\\s+", "").contains("\"allowed\":false")));

    private InfluxDBContainers() {}

    static synchronized GenericContainer<?> v1() {
        if (!V1.isRunning()) {
            V1.start();
        }
        return V1;
    }

    static synchronized GenericContainer<?> v2() {
        if (!V2.isRunning()) {
            V2.start();
        }
        return V2;
    }
}
