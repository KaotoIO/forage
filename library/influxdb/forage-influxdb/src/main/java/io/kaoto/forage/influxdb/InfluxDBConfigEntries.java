package io.kaoto.forage.influxdb;

import io.kaoto.forage.core.util.config.ConfigEntries;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigTag;

public final class InfluxDBConfigEntries extends ConfigEntries {
    public static final ConfigModule URL = ConfigModule.of(
            InfluxDBConfig.class, "forage.influxdb.url", "Server URL", "URL", null, "string", true, ConfigTag.COMMON);
    public static final ConfigModule USERNAME = ConfigModule.of(
            InfluxDBConfig.class,
            "forage.influxdb.username",
            "Authentication username (optional)",
            "Username",
            null,
            "string",
            false,
            ConfigTag.SECURITY);
    public static final ConfigModule PASSWORD = ConfigModule.of(
            InfluxDBConfig.class,
            "forage.influxdb.password",
            "Authentication password (required with username)",
            "Password",
            null,
            "password",
            false,
            ConfigTag.SECURITY);

    static {
        initModules(InfluxDBConfigEntries.class, URL, USERNAME, PASSWORD);
    }
}
