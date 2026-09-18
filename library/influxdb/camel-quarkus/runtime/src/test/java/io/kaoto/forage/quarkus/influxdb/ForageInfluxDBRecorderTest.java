package io.kaoto.forage.quarkus.influxdb;

import java.util.ArrayList;
import java.util.List;
import org.influxdb.InfluxDB;
import io.kaoto.forage.influxdb.InfluxDBProvider;
import io.quarkus.runtime.ShutdownContext;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ForageInfluxDBRecorderTest {
    @Test
    void closesCreatedClientOnShutdown() {
        InfluxDB client = mock(InfluxDB.class);
        List<Runnable> tasks = new ArrayList<>();
        ShutdownContext shutdown = mock(ShutdownContext.class);
        doAnswer(invocation -> {
                    tasks.add(invocation.getArgument(0));
                    return null;
                })
                .when(shutdown)
                .addShutdownTask(any(Runnable.class));
        try (MockedConstruction<InfluxDBProvider> providers =
                mockConstruction(InfluxDBProvider.class, (provider, ignored) -> when(provider.create("metrics"))
                        .thenReturn(client))) {
            assertThat(new ForageInfluxDBRecorder()
                            .createClient("metrics", shutdown)
                            .getValue())
                    .isSameAs(client);
            verify(client, never()).close();
            assertThat(tasks).hasSize(1);
            tasks.get(0).run();
            verify(client).close();
        }
    }
}
