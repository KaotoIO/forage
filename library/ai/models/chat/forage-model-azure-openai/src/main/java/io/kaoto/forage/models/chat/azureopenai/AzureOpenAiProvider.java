package io.kaoto.forage.models.chat.azureopenai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.core.ai.ModelProvider;
import io.kaoto.forage.core.annotations.ForageBean;
import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.chat.ChatModel;

import static java.time.Duration.ofSeconds;

/**
 * Provider for creating Azure OpenAI chat models
 */
@ForageBean(
        value = "azure-openai",
        components = {"camel-langchain4j-agent"},
        feature = "Chat Model",
        description = "OpenAI models hosted on Microsoft Azure")
public class AzureOpenAiProvider implements ModelProvider {
    private static final Logger LOG = LoggerFactory.getLogger(AzureOpenAiProvider.class);

    @Override
    public ChatModel create(String id) {
        final AzureOpenAiConfig config = new AzureOpenAiConfig(id);
        LOG.trace("Creating Azure OpenAI chat model");

        AzureOpenAiChatModel.Builder builder = AzureOpenAiChatModel.builder()
                .apiKey(config.apiKey())
                .endpoint(config.endpoint())
                .deploymentName(config.deploymentName());

        // Configure optional service version
        if (config.serviceVersion() != null) {
            builder.serviceVersion(config.serviceVersion());
        }

        // Configure model behavior parameters with defaults for unset values
        builder.temperature(config.temperature() != null ? config.temperature() : 1.0);

        if (config.maxTokens() != null) {
            builder.maxTokens(config.maxTokens());
        }

        if (config.topP() != null) {
            builder.topP(config.topP());
        }

        if (config.presencePenalty() != null) {
            builder.presencePenalty(config.presencePenalty());
        }

        if (config.frequencyPenalty() != null) {
            builder.frequencyPenalty(config.frequencyPenalty());
        }

        if (config.seed() != null) {
            builder.seed(config.seed());
        }

        if (config.user() != null) {
            builder.user(config.user());
        }

        // Configure connection and reliability settings
        int timeoutSeconds = config.timeoutSeconds() != null ? config.timeoutSeconds() : 60;
        builder.timeout(ofSeconds(timeoutSeconds));

        if (config.maxRetries() != null) {
            builder.maxRetries(config.maxRetries());
        }

        // Configure logging settings
        boolean logRequestsAndResponses = config.logRequestsAndResponses() == null || config.logRequestsAndResponses();
        builder.logRequestsAndResponses(logRequestsAndResponses);

        return builder.build();
    }
}
