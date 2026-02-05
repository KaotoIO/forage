package io.kaoto.forage.vectordb.infinispan;

import dev.langchain4j.store.embedding.infinispan.InfinispanEmbeddingStore;
import io.kaoto.forage.core.ai.EmbeddingStoreProvider;
import io.kaoto.forage.core.annotations.ForageBean;
import org.infinispan.client.hotrod.configuration.ClientIntelligence;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provider for creating Infinispan embedding stores within the Camel Forage framework.
 *
 * <p>Infinispan is a distributed in-memory key/value data store that can be used as an
 * embedding database for vector similarity search. This provider creates configured
 * {@link InfinispanEmbeddingStore} instances for high-performance vector operations
 * with distributed caching capabilities.</p>
 *
 * <p>The provider automatically configures the Infinispan connection using properties from the
 * {@link InfinispanConfig} which supports multiple configuration sources:</p>
 * <ul>
 *   <li>Environment variables (e.g., INFINISPAN_CACHE_NAME, INFINISPAN_DIMENSION, INFINISPAN_DISTANCE)</li>
 *   <li>System properties (e.g., infinispan.cache.name, infinispan.dimension, infinispan.distance)</li>
 *   <li>Configuration files (forage-vectordb-infinispan.properties)</li>
 *   <li>Default values where applicable</li>
 * </ul>
 *
 * <p>Key configuration options include:</p>
 * <ul>
 *   <li><strong>Connection:</strong> host, port, username, password for server connection</li>
 *   <li><strong>Cache:</strong> cache name and creation settings</li>
 *   <li><strong>Vector Settings:</strong> dimension, distance metric, similarity algorithm</li>
 *   <li><strong>Schema:</strong> package name, file name, class names for generated schemas</li>
 *   <li><strong>Behavior:</strong> automatic schema registration and cache creation</li>
 * </ul>
 *
 * <p>This provider supports both embedded and remote Infinispan deployments. For distributed
 * setups, Infinispan provides excellent scalability and fault tolerance for vector storage.</p>
 *
 * <p>This provider is automatically discovered via Java's ServiceLoader mechanism and can be used
 * with Apache Camel's LangChain4j components for AI-powered routes.</p>
 *
 * @see InfinispanConfig
 * @see InfinispanEmbeddingStore
 * @see EmbeddingStoreProvider
 *
 * @since 1.0
 */
@ForageBean(
        value = "infinispan",
        components = {"camel-langchain4j-embeddings"},
        description = "Infinispan distributed data store")
public class InfinispanProvider implements EmbeddingStoreProvider {
    private static final Logger LOG = LoggerFactory.getLogger(InfinispanProvider.class);

    /**
     * Creates a new {@link InfinispanEmbeddingStore} instance configured for the specified ID.
     *
     * <p>The ID parameter allows for multiple Infinispan configurations within the same application.
     * Configuration properties can be scoped by ID using the pattern: {@code infinispan.<id>.<property>}</p>
     *
     * @param id the configuration identifier for this embedding store instance
     * @return a fully configured {@link InfinispanEmbeddingStore} ready for use
     * @throws RuntimeException if required configuration is missing or invalid
     */
    @Override
    public InfinispanEmbeddingStore create(String id) {
        LOG.trace("Creating infinispan embedding store");

        final InfinispanConfig config = new InfinispanConfig(id);

        String host = config.host();
        Integer port = config.port();
        String username = config.username();
        String password = config.password();

        LOG.trace(
                "Creating Infinispan embedding store: cache={} at {}:{} with configuration: dimension={}, distance={}, similarity={}, packageName={}, fileName={}, registerSchema={}, createCache={}, auth={}",
                config.cacheName(),
                host,
                port,
                config.dimension(),
                config.distance(),
                config.similarity(),
                config.packageName(),
                config.fileName(),
                config.registerSchema(),
                config.createCache(),
                username != null ? "enabled" : "disabled");

        ConfigurationBuilder builder = new ConfigurationBuilder();
        builder.clientIntelligence(ClientIntelligence.BASIC)
                .addServer()
                .host(host)
                .port(port);

        // Only set authentication if both username and password are provided
        if (username != null && password != null) {
            builder.security().authentication().username(username).password(password);
        }

        return InfinispanEmbeddingStore.builder()
                .infinispanConfigBuilder(builder)
                .cacheName(config.cacheName())
                .dimension(config.dimension())
                .distance(config.distance())
                .similarity(config.similarity())
                .packageName(config.packageName())
                .fileName(config.fileName())
                .langchainItemName(config.langchainItemName())
                .metadataItemName(config.metadataItemName())
                .registerSchema(config.registerSchema())
                .createCache(config.createCache())
                .build();
    }
}
