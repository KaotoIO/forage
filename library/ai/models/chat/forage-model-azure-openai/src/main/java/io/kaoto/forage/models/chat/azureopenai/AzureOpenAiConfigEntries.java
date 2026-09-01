package io.kaoto.forage.models.chat.azureopenai;

import io.kaoto.forage.core.util.config.ConfigEntries;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigTag;

public final class AzureOpenAiConfigEntries extends ConfigEntries {
    public static final ConfigModule API_KEY = ConfigModule.of(
            AzureOpenAiConfig.class,
            "forage.azure.openai.api.key",
            "Azure OpenAI API key for authentication",
            "API Key",
            null,
            "password",
            true,
            ConfigTag.COMMON);
    public static final ConfigModule ENDPOINT = ConfigModule.of(
            AzureOpenAiConfig.class,
            "forage.azure.openai.endpoint",
            "Azure OpenAI resource endpoint URL (e.g., https://your-resource.openai.azure.com/)",
            "Endpoint",
            null,
            ConfigModule.TYPE_STRING,
            true,
            ConfigTag.COMMON);
    public static final ConfigModule DEPLOYMENT_NAME = ConfigModule.of(
            AzureOpenAiConfig.class,
            "forage.azure.openai.deployment.name",
            "Azure OpenAI deployment name (e.g., gpt-35-turbo, gpt-4)",
            "Deployment Name",
            null,
            ConfigModule.TYPE_STRING,
            true,
            ConfigTag.COMMON);
    public static final ConfigModule SERVICE_VERSION = ConfigModule.of(
            AzureOpenAiConfig.class,
            "forage.azure.openai.service.version",
            "Azure OpenAI API service version (e.g., 2024-02-01)",
            "Service Version",
            null,
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.COMMON);
    public static final ConfigModule TEMPERATURE = ConfigModule.of(
            AzureOpenAiConfig.class,
            "forage.azure.openai.temperature",
            "Temperature for response generation (0.0-2.0): lower values are more deterministic, higher values are more creative",
            "Temperature",
            null,
            ConfigModule.TYPE_DOUBLE,
            false,
            ConfigTag.COMMON);
    public static final ConfigModule MAX_TOKENS = ConfigModule.of(
            AzureOpenAiConfig.class,
            "forage.azure.openai.max.tokens",
            "Maximum number of tokens in the model's response",
            "Max Tokens",
            null,
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.COMMON);
    public static final ConfigModule TOP_P = ConfigModule.of(
            AzureOpenAiConfig.class,
            "forage.azure.openai.top.p",
            "Top-p (nucleus sampling) probability threshold (0.0-1.0)",
            "Top P",
            null,
            ConfigModule.TYPE_DOUBLE,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule PRESENCE_PENALTY = ConfigModule.of(
            AzureOpenAiConfig.class,
            "forage.azure.openai.presence.penalty",
            "Presence penalty for discouraging new topic introduction (-2.0 to 2.0)",
            "Presence Penalty",
            null,
            ConfigModule.TYPE_DOUBLE,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule FREQUENCY_PENALTY = ConfigModule.of(
            AzureOpenAiConfig.class,
            "forage.azure.openai.frequency.penalty",
            "Frequency penalty for discouraging token repetition (-2.0 to 2.0)",
            "Frequency Penalty",
            null,
            ConfigModule.TYPE_DOUBLE,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule SEED = ConfigModule.of(
            AzureOpenAiConfig.class,
            "forage.azure.openai.seed",
            "Seed for deterministic response generation (same seed + same input = similar output)",
            "Seed",
            null,
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule USER = ConfigModule.of(
            AzureOpenAiConfig.class,
            "forage.azure.openai.user",
            "User identifier for tracking and monitoring API usage",
            "User ID",
            null,
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule TIMEOUT = ConfigModule.of(
            AzureOpenAiConfig.class,
            "forage.azure.openai.timeout",
            "Request timeout in seconds",
            "Timeout",
            null,
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule MAX_RETRIES = ConfigModule.of(
            AzureOpenAiConfig.class,
            "forage.azure.openai.max.retries",
            "Maximum number of retry attempts for failed requests",
            "Max Retries",
            null,
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule LOG_REQUESTS_AND_RESPONSES = ConfigModule.of(
            AzureOpenAiConfig.class,
            "forage.azure.openai.log.requests.and.responses",
            "Enable logging of requests and responses (warning: may log sensitive data)",
            "Log Requests",
            null,
            "boolean",
            false,
            ConfigTag.ADVANCED);

    static {
        initModules(
                AzureOpenAiConfigEntries.class,
                API_KEY,
                ENDPOINT,
                DEPLOYMENT_NAME,
                SERVICE_VERSION,
                TEMPERATURE,
                MAX_TOKENS,
                TOP_P,
                PRESENCE_PENALTY,
                FREQUENCY_PENALTY,
                SEED,
                USER,
                TIMEOUT,
                MAX_RETRIES,
                LOG_REQUESTS_AND_RESPONSES);
    }
}
