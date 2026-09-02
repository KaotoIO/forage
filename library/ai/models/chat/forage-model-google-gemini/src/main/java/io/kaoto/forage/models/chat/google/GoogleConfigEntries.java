package io.kaoto.forage.models.chat.google;

import io.kaoto.forage.core.util.config.ConfigEntries;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigTag;

public final class GoogleConfigEntries extends ConfigEntries {
    public static final ConfigModule API_KEY = ConfigModule.of(
            GoogleConfig.class,
            "forage.google.api.key",
            "Google AI API key for authentication",
            "API Key",
            null,
            "password",
            true,
            ConfigTag.COMMON);
    public static final ConfigModule MODEL_NAME = ConfigModule.of(
            GoogleConfig.class,
            "forage.google.model.name",
            "Google Gemini model name (e.g., gemini-pro, gemini-pro-vision, gemini-1.5-pro)",
            "Model Name",
            null,
            "string",
            true,
            ConfigTag.COMMON);
    public static final ConfigModule TEMPERATURE = ConfigModule.of(
            GoogleConfig.class,
            "forage.google.temperature",
            "Temperature for response randomness (0.0-2.0)",
            "Temperature",
            null,
            "double",
            false,
            ConfigTag.COMMON);
    public static final ConfigModule TIMEOUT = ConfigModule.of(
            GoogleConfig.class,
            "forage.google.timeout",
            "Request timeout in seconds",
            "Timeout",
            null,
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule LOG_REQUESTS = ConfigModule.of(
            GoogleConfig.class,
            "forage.google.log.requests",
            "Enable request and response logging",
            "Log Requests",
            null,
            "boolean",
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule MAX_OUTPUT_TOKENS = ConfigModule.of(
            GoogleConfig.class,
            "forage.google.max.output.tokens",
            "Maximum number of output tokens",
            "Max Output Tokens",
            null,
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule TOP_P = ConfigModule.of(
            GoogleConfig.class,
            "forage.google.top.p",
            "Top-p (nucleus sampling) parameter (0.0-1.0)",
            "Top P",
            null,
            "double",
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule TOP_K = ConfigModule.of(
            GoogleConfig.class,
            "forage.google.top.k",
            "Top-k sampling parameter",
            "Top K",
            null,
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.ADVANCED);

    static {
        initModules(
                GoogleConfigEntries.class,
                API_KEY,
                MODEL_NAME,
                TEMPERATURE,
                TIMEOUT,
                LOG_REQUESTS,
                MAX_OUTPUT_TOKENS,
                TOP_P,
                TOP_K);
    }
}
