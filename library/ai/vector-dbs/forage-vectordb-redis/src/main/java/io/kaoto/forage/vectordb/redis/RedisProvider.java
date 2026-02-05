package io.kaoto.forage.vectordb.redis;

import dev.langchain4j.community.store.embedding.redis.MetricType;
import dev.langchain4j.community.store.embedding.redis.RedisEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.kaoto.forage.core.ai.EmbeddingStoreProvider;
import io.kaoto.forage.core.annotations.ForageBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provider for creating Redis embedding stores with configurable parameters.
 *
 * <p>This provider creates instances of {@link RedisEmbeddingStore} using configuration
 * values managed by {@link RedisConfig}. The configuration supports environment
 * variables, system properties, and configuration files for flexible deployment.
 *
 * <p><strong>Configuration:</strong>
 * <ul>
 *   <li>Host: Configured via REDIS_HOST environment variable or defaults to "localhost"</li>
 *   <li>Port: Configured via REDIS_PORT environment variable or defaults to 6379</li>
 *   <li>User: Optionally configured via REDIS_USER environment variable</li>
 *   <li>Password: Optionally configured via REDIS_PASSWORD environment variable</li>
 *   <li>Dimension: Required configuration via REDIS_DIMENSION environment variable</li>
 *   <li>Prefix: Configured via REDIS_PREFIX environment variable or defaults to "vector:"</li>
 *   <li>Index Name: Configured via REDIS_INDEX_NAME environment variable or defaults to "vector_index"</li>
 *   <li>Metadata Fields: Optionally configured via REDIS_METADATA_FIELDS environment variable</li>
 *   <li>Distance Metric: Configured via REDIS_DISTANCE_METRIC environment variable or defaults to "COSINE"</li>
 * </ul>
 *
 * <p><strong>Usage:</strong>
 * <pre>{@code
 * // Configuration is automatic through environment variables or defaults
 * RedisProvider provider = new RedisProvider();
 * EmbeddingStore<TextSegment> store = provider.create();
 * }</pre>
 *
 * @see RedisConfig
 * @see EmbeddingStoreProvider
 * @since 1.0
 */
@ForageBean(
        value = "redis",
        components = {"camel-langchain4j-embeddings"},
        description = "Redis with vector search support")
public class RedisProvider implements EmbeddingStoreProvider {
    private static final Logger LOG = LoggerFactory.getLogger(RedisProvider.class);

    /**
     * Creates a new Redis embedding store instance with the configured parameters.
     *
     * <p>This method creates a {@link RedisEmbeddingStore} using the connection details
     * and configuration from the RedisConfig. The store is ready to use for vector
     * operations once created.
     *
     * @param id optional configuration prefix for named instances
     * @return a new configured Redis embedding store instance
     */
    @Override
    public EmbeddingStore<TextSegment> create(String id) {
        final RedisConfig config = new RedisConfig(id);

        String host = config.host();
        int port = config.port();
        String user = config.user();
        String password = config.password();
        int dimension = config.dimension();
        String prefix = config.prefix();
        String indexName = config.indexName();
        String metadataFields = config.metadataFields();
        String distanceMetric = config.distanceMetric();

        LOG.trace(
                "Creating Redis embedding store: {}:{} with configuration: user={}, dimension={}, prefix={}, indexName={}, metadataFields={}, distanceMetric={}",
                host,
                port,
                user,
                dimension,
                prefix,
                indexName,
                metadataFields,
                distanceMetric);

        RedisEmbeddingStore.Builder builder = RedisEmbeddingStore.builder()
                .host(host)
                .port(port)
                .dimension(dimension)
                .prefix(prefix)
                .indexName(indexName);

        // Set optional authentication parameters if configured
        if (user != null) {
            builder.user(user);
        }

        if (password != null) {
            builder.password(password);
        }

        // Set optional metadata fields if configured
        if (metadataFields != null && !metadataFields.trim().isEmpty()) {
            String[] fields = metadataFields.split(",");
            Collection<String> fieldNames = new ArrayList<>(Arrays.asList(fields));
            builder.metadataFieldsName(fieldNames);
        }

        // TODO : figure out how to set the metric type
        MetricType mt = MetricType.COSINE;
        // Set distance metric
        switch (distanceMetric.toUpperCase()) {
            case "L2":
                mt = MetricType.L2;
            case "IP":
                mt = MetricType.IP;
            default:
                mt = MetricType.COSINE;
                LOG.warn("Unknown distance metric: {}, using default (COSINE)", distanceMetric);
                break;
        }

        return builder.build();
    }
}
