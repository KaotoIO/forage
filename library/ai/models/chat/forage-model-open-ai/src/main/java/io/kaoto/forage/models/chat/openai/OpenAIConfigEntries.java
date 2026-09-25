package io.kaoto.forage.models.chat.openai;

import io.kaoto.forage.core.util.config.ConfigEntries;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigTag;

public final class OpenAIConfigEntries extends ConfigEntries {
    public static final ConfigModule API_KEY = ConfigModule.of(
            OpenAIConfig.class,
            "forage.openai.api.key",
            "OpenAI API key for authentication",
            "API Key",
            null,
            ConfigModule.TYPE_PASSWORD,
            true,
            ConfigTag.SECURITY);
    public static final ConfigModule MODEL_NAME = ConfigModule.of(
            OpenAIConfig.class,
            "forage.openai.model.name",
            "The specific OpenAI model to use",
            "Model Name",
            "gpt-4o-mini",
            ConfigModule.TYPE_STRING,
            true,
            ConfigTag.COMMON);
    public static final ConfigModule BASE_URL = ConfigModule.of(
            OpenAIConfig.class,
            "forage.openai.base.url",
            "Custom base URL for OpenAI API",
            "Base URL",
            null,
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.COMMON);
    public static final ConfigModule TEMPERATURE = ConfigModule.of(
            OpenAIConfig.class,
            "forage.openai.temperature",
            "Temperature for response randomness (0.0-2.0)",
            "Temperature",
            null,
            ConfigModule.TYPE_DOUBLE,
            false,
            ConfigTag.COMMON);
    public static final ConfigModule MAX_TOKENS = ConfigModule.of(
            OpenAIConfig.class,
            "forage.openai.max.tokens",
            "Maximum number of tokens to generate",
            "Max Tokens",
            null,
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule TOP_P = ConfigModule.of(
            OpenAIConfig.class,
            "forage.openai.top.p",
            "Top-P (nucleus) sampling parameter (0.0-1.0)",
            "Top P",
            null,
            ConfigModule.TYPE_DOUBLE,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule FREQUENCY_PENALTY = ConfigModule.of(
            OpenAIConfig.class,
            "forage.openai.frequency.penalty",
            "Frequency penalty (-2.0 to 2.0)",
            "Frequency Penalty",
            null,
            ConfigModule.TYPE_DOUBLE,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule PRESENCE_PENALTY = ConfigModule.of(
            OpenAIConfig.class,
            "forage.openai.presence.penalty",
            "Presence penalty (-2.0 to 2.0)",
            "Presence Penalty",
            null,
            ConfigModule.TYPE_DOUBLE,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule LOG_REQUESTS = ConfigModule.of(
            OpenAIConfig.class,
            "forage.openai.log.requests",
            "Enable request logging",
            "Log Requests",
            null,
            ConfigModule.TYPE_BOOLEAN,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule LOG_RESPONSES = ConfigModule.of(
            OpenAIConfig.class,
            "forage.openai.log.responses",
            "Enable response logging",
            "Log Responses",
            null,
            ConfigModule.TYPE_BOOLEAN,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule TIMEOUT = ConfigModule.of(
            OpenAIConfig.class,
            "forage.openai.timeout",
            "Request timeout duration",
            "Timeout",
            null,
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule HTTP1_1 = ConfigModule.of(
            OpenAIConfig.class,
            "forage.openai.http1",
            "Use HTTP/1.1 instead of HTTP/2",
            "Use HTTP/1.1",
            null,
            ConfigModule.TYPE_BOOLEAN,
            false,
            ConfigTag.ADVANCED);

    static {
        initModules(
                OpenAIConfigEntries.class,
                API_KEY,
                MODEL_NAME,
                BASE_URL,
                TEMPERATURE,
                MAX_TOKENS,
                TOP_P,
                FREQUENCY_PENALTY,
                PRESENCE_PENALTY,
                LOG_REQUESTS,
                LOG_RESPONSES,
                TIMEOUT,
                HTTP1_1);
    }
}
