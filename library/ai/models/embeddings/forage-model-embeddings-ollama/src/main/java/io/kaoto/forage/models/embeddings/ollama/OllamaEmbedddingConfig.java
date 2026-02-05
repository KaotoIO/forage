package io.kaoto.forage.models.embeddings.ollama;

import static io.kaoto.forage.models.embeddings.ollama.OllamaEmbeddingConfigEntries.*;

import io.kaoto.forage.core.util.config.Config;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigStore;
import java.time.Duration;
import java.util.Optional;

/**
 * Configuration class for Ollama Embedding model integration in the Forage framework.
 *
 * <p>This configuration class manages the settings required to connect to and use Ollama Embedding
 * models. It handles server connection details and model selection through environment
 * variables with appropriate fallback mechanisms and default values.
 *
 * <p><strong>Configuration Parameters:</strong>
 * <ul>
 *   <li><strong>OLLAMA_MODEL_NAME</strong> - The specific Ollama model to use (default: "llama3")</li>
 *   <li><strong>OLLAMA_TIMEOUT</strong> - Used for the HttpClientBuilder that will be used to create the HttpClient
 *   that will be used to communicate with Ollama.
 * NOTE: timeout(Duration) overrides timeouts set on the HttpClientBuilder.</li>
 *   <li><strong>OLLAMA_MAX_RETRIES</strong> - Used for the HttpClientBuilder that will be used to create the HttpClient
 *  *   that will be used to communicate with Ollama.</li>
 *   <li><strong>OLLAMA_LOG_REQUESTS</strong> - Enable request logging, true/false (no default)</li>
 *   <li><strong>OLLAMA_LOG_RESPONSES</strong> - Enable response logging, true/false (no default)</li>
 * </ul>
 *
 * @see Config
 * @see ConfigStore
 * @see ConfigModule
 * @since 1.0
 */
public class OllamaEmbedddingConfig implements Config {

    private final String prefix;

    /**
     * Creates an Ollama Embedding model instance.
     *
     * <p>This configuration class manages the settings required to connect to and use Ollama Embedding
     * models. It handles server connection details and model selection through environment
     * variables with appropriate fallback mechanisms and default values.
     *
     * <p><strong>Configuration Parameters:</strong>
     * <ul>
     *   <li><strong>OLLAMA_MODEL_NAME</strong> - The specific Ollama model to use (default: "llama3")</li>
     *   <li><strong>OLLAMA_TIMEOUT</strong> - Used for the HttpClientBuilder that will be used to create the HttpClient
     *   that will be used to communicate with Ollama.
     * NOTE: timeout(Duration) overrides timeouts set on the HttpClientBuilder.</li>
     *   <li><strong>OLLAMA_MAX_RETRIES</strong> - Used for the HttpClientBuilder that will be used to create the HttpClient
     *  *   that will be used to communicate with Ollama.</li>
     *   <li><strong>OLLAMA_LOG_REQUESTS</strong> - Enable request logging, true/false (no default)</li>
     *   <li><strong>OLLAMA_LOG_RESPONSES</strong> - Enable response logging, true/false (no default)</li>
     * </ul>
     *
     * @see Config
     * @see ConfigStore
     * @see ConfigModule
     * @since 1.0
     */
    public OllamaEmbedddingConfig() {
        this(null);
    }

    public OllamaEmbedddingConfig(String prefix) {
        this.prefix = prefix;

        // First register new configuration modules. This happens only if a prefix is provided
        OllamaEmbeddingConfigEntries.register(prefix);

        // Then, loads the configurations from the properties file associated with this Config module
        ConfigStore.getInstance().load(OllamaEmbedddingConfig.class, this, this::register);

        // Lastly, load the overrides defined in system properties and environment variables
        OllamaEmbeddingConfigEntries.loadOverrides(prefix);
    }

    @Override
    public void register(String name, String value) {
        Optional<ConfigModule> config = OllamaEmbeddingConfigEntries.find(prefix, name);

        config.ifPresent(module -> ConfigStore.getInstance().set(module, value));
    }

