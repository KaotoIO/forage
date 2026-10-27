package io.kaoto.forage.models.chat.localai;

import io.kaoto.forage.core.util.config.ConfigEntries;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigTag;

public final class LocalAiConfigEntries extends ConfigEntries {
    public static final ConfigModule API_KEY = ConfigModule.of(
            LocalAiConfig.class,
            "forage.localai.api.key",
            "LocalAI API key for authentication (optional)",
            "API Key",
            null,
            "password",
            false,
            ConfigTag.SECURITY);
    public static final ConfigModule BASE_URL = ConfigModule.of(
            LocalAiConfig.class,
            "forage.localai.base.url",
            "The LocalAI server endpoint URL",
            "Base URL",
            null,
            ConfigModule.TYPE_STRING,
            true,
            ConfigTag.COMMON);
    public static final ConfigModule MODEL_NAME = ConfigModule.of(
            LocalAiConfig.class,
            "forage.localai.model.name",
            "The model to use (must be available on LocalAI server)",
            "Model Name",
            null,
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.COMMON);
    public static final ConfigModule TEMPERATURE = ConfigModule.of(
            LocalAiConfig.class,
            "forage.localai.temperature",
            "Temperature for response generation (0.0-2.0)",
            "Temperature",
            null,
            ConfigModule.TYPE_DOUBLE,
            false,
            ConfigTag.COMMON);
    public static final ConfigModule MAX_TOKENS = ConfigModule.of(
            LocalAiConfig.class,
            "forage.localai.max.tokens",
            "Maximum number of tokens for model responses",
            "Max Tokens",
            null,
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule TOP_P = ConfigModule.of(
            LocalAiConfig.class,
            "forage.localai.top.p",
            "Top-p (nucleus sampling) probability threshold (0.0-1.0)",
            "Top P",
            null,
            ConfigModule.TYPE_DOUBLE,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule PRESENCE_PENALTY = ConfigModule.of(
            LocalAiConfig.class,
            "forage.localai.presence.penalty",
            "Presence penalty for discouraging new topic introduction (-2.0 to 2.0)",
            "Presence Penalty",
            null,
            ConfigModule.TYPE_DOUBLE,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule FREQUENCY_PENALTY = ConfigModule.of(
            LocalAiConfig.class,
            "forage.localai.frequency.penalty",
            "Frequency penalty for discouraging token repetition (-2.0 to 2.0)",
            "Frequency Penalty",
            null,
            ConfigModule.TYPE_DOUBLE,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule SEED = ConfigModule.of(
            LocalAiConfig.class,
            "forage.localai.seed",
            "Seed for deterministic response generation",
            "Seed",
            null,
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule USER = ConfigModule.of(
            LocalAiConfig.class,
            "forage.localai.user",
            "User identifier for tracking and monitoring",
            "User",
            null,
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.COMMON);
    public static final ConfigModule TIMEOUT = ConfigModule.of(
            LocalAiConfig.class,
            "forage.localai.timeout",
            "Request timeout in seconds",
            "Timeout",
            null,
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule MAX_RETRIES = ConfigModule.of(
            LocalAiConfig.class,
            "forage.localai.max.retries",
            "Maximum number of retry attempts for failed requests",
            "Max Retries",
            null,
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule LOG_REQUESTS_AND_RESPONSES = ConfigModule.of(
            LocalAiConfig.class,
            "forage.localai.log.requests.and.responses",
            "Enable request and response logging",
            "Log Requests/Responses",
            null,
            "boolean",
            false,
            ConfigTag.ADVANCED);

    static {
        initModules(
                LocalAiConfigEntries.class,
                API_KEY,
                BASE_URL,
                MODEL_NAME,
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
