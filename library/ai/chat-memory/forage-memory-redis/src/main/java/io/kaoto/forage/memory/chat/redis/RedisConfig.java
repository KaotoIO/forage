package io.kaoto.forage.memory.chat.redis;

import io.kaoto.forage.core.util.config.AbstractConfig;

import static io.kaoto.forage.memory.chat.redis.RedisConfigEntries.DATABASE;
import static io.kaoto.forage.memory.chat.redis.RedisConfigEntries.HOST;
import static io.kaoto.forage.memory.chat.redis.RedisConfigEntries.PASSWORD;
import static io.kaoto.forage.memory.chat.redis.RedisConfigEntries.POOL_MAX_IDLE;
import static io.kaoto.forage.memory.chat.redis.RedisConfigEntries.POOL_MAX_TOTAL;
import static io.kaoto.forage.memory.chat.redis.RedisConfigEntries.POOL_MAX_WAIT_MILLIS;
import static io.kaoto.forage.memory.chat.redis.RedisConfigEntries.POOL_MIN_IDLE;
import static io.kaoto.forage.memory.chat.redis.RedisConfigEntries.POOL_TEST_ON_BORROW;
import static io.kaoto.forage.memory.chat.redis.RedisConfigEntries.POOL_TEST_ON_RETURN;
import static io.kaoto.forage.memory.chat.redis.RedisConfigEntries.POOL_TEST_WHILE_IDLE;
import static io.kaoto.forage.memory.chat.redis.RedisConfigEntries.PORT;
import static io.kaoto.forage.memory.chat.redis.RedisConfigEntries.TIMEOUT;

/**
 * Configuration class for Redis-based chat memory storage in the Forage framework.
 *
 * <p>This configuration manages connection parameters for Redis instances used to store
 * persistent chat conversation history. It follows the standard Forage configuration
 * pattern with support for multiple configuration sources and environment-specific overrides.
 *
 * <p><strong>Configuration Properties:</strong>
 * <ul>
 *   <li><code>redis.host</code> - Redis server hostname (default: localhost) - required</li>
 *   <li><code>redis.port</code> - Redis server port (default: 6379) - required</li>
 *   <li><code>redis.password</code> - Redis authentication password (optional)</li>
 *   <li><code>redis.database</code> - Redis database number (default: 0)</li>
 *   <li><code>redis.timeout</code> - Connection timeout in milliseconds (default: 2000)</li>
 *   <li><code>redis.pool.max-total</code> - Maximum number of connections in the pool (default: 10)</li>
 *   <li><code>redis.pool.max-idle</code> - Maximum number of idle connections (default: 5)</li>
 *   <li><code>redis.pool.min-idle</code> - Minimum number of idle connections (default: 1)</li>
 *   <li><code>redis.pool.test-on-borrow</code> - Test connections when borrowing from pool (default: true)</li>
 *   <li><code>redis.pool.test-on-return</code> - Test connections when returning to pool (default: true)</li>
 *   <li><code>redis.pool.test-while-idle</code> - Test idle connections periodically (default: true)</li>
 *   <li><code>redis.pool.max-wait-millis</code> - Maximum time to wait for a connection (default: 2000)</li>
 * </ul>
 *
 * <p><strong>Configuration Sources (in order of precedence):</strong>
 * <ol>
 *   <li>Environment variables: {@code REDIS_HOST}, {@code REDIS_PORT}, {@code REDIS_PASSWORD}, {@code REDIS_POOL_MAX_TOTAL}, etc.</li>
 *   <li>System properties: {@code redis.host}, {@code redis.port}, {@code redis.password}, {@code redis.pool.max-total}, etc.</li>
 *   <li>Configuration file: {@code forage-memory-redis.properties}</li>
 *   <li>Default values where applicable</li>
 * </ol>
 *
 * <p><strong>Example Environment Configuration:</strong>
 * <pre>{@code
 * export REDIS_HOST=redis.example.com
 * export REDIS_PORT=6379
 * export REDIS_PASSWORD=secret123
 * export REDIS_DATABASE=1
 * export REDIS_POOL_MAX_TOTAL=20
 * export REDIS_POOL_MAX_IDLE=10
 * }</pre>
 *
 * <p><strong>Example System Properties:</strong>
 * <pre>{@code
 * -Dredis.host=redis.example.com
 * -Dredis.port=6379
 * -Dredis.password=secret123
 * -Dredis.database=1
 * -Dredis.pool.max-total=20
 * -Dredis.pool.max-idle=10
 * }</pre>
 *
 * <p><strong>Example Properties File (forage-memory-redis.properties):</strong>
 * <pre>
 * redis.host=redis.example.com
 * redis.port=6379
 * redis.password=secret123
 * redis.database=1
 * redis.timeout=5000
 * redis.pool.max-total=20
 * redis.pool.max-idle=10
 * redis.pool.min-idle=2
 * redis.pool.test-on-borrow=true
 * redis.pool.max-wait-millis=3000
 * </pre>
 *
 * @see io.kaoto.forage.core.util.config.Config
 * @see io.kaoto.forage.core.util.config.ConfigStore
 * @see PersistentRedisStore
 * @since 1.0
 */
