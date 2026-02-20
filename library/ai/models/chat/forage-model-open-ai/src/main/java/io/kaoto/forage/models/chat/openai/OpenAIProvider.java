package io.kaoto.forage.models.chat.openai;

import java.net.http.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.core.ai.ModelProvider;
import io.kaoto.forage.core.annotations.ForageBean;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

/**
 * Provider for creating OpenAI chat models with configurable parameters.
 *
 * <p>This provider creates instances of {@link OpenAiChatModel} using configuration
 * values managed by {@link OpenAIConfig}. The configuration supports environment
 * variables, system properties, and configuration files for flexible deployment.
 *
 * <p><strong>Configuration:</strong>
 * <ul>
 *   <li>API Key: Configured via OPENAI_API_KEY environment variable (required)</li>
 *   <li>Model Name: Configured via OPENAI_MODEL_NAME environment variable or defaults to "gpt-3.5-turbo"</li>
 *   <li>Base URL: Optionally configured via OPENAI_BASE_URL environment variable (no default)</li>
 *   <li>Temperature: Optionally configured via OPENAI_TEMPERATURE environment variable (no default)</li>
 *   <li>Max Tokens: Optionally configured via OPENAI_MAX_TOKENS environment variable (no default)</li>
 *   <li>Top-P: Optionally configured via OPENAI_TOP_P environment variable (no default)</li>
 *   <li>Frequency Penalty: Optionally configured via OPENAI_FREQUENCY_PENALTY environment variable (no default)</li>
 *   <li>Presence Penalty: Optionally configured via OPENAI_PRESENCE_PENALTY environment variable (no default)</li>
 *   <li>Request Logging: Optionally configured via OPENAI_LOG_REQUESTS environment variable (no default)</li>
 *   <li>Response Logging: Optionally configured via OPENAI_LOG_RESPONSES environment variable (no default)</li>
 * </ul>
 *
 * <p><strong>Usage:</strong>
 * <pre>{@code
 * // Configuration is automatic through environment variables or defaults
 * OpenAIProvider provider = new OpenAIProvider();
 * ChatModel model = provider.newModel();
 * }</pre>
 *
 * @see OpenAIConfig
 * @see ModelProvider
 * @since 1.0
 */
@ForageBean(
        value = "openai",
        components = {"camel-langchain4j-agent"},
        feature = "Chat Model",
        description = "OpenAI API-compatible models")
public class OpenAIProvider implements ModelProvider {
    private static final Logger LOG = LoggerFactory.getLogger(OpenAIProvider.class);

    /**
     * Creates a new OpenAI chat model instance with the configured parameters.
     *
     * <p>This method creates an {@link OpenAiChatModel} using the API key, model name,
     * and other parameters from the configuration. The model is ready to use for chat
     * operations once created.
     *
     * @return a new configured OpenAI chat model instance
     */
    @Override
    public ChatModel create(String id) {
        OpenAIConfig config = new OpenAIConfig(id);

        String apiKey = config.apiKey();
        String modelName = config.modelName();
        String baseUrl = config.baseUrl();
        Double temperature = config.temperature();
        Integer maxTokens = config.maxTokens();
        Double topP = config.topP();
        Double frequencyPenalty = config.frequencyPenalty();
        Double presencePenalty = config.presencePenalty();
        Boolean logRequests = config.logRequests();
        Boolean logResponses = config.logResponses();

        LOG.trace(
                "Creating OpenAI model: {} with configuration: baseUrl={}, temperature={}, maxTokens={}, topP={}, frequencyPenalty={}, presencePenalty={}, logRequests={}, logResponses={}",
                modelName,
                baseUrl,
                temperature,
                maxTokens,
                topP,
                frequencyPenalty,
                presencePenalty,
                logRequests,
                logResponses);

        OpenAiChatModel.OpenAiChatModelBuilder builder =
                OpenAiChatModel.builder().apiKey(apiKey).modelName(modelName);

        // Only set optional parameters if they are configured
        if (baseUrl != null) {
            builder.baseUrl(baseUrl);
        }

        if (temperature != null) {
            builder.temperature(temperature);
        }

        if (maxTokens != null) {
            builder.maxTokens(maxTokens);
        }

        if (topP != null) {
            builder.topP(topP);
        }

        if (frequencyPenalty != null) {
            builder.frequencyPenalty(frequencyPenalty);
        }

        if (presencePenalty != null) {
            builder.presencePenalty(presencePenalty);
        }

        if (logRequests != null) {
            builder.logRequests(logRequests);
        }

        if (logResponses != null) {
            builder.logResponses(logResponses);
        }

        if (config.timeout() != null) {
            builder.timeout(config.timeout());
        }

        if (Boolean.TRUE.equals(config.http1_1())) {
            JdkHttpClientBuilder clientBuilder = new JdkHttpClientBuilder();

            clientBuilder.httpClientBuilder(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1));

            builder.httpClientBuilder(clientBuilder);
        }

        return builder.build();
    }
}
