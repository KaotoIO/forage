package io.kaoto.forage.vectordb.chroma;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.chromadb.ChromaDBContainer;
import org.testcontainers.containers.wait.strategy.Wait;
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

@Testcontainers
public class ChromaIntegrationTest {
    private static final Logger LOG = LoggerFactory.getLogger(ChromaIntegrationTest.class);

    private static final String COLLECTION_NAME = "test_collection";
    private static final String PROPERTIES_FILE_NAME = "forage-vectordb-chroma.properties";

    @Container
    public static ChromaDBContainer chromaContainer =
            new ChromaDBContainer("chromadb/chroma:0.5.4").waitingFor(Wait.forListeningPort());

    @BeforeAll
    public static void setupChromaConfiguration() throws Exception {
        LOG.info("Clear any existing {} properties file ...", PROPERTIES_FILE_NAME);
        Path sourceFile = Paths.get(PROPERTIES_FILE_NAME);
        Path targetFile = Paths.get("src/test/resources", PROPERTIES_FILE_NAME);

        if (Files.exists(targetFile)) {
            Files.delete(targetFile);
            LOG.info("Removed {}", targetFile);
        }

        // Clear any existing system properties first
        System.clearProperty("chroma.url");
        System.clearProperty("chroma.collection.name");
        System.clearProperty("chroma.timeout");
        System.clearProperty("chroma.log.requests");
        System.clearProperty("chroma.log.responses");

        LOG.info("Setting up Chroma configuration with container: {}", chromaContainer.getDockerImageName());

        String endpoint = chromaContainer.getEndpoint();

        LOG.info("Chroma container configured - Endpoint: {}", endpoint);

        System.setProperty("chroma.url", endpoint);
        System.setProperty("chroma.collection.name", COLLECTION_NAME);
        System.setProperty("chroma.timeout", "30");
        System.setProperty("chroma.log.requests", "true");
        System.setProperty("chroma.log.responses", "true");

        LOG.info("Chroma configuration completed for collection: {}", COLLECTION_NAME);
    }

    @AfterAll
    public static void teardownChromaConfiguration() {
        LOG.info("Cleaning up Chroma system properties");

        System.clearProperty("chroma.url");
        System.clearProperty("chroma.collection.name");
        System.clearProperty("chroma.timeout");
        System.clearProperty("chroma.log.requests");
        System.clearProperty("chroma.log.responses");

        LOG.info("Chroma system properties cleared");
    }

    @Test
    public void shouldPerformBasicChromaOperations() {
        LOG.info("Testing basic Chroma operations");

        ChromaProvider provider = new ChromaProvider();
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

            // Add embeddings
            embeddingStore.add(embedding1, segment1);
            embeddingStore.add(embedding2, segment2);

            // Search for similar embeddings
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(embedding1)
                    .maxResults(2)
                    .minScore(0.0)
                    .build();

            EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);

            org.assertj.core.api.Assertions.assertThat(searchResult).isNotNull();
            List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();
            org.assertj.core.api.Assertions.assertThat(matches).isNotEmpty();

            LOG.info("Found {} matches", matches.size());
            for (EmbeddingMatch<TextSegment> match : matches) {
                LOG.info("Match: {} with score: {}", match.embedded().text(), match.score());
            }
        } catch (Exception e) {
            fail("Chroma basic operations test failed (expected for some configurations): {}", e.getMessage());
        }
    }
}
