package io.kaoto.forage.vectordb.pinecone;

import dev.langchain4j.store.embedding.pinecone.PineconeEmbeddingStore;
import io.kaoto.forage.core.ai.EmbeddingStoreProvider;
import io.kaoto.forage.core.annotations.ForageBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provider for creating Pinecone embedding stores within the Forage framework.
 *
 * <p>Pinecone is a fully managed vector database service designed for high-performance similarity search
 * at scale. This provider creates configured {@link PineconeEmbeddingStore} instances that connect to
 * your Pinecone index for storing and retrieving vector embeddings.</p>
 *
 * <p>The provider automatically configures the Pinecone connection using properties from the
 * {@link PineconeConfig} which supports multiple configuration sources:</p>
 * <ul>
 *   <li>Environment variables (e.g., PINECONE_API_KEY, PINECONE_INDEX)</li>
 *   <li>System properties (e.g., pinecone.api.key, pinecone.index)</li>
 *   <li>Configuration files (forage-vectordb-pinecone.properties)</li>
 * </ul>
 *
 * <p>Required configuration includes:</p>
 * <ul>
 *   <li><strong>api.key:</strong> Your Pinecone API key for authentication</li>
 *   <li><strong>index:</strong> The name of your Pinecone index</li>
 * </ul>
 *
 * <p>Optional configuration includes:</p>
 * <ul>
 *   <li><strong>name.space:</strong> Namespace for partitioning vectors within an index</li>
 *   <li><strong>metadata.text.key:</strong> Key name for storing original text in metadata</li>
 * </ul>
 *
 * <p>This provider is automatically discovered via Java's ServiceLoader mechanism and can be used
 * with Apache Camel's LangChain4j components for AI-powered routes.</p>
 *
 * @see PineconeConfig
 * @see PineconeEmbeddingStore
 * @see EmbeddingStoreProvider
 *
 * @since 1.0
 */
@ForageBean(
        value = "pinecone",
        components = {"camel-langchain4j-embeddings"},
        description = "Pinecone managed vector database service")
public class PineconeProvider implements EmbeddingStoreProvider {
    private static final Logger LOG = LoggerFactory.getLogger(PineconeProvider.class);

    /**
     * Creates a new {@link PineconeEmbeddingStore} instance configured for the specified ID.
     *
     * <p>The ID parameter allows for multiple Pinecone configurations within the same application.
     * Configuration properties can be scoped by ID using the pattern: {@code pinecone.<id>.<property>}</p>
     *
     * @param id the configuration identifier for this embedding store instance
     * @return a fully configured {@link PineconeEmbeddingStore} ready for use
     * @throws RuntimeException if required configuration (API key, index) is missing
     */
    @Override
    public PineconeEmbeddingStore create(String id) {
        LOG.trace("Creating pinecone embedding store");

        PineconeConfig config = new PineconeConfig(id);

        return PineconeEmbeddingStore.builder()
                .apiKey(config.apiKey())
                .index(config.index())
                .nameSpace(config.nameSpace())
                .createIndex(null)
                .metadataTextKey(config.metadataTextKey())
                .build();
    }
}
