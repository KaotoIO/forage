package io.kaoto.forage.models.chat.openai;

import java.time.Duration;
import java.util.Optional;
import io.kaoto.forage.core.util.config.Config;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.core.util.config.MissingConfigException;

import static io.kaoto.forage.models.chat.openai.OpenAIConfigEntries.API_KEY;
import static io.kaoto.forage.models.chat.openai.OpenAIConfigEntries.BASE_URL;
import static io.kaoto.forage.models.chat.openai.OpenAIConfigEntries.FREQUENCY_PENALTY;
import static io.kaoto.forage.models.chat.openai.OpenAIConfigEntries.HTTP1_1;
import static io.kaoto.forage.models.chat.openai.OpenAIConfigEntries.LOG_REQUESTS;
import static io.kaoto.forage.models.chat.openai.OpenAIConfigEntries.LOG_RESPONSES;
import static io.kaoto.forage.models.chat.openai.OpenAIConfigEntries.MAX_TOKENS;
import static io.kaoto.forage.models.chat.openai.OpenAIConfigEntries.MODEL_NAME;
import static io.kaoto.forage.models.chat.openai.OpenAIConfigEntries.PRESENCE_PENALTY;
import static io.kaoto.forage.models.chat.openai.OpenAIConfigEntries.TEMPERATURE;
import static io.kaoto.forage.models.chat.openai.OpenAIConfigEntries.TIMEOUT;
import static io.kaoto.forage.models.chat.openai.OpenAIConfigEntries.TOP_P;

/**
 * Configuration class for OpenAI model integration in the Forage framework.
 *
 * <p>This configuration class manages the settings required to connect to and use OpenAI
 * models. It handles authentication credentials, model selection, and various parameters
 * through environment variables with appropriate fallback mechanisms.
 *
 * <p><strong>Required Configuration:</strong>
 * <ul>
 *   <li><strong>OPENAI_API_KEY</strong> - Your OpenAI API key for authentication</li>
 * </ul>
 *
 * <p><strong>Optional Configuration:</strong>
 * <ul>
 *   <li><strong>OPENAI_MODEL_NAME</strong> - The specific OpenAI model to use (default: "gpt-3.5-turbo")</li>
 *   <li><strong>OPENAI_BASE_URL</strong> - Custom base URL for OpenAI API (default: OpenAI's standard API)</li>
 *   <li><strong>OPENAI_TEMPERATURE</strong> - Temperature for response randomness, 0.0-2.0 (no default)</li>
 *   <li><strong>OPENAI_MAX_TOKENS</strong> - Maximum tokens to generate (no default)</li>
 *   <li><strong>OPENAI_TOP_P</strong> - Top-P (nucleus) sampling parameter, 0.0-1.0 (no default)</li>
 *   <li><strong>OPENAI_FREQUENCY_PENALTY</strong> - Frequency penalty, -2.0 to 2.0 (no default)</li>
 *   <li><strong>OPENAI_PRESENCE_PENALTY</strong> - Presence penalty, -2.0 to 2.0 (no default)</li>
 *   <li><strong>OPENAI_LOG_REQUESTS</strong> - Enable request logging, true/false (no default)</li>
 *   <li><strong>OPENAI_LOG_RESPONSES</strong> - Enable response logging, true/false (no default)</li>
 * </ul>
 *
 * <p><strong>Configuration Sources:</strong>
 * Configuration values are resolved in the following order of precedence:
 * <ol>
 *   <li>Environment variables (OPENAI_API_KEY, OPENAI_MODEL_NAME, etc.)</li>
 *   <li>System properties (openai.api.key, openai.model.name, etc.)</li>
 *   <li>forage-model-open-ai.properties file in classpath</li>
 *   <li>Default values if none of the above are provided (only for model name)</li>
 * </ol>
 *
 * <p><strong>Usage Example:</strong>
 * <pre>{@code
 * // Set environment variables
 * export OPENAI_API_KEY="your-api-key-here"
 * export OPENAI_MODEL_NAME="gpt-4"
 * export OPENAI_TEMPERATURE="0.7"
 * export OPENAI_MAX_TOKENS="1000"
 *
 * // Create and use configuration
 * OpenAIConfig config = new OpenAIConfig();
 * String apiKey = config.apiKey();        // Returns the configured API key
 * String model = config.modelName();      // Returns the configured model name
 * Double temperature = config.temperature(); // Returns 0.7 or null if not set
 * }</pre>
 *
 * <p><strong>Security Considerations:</strong>
 * The API key is sensitive information and should be properly secured. Never commit API keys
 * to version control. Use environment variables or secure configuration management systems
 * in production environments.
 *
 * <p>This class automatically registers itself and its configuration parameters with the
 * {@link ConfigStore} during construction, making the configuration values available
 * to other components in the framework.
 *
 * @see Config
 * @see ConfigStore
 * @see ConfigModule
 * @since 1.0
 */
