package io.kaoto.forage.models.embeddings.ollama;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import io.kaoto.forage.core.ai.EmbeddingModelProvider;
import io.kaoto.forage.core.ai.ModelProvider;
import io.kaoto.forage.core.annotations.ForageBean;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provider for creating Ollama embedding models with configurable parameters.
 *
 * <p>This provider creates instances of {@link OllamaEmbeddingModel} using configuration
 * values managed by {@link OllamaConfig}. The configuration supports environment
 * variables, system properties, and configuration files for flexible deployment.
 *
 * <p><strong>Configuration:</strong>
 * <ul>
 *   <li>Base URL: Configured via OLLAMA_BASE_URL environment variable or defaults to "http://localhost:11434"</li>
 *   <li>Model Name: Configured via OLLAMA_MODEL_NAME environment variable or defaults to "llama3"</li>
 *   <li>Temperature: Optionally configured via OLLAMA_TEMPERATURE environment variable (no default)</li>
 *   <li>Top-K: Optionally configured via OLLAMA_TOP_K environment variable (no default)</li>
 *   <li>Top-P: Optionally configured via OLLAMA_TOP_P environment variable (no default)</li>
 *   <li>Min-P: Optionally configured via OLLAMA_MIN_P environment variable (no default)</li>
 *   <li>Context Size: Optionally configured via OLLAMA_NUM_CTX environment variable (no default)</li>
 *   <li>Request Logging: Optionally configured via OLLAMA_LOG_REQUESTS environment variable (no default)</li>
 *   <li>Response Logging: Optionally configured via OLLAMA_LOG_RESPONSES environment variable (no default)</li>
 * </ul>
 *
 * <p><strong>Usage:</strong>
 * <pre>{@code
 * // Configuration is automatic through environment variables or defaults
 * OllamaProvider provider = new OllamaProvider();
 * ChatModel model = provider.newModel();
 * }</pre>
 *
 * @see OllamaEmbedddingConfig
 * @see ModelProvider
 * @since 1.0
 */
@ForageBean(
        value = "ollama",
        components = {"camel-langchain4j-agent"},
        feature = "Embeddings Model",
        description = "Locally-hosted models via Ollama (Llama, Mistral, etc.)")
public class OllamaEmbeddingProvider implements EmbeddingModelProvider {
    private static final Logger LOG = LoggerFactory.getLogger(OllamaEmbeddingProvider.class);

    /**
     * Creates a new Ollama chat model instance with the configured parameters.
     *
     * <p>This method creates an {@link OllamaChatModel} using the base URL and
     * model name from the configuration. The model is ready to use for chat
     * operations once created.
     *
     * @return a new configured Ollama chat model instance
     */
    @Override
    public EmbeddingModel create(String id) {
        final OllamaEmbedddingConfig config = new OllamaEmbedddingConfig(id);

        String baseUrl = config.baseUrl();
        String modelName = config.modelName();
        Integer maxRetries = config.maxRetries();
        Duration timeout = config.timeout();
        Boolean logRequests = config.logRequests();
        Boolean logResponses = config.logResponses();

        if (modelName == null) {
            LOG.trace("Embedding Ollama model name is not created. Model name is not provided.");
            return null;
        }

        LOG.trace(
                "Creating Ollama model: {} at {} with configuration: maxRetries={}, toptimeoutK={}, logRequests={}, logResponses={}",
                modelName,
                baseUrl,
                maxRetries,
                timeout,
                logRequests,
                logResponses);

        OllamaEmbeddingModel.OllamaEmbeddingModelBuilder builder =
                OllamaEmbeddingModel.builder().baseUrl(baseUrl).modelName(modelName);
        // Only set optional parameters if they are configured
        if (maxRetries != null) {
            builder.maxRetries(maxRetries);
        }

        if (timeout != null) {
            builder.timeout(timeout);
        }
        if (logRequests != null) {
            builder.logRequests(logRequests);
        }

        if (logResponses != null) {
            builder.logResponses(logResponses);
        }

        return builder.build();
    }
}
