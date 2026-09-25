package io.kaoto.forage.memory.chat.infinispan;

import java.util.concurrent.locks.ReentrantLock;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.core.ai.ChatMemoryBeanProvider;
import io.kaoto.forage.core.ai.MaxMessagesAware;
import io.kaoto.forage.core.annotations.ForageBean;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

/**
 * Infinispan-based implementation of {@link ChatMemoryBeanProvider} that creates chat memory providers
 * with persistent storage using Infinispan as the backing store.
 *
 * <p>This factory creates {@link ChatMemoryProvider} instances that use Infinispan for storing
 * conversation history, enabling chat memory to persist across application restarts and
 * be shared across multiple application instances in a distributed environment.
 *
 * <p><strong>Key Features:</strong>
 * <ul>
 *   <li>Persistent chat memory storage using Infinispan</li>
 *   <li>Configurable message window size for memory management</li>
 *   <li>Distributed caching for scalability and high availability</li>
 *   <li>Automatic discovery via ServiceLoader mechanism</li>
 *   <li>Thread-safe memory provider creation</li>
 * </ul>
 *
 * <p><strong>Configuration:</strong>
 * The factory uses {@link InfinispanConfig} to obtain Infinispan connection parameters.
 * Configuration can be provided through environment variables, system properties,
 * or configuration files. See {@link InfinispanConfig} for detailed configuration options.
 *
 * <p><strong>Thread Safety:</strong>
 * This factory is thread-safe and can be safely used in concurrent environments.
 * Each call to {@link #create()} ()} returns a provider that can handle multiple
 * concurrent memory operations.
 *
 * @see ChatMemoryBeanProvider
 * @see InfinispanConfig
 * @see PersistentInfinispanStore
 * @since 1.0
 */
@ForageBean(
        value = "infinispan",
        components = {"camel-langchain4j-agent"},
        feature = "Memory",
        description = "Distributed storage using Infinispan")
public class InfinispanMemoryBeanProvider implements ChatMemoryBeanProvider, MaxMessagesAware {
    private static final Logger LOG = LoggerFactory.getLogger(InfinispanMemoryBeanProvider.class);
    private static final int DEFAULT_MAX_MESSAGES = 10;
    private volatile Integer maxMessagesOverride;

    private final ReentrantLock initLock = new ReentrantLock();
    private volatile RemoteCacheManager defaultCacheManager;
    private volatile PersistentInfinispanStore defaultStore;

    public InfinispanMemoryBeanProvider() {
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
        PersistentInfinispanStore store = getOrCreateStore(id);
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

    private PersistentInfinispanStore getOrCreateStore(String id) {
        if (defaultStore == null) {
            initLock.lock();
            try {
                if (defaultStore == null) {
                    InfinispanConfig config = new InfinispanConfig();
                    defaultCacheManager = createCacheManager(config);
                    RemoteCache<String, String> cache = getOrCreateCache(defaultCacheManager, config.cacheName());
                    defaultStore = new PersistentInfinispanStore(cache);
                }
            } finally {
                initLock.unlock();
            }
        }
        return defaultStore;
    }

    private RemoteCacheManager createCacheManager(InfinispanConfig config) {
        LOG.info(
                "Initializing Infinispan chat memory provider with servers: {}, cache: {}",
                config.serverList(),
                config.cacheName());

        final ConfigurationBuilder builder = config.toConfigurationBuilder();
        RemoteCacheManager cacheManager = new RemoteCacheManager(builder.build());
        try {
            cacheManager.start();
            LOG.info("Successfully connected to Infinispan cluster at {}", config.serverList());
            return cacheManager;
        } catch (Exception e) {
            cacheManager.close();
            LOG.error("Failed to initialize Infinispan connection for chat memory", e);
            throw new RuntimeException("Failed to connect to Infinispan for chat memory storage", e);
        }
    }

    private RemoteCache<String, String> getOrCreateCache(RemoteCacheManager cacheManager, String cacheName) {
        try {
            RemoteCache<String, String> cache = cacheManager.getCache(cacheName);
            if (cache == null) {
                LOG.info("Cache '{}' not found, creating it with default template", cacheName);
                cacheManager.administration().createCache(cacheName, (String) null);
                cache = cacheManager.getCache(cacheName);
            }
            cache.size();
            return cache;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to get or create named cache %s".formatted(cacheName), e);
        }
    }

    public void close() {
        if (defaultCacheManager != null) {
            LOG.info("Closing Infinispan cache manager for chat memory");
            defaultCacheManager.close();
        }
    }
}