public class RedisConfig extends AbstractConfig {

    private static final String BOOLEAN_FALSE = "false";
    private static final String BOOLEAN_TRUE = "true";

    /**
     * Creates a new Redis configuration instance and registers configuration entries
     * with the central configuration store.
     *
     * <p>This constructor automatically registers all Redis configuration parameters
     * and their corresponding environment variable mappings. It also sets up the
     * configuration loader to process property files if they exist.
     */
    public RedisConfig() {
        this(null);
    }

    public RedisConfig(String prefix) {
        super(prefix, RedisConfigEntries.class);
    }

    /**
     * Returns the Redis server hostname.
     *
     * @return the Redis server hostname, defaults to "localhost" if not configured
     */
    public String host() {
        return get(HOST).orElse(HOST.defaultValue());
    }

    /**
     * Returns the Redis server port number.
     *
     * @return the Redis server port, defaults to 6379 (standard Redis port) if not configured
     * @throws NumberFormatException if the configured port value is not a valid integer
     */
    public int port() {
        return get(PORT)
                .map(value -> {
                    try {
                        return Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid Redis port number: " + value, e);
                    }
                })
                .orElse(Integer.parseInt(PORT.defaultValue()));
    }

    /**
     * Returns the Redis authentication password.
     *
     * @return the Redis password, or {@code null} if no authentication is required
     */
    public String password() {
        return get(PASSWORD).orElse(PASSWORD.defaultValue());
    }

    /**
     * Returns the Redis database number to connect to.
     *
     * @return the Redis database number, defaults to 0 if not configured
     * @throws NumberFormatException if the configured database value is not a valid integer
     */
    public int database() {
        return get(DATABASE)
                .map(value -> {
                    try {
                        return Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid Redis database number: " + value, e);
                    }
                })
                .orElse(Integer.parseInt(DATABASE.defaultValue()));
    }

    /**
     * Returns the Redis connection timeout in milliseconds.
     *
     * @return the connection timeout in milliseconds, defaults to 2000ms if not configured
     * @throws NumberFormatException if the configured timeout value is not a valid integer
     */
    public int timeout() {
        return get(TIMEOUT)
                .map(value -> {
                    try {
                        return Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid Redis timeout value: " + value, e);
                    }
                })
                .orElse(Integer.parseInt(TIMEOUT.defaultValue()));
    }

    /**
     * Returns the maximum number of connections in the Redis connection pool.
     *
     * @return the maximum total connections, defaults to 10 if not configured
     * @throws IllegalArgumentException if the configured value is not a valid integer
     */
    public int poolMaxTotal() {
        return get(POOL_MAX_TOTAL)
                .map(value -> {
                    try {
                        return Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid Redis pool max-total value: " + value, e);
                    }
                })
                .orElse(Integer.parseInt(POOL_MAX_TOTAL.defaultValue()));
    }

