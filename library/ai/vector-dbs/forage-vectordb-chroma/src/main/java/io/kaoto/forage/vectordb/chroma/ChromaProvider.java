package io.kaoto.forage.vectordb.chroma;

import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import io.kaoto.forage.core.ai.EmbeddingStoreProvider;
import io.kaoto.forage.core.annotations.ForageBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provider for creating Chroma embedding stores within the Forage framework.
 *
 * <p>Chroma is an open-source embedding database that provides a simple, scalable way to
 * build AI applications with embeddings. This provider creates configured {@link ChromaEmbeddingStore}
 * instances for high-performance vector operations and similarity search.</p>
 *
 * <p>The provider automatically configures the Chroma connection using properties from the
 * {@link ChromaConfig} which supports multiple configuration sources:</p>
 * <ul>
 *   <li>Environment variables (e.g., CHROMA_URL, CHROMA_COLLECTION_NAME, CHROMA_TIMEOUT)</li>
 *   <li>System properties (e.g., chroma.url, chroma.collection.name, chroma.timeout)</li>
 *   <li>Configuration files (forage-vectordb-chroma.properties)</li>
 * </ul>
 *
 * <p>Key configuration options include:</p>
 * <ul>
 *   <li><strong>Connection:</strong> URL for the Chroma server</li>
 *   <li><strong>Collection:</strong> collection name for organizing vectors</li>
 *   <li><strong>Timeout:</strong> request timeout duration</li>
 *   <li><strong>Logging:</strong> request and response logging options</li>
 * </ul>
 *
 * <p>This provider supports both local Chroma instances (typically on localhost:8000) and
 * remote Chroma deployments. Chroma typically runs as a standalone server that can be
 * accessed via HTTP API.</p>
 *
 * <p>This provider is automatically discovered via Java's ServiceLoader mechanism and can be used
 * with Apache Camel's LangChain4j components for AI-powered routes.</p>
 *
 * @see ChromaConfig
 * @see ChromaEmbeddingStore
 * @see EmbeddingStoreProvider
 *
 * @since 1.0
 */
@ForageBean(
        value = "chroma",
        components = {"camel-langchain4j-embeddings"},
        description = "Chroma embedding database")
public class ChromaProvider implements EmbeddingStoreProvider {
    private static final Logger LOG = LoggerFactory.getLogger(ChromaProvider.class);

    /**
     * Creates a new {@link ChromaEmbeddingStore} instance configured for the specified ID.
     *
     * <p>The ID parameter allows for multiple Chroma configurations within the same application.
     * Configuration properties can be scoped by ID using the pattern: {@code chroma.<id>.<property>}</p>
     *
     * @param id the configuration identifier for this embedding store instance
     * @return a fully configured {@link ChromaEmbeddingStore} ready for use
     * @throws RuntimeException if required configuration is missing or invalid
     */
    @Override
    public ChromaEmbeddingStore create(String id) {
        LOG.trace("Creating chroma embedding store");

        final ChromaConfig config = new ChromaConfig(id);

        return ChromaEmbeddingStore.builder()
                .baseUrl(config.url())
                .collectionName(config.collectionName())
                .timeout(config.timeout())
                .logRequests(config.logRequests())
                .logResponses(config.logResponses())
                .build();
    }
}
