package io.kaoto.forage.memory.chat.redis;

import java.time.Duration;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.core.ai.ChatMemoryBeanProvider;
import io.kaoto.forage.core.ai.MaxMessagesAware;
import io.kaoto.forage.core.annotations.ForageBean;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisException;

/**
 * Redis-based implementation of {@link ChatMemoryBeanProvider} that creates chat memory providers
 * with persistent storage using Redis as the backing store.
 *
 * <p>This factory creates {@link ChatMemoryProvider} instances that use Redis for storing
 * conversation history, enabling chat memory to persist across application restarts and
 * be shared across multiple application instances.
 *
 * <p><strong>Key Features:</strong>
 * <ul>
 *   <li>Persistent chat memory storage using Redis</li>
 *   <li>Configurable message window size for memory management</li>
 *   <li>Connection pooling for optimal Redis performance</li>
 *   <li>Automatic discovery via ServiceLoader mechanism</li>
 *   <li>Thread-safe memory provider creation</li>
 * </ul>
 *
 * <p><strong>Configuration:</strong>
 * The factory uses {@link RedisConfig} to obtain Redis connection parameters.
 * Configuration can be provided through environment variables, system properties,
 * or configuration files. See {@link RedisConfig} for detailed configuration options.
 *
 * <p><strong>Thread Safety:</strong>
 * This factory is thread-safe and can be safely used in concurrent environments.
 * Each call to {@link #create()} returns a provider that can handle multiple
 * concurrent memory operations.
 *
 * @see ChatMemoryBeanProvider
 * @see RedisConfig
 * @see PersistentRedisStore
 * @since 1.0
 */
@ForageBean(
        value = "redis",
        components = {"camel-langchain4j-agent"},
        feature = "Memory",
        description = "Persistent storage using Redis")
public class RedisMemoryBeanProvider implements ChatMemoryBeanProvider, MaxMessagesAware {
    private static final Logger LOG = LoggerFactory.getLogger(RedisMemoryBeanProvider.class);
    private static final int DEFAULT_MAX_MESSAGES = 100;
    private volatile Integer maxMessagesOverride;

    private final ReentrantLock initLock = new ReentrantLock();
    private volatile JedisPool defaultPool;
    private volatile PersistentRedisStore defaultStore;

    public RedisMemoryBeanProvider() {
        // Required for ServiceLoader-based instantiation
    }

    @Override
    public void withMaxMessages(int maxMessages) {
        this.maxMessagesOverride = maxMessages;
    }

    @Override
    public ChatMemoryProvider create() {
        return create(null);
    }

    @Override
    public ChatMemoryProvider create(String id) {
        PersistentRedisStore store = getOrCreateStore(id);
        int maxMessages = maxMessagesOverride != null ? maxMessagesOverride : DEFAULT_MAX_MESSAGES;
        return memoryId -> {
            LOG.debug("Creating message window chat memory for ID: {} with maxMessages={}", memoryId, maxMessages);
            return MessageWindowChatMemory.builder()
                    .id(memoryId)
                    .maxMessages(maxMessages)
                    .chatMemoryStore(store)
                    .build();
        };
    }

    private PersistentRedisStore getOrCreateStore(String id) {
        if (defaultStore == null) {
            initLock.lock();
            try {
                if (defaultStore == null) {
                    RedisConfig config = new RedisConfig();
                    defaultPool = createPool(config);
                    defaultStore = new PersistentRedisStore(defaultPool);
                }
            } finally {
                initLock.unlock();
            }
        }
        return defaultStore;
    }

    private JedisPool createPool(RedisConfig config) {
        LOG.info(
                "Initializing Redis chat memory provider with host: {}, port: {}, database: {}",
                config.host(),
                config.port(),
                config.database());

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(config.poolMaxTotal());
        poolConfig.setMaxIdle(config.poolMaxIdle());
        poolConfig.setMinIdle(config.poolMinIdle());
        poolConfig.setTestOnBorrow(config.poolTestOnBorrow());
        poolConfig.setTestOnReturn(config.poolTestOnReturn());
        poolConfig.setTestWhileIdle(config.poolTestWhileIdle());
        poolConfig.setMaxWait(Duration.ofMillis(config.poolMaxWaitMillis()));

        JedisPool pool = new JedisPool(
                poolConfig, config.host(), config.port(), config.timeout(), config.password(), config.database());

        try (var jedis = pool.getResource()) {
            jedis.ping();
            LOG.info("Successfully connected to Redis at {}:{}/{}", config.host(), config.port(), config.database());
        } catch (JedisException e) {
            pool.close();
            LOG.error("Failed to initialize Redis connection pool for chat memory", e);
            throw new RuntimeException("Failed to connect to Redis for chat memory storage", e);
        }

        return pool;
    }

    public void close() {
        if (defaultPool != null && !defaultPool.isClosed()) {
            LOG.info("Closing Redis connection pool for chat memory");
            defaultPool.close();
        }
    }
}