public class OpenAIConfig implements Config {

    private final String prefix;

    /**
     * Constructs a new OpenAIConfig and registers configuration parameters with the ConfigStore.
     *
     * <p>During construction, this class:
     * <ul>
     *   <li>Registers the API key configuration to be sourced from OPENAI_API_KEY environment variable</li>
     *   <li>Registers the model name configuration to be sourced from OPENAI_MODEL_NAME environment variable</li>
     *   <li>Registers optional parameters (base URL, temperature, max tokens, etc.) from their respective environment variables</li>
     *   <li>Attempts to load additional properties from forage-model-open-ai.properties</li>
     * </ul>
     *
     * <p>Configuration values are resolved when this constructor is called, with default values
     * used when no configuration is provided through environment variables, system properties,
     * or configuration files (only for model name).
     */
    public OpenAIConfig() {
        this(null);
    }

    public OpenAIConfig(String prefix) {
        this.prefix = prefix;

        // First register new configuration modules. This happens only if a prefix is provided
        OpenAIConfigEntries.register(prefix);

        // Then, loads the configurations from the properties file associated with this Config module
        ConfigStore.getInstance().load(OpenAIConfig.class, this, this::register);

        // Lastly, load the overrides defined in system properties and environment variables
        OpenAIConfigEntries.loadOverrides(prefix);
    }

    @Override
    public void register(String name, String value) {
        Optional<ConfigModule> config = OpenAIConfigEntries.find(prefix, name);

        config.ifPresent(module -> ConfigStore.getInstance().set(module, value));
    }

    /**
     * Returns the unique identifier for this OpenAI configuration module.
     *
     * <p>This name corresponds to the module artifact and is used for:
     * <ul>
     *   <li>Loading configuration files (forage-model-open-ai.properties)</li>
     *   <li>Identifying this module in logs and error messages</li>
     *   <li>Distinguishing this configuration from other AI model configurations</li>
     * </ul>
     *
     * @return the module name "forage-model-open-ai"
     */
    @Override
    public String name() {
        return "forage-model-open-ai";
    }

    /**
     * Returns the OpenAI API key for authentication.
     *
     * <p>This method retrieves the API key that was configured through environment variables,
     * system properties, or configuration files. The API key is required for all interactions
     * with OpenAI's services.
     *
     * <p><strong>Configuration Sources (in order of precedence):</strong>
     * <ol>
     *   <li>OPENAI_API_KEY environment variable</li>
     *   <li>openai.api.key system property</li>
     *   <li>api-key property in forage-model-open-ai.properties</li>
     * </ol>
     *
     * @return the OpenAI API key
     * @throws MissingConfigException if no API key is configured
     */
    public String apiKey() {
        return ConfigStore.getInstance()
                .get(API_KEY.asNamed(prefix))
                .orElseThrow(() -> new MissingConfigException("Missing OpenAI API key"));
    }

