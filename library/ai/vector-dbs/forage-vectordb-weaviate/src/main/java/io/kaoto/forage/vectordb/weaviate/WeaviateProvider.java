package io.kaoto.forage.vectordb.weaviate;

import dev.langchain4j.store.embedding.weaviate.WeaviateEmbeddingStore;
import io.kaoto.forage.core.ai.EmbeddingStoreProvider;
import io.kaoto.forage.core.annotations.ForageBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provider for creating Weaviate embedding stores within the Camel Forage framework.
 *
 * <p>Weaviate is an open-source vector database that stores both objects and vectors, allowing for
 * combining vector search with structured filtering. It features out-of-the-box support for various
 * ML models and enables semantic search, classification, and contextual recommendation systems.
 * This provider creates configured {@link WeaviateEmbeddingStore} instances for advanced vector operations.</p>
 *
 * <p>The provider automatically configures the Weaviate connection using properties from the
 * {@link WeaviateConfig} which supports multiple configuration sources:</p>
 * <ul>
 *   <li>Environment variables (e.g., WEAVIATE_API_KEY, WEAVIATE_HOST)</li>
 *   <li>System properties (e.g., weaviate.api.key, weaviate.host)</li>
 *   <li>Configuration files (forage-vectordb-weaviate.properties)</li>
 *   <li>Default values where applicable</li>
 * </ul>
 *
 * <p>Key configuration options include:</p>
 * <ul>
 *   <li><strong>Connection:</strong> scheme, host, port, API key</li>
 *   <li><strong>Performance:</strong> gRPC usage for inserts, secured gRPC settings</li>
 *   <li><strong>Schema:</strong> object class, field names for text and metadata</li>
 *   <li><strong>Behavior:</strong> duplicate avoidance, consistency level</li>
 *   <li><strong>Metadata:</strong> configurable metadata keys and field mappings</li>
 * </ul>
 *
 * <p>This provider supports both local Weaviate instances and cloud-hosted Weaviate deployments,
 * with optimizations for both REST API and gRPC protocols.</p>
 *
 * <p>This provider is automatically discovered via Java's ServiceLoader mechanism and can be used
 * with Apache Camel's LangChain4j components for AI-powered routes.</p>
 *
 * @see WeaviateConfig
 * @see WeaviateEmbeddingStore
 * @see EmbeddingStoreProvider
 *
 * @since 1.0
 */
@ForageBean(
        value = "weaviate",
        components = {"camel-langchain4j-embeddings"},
        description = "Weaviate vector database")
public class WeaviateProvider implements EmbeddingStoreProvider {
    private static final Logger LOG = LoggerFactory.getLogger(WeaviateProvider.class);

    /**
     * Creates a new {@link WeaviateEmbeddingStore} instance configured for the specified ID.
     *
     * <p>The ID parameter allows for multiple Weaviate configurations within the same application.
     * Configuration properties can be scoped by ID using the pattern: {@code weaviate.<id>.<property>}</p>
     *
     * @param id the configuration identifier for this embedding store instance
     * @return a fully configured {@link WeaviateEmbeddingStore} ready for use
     * @throws RuntimeException if required configuration is missing or invalid
     */
    @Override
    public WeaviateEmbeddingStore create(String id) {
        LOG.trace("Creating weaviate embedding store");

        WeaviateConfig config = new WeaviateConfig(id);

        return WeaviateEmbeddingStore.builder()
                .apiKey(config.apiKey())
                .scheme(config.scheme())
                .host(config.host())
                .port(config.port())
                .useGrpcForInserts(config.useGrpcForInserts())
                .securedGrpc(config.securedGrpc())
                .grpcPort(config.grpcPort())
                .objectClass(config.objectClass())
                .avoidDups(config.avoidDups())
                .consistencyLevel(config.consistencyLevel())
                .metadataKeys(config.metadataKeys())
                .textFieldName(config.textFieldName())
                .metadataFieldName(config.metadataFieldName())
                .build();
    }
}