    /**
     * Returns the unique identifier for this Ollama configuration module.
     */
    @Override
    public String name() {
        return "forage-embedding-model-ollama";
    }

    /**
     * Returns the base URL of the Ollama embedding server.
     *
     * <p>This method retrieves the base URL that was configured through environment variables,
     * system properties, or configuration files. The base URL specifies where the Ollama
     * server is running and should include the protocol and port.
     *
     * <p><strong>Configuration Sources (in order of precedence):</strong>
     * <ol>
     *   <li>OLLAMA_BASE_URL environment variable</li>
     *   <li>ollama.base.url system property</li>
     *   <li>base-url property in forage-model-ollama.properties</li>
     *   <li>Default value: "http://localhost:11434"</li>
     * </ol>
     *
     * <p><strong>Example Values:</strong>
     * <ul>
     *   <li>"http://localhost:11434" - Local Ollama installation</li>
     *   <li>"http://ollama-server:11434" - Remote server</li>
     *   <li>"https://my-ollama.example.com" - HTTPS endpoint</li>
     * </ul>
     *
     * @return the Ollama server base URL, never null
     */
    public String baseUrl() {
        return ConfigStore.getInstance().get(BASE_URL.asNamed(prefix)).orElse(BASE_URL.defaultValue());
    }

    /**
     * Returns the name of the Ollama embedding model to use.
     *
     * <p>This method retrieves the model name that specifies which Ollama model
     * should be used for AI operations. The model must be available on the
     * configured Ollama server.
     *
     * <p><strong>Common Model Names:</strong>
     * <ul>
     *   <li><strong>llama3</strong> - Meta's Llama 3 model (8B parameters)</li>
     *   <li><strong>llama3.1</strong> - Updated Llama 3.1 model</li>
     *   <li><strong>mistral</strong> - Mistral 7B model</li>
     *   <li><strong>codellama</strong> - Code-specialized Llama model</li>
     *   <li><strong>phi3</strong> - Microsoft's Phi-3 model</li>
     * </ul>
     *
     * @return the Ollama model name, never null
     */
    public String modelName() {
        return ConfigStore.getInstance().get(MODEL_NAME.asNamed(prefix)).orElse("llama3");
    }

    /**
     * Returns the max-result parameter.
     *
     * <p>Used for the HttpClientBuilder that will be used to create the HttpClient that will be used to communicate with Ollama.</p>
     */
    public Integer maxRetries() {
        return ConfigStore.getInstance()
                .get(MAX_RETRIES.asNamed(prefix))
                .map(Integer::parseInt)
                .orElse(null);
    }

    /**
     * Returns the max-result parameter.
     *
     * <p>Used for the HttpClientBuilder that will be used to create the HttpClient that will be used to communicate
     * with Ollama.
     *  NOTE: timeout(Duration) overrides timeouts set on the HttpClientBuilder.
     */
    public Duration timeout() {
        return ConfigStore.getInstance()
                .get(TIMEOUT.asNamed(prefix))
                .map(Duration::parse)
                .orElse(null);
    }

    /**
     * Returns whether request logging is enabled.
     *
     * <p>When enabled, the Ollama client will log all requests sent to the server.
     * This is useful for debugging and monitoring but should be disabled in production
     * to avoid logging sensitive information.
     *
     * @return true if request logging is enabled, false if disabled, null if not configured
     */
    public Boolean logRequests() {
        return ConfigStore.getInstance()
                .get(LOG_REQUESTS.asNamed(prefix))
                .map(Boolean::parseBoolean)
                .orElse(null);
    }

    /**
     * Returns whether response logging is enabled.
     *
     * <p>When enabled, the Ollama client will log all responses received from the server.
     * This is useful for debugging and monitoring but should be disabled in production
     * to avoid logging sensitive information.
     *
     * @return true if response logging is enabled, false if disabled, null if not configured
     */
    public Boolean logResponses() {
        return ConfigStore.getInstance()
                .get(LOG_RESPONSES.asNamed(prefix))
                .map(Boolean::parseBoolean)
                .orElse(null);
    }
}
