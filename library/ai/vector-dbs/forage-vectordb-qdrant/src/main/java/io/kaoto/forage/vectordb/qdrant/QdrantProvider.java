package io.kaoto.forage.vectordb.qdrant;

import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.kaoto.forage.core.ai.EmbeddingStoreProvider;
import io.kaoto.forage.core.annotations.ForageBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provider for creating Qdrant embedding stores within the Forage framework.
 *
 * <p>Qdrant is an open-source vector similarity search engine and vector database. It provides a
 * production-ready service with a convenient REST API to store, search and manage points (vectors)
 * with additional metadata payload. This provider creates configured {@link QdrantEmbeddingStore}
 * instances for high-performance vector operations.</p>
 *
 * <p>The provider automatically configures the Qdrant connection using properties from the
 * {@link QdrantConfig} which supports multiple configuration sources:</p>
 * <ul>
 *   <li>Environment variables (e.g., QDRANT_HOST, QDRANT_PORT, QDRANT_API_KEY)</li>
 *   <li>System properties (e.g., qdrant.host, qdrant.port, qdrant.api.key)</li>
 *   <li>Configuration files (forage-vectordb-qdrant.properties)</li>
 *   <li>Default values where applicable</li>
 * </ul>
 *
 * <p>Key configuration options include:</p>
 * <ul>
 *   <li><strong>Connection:</strong> host, port, TLS settings</li>
 *   <li><strong>Authentication:</strong> API key (optional for local deployments)</li>
 *   <li><strong>Collection:</strong> collection name for organizing vectors</li>
 *   <li><strong>Metadata:</strong> payload text key for storing original text</li>
 * </ul>
 *
 * <p>This provider supports both local Qdrant instances (typically on localhost:6333) and
 * cloud-hosted Qdrant deployments with API key authentication.</p>
 *
 * <p>This provider is automatically discovered via Java's ServiceLoader mechanism and can be used
 * with Apache Camel's LangChain4j components for AI-powered routes.</p>
 *
 * @see QdrantConfig
 * @see QdrantEmbeddingStore
 * @see EmbeddingStoreProvider
 *
 * @since 1.0
 */
@ForageBean(
        value = "qdrant",
        components = {"camel-langchain4j-embeddings"},
        description = "Qdrant vector database")
public class QdrantProvider implements EmbeddingStoreProvider {
    private static final Logger LOG = LoggerFactory.getLogger(QdrantProvider.class);

    /**
     * Creates a new {@link QdrantEmbeddingStore} instance configured for the specified ID.
     *
     * <p>The ID parameter allows for multiple Qdrant configurations within the same application.
     * Configuration properties can be scoped by ID using the pattern: {@code qdrant.<id>.<property>}</p>
     *
     * @param id the configuration identifier for this embedding store instance
     * @return a fully configured {@link QdrantEmbeddingStore} ready for use
     * @throws RuntimeException if required configuration is missing or invalid
     */
    @Override
    public QdrantEmbeddingStore create(String id) {
        LOG.trace("Creating qdrant embedding store");

        final QdrantConfig config = new QdrantConfig(id);

        return QdrantEmbeddingStore.builder()
                .collectionName(config.collectionName())
                .apiKey(config.apiKey())
                .host(config.host())
                .port(config.port())
                .useTls(config.useTls())
                .payloadTextKey(config.payloadTextKey())
                .build();
    }
}
