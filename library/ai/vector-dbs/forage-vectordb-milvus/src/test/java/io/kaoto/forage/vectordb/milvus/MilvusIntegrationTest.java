package io.kaoto.forage.vectordb.milvus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.milvus.MilvusContainer;
import io.milvus.param.MetricType;
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
 * Integration test for Milvus vector database using Testcontainers.
 *
 * <p>This test demonstrates how the Forage framework can integrate with Milvus
 * using dynamic configuration and real database operations. The test uses Testcontainers
 * to spin up a real Milvus instance and performs actual vector operations.
 */
@Testcontainers
public class MilvusIntegrationTest {
    private static final Logger LOG = LoggerFactory.getLogger(MilvusIntegrationTest.class);

    private static final int DIMENSION = 4;
    private static final String COLLECTION_NAME = "test_collection";

    @Container
    private static final MilvusContainer milvusContainer =
            new MilvusContainer("milvusdb/milvus:v2.3.2").withStartupAttempts(3);

    @BeforeAll
    public static void setupMilvusConfiguration() {
        // Remove any existing properties file to ensure clean state
        try {
            Path propertiesFile = Paths.get("src/test/resources/forage-vectordb-milvus.properties");
            if (Files.exists(propertiesFile)) {
                Files.delete(propertiesFile);
                LOG.info("Removed src/test/resources/forage-vectordb-milvus.properties");
            }
        } catch (Exception e) {
            LOG.warn("Failed to remove properties file: {}", e.getMessage());
        }

        LOG.info("Setting up Milvus configuration with container: {}", milvusContainer.getDockerImageName());

        String host = milvusContainer.getHost();
        Integer grpcPort = milvusContainer.getMappedPort(19530); // Milvus gRPC port

        // Build URI for Milvus connection
        String uri = String.format("http://%s:%d", host, grpcPort);

        // Set system properties with the dynamic container values
        System.setProperty("milvus.host", host);
        System.setProperty("milvus.port", Integer.toString(grpcPort));
        System.setProperty("milvus.uri", uri);
        System.setProperty("milvus.collection.name", COLLECTION_NAME);
        System.setProperty("milvus.dimension", Integer.toString(DIMENSION));
        System.setProperty("milvus.metric.type", MetricType.COSINE.name());
        System.setProperty("milvus.auto.flush.on.insert", "true");
        System.setProperty("milvus.retrieve.embeddings.on.search", "false");
        System.setProperty("milvus.token", "test-token");
        System.setProperty("milvus.username", "test-user");
        System.setProperty("milvus.password", "test-password");

        LOG.info("Milvus container configured - Host: {}, GRPC Port: {}", host, grpcPort);
        LOG.info("Milvus collection configured: {}", COLLECTION_NAME);
    }

    @AfterAll
    public static void cleanupMilvusConfiguration() {
        LOG.info("Cleaning up Milvus system properties");

        // Clear system properties
        System.clearProperty("milvus.host");
        System.clearProperty("milvus.port");
        System.clearProperty("milvus.uri");
        System.clearProperty("milvus.collection.name");
        System.clearProperty("milvus.dimension");
        System.clearProperty("milvus.metric.type");
        System.clearProperty("milvus.auto.flush.on.insert");
        System.clearProperty("milvus.retrieve.embeddings.on.search");
        System.clearProperty("milvus.token");
        System.clearProperty("milvus.username");
        System.clearProperty("milvus.password");

        LOG.info("Milvus system properties cleared");
    }

    @Test
    public void shouldPerformBasicMilvusOperations() {
        LOG.info("Testing basic Milvus operations");

        MilvusProvider provider = new MilvusProvider();
        EmbeddingStore<TextSegment> embeddingStore = provider.create();

        try {
            LOG.info("Testing basic embedding operations");

            // Create test embeddings
            float[] vector1 = {0.1f, 0.2f, 0.3f, 0.4f};
            float[] vector2 = {0.5f, 0.6f, 0.7f, 0.8f};

            Embedding embedding1 = new Embedding(vector1);
            Embedding embedding2 = new Embedding(vector2);

            TextSegment segment1 = TextSegment.from("Test document 1", Metadata.from("id", "1"));
            TextSegment segment2 = TextSegment.from("Test document 2", Metadata.from("id", "2"));

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
            fail("Milvus basic operations test failed (expected for some configurations): {}", e.getMessage());
        }
    }
}