    /**
     * Returns the name of the OpenAI model to use.
     *
     * <p>This method retrieves the model name that specifies which OpenAI model
     * should be used for AI operations. Different models have different capabilities,
     * performance characteristics, and pricing.
     *
     * <p><strong>Common Model Names:</strong>
     * <ul>
     *   <li><strong>gpt-3.5-turbo</strong> - Cost-effective model for most tasks</li>
     *   <li><strong>gpt-4</strong> - More capable model for complex tasks</li>
     *   <li><strong>gpt-4-turbo</strong> - Latest GPT-4 with improved performance</li>
     *   <li><strong>gpt-4o</strong> - Optimized model with multimodal capabilities</li>
     * </ul>
     *
     * <p><strong>Configuration Sources (in order of precedence):</strong>
     * <ol>
     *   <li>OPENAI_MODEL_NAME environment variable</li>
     *   <li>openai.model.name system property</li>
     *   <li>model-name property in forage-model-open-ai.properties</li>
     *   <li>Default value: "gpt-3.5-turbo"</li>
     * </ol>
     *
     * @return the OpenAI model name, never null
     */
    public String modelName() {
        return ConfigStore.getInstance().get(MODEL_NAME.asNamed(prefix)).orElse(MODEL_NAME.defaultValue());
    }

    /**
     * Returns the base URL for the OpenAI API.
     *
     * <p>This method retrieves the base URL for OpenAI API calls. This is typically
     * used when connecting to OpenAI-compatible services or when using a custom proxy.
     *
     * <p><strong>Configuration Sources (in order of precedence):</strong>
     * <ol>
     *   <li>OPENAI_BASE_URL environment variable</li>
     *   <li>openai.base.url system property</li>
     *   <li>base-url property in forage-model-open-ai.properties</li>
     *   <li>No default value (returns null if not configured)</li>
     * </ol>
     *
     * @return the base URL, or null if not configured (uses OpenAI's default)
     */
    public String baseUrl() {
        return ConfigStore.getInstance().get(BASE_URL.asNamed(prefix)).orElse(null);
    }

    /**
     * Returns the temperature setting for response randomness.
     *
     * <p>Temperature controls the randomness of the model's responses. Lower values make
     * the output more focused and deterministic, while higher values increase creativity
     * and randomness.
     *
     * <p><strong>Configuration Sources (in order of precedence):</strong>
     * <ol>
     *   <li>OPENAI_TEMPERATURE environment variable</li>
     *   <li>openai.temperature system property</li>
     *   <li>temperature property in forage-model-open-ai.properties</li>
     *   <li>No default value (returns null if not configured)</li>
     * </ol>
     *
     * <p><strong>Valid Range:</strong> 0.0 to 2.0 (typical range is 0.0 to 1.0)
     *
     * @return the temperature value, or null if not configured
     */
    public Double temperature() {
        return ConfigStore.getInstance()
                .get(TEMPERATURE.asNamed(prefix))
                .map(Double::parseDouble)
                .orElse(null);
    }

    /**
     * Returns the maximum number of tokens to generate.
     *
     * <p>This parameter controls the maximum length of the generated response.
     * The total length of input tokens and generated tokens is limited by the
     * model's context length.
     *
     * <p><strong>Configuration Sources (in order of precedence):</strong>
     * <ol>
     *   <li>OPENAI_MAX_TOKENS environment variable</li>
     *   <li>openai.max.tokens system property</li>
     *   <li>max-tokens property in forage-model-open-ai.properties</li>
     *   <li>No default value (returns null if not configured)</li>
     * </ol>
     *
     * <p><strong>Valid Range:</strong> Positive integers (model-dependent maximum)
     *
     * @return the maximum tokens value, or null if not configured
     */
    public Integer maxTokens() {
        return ConfigStore.getInstance()
                .get(MAX_TOKENS.asNamed(prefix))
                .map(Integer::parseInt)
                .orElse(null);
    }

    /**
     * Returns the top-P (nucleus) sampling parameter.
     *
     * <p>Top-P sampling selects from the smallest set of tokens whose cumulative
     * probability exceeds P. This provides dynamic vocabulary filtering based
     * on the probability distribution.
     *
     * <p><strong>Configuration Sources (in order of precedence):</strong>
     * <ol>
     *   <li>OPENAI_TOP_P environment variable</li>
     *   <li>openai.top.p system property</li>
     *   <li>top-p property in forage-model-open-ai.properties</li>
     *   <li>No default value (returns null if not configured)</li>
     * </ol>
     *
     * <p><strong>Valid Range:</strong> 0.0 to 1.0
     *
     * @return the top-P value, or null if not configured
     */
    public Double topP() {
        return ConfigStore.getInstance()
                .get(TOP_P.asNamed(prefix))
                .map(Double::parseDouble)
                .orElse(null);
    }

