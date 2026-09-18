package io.kaoto.forage.influxdb;

import java.nio.file.Files;
import java.nio.file.Path;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.core.util.config.MissingConfigException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InfluxDBConfigTest {
    @TempDir
    Path directory;

    @AfterEach
    void reset() {
        System.clearProperty("forage.config.dir");
        System.clearProperty("forage.influxdb.url");
        System.clearProperty("forage.influxdb.password");
        ConfigStore.getInstance().reload();
    }

    @Test
    void requiresUrl() {
        assertThatThrownBy(() -> new InfluxDBConfig().url()).isInstanceOf(MissingConfigException.class);
        System.setProperty("forage.influxdb.url", " ");
        assertThatThrownBy(() -> new InfluxDBConfig().url()).isInstanceOf(MissingConfigException.class);
    }

    @Test
    void loadsFilesAndNamedClientsWithSystemOverrides() throws Exception {
        Files.writeString(
                directory.resolve("forage-influxdb.properties"),
                "forage.influxdb.url=http://file:8086\nforage.metrics.influxdb.url=http://named:8086\n");
        System.setProperty("forage.config.dir", directory.toString());
        assertThat(new InfluxDBConfig().url()).isEqualTo("http://file:8086");
        assertThat(new InfluxDBConfig("metrics").url()).isEqualTo("http://named:8086");
        System.setProperty("forage.influxdb.url", "http://override:8086");
        assertThat(new InfluxDBConfig().url()).isEqualTo("http://override:8086");
    }

    @Test
    void rejectsIncompleteCredentials() {
        System.setProperty("forage.influxdb.url", "http://localhost:8086");
        System.setProperty("forage.influxdb.password", "secret");
        assertThatThrownBy(() -> new InfluxDBProvider().create()).isInstanceOf(MissingConfigException.class);
    }
}
