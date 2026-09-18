package io.kaoto.forage.influxdb2;

import io.kaoto.forage.core.util.config.AbstractConfig;
import io.kaoto.forage.core.util.config.MissingConfigException;

import static io.kaoto.forage.influxdb2.InfluxDB2ConfigEntries.TOKEN;
import static io.kaoto.forage.influxdb2.InfluxDB2ConfigEntries.URL;

public class InfluxDB2Config extends AbstractConfig {
    public InfluxDB2Config() {
        this(null);
    }

    public InfluxDB2Config(String prefix) {
        super(prefix, InfluxDB2ConfigEntries.class);
    }

    @Override
    public String name() {
        return "forage-influxdb2";
    }

    public String url() {
        String value = getRequired(URL, "Missing InfluxDB 2 URL");
        if (value.isBlank()) {
            throw new MissingConfigException("Missing InfluxDB 2 URL");
        }
        return value;
    }

    public String token() {
        String value = getRequired(TOKEN, "Missing InfluxDB 2 token");
        if (value.isBlank()) {
            throw new MissingConfigException("Missing InfluxDB 2 token");
        }
        return value;
    }
}
