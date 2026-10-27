package io.kaoto.forage.influxdb2;

import io.kaoto.forage.core.util.config.ConfigEntries;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigTag;

public final class InfluxDB2ConfigEntries extends ConfigEntries {
    public static final ConfigModule URL = ConfigModule.of(
            InfluxDB2Config.class, "forage.influxdb2.url", "Server URL", "URL", null, "string", true, ConfigTag.COMMON);
    public static final ConfigModule TOKEN = ConfigModule.of(
            InfluxDB2Config.class,
            "forage.influxdb2.token",
            "Authentication token",
            "Token",
            null,
            "password",
            true,
            ConfigTag.SECURITY);

    static {
        initModules(InfluxDB2ConfigEntries.class, URL, TOKEN);
    }
}
