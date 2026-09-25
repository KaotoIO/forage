package io.kaoto.forage.memory.chat.infinispan;

import io.kaoto.forage.core.util.config.ConfigEntries;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigTag;

public final class InfinispanConfigEntries extends ConfigEntries {
    public static final ConfigModule SERVER_LIST = ConfigModule.of(
            InfinispanConfig.class,
            "forage.infinispan.server-list",
            "Comma-separated list of Infinispan server addresses in format 'host1:port1,host2:port2'",
            "Server List",
            "localhost:11222",
            ConfigModule.TYPE_STRING,
            true,
            ConfigTag.COMMON);
    public static final ConfigModule CACHE_NAME = ConfigModule.of(
            InfinispanConfig.class,
            "forage.infinispan.cache-name",
            "Name of the cache for storing chat messages",
            "Cache Name",
            "chat-memory",
            ConfigModule.TYPE_STRING,
            true,
            ConfigTag.COMMON);
    public static final ConfigModule USERNAME = ConfigModule.of(
            InfinispanConfig.class,
            "forage.infinispan.username",
            "Username for authentication (optional)",
            "Username",
            null,
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.SECURITY);
    public static final ConfigModule PASSWORD = ConfigModule.of(
            InfinispanConfig.class,
            "forage.infinispan.password",
            "Password for authentication (optional)",
            "Password",
            null,
            ConfigModule.TYPE_PASSWORD,
            false,
            ConfigTag.SECURITY);
    public static final ConfigModule REALM = ConfigModule.of(
            InfinispanConfig.class,
            "forage.infinispan.realm",
            "Security realm for authentication",
            "Realm",
            "default",
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.SECURITY);
    public static final ConfigModule SASL_MECHANISM = ConfigModule.of(
            InfinispanConfig.class,
            "forage.infinispan.sasl-mechanism",
            "SASL mechanism for authentication",
            "SASL Mechanism",
            "DIGEST-MD5",
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.SECURITY);
    public static final ConfigModule CONNECTION_TIMEOUT = ConfigModule.of(
            InfinispanConfig.class,
            "forage.infinispan.connection-timeout",
            "Connection timeout in milliseconds",
            "Connection Timeout",
            "60000",
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule SOCKET_TIMEOUT = ConfigModule.of(
            InfinispanConfig.class,
            "forage.infinispan.socket-timeout",
            "Socket timeout in milliseconds",
            "Socket Timeout",
            "60000",
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule MAX_RETRIES = ConfigModule.of(
            InfinispanConfig.class,
            "forage.infinispan.max-retries",
            "Maximum number of connection retries",
            "Max Retries",
            "3",
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule POOL_MAX_ACTIVE = ConfigModule.of(
            InfinispanConfig.class,
            "forage.infinispan.pool.max-active",
            "Maximum number of active connections per server",
            "Pool Max Active",
            "20",
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule POOL_MIN_IDLE = ConfigModule.of(
            InfinispanConfig.class,
            "forage.infinispan.pool.min-idle",
            "Minimum number of idle connections per server",
            "Pool Min Idle",
            "1",
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule POOL_MAX_WAIT = ConfigModule.of(
            InfinispanConfig.class,
            "forage.infinispan.pool.max-wait",
            "Maximum time to wait for a connection in milliseconds",
            "Pool Max Wait",
            "3000",
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.ADVANCED);

    static {
        initModules(
                InfinispanConfigEntries.class,
                SERVER_LIST,
                CACHE_NAME,
                USERNAME,
                PASSWORD,
                REALM,
                SASL_MECHANISM,
                CONNECTION_TIMEOUT,
                SOCKET_TIMEOUT,
                MAX_RETRIES,
                POOL_MAX_ACTIVE,
                POOL_MIN_IDLE,
                POOL_MAX_WAIT);
    }
}
