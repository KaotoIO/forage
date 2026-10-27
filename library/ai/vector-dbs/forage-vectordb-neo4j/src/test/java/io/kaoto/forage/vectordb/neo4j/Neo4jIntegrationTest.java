package io.kaoto.forage.vectordb.neo4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
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
 * Integration test for Neo4j vector database using Testcontainers.
 *
 * <p>This test demonstrates how the Forage framework can integrate with Neo4j
 * using dynamic configuration and real database operations. The test uses Testcontainers
 * to spin up a real Neo4j instance and performs basic vector operations.
 */
@Testcontainers
public class Neo4jIntegrationTest {
    private static final Logger LOG = LoggerFactory.getLogger(Neo4jIntegrationTest.class);

    private static final int DIMENSION = 4;
    private static final String INDEX_NAME = "test_vector_index";
    private static final String LABEL = "TestDocument";

    @Container
    private static final Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:5.15")
            .withAdminPassword("test_password")
            .withStartupAttempts(3);

    @BeforeAll
    public static void setupNeo4jConfiguration() {
        // Remove any existing properties file to ensure clean state
        try {
            Path propertiesFile = Paths.get("src/test/resources/forage-vectordb-neo4j.properties");
            if (Files.exists(propertiesFile)) {
                Files.delete(propertiesFile);
                LOG.info("Removed src/test/resources/forage-vectordb-neo4j.properties");
            }
        } catch (Exception e) {
            LOG.warn("Failed to remove properties file: {}", e.getMessage());
        }

        LOG.info("Setting up Neo4j configuration with container: {}", neo4jContainer.getDockerImageName());

        String boltUrl = neo4jContainer.getBoltUrl();
        String password = neo4jContainer.getAdminPassword();

        // Set system properties with the dynamic container values
        System.setProperty("neo4j.uri", boltUrl);
        System.setProperty("neo4j.user", "neo4j");
        System.setProperty("neo4j.password", password);
        System.setProperty("neo4j.database.name", "neo4j");
        System.setProperty("neo4j.index.name", INDEX_NAME);
        System.setProperty("neo4j.label", LABEL);
        System.setProperty("neo4j.embedding.property", "embedding");
        System.setProperty("neo4j.text.property", "text");
        System.setProperty("neo4j.id.property", "id");
        System.setProperty("neo4j.metadata.prefix", "meta_");
        System.setProperty("neo4j.dimension", Integer.toString(DIMENSION));
        System.setProperty("neo4j.with.encryption", "false");
        System.setProperty("neo4j.await.index.timeout", "120");

        LOG.info("Neo4j container configured - Bolt URL: {}", boltUrl);
        LOG.info("Neo4j index configured: {}", INDEX_NAME);
    }

    @AfterAll
    public static void cleanupNeo4jConfiguration() {
        LOG.info("Cleaning up Neo4j system properties");

        // Clear system properties
        System.clearProperty("neo4j.uri");
        System.clearProperty("neo4j.user");
        System.clearProperty("neo4j.password");
        System.clearProperty("neo4j.database.name");
        System.clearProperty("neo4j.index.name");
        System.clearProperty("neo4j.label");
        System.clearProperty("neo4j.embedding.property");
        System.clearProperty("neo4j.text.property");
        System.clearProperty("neo4j.id.property");
        System.clearProperty("neo4j.metadata.prefix");
        System.clearProperty("neo4j.dimension");
        System.clearProperty("neo4j.with.encryption");
        System.clearProperty("neo4j.await.index.timeout");

        LOG.info("Neo4j system properties cleared");
    }

    @Test
    public void shouldPerformBasicNeo4jOperations() {
        LOG.info("Testing basic Neo4j operations");

        Neo4jProvider provider = new Neo4jProvider();
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
            fail("Neo4j basic operations test failed (expected for some configurations): {}", e.getMessage());
        }
    }
}
