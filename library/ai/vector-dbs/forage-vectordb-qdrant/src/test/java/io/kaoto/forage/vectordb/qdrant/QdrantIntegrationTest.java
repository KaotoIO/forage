package io.kaoto.forage.vectordb.qdrant;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.qdrant.QdrantContainer;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections;
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
public class QdrantIntegrationTest {
    private static final Logger LOG = LoggerFactory.getLogger(QdrantIntegrationTest.class);

    private static final Collections.Distance DISTANCE = Collections.Distance.Cosine;
    private static final int DIMENSION = 4;
    private static final String COLLECTION_NAME = "test_collection";
    private static final String PROPERTIES_FILE_NAME = "forage-vectordb-qdrant.properties";

    @Container
    public static QdrantContainer qdrantContainer =
            new QdrantContainer("qdrant/qdrant:latest").waitingFor(Wait.forListeningPort());

    @BeforeAll
    public static void setupQdrantConfiguration() throws Exception {
        LOG.info("Clear any existing {} properties file ...", PROPERTIES_FILE_NAME);
        Path sourceFile = Paths.get(PROPERTIES_FILE_NAME);
        Path targetFile = Paths.get("src/test/resources", PROPERTIES_FILE_NAME);

        if (Files.exists(targetFile)) {
            Files.delete(targetFile);
            LOG.info("Removed {}", targetFile);
        }

        // Clear any existing system properties first
        System.clearProperty("qdrant.host");
        System.clearProperty("qdrant.port");
        System.clearProperty("qdrant.collection.name");
        System.clearProperty("qdrant.use.tls");

        LOG.info("Setting up Qdrant configuration with container: {}", qdrantContainer.getDockerImageName());

        String host = qdrantContainer.getHost();

        Integer grpcPort = qdrantContainer.getGrpcPort();

        LOG.info("Qdrant container configured - Host: {}, GRPC Port: {}", host, grpcPort);

        System.setProperty("qdrant.host", host);
        System.setProperty("qdrant.port", Integer.toString(grpcPort));
        System.setProperty("qdrant.collection.name", COLLECTION_NAME);
        System.setProperty("qdrant.use.tls", "false");

        // Create the collection
        QdrantClient client = new QdrantClient(
                QdrantGrpcClient.newBuilder(host, grpcPort, false).build());

        client.createCollectionAsync(
                        COLLECTION_NAME,
                        Collections.VectorParams.newBuilder()
                                .setDistance(DISTANCE)
                                .setSize(4)
                                .build())
                .get();

        LOG.info("Qdrant collection created {}", COLLECTION_NAME);
    }

    @AfterAll
    public static void teardownQdrantConfiguration() {
        LOG.info("Cleaning up Qdrant system properties");

        System.clearProperty("qdrant.host");
        System.clearProperty("qdrant.port");
        System.clearProperty("qdrant.collection.name");
        System.clearProperty("qdrant.use.tls");

        LOG.info("Qdrant system properties cleared");
    }

    @Test
    public void shouldPerformBasicQdrantOperations() {
        LOG.info("Testing basic Qdrant operations");

        QdrantProvider provider = new QdrantProvider();
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
            fail("Qdrant basic operations test failed (expected for some configurations): {}", e.getMessage());
        }
    }
}
