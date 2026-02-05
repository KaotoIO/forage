package io.kaoto.forage.vectordb.pgvector;

import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import io.kaoto.forage.core.ai.EmbeddingStoreProvider;
import io.kaoto.forage.core.annotations.ForageBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provider for creating pgvector embedding stores within the Forage framework.
 *
 * <p>pgvector is a PostgreSQL extension that provides vector similarity search capabilities.
 * It allows storing and querying high-dimensional vectors directly in PostgreSQL, leveraging
 * the database's ACID properties, mature ecosystem, and existing infrastructure. This provider
 * creates configured {@link PgVectorEmbeddingStore} instances for PostgreSQL-based vector operations.</p>
 *
 * <p>The provider automatically configures the PostgreSQL connection using properties from the
 * {@link PgVectorConfig} which supports multiple configuration sources:</p>
 * <ul>
 *   <li>Environment variables (e.g., PGVECTOR_HOST, PGVECTOR_PORT, PGVECTOR_DATABASE)</li>
 *   <li>System properties (e.g., pgvector.host, pgvector.port, pgvector.database)</li>
 *   <li>Configuration files (forage-vectordb-pgvector.properties)</li>
 *   <li>Default values where applicable</li>
 * </ul>
 *
 * <p>Key configuration options include:</p>
 * <ul>
 *   <li><strong>Connection:</strong> host, port, database, user, password</li>
 *   <li><strong>Schema:</strong> table name, vector dimension</li>
 *   <li><strong>Performance:</strong> index usage, index list size for IVFFlat</li>
 *   <li><strong>Management:</strong> table creation/dropping, metadata storage configuration</li>
 * </ul>
 *
 * <p>The provider automatically handles PostgreSQL-specific vector operations including:</p>
 * <ul>
 *   <li>Vector index creation (IVFFlat) for improved search performance</li>
 *   <li>Table schema management with proper vector column types</li>
 *   <li>Metadata storage in JSON columns</li>
 *   <li>Distance calculations using PostgreSQL's vector operators</li>
 * </ul>
 *
 * <p>This provider is automatically discovered via Java's ServiceLoader mechanism and can be used
 * with Apache Camel's LangChain4j components for AI-powered routes.</p>
 *
 * @see PgVectorConfig
 * @see PgVectorEmbeddingStore
 * @see EmbeddingStoreProvider
 *
 * @since 1.0
 */
@ForageBean(
        value = "pgvector",
        components = {"camel-langchain4j-embeddings"},
        description = "PostgreSQL with pgvector extension")
public class PgVectorProvider implements EmbeddingStoreProvider {
    private static final Logger LOG = LoggerFactory.getLogger(PgVectorProvider.class);

    /**
     * Creates a new {@link PgVectorEmbeddingStore} instance configured for the specified ID.
     *
     * <p>The ID parameter allows for multiple PostgreSQL/pgvector configurations within the same application.
     * Configuration properties can be scoped by ID using the pattern: {@code pgvector.<id>.<property>}</p>
     *
     * <p>The provider will automatically create the necessary table schema and indexes if configured
     * to do so via the {@code createTable} and {@code useIndex} properties.</p>
     *
     * @param id the configuration identifier for this embedding store instance
     * @return a fully configured {@link PgVectorEmbeddingStore} ready for use
     * @throws RuntimeException if required configuration is missing or database connection fails
     */
    @Override
    public PgVectorEmbeddingStore create(String id) {
        LOG.trace("Creating PgVector Embedding Store");

        PgVectorConfig config = new PgVectorConfig(id);

        return PgVectorEmbeddingStore.builder()
                .host(config.host())
                .port(config.port())
                .user(config.user())
                .password(config.password())
                .database(config.database())
                .table(config.table())
                .dimension(config.dimension())
                .useIndex(config.useIndex())
                .indexListSize(config.indexListSize())
                .createTable(config.createTable())
                .dropTableFirst(config.dropTableFirst())
                .metadataStorageConfig(config.metadataStorageConfig())
                .build();
    }
}
