package io.kaoto.forage.influxdb2;

import java.nio.file.Files;
import java.nio.file.Path;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.core.util.config.MissingConfigException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InfluxDB2ConfigTest {
    @TempDir
    Path directory;

    @AfterEach
    void reset() {
        System.clearProperty("forage.config.dir");
        System.clearProperty("forage.influxdb2.url");
        System.clearProperty("forage.influxdb2.token");
        ConfigStore.getInstance().reload();
    }

    @Test
    void requiresUrlAndToken() {
        assertThatThrownBy(() -> new InfluxDB2Config().url()).isInstanceOf(MissingConfigException.class);
        assertThatThrownBy(() -> new InfluxDB2Config().token()).isInstanceOf(MissingConfigException.class);
        System.setProperty("forage.influxdb2.url", " ");
        assertThatThrownBy(() -> new InfluxDB2Config().url()).isInstanceOf(MissingConfigException.class);
    }

    @Test
    void loadsFilesAndNamedClientsWithSystemOverrides() throws Exception {
        Files.writeString(
                directory.resolve("forage-influxdb2.properties"),
                "forage.influxdb2.url=http://file:8086\nforage.metrics.influxdb2.url=http://named:8086\n");
        System.setProperty("forage.config.dir", directory.toString());
        assertThat(new InfluxDB2Config().url()).isEqualTo("http://file:8086");
        assertThat(new InfluxDB2Config("metrics").url()).isEqualTo("http://named:8086");
        System.setProperty("forage.influxdb2.url", "http://override:8086");
        assertThat(new InfluxDB2Config().url()).isEqualTo("http://override:8086");
    }

    @Test
    void rejectsBlankToken() {
        System.setProperty("forage.influxdb2.url", "http://localhost:8086");
        System.setProperty("forage.influxdb2.token", " ");
        assertThatThrownBy(() -> new InfluxDB2Provider().create()).isInstanceOf(MissingConfigException.class);
    }
}
