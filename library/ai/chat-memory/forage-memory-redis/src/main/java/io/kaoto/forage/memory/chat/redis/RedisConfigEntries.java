package io.kaoto.forage.memory.chat.redis;

import io.kaoto.forage.core.util.config.ConfigEntries;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigTag;

public final class RedisConfigEntries extends ConfigEntries {
    public static final ConfigModule HOST = ConfigModule.of(
            RedisConfig.class,
            "forage.redis.host",
            "Redis server hostname or IP address",
            "Host",
            "localhost",
            "string",
            true,
            ConfigTag.COMMON);
    public static final ConfigModule PORT = ConfigModule.of(
            RedisConfig.class,
            "forage.redis.port",
            "Redis server port number",
            "Port",
            "6379",
            ConfigModule.TYPE_INTEGER,
            true,
            ConfigTag.COMMON);
    public static final ConfigModule PASSWORD = ConfigModule.of(
            RedisConfig.class,
            "forage.redis.password",
            "Redis authentication password (optional)",
            "Password",
            null,
            ConfigModule.TYPE_PASSWORD,
            false,
            ConfigTag.SECURITY);
    public static final ConfigModule DATABASE = ConfigModule.of(
            RedisConfig.class,
            "forage.redis.database",
            "Redis database number to connect to",
            "Database",
            "0",
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.COMMON);
    public static final ConfigModule TIMEOUT = ConfigModule.of(
            RedisConfig.class,
            "forage.redis.timeout",
            "Connection timeout in milliseconds",
            "Timeout",
            "2000",
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule POOL_MAX_TOTAL = ConfigModule.of(
            RedisConfig.class,
            "forage.redis.pool.max-total",
            "Maximum number of connections in the pool",
            "Pool Max Total",
            "10",
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule POOL_MAX_IDLE = ConfigModule.of(
            RedisConfig.class,
            "forage.redis.pool.max-idle",
            "Maximum number of idle connections in the pool",
            "Pool Max Idle",
            "5",
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule POOL_MIN_IDLE = ConfigModule.of(
            RedisConfig.class,
            "forage.redis.pool.min-idle",
            "Minimum number of idle connections in the pool",
            "Pool Min Idle",
            "1",
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule POOL_TEST_ON_BORROW = ConfigModule.of(
            RedisConfig.class,
            "forage.redis.pool.test-on-borrow",
            "Test connections when borrowing from pool",
            "Test On Borrow",
            "true",
            ConfigModule.TYPE_BOOLEAN,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule POOL_TEST_ON_RETURN = ConfigModule.of(
            RedisConfig.class,
            "forage.redis.pool.test-on-return",
            "Test connections when returning to pool",
            "Test On Return",
            "true",
            ConfigModule.TYPE_BOOLEAN,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule POOL_TEST_WHILE_IDLE = ConfigModule.of(
            RedisConfig.class,
            "forage.redis.pool.test-while-idle",
            "Test idle connections periodically",
            "Test While Idle",
            "true",
            ConfigModule.TYPE_BOOLEAN,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule POOL_MAX_WAIT_MILLIS = ConfigModule.of(
            RedisConfig.class,
            "forage.redis.pool.max-wait-millis",
            "Maximum time to wait for a connection from the pool in milliseconds",
            "Pool Max Wait",
            "2000",
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.ADVANCED);

    static {
        initModules(
                RedisConfigEntries.class,
                HOST,
                PORT,
                PASSWORD,
                DATABASE,
                TIMEOUT,
                POOL_MAX_TOTAL,
                POOL_MAX_IDLE,
                POOL_MIN_IDLE,
                POOL_TEST_ON_BORROW,
                POOL_TEST_ON_RETURN,
                POOL_TEST_WHILE_IDLE,
                POOL_MAX_WAIT_MILLIS);
    }
}
