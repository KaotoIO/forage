package io.kaoto.forage.vectordb.redis;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Fail.fail;

/**
 * Integration test for Redis vector database using Testcontainers.
 *
 * <p>This test demonstrates how the Forage framework can integrate with Redis
 * using dynamic configuration and real database operations. The test uses Testcontainers
 * to spin up a real Redis instance and performs basic vector operations.
 */
@Testcontainers
public class RedisIntegrationTest {
    private static final Logger LOG = LoggerFactory.getLogger(RedisIntegrationTest.class);

    private static final int DIMENSION = 4;
    private static final String INDEX_NAME = "test_index";
    private static final String PREFIX = "test:";

    @Container
    private static final GenericContainer<?> redisContainer = new GenericContainer<>(
                    DockerImageName.parse("redis/redis-stack:latest"))
            .withExposedPorts(6379)
            .withStartupAttempts(3);

    @BeforeAll
    public static void setupRedisConfiguration() {
        // Remove any existing properties file to ensure clean state
        try {
            Path propertiesFile = Paths.get("src/test/resources/forage-vectordb-redis.properties");
            if (Files.exists(propertiesFile)) {
                Files.delete(propertiesFile);
                LOG.info("Removed src/test/resources/forage-vectordb-redis.properties");
            }
        } catch (Exception e) {
            LOG.warn("Failed to remove properties file: {}", e.getMessage());
        }

        LOG.info("Setting up Redis configuration with container: {}", redisContainer.getDockerImageName());

        String host = redisContainer.getHost();
        Integer port = redisContainer.getMappedPort(6379);

        // Set system properties with the dynamic container values
        System.setProperty("redis.host", host);
        System.setProperty("redis.port", Integer.toString(port));
        System.setProperty("redis.dimension", Integer.toString(DIMENSION));
        System.setProperty("redis.index.name", INDEX_NAME);
        System.setProperty("redis.prefix", PREFIX);
        System.setProperty("redis.distance.metric", "COSINE");
        System.setProperty("redis.metadata.fields", "category,source");

        LOG.info("Redis container configured - Host: {}, Port: {}", host, port);
        LOG.info("Redis index configured: {}", INDEX_NAME);
    }

    @AfterAll
    public static void cleanupRedisConfiguration() {
        LOG.info("Cleaning up Redis system properties");

        // Clear system properties
        System.clearProperty("redis.host");
        System.clearProperty("redis.port");
        System.clearProperty("redis.dimension");
        System.clearProperty("redis.index.name");
        System.clearProperty("redis.prefix");
        System.clearProperty("redis.distance.metric");
        System.clearProperty("redis.metadata.fields");

        LOG.info("Redis system properties cleared");
    }

    @Test
    public void shouldPerformBasicRedisOperations() {
        LOG.info("Testing basic Redis operations");

        RedisProvider provider = new RedisProvider();
        EmbeddingStore<TextSegment> embeddingStore = provider.create();

        try {
            LOG.info("Testing basic embedding operations");

            // Create test embeddings
            float[] vector1 = {0.1f, 0.2f, 0.3f, 0.4f};
            float[] vector2 = {0.5f, 0.6f, 0.7f, 0.8f};

            Embedding embedding1 = new Embedding(vector1);
            Embedding embedding2 = new Embedding(vector2);

            TextSegment segment1 = TextSegment.from("Test document 1", Metadata.from("key1", "value1"));
            TextSegment segment2 = TextSegment.from("Test document 2", Metadata.from("key2", "value2"));

            // Store embeddings
            embeddingStore.add(embedding1, segment1);
            embeddingStore.add(embedding2, segment2);

            // Search for similar embeddings
            float[] searchVector = {0.1f, 0.2f, 0.3f, 0.4f};
            Embedding searchEmbedding = new Embedding(searchVector);

            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(searchEmbedding)
                    .maxResults(2)
                    .minScore(0.0)
                    .build();

            EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
            List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();

            LOG.info("Found {} matches", matches.size());
            for (EmbeddingMatch<TextSegment> match : matches) {
                LOG.info("Match: {} with score: {}", match.embedded().text(), match.score());
            }

            // Verify results
            org.assertj.core.api.Assertions.assertThat(matches).hasSize(2);
            org.assertj.core.api.Assertions.assertThat(matches.get(0).embedded().text())
                    .isEqualTo("Test document 1");
            org.assertj.core.api.Assertions.assertThat(matches.get(0).score()).isGreaterThan(0.8);

        } catch (Exception e) {
            fail("Redis basic operations test failed (expected for some configurations): {}", e.getMessage());
        }
    }
}
