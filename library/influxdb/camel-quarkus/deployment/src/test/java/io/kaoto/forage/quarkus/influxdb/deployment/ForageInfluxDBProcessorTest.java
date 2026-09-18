package io.kaoto.forage.quarkus.influxdb.deployment;

import java.util.ArrayList;
import java.util.List;
import org.apache.camel.quarkus.core.deployment.spi.CamelRuntimeBeanBuildItem;
import org.influxdb.InfluxDB;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.quarkus.influxdb.ForageInfluxDBRecorder;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;
import io.quarkus.runtime.RuntimeValue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ForageInfluxDBProcessorTest {
    @AfterEach
    void reset() {
        System.clearProperty("forage.influxdb.url");
        System.clearProperty("forage.metrics.influxdb.url");
        ConfigStore.getInstance().reload();
    }

    @Test
    void registersDefaultClientWithRuntimeCreation() {
        System.setProperty("forage.influxdb.url", "http://localhost:8086");
        verifyRegistration(null, "influxdb");
    }

    @Test
    void registersNamedClient() {
        System.setProperty("forage.metrics.influxdb.url", "http://localhost:8086");
        verifyRegistration("metrics", "metrics");
    }

    @Test
    void staysInactiveWithoutConfiguration() {
        List<CamelRuntimeBeanBuildItem> beans = new ArrayList<>();
        ForageInfluxDBRecorder recorder = mock(ForageInfluxDBRecorder.class);
        new ForageInfluxDBProcessor().registerClients(recorder, new ShutdownContextBuildItem(), beans::add);
        assertThat(beans).isEmpty();
        verifyNoInteractions(recorder);
    }

    private void verifyRegistration(String prefix, String name) {
        List<CamelRuntimeBeanBuildItem> beans = new ArrayList<>();
        ForageInfluxDBRecorder recorder = mock(ForageInfluxDBRecorder.class);
        ShutdownContextBuildItem shutdown = new ShutdownContextBuildItem();
        RuntimeValue<InfluxDB> client = new RuntimeValue<>(mock(InfluxDB.class));
        when(recorder.createClient(prefix, shutdown)).thenReturn(client);
        new ForageInfluxDBProcessor().registerClients(recorder, shutdown, beans::add);
        assertThat(beans).hasSize(1);
        assertThat(beans.get(0).getName()).isEqualTo(name);
        verify(recorder).createClient(prefix, shutdown);
    }
}
