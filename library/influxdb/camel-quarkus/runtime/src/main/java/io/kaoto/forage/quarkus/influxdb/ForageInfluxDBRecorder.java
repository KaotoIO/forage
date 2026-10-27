package io.kaoto.forage.quarkus.influxdb;

import org.influxdb.InfluxDB;
import io.kaoto.forage.influxdb.InfluxDBProvider;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class ForageInfluxDBRecorder {
    public RuntimeValue<InfluxDB> createClient(String prefix, ShutdownContext shutdown) {
        InfluxDB client = new InfluxDBProvider().create(prefix);
        shutdown.addShutdownTask(client::close);
        return new RuntimeValue<>(client);
    }
}
