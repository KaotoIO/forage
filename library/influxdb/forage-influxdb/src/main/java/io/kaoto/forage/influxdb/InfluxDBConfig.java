package io.kaoto.forage.influxdb;

import io.kaoto.forage.core.util.config.AbstractConfig;
import io.kaoto.forage.core.util.config.MissingConfigException;

import static io.kaoto.forage.influxdb.InfluxDBConfigEntries.PASSWORD;
import static io.kaoto.forage.influxdb.InfluxDBConfigEntries.URL;
import static io.kaoto.forage.influxdb.InfluxDBConfigEntries.USERNAME;

public class InfluxDBConfig extends AbstractConfig {
    public InfluxDBConfig() {
        this(null);
    }

    public InfluxDBConfig(String prefix) {
        super(prefix, InfluxDBConfigEntries.class);
    }

    @Override
    public String name() {
        return "forage-influxdb";
    }

    public String url() {
        String value = getRequired(URL, "Missing InfluxDB 1 URL");
        if (value.isBlank()) {
            throw new MissingConfigException("Missing InfluxDB 1 URL");
        }
        return value;
    }

    public String username() {
        return get(USERNAME).filter(value -> !value.isBlank()).orElse(null);
    }

    public String password() {
        return get(PASSWORD).orElse(null);
    }
}
