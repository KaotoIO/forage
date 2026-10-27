package io.kaoto.forage.models.chat.bedrock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.core.ai.ModelProvider;
import io.kaoto.forage.core.annotations.ForageBean;
import dev.langchain4j.model.bedrock.BedrockChatModel;
import dev.langchain4j.model.bedrock.BedrockChatRequestParameters;
import dev.langchain4j.model.chat.ChatModel;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

/**
 * Provider for creating Amazon Bedrock chat models with configurable parameters.
 *
 * <p>This provider creates instances of Bedrock models using configuration
 * values managed by {@link BedrockConfig}. It supports all Bedrock foundation models
 * through a unified API.
 *
 * <p><strong>Supported Model Families:</strong>
 * <ul>
 *   <li>Anthropic Claude - anthropic.claude-* models</li>
 *   <li>Meta Llama - meta.llama* models</li>
 *   <li>Amazon Titan - amazon.titan-* models</li>
 *   <li>Cohere Command - cohere.command-* models</li>
 *   <li>Mistral AI - mistral.* models</li>
 * </ul>
 *
 * <p><strong>Configuration:</strong>
 * <ul>
 *   <li>Model ID: Configured via BEDROCK_MODEL_ID environment variable (required)</li>
 *   <li>Region: Configured via BEDROCK_REGION environment variable or defaults to "us-east-1"</li>
 *   <li>AWS Credentials: Optional, uses default credential chain if not provided</li>
 *   <li>Temperature: Optionally configured via BEDROCK_TEMPERATURE environment variable</li>
 *   <li>Max Tokens: Optionally configured via BEDROCK_MAX_TOKENS environment variable</li>
 *   <li>Top-P: Optionally configured via BEDROCK_TOP_P environment variable</li>
 * </ul>
 *
 * <p><strong>Usage:</strong>
 * <pre>{@code
 * // Configuration is automatic through environment variables
 * BedrockProvider provider = new BedrockProvider();
 * ChatModel model = provider.create();
 * }</pre>
 *
 * @see BedrockConfig
 * @see ModelProvider
 * @since 1.0
 */
@ForageBean(
        value = "bedrock",
        components = {"camel-langchain4j-agent"},
        feature = "Chat Model",
        configClass = BedrockConfig.class,
        description = "Amazon Bedrock multi-model provider supporting Claude, Llama, Titan, Cohere, and Mistral")
public class BedrockProvider implements ModelProvider {
    private static final Logger LOG = LoggerFactory.getLogger(BedrockProvider.class);

    /**
     * Creates a new Bedrock chat model instance with the configured parameters.
     *
     * <p>This method creates a BedrockChatModel using the unified Bedrock API.
     * All model families are supported through the same interface.
     *
     * @param id optional prefix for named configurations
     * @return a new configured Bedrock chat model instance
     */
    @Override
    public ChatModel create(String id) {
        BedrockConfig config = new BedrockConfig(id);

        String modelId = config.modelId();
        String region = config.region();

        LOG.trace(
                "Creating Bedrock model: {} in region {} with configuration: temperature={}, maxTokens={}, topP={}",
                modelId,
                region,
                config.temperature(),
                config.maxTokens(),
                config.topP());

        BedrockRuntimeClient client = buildBedrockClient(config);

        BedrockChatRequestParameters.Builder requestParamsBuilder = BedrockChatRequestParameters.builder();

        if (config.temperature() != null) {
            requestParamsBuilder.temperature(config.temperature());
        }

        if (config.maxTokens() != null) {
            requestParamsBuilder.maxOutputTokens(config.maxTokens());
        }

        if (config.topP() != null) {
            requestParamsBuilder.topP(config.topP());
        }

        return BedrockChatModel.builder()
                .client(client)
                .modelId(modelId)
                .defaultRequestParameters(requestParamsBuilder.build())
                .build();
    }

    private BedrockRuntimeClient buildBedrockClient(BedrockConfig config) {
        AwsCredentialsProvider credentialsProvider;

        if (config.accessKeyId() != null && config.secretAccessKey() != null && config.sessionToken() != null) {
            credentialsProvider = StaticCredentialsProvider.create(AwsSessionCredentials.create(
                    config.accessKeyId(), config.secretAccessKey(), config.sessionToken()));
        } else if (config.accessKeyId() != null && config.secretAccessKey() != null) {
            credentialsProvider = StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(config.accessKeyId(), config.secretAccessKey()));
        } else {
            credentialsProvider = DefaultCredentialsProvider.create();
        }

        return BedrockRuntimeClient.builder()
                .region(Region.of(config.region()))
                .credentialsProvider(credentialsProvider)
                .build();
    }
}
