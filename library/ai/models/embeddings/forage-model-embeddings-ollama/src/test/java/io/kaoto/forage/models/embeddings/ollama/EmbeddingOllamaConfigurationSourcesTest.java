package io.kaoto.forage.models.embeddings.ollama;

import static org.assertj.core.api.Assertions.assertThat;

import io.kaoto.forage.core.util.config.ConfigStore;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for OllamaConfig focusing on configuration source precedence.
 *
 * <p>This test class verifies the precedence order:
 * <ol>
 *   <li>Environment variables (highest precedence)</li>
 *   <li>System properties</li>
 *   <li>Configuration files</li>
 *   <li>Default values (lowest precedence)</li>
 * </ol>
 */
@DisplayName("OllamaConfig Configuration Source Precedence Tests")
class EmbeddingOllamaConfigurationSourcesTest {

    @BeforeEach
    void setUp() {
        ConfigStore.getInstance().setClassLoader(getClass().getClassLoader());
    }

    @Test
    @DisplayName("Should load from configuration file only")
    void shouldLoadFromConfigurationFileOnly() throws IOException {
        OllamaEmbedddingConfig config = new OllamaEmbedddingConfig();

        assertThat(config.baseUrl()).isEqualTo("testUrl");
        assertThat(config.modelName()).isEqualTo("gnomic-embed-text");
        assertThat(config.timeout()).isEqualTo(Duration.of(30, ChronoUnit.SECONDS));
        assertThat(config.logRequests()).isTrue();
        assertThat(config.logResponses()).isFalse();
    }
}
