package io.kaoto.forage.vectordb.milvus;

import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.kaoto.forage.core.ai.EmbeddingStoreProvider;
import io.kaoto.forage.core.annotations.ForageBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provider for creating Milvus embedding stores within the Camel Forage framework.
 *
 * <p>Milvus is an open-source vector database built for scalable similarity search and AI applications.
 * This provider creates configured {@link MilvusEmbeddingStore} instances that can be used for storing
 * and retrieving high-dimensional vector embeddings.</p>
 *
 * <p>The provider automatically configures the Milvus connection using properties from the
 * {@link MilvusConfig} which supports multiple configuration sources:</p>
 * <ul>
 *   <li>Environment variables (e.g., MILVUS_HOST, MILVUS_PORT)</li>
 *   <li>System properties (e.g., milvus.host, milvus.port)</li>
 *   <li>Configuration files (forage-vectordb-milvus.properties)</li>
 *   <li>Default values where applicable</li>
 * </ul>
 *
 * <p>Key configuration options include:</p>
 * <ul>
 *   <li><strong>Connection:</strong> host, port, URI, authentication (token, username/password)</li>
 *   <li><strong>Collection:</strong> collection name, dimension, index type, metric type</li>
 *   <li><strong>Performance:</strong> consistency level, auto-flush behavior</li>
 *   <li><strong>Search:</strong> whether to retrieve embeddings on search operations</li>
 * </ul>
 *
 * <p>This provider is automatically discovered via Java's ServiceLoader mechanism and can be used
 * with Apache Camel's LangChain4j components for AI-powered routes.</p>
 *
 * @see MilvusConfig
 * @see MilvusEmbeddingStore
 * @see EmbeddingStoreProvider
 *
 * @since 1.0
 */
@ForageBean(
        value = "milvus",
        components = {"camel-langchain4j-embeddings"},
        description = "Milvus vector database")
public class MilvusProvider implements EmbeddingStoreProvider {
    private static final Logger LOG = LoggerFactory.getLogger(MilvusProvider.class);

    /**
     * Creates a new {@link MilvusEmbeddingStore} instance configured for the specified ID.
     *
     * <p>The ID parameter allows for multiple Milvus configurations within the same application.
     * Configuration properties can be scoped by ID using the pattern: {@code milvus.<id>.<property>}</p>
     *
     * @param id the configuration identifier for this embedding store instance
     * @return a fully configured {@link MilvusEmbeddingStore} ready for use
     * @throws RuntimeException if required configuration is missing or invalid
     */
    @Override
    public MilvusEmbeddingStore create(String id) {
        MilvusConfig config = new MilvusConfig(id);

        LOG.trace("Creating Milvus Embedding Store");

        return MilvusEmbeddingStore.builder()
                .host(config.host())
                .port(config.port())
                .collectionName(config.collectionName())
                .dimension(config.dimension())
                .indexType(config.indexType())
                .metricType(config.metricType())
                .uri(config.uri())
                .token(config.token())
                .username(config.username())
                .password(config.password())
                .consistencyLevel(config.consistencyLevel())
                .retrieveEmbeddingsOnSearch(config.retrieveEmbeddingsOnSearch())
                .autoFlushOnInsert(config.autoFlushOnInsert())
                .build();
    }
}
