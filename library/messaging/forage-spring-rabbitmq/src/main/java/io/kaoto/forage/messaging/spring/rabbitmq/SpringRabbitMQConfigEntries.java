package io.kaoto.forage.messaging.spring.rabbitmq;

import io.kaoto.forage.core.util.config.ConfigEntries;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigTag;

public final class SpringRabbitMQConfigEntries extends ConfigEntries {

    public static final ConfigModule HOST = ConfigModule.of(
            SpringRabbitMQConfig.class,
            "forage.spring.rabbitmq.host",
            "The RabbitMQ broker host",
            "Host",
            "localhost",
            "string",
            false,
            ConfigTag.COMMON);

    public static final ConfigModule PORT = ConfigModule.of(
            SpringRabbitMQConfig.class,
            "forage.spring.rabbitmq.port",
            "The RabbitMQ broker port",
            "Port",
            "5672",
            "integer",
            false,
            ConfigTag.COMMON);

    public static final ConfigModule USERNAME = ConfigModule.of(
            SpringRabbitMQConfig.class,
            "forage.spring.rabbitmq.username",
            "The RabbitMQ username",
            "Username",
            "guest",
            "string",
            false,
            ConfigTag.SECURITY);

    public static final ConfigModule PASSWORD = ConfigModule.of(
            SpringRabbitMQConfig.class,
            "forage.spring.rabbitmq.password",
            "The RabbitMQ password",
            "Password",
            "guest",
            "password",
            false,
            ConfigTag.SECURITY);

    public static final ConfigModule VIRTUAL_HOST = ConfigModule.of(
            SpringRabbitMQConfig.class,
            "forage.spring.rabbitmq.virtual.host",
            "The RabbitMQ virtual host",
            "Virtual Host",
            "/",
            "string",
            false,
            ConfigTag.COMMON);

    public static final ConfigModule CHANNEL_CACHE_SIZE = ConfigModule.of(
            SpringRabbitMQConfig.class,
            "forage.spring.rabbitmq.channel.cache.size",
            "The number of channels to maintain in cache",
            "Channel Cache Size",
            "25",
            "integer",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule CACHE_MODE = ConfigModule.of(
            SpringRabbitMQConfig.class,
            "forage.spring.rabbitmq.cache.mode",
            "The cache mode (CHANNEL or CONNECTION)",
            "Cache Mode",
            "CHANNEL",
            "string",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule CONNECTION_CACHE_SIZE = ConfigModule.of(
            SpringRabbitMQConfig.class,
            "forage.spring.rabbitmq.connection.cache.size",
            "The number of connections to cache (CONNECTION mode only)",
            "Connection Cache Size",
            "1",
            "integer",
            false,
            ConfigTag.ADVANCED);

    static {
        initModules(
                SpringRabbitMQConfigEntries.class,
                HOST,
                PORT,
                USERNAME,
                PASSWORD,
                VIRTUAL_HOST,
                CHANNEL_CACHE_SIZE,
                CACHE_MODE,
                CONNECTION_CACHE_SIZE);
    }
}