    /**
     * Returns the frequency penalty parameter.
     *
     * <p>Frequency penalty reduces the likelihood of repeating the same line of text.
     * Positive values penalize tokens based on their frequency in the text so far.
     *
     * <p><strong>Configuration Sources (in order of precedence):</strong>
     * <ol>
     *   <li>OPENAI_FREQUENCY_PENALTY environment variable</li>
     *   <li>openai.frequency.penalty system property</li>
     *   <li>frequency-penalty property in forage-model-open-ai.properties</li>
     *   <li>No default value (returns null if not configured)</li>
     * </ol>
     *
     * <p><strong>Valid Range:</strong> -2.0 to 2.0
     *
     * @return the frequency penalty value, or null if not configured
     */
    public Double frequencyPenalty() {
        return ConfigStore.getInstance()
                .get(FREQUENCY_PENALTY.asNamed(prefix))
                .map(Double::parseDouble)
                .orElse(null);
    }

    /**
     * Returns the presence penalty parameter.
     *
     * <p>Presence penalty increases the likelihood of talking about new topics.
     * Positive values penalize tokens that have already appeared in the text.
     *
     * <p><strong>Configuration Sources (in order of precedence):</strong>
     * <ol>
     *   <li>OPENAI_PRESENCE_PENALTY environment variable</li>
     *   <li>openai.presence.penalty system property</li>
     *   <li>presence-penalty property in forage-model-open-ai.properties</li>
     *   <li>No default value (returns null if not configured)</li>
     * </ol>
     *
     * <p><strong>Valid Range:</strong> -2.0 to 2.0
     *
     * @return the presence penalty value, or null if not configured
     */
    public Double presencePenalty() {
        return ConfigStore.getInstance()
                .get(PRESENCE_PENALTY.asNamed(prefix))
                .map(Double::parseDouble)
                .orElse(null);
    }

    /**
     * Returns whether request logging is enabled.
     *
     * <p>When enabled, the OpenAI client will log all requests sent to the server.
     * This is useful for debugging and monitoring but should be disabled in production
     * to avoid logging sensitive information.
     *
     * <p><strong>Configuration Sources (in order of precedence):</strong>
     * <ol>
     *   <li>OPENAI_LOG_REQUESTS environment variable</li>
     *   <li>openai.log.requests system property</li>
     *   <li>log-requests property in forage-model-open-ai.properties</li>
     *   <li>No default value (returns null if not configured)</li>
     * </ol>
     *
     * <p><strong>Valid Values:</strong> "true" or "false" (case-insensitive)
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
     * <p>When enabled, the OpenAI client will log all responses received from the server.
     * This is useful for debugging and monitoring but should be disabled in production
     * to avoid logging sensitive information.
     *
     * <p><strong>Configuration Sources (in order of precedence):</strong>
     * <ol>
     *   <li>OPENAI_LOG_RESPONSES environment variable</li>
     *   <li>openai.log.responses system property</li>
     *   <li>log-responses property in camel-forage-model-open-ai.properties</li>
     *   <li>No default value (returns null if not configured)</li>
     * </ol>
     *
     * <p><strong>Valid Values:</strong> "true" or "false" (case-insensitive)
     *
     * @return true if response logging is enabled, false if disabled, null if not configured
     */
    public Boolean logResponses() {
        return ConfigStore.getInstance()
                .get(LOG_RESPONSES.asNamed(prefix))
                .map(Boolean::parseBoolean)
                .orElse(null);
    }

    public Duration timeout() {
        return ConfigStore.getInstance()
                .get(TIMEOUT.asNamed(prefix))
                .map(Duration::parse)
                .orElse(null);
    }

    public Boolean http1_1() {
        return ConfigStore.getInstance()
                .get(HTTP1_1.asNamed(prefix))
                .map(Boolean::parseBoolean)
                .orElse(null);
    }
}
