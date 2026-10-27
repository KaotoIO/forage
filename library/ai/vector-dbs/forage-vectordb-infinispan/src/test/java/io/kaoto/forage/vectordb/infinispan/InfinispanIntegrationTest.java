package io.kaoto.forage.vectordb.infinispan;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Fail.fail;

@Testcontainers
public class InfinispanIntegrationTest {
    private static final Logger LOG = LoggerFactory.getLogger(InfinispanIntegrationTest.class);

    private static final String CACHE_NAME = "test_cache";
    private static final int DIMENSION = 4;
    private static final String PROPERTIES_FILE_NAME = "forage-vectordb-infinispan.properties";

    @Container
    public static GenericContainer<?> infinispanContainer = new GenericContainer<>("infinispan/server:15.0")
            .withExposedPorts(11222)
            .withEnv("USER", "admin")
            .withEnv("PASS", "admin")
            .waitingFor(Wait.forListeningPort());

    @BeforeAll
    public static void setupInfinispanConfiguration() throws Exception {
        LOG.info("Clear any existing {} properties file ...", PROPERTIES_FILE_NAME);
        Path sourceFile = Paths.get(PROPERTIES_FILE_NAME);
        Path targetFile = Paths.get("src/test/resources", PROPERTIES_FILE_NAME);

        if (Files.exists(targetFile)) {
            Files.delete(targetFile);
            LOG.info("Removed {}", targetFile);
        }

        // Clear any existing system properties first
        clearProperties();

        LOG.info("Setting up Infinispan configuration with container: {}", infinispanContainer.getDockerImageName());

        String host = infinispanContainer.getHost();
        Integer port = infinispanContainer.getFirstMappedPort();

        LOG.info("Infinispan container configured - Host: {}, Port: {}", host, port);

        // Configure connection properties for the test
        System.setProperty("infinispan.cache.name", CACHE_NAME);
        System.setProperty("infinispan.dimension", String.valueOf(DIMENSION));
        System.setProperty("infinispan.distance", "3");
        System.setProperty("infinispan.similarity", "COSINE");
        System.setProperty("infinispan.register.schema", "true");
        System.setProperty("infinispan.create.cache", "true");
        System.setProperty("infinispan.host", host);
        System.setProperty("infinispan.port", port.toString());
        System.setProperty("infinispan.username", "admin");
        System.setProperty("infinispan.password", "admin");

        LOG.info("Infinispan configuration completed for cache: {}", CACHE_NAME);
    }

    public static void clearProperties() {
        System.clearProperty("infinispan.cache.name");
        System.clearProperty("infinispan.dimension");
        System.clearProperty("infinispan.distance");
        System.clearProperty("infinispan.similarity");
        System.clearProperty("infinispan.register.schema");
        System.clearProperty("infinispan.create.cache");
        System.clearProperty("infinispan.host");
        System.clearProperty("infinispan.port");
        System.clearProperty("infinispan.username");
        System.clearProperty("infinispan.password");
    }

    @AfterAll
    public static void teardownInfinispanConfiguration() {
        LOG.info("Cleaning up Infinispan system properties");

        clearProperties();

        LOG.info("Infinispan system properties cleared");
    }

    @Test
    public void shouldPerformBasicInfinispanOperations() {
        LOG.info("Testing basic Infinispan operations");

        InfinispanProvider provider = new InfinispanProvider();

        try {
            EmbeddingStore<TextSegment> embeddingStore = provider.create();

            LOG.info("Successfully created Infinispan embedding store {}", embeddingStore);

            // Basic test - just verify we can create test embeddings
            // In a real scenario, these would be stored and retrieved from Infinispan
            float[] vector1 = {0.1f, 0.2f, 0.3f, 0.4f};
            float[] vector2 = {0.5f, 0.6f, 0.7f, 0.8f};

            Embedding embedding1 = new Embedding(vector1);
            Embedding embedding2 = new Embedding(vector2);

            TextSegment segment1 = TextSegment.from("Test document 1", Metadata.from("key1", "value1"));
            TextSegment segment2 = TextSegment.from("Test document 2", Metadata.from("key2", "value2"));

            LOG.info("Created test embeddings and segments for validation");

        } catch (Exception e) {
            fail("Infinispan basic operations test failed (expected for some configurations): {}", e.getMessage());
        }
    }
}