    /**
     * Returns the maximum number of idle connections in the Redis connection pool.
     *
     * @return the maximum idle connections, defaults to 5 if not configured
     * @throws IllegalArgumentException if the configured value is not a valid integer
     */
    public int poolMaxIdle() {
        return get(POOL_MAX_IDLE)
                .map(value -> {
                    try {
                        return Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid Redis pool max-idle value: " + value, e);
                    }
                })
                .orElse(Integer.parseInt(POOL_MAX_IDLE.defaultValue()));
    }

    /**
     * Returns the minimum number of idle connections in the Redis connection pool.
     *
     * @return the minimum idle connections, defaults to 1 if not configured
     * @throws IllegalArgumentException if the configured value is not a valid integer
     */
    public int poolMinIdle() {
        return get(POOL_MIN_IDLE)
                .map(value -> {
                    try {
                        return Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid Redis pool min-idle value: " + value, e);
                    }
                })
                .orElse(Integer.parseInt(POOL_MIN_IDLE.defaultValue()));
    }

    /**
     * Returns whether to test connections when borrowing from the pool.
     *
     * @return true if connections should be tested on borrow, defaults to true if not configured
     * @throws IllegalArgumentException if the configured value is not a valid boolean
     */
    public boolean poolTestOnBorrow() {
        return get(POOL_TEST_ON_BORROW)
                .map(value -> {
                    if (BOOLEAN_TRUE.equalsIgnoreCase(value) || BOOLEAN_FALSE.equalsIgnoreCase(value)) {
                        return Boolean.parseBoolean(value);
                    }
                    throw new IllegalArgumentException(
                            "Invalid Redis pool test-on-borrow value: " + value + " (must be true or false)");
                })
                .orElse(Boolean.parseBoolean(POOL_TEST_ON_BORROW.defaultValue()));
    }

    /**
     * Returns whether to test connections when returning to the pool.
     *
     * @return true if connections should be tested on return, defaults to true if not configured
     * @throws IllegalArgumentException if the configured value is not a valid boolean
     */
    public boolean poolTestOnReturn() {
        return get(POOL_TEST_ON_RETURN)
                .map(value -> {
                    if (BOOLEAN_TRUE.equalsIgnoreCase(value) || BOOLEAN_FALSE.equalsIgnoreCase(value)) {
                        return Boolean.parseBoolean(value);
                    }
                    throw new IllegalArgumentException(
                            "Invalid Redis pool test-on-return value: " + value + " (must be true or false)");
                })
                .orElse(Boolean.parseBoolean(POOL_TEST_ON_RETURN.defaultValue()));
    }

    /**
     * Returns whether to test idle connections periodically.
     *
     * @return true if idle connections should be tested, defaults to true if not configured
     * @throws IllegalArgumentException if the configured value is not a valid boolean
     */
    public boolean poolTestWhileIdle() {
        return get(POOL_TEST_WHILE_IDLE)
                .map(value -> {
                    if (BOOLEAN_TRUE.equalsIgnoreCase(value) || BOOLEAN_FALSE.equalsIgnoreCase(value)) {
                        return Boolean.parseBoolean(value);
                    }
                    throw new IllegalArgumentException(
                            "Invalid Redis pool test-while-idle value: " + value + " (must be true or false)");
                })
                .orElse(Boolean.parseBoolean(POOL_TEST_WHILE_IDLE.defaultValue()));
    }

    /**
     * Returns the maximum time to wait for a connection from the pool in milliseconds.
     *
     * @return the maximum wait time in milliseconds, defaults to 2000ms if not configured
     * @throws IllegalArgumentException if the configured value is not a valid integer
     */
    public int poolMaxWaitMillis() {
        return get(POOL_MAX_WAIT_MILLIS)
                .map(value -> {
                    try {
                        return Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid Redis pool max-wait-millis value: " + value, e);
                    }
                })
                .orElse(Integer.parseInt(POOL_MAX_WAIT_MILLIS.defaultValue()));
    }

    /**
     * Returns the unique name identifier for this Redis memory configuration module.
     *
     * <p>This name is used to identify the module and corresponds to the expected
     * properties file name ({@code forage-memory-redis.properties}).
     *
     * @return the module name "forage-memory-redis"
     */
    @Override
    public String name() {
        return "forage-memory-redis";
    }
}
