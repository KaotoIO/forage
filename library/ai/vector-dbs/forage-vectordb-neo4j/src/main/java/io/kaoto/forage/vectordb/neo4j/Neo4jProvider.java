package io.kaoto.forage.vectordb.neo4j;

import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.kaoto.forage.core.ai.EmbeddingStoreProvider;
import io.kaoto.forage.core.annotations.ForageBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provider for creating Neo4j embedding stores with configurable parameters.
 *
 * <p>This provider creates instances of {@link Neo4jEmbeddingStore} using configuration
 * values managed by {@link Neo4jConfig}. The configuration supports environment
 * variables, system properties, and configuration files for flexible deployment.
 *
 * <p><strong>Configuration:</strong>
 * <ul>
 *   <li>URI: Configured via NEO4J_URI environment variable or defaults to "bolt://localhost:7687"</li>
 *   <li>User: Configured via NEO4J_USER environment variable or defaults to "neo4j"</li>
 *   <li>Password: Required configuration via NEO4J_PASSWORD environment variable</li>
 *   <li>Database Name: Configured via NEO4J_DATABASE_NAME environment variable or defaults to "neo4j"</li>
 *   <li>Index Name: Configured via NEO4J_INDEX_NAME environment variable or defaults to "vector-index"</li>
 *   <li>Label: Configured via NEO4J_LABEL environment variable or defaults to "Document"</li>
 *   <li>Embedding Property: Configured via NEO4J_EMBEDDING_PROPERTY environment variable or defaults to "embedding"</li>
 *   <li>Text Property: Configured via NEO4J_TEXT_PROPERTY environment variable or defaults to "text"</li>
 *   <li>ID Property: Configured via NEO4J_ID_PROPERTY environment variable or defaults to "id"</li>
 *   <li>Metadata Prefix: Configured via NEO4J_METADATA_PREFIX environment variable or defaults to "metadata_"</li>
 *   <li>Dimension: Required configuration via NEO4J_DIMENSION environment variable</li>
 *   <li>With Encryption: Configured via NEO4J_WITH_ENCRYPTION environment variable or defaults to false</li>
 *   <li>Connection Timeout: Configured via NEO4J_CONNECTION_TIMEOUT environment variable or defaults to 30 seconds</li>
 *   <li>Max Connection Lifetime: Configured via NEO4J_MAX_CONNECTION_LIFETIME environment variable or defaults to 60 minutes</li>
 *   <li>Max Connection Pool Size: Configured via NEO4J_MAX_CONNECTION_POOL_SIZE environment variable or defaults to 100</li>
 *   <li>Connection Acquisition Timeout: Configured via NEO4J_CONNECTION_ACQUISITION_TIMEOUT environment variable or defaults to 60 seconds</li>
 *   <li>Await Index Timeout: Configured via NEO4J_AWAIT_INDEX_TIMEOUT environment variable or defaults to 60 seconds</li>
 * </ul>
 *
 * <p><strong>Usage:</strong>
 * <pre>{@code
 * // Configuration is automatic through environment variables or defaults
 * Neo4jProvider provider = new Neo4jProvider();
 * EmbeddingStore<TextSegment> store = provider.create();
 * }</pre>
 *
 * @see Neo4jConfig
 * @see EmbeddingStoreProvider
 * @since 1.0
 */
@ForageBean(
        value = "neo4j",
        components = {"camel-langchain4j-embeddings"},
        description = "Neo4j graph database with vector support")
public class Neo4jProvider implements EmbeddingStoreProvider {
    private static final Logger LOG = LoggerFactory.getLogger(Neo4jProvider.class);

    /**
     * Creates a new Neo4j embedding store instance with the configured parameters.
     *
     * <p>This method creates a {@link Neo4jEmbeddingStore} using the connection details
     * and configuration from the Neo4jConfig. The store is ready to use for vector
     * operations once created.
     *
     * @param id optional configuration prefix for named instances
     * @return a new configured Neo4j embedding store instance
     */
    @Override
    public EmbeddingStore<TextSegment> create(String id) {
        final Neo4jConfig config = new Neo4jConfig(id);

        String uri = config.uri();
        String user = config.user();
        String password = config.password();
        String databaseName = config.databaseName();
        String indexName = config.indexName();
        String label = config.label();
        String embeddingProperty = config.embeddingProperty();
        String textProperty = config.textProperty();
        String idProperty = config.idProperty();
        String metadataPrefix = config.metadataPrefix();
        int dimension = config.dimension();
        boolean withEncryption = config.withEncryption();

        LOG.trace(
                "Creating Neo4j embedding store: {} with configuration: user={}, database={}, index={}, label={}, embeddingProperty={}, textProperty={}, idProperty={}, metadataPrefix={}, dimension={}, withEncryption={}",
                uri,
                user,
                databaseName,
                indexName,
                label,
                embeddingProperty,
                textProperty,
                idProperty,
                metadataPrefix,
                dimension,
                withEncryption);

        Neo4jEmbeddingStore.Builder builder = Neo4jEmbeddingStore.builder()
                .databaseName(databaseName)
                .indexName(indexName)
                .label(label)
                .embeddingProperty(embeddingProperty)
                .textProperty(textProperty)
                .idProperty(idProperty)
                .metadataPrefix(metadataPrefix)
                .dimension(dimension);

        // use basic auth
        if ((uri != null && !uri.trim().isEmpty())
                && (user != null && !user.trim().isEmpty())
                && (password != null && !password.trim().isEmpty())) {
            builder.withBasicAuth(uri, user, password);
        }

        // Set optional custom queries if configured
        String retrievalQuery = config.retrievalQuery();
        if (retrievalQuery != null && !retrievalQuery.trim().isEmpty()) {
            builder.retrievalQuery(retrievalQuery);
        }

        String entityCreationQuery = config.entityCreationQuery();
        if (entityCreationQuery != null && !entityCreationQuery.trim().isEmpty()) {
            builder.entityCreationQuery(entityCreationQuery);
        }

        String fullTextIndexName = config.fullTextIndexName();
        if (fullTextIndexName != null && !fullTextIndexName.trim().isEmpty()) {
            builder.fullTextIndexName(fullTextIndexName);
        }

        String fullTextQuery = config.fullTextQuery();
        if (fullTextQuery != null && !fullTextQuery.trim().isEmpty()) {
            builder.fullTextQuery(fullTextQuery);
        }

        String fullTextRetrievalQuery = config.fullTextRetrievalQuery();
        if (fullTextRetrievalQuery != null && !fullTextRetrievalQuery.trim().isEmpty()) {
            builder.fullTextRetrievalQuery(fullTextRetrievalQuery);
        }

        builder.autoCreateFullText(config.autoCreateFullText());

        return builder.build();
    }
}
