package io.kaoto.forage.models.chat.ollama;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Default Value Tests")
class OllamaDefaultValueTests {

    private static final String DEFAULT_BASE_URL = "http://localhost:11434";
    private static final String DEFAULT_MODEL_NAME = "llama3";

    @Test
    @DisplayName("Should return default base URL when no configuration provided")
    void shouldReturnDefaultBaseUrl() {
        OllamaConfig config = new OllamaConfig();

        assertThat(config.baseUrl()).isEqualTo(DEFAULT_BASE_URL);
    }

    @Test
    @DisplayName("Should return default model name when no configuration provided")
    void shouldReturnDefaultModelName() {
        OllamaConfig config = new OllamaConfig();

        assertThat(config.modelName()).isEqualTo(DEFAULT_MODEL_NAME);
    }

    @Test
    @DisplayName("Should return null for optional parameters when not configured")
    void shouldReturnNullForOptionalParameters() {
        OllamaConfig config = new OllamaConfig();

        assertThat(config.temperature()).isNull();
        assertThat(config.topK()).isNull();
        assertThat(config.topP()).isNull();
        assertThat(config.minP()).isNull();
        assertThat(config.numCtx()).isNull();
        assertThat(config.logRequests()).isNull();
        assertThat(config.logResponses()).isNull();
        assertThat(config.timeout()).isNull();
    }

    @Test
    @DisplayName("Should return correct module name")
    void shouldReturnCorrectModuleName() {
        OllamaConfig config = new OllamaConfig();

        assertThat(config.name()).isEqualTo("forage-model-ollama");
    }
}
