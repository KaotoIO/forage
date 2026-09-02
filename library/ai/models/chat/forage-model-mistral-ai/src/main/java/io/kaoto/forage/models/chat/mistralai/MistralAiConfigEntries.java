package io.kaoto.forage.models.chat.mistralai;

import io.kaoto.forage.core.util.config.ConfigEntries;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigTag;

public final class MistralAiConfigEntries extends ConfigEntries {
    public static final ConfigModule API_KEY = ConfigModule.of(
            MistralAiConfig.class,
            "forage.mistralai.api.key",
            "MistralAI API key for authentication",
            "API Key",
            null,
            "password",
            true,
            ConfigTag.COMMON);
    public static final ConfigModule MODEL_NAME = ConfigModule.of(
            MistralAiConfig.class,
            "forage.mistralai.model.name",
            "The MistralAI model to use",
            "Model Name",
            "mistral-large-latest",
            "string",
            false,
            ConfigTag.COMMON);
    public static final ConfigModule TEMPERATURE = ConfigModule.of(
            MistralAiConfig.class,
            "forage.mistralai.temperature",
            "Temperature for response generation (0.0-1.0)",
            "Temperature",
            null,
            "double",
            false,
            ConfigTag.COMMON);
    public static final ConfigModule MAX_TOKENS = ConfigModule.of(
            MistralAiConfig.class,
            "forage.mistralai.max.tokens",
            "Maximum number of tokens for model responses",
            "Max Tokens",
            null,
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule TOP_P = ConfigModule.of(
            MistralAiConfig.class,
            "forage.mistralai.top.p",
            "Top-p (nucleus sampling) parameter (0.0-1.0)",
            "Top P",
            null,
            "double",
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule RANDOM_SEED = ConfigModule.of(
            MistralAiConfig.class,
            "forage.mistralai.random.seed",
            "Random seed for reproducible results",
            "Random Seed",
            null,
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule TIMEOUT = ConfigModule.of(
            MistralAiConfig.class,
            "forage.mistralai.timeout",
            "Request timeout in seconds",
            "Timeout",
            null,
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule MAX_RETRIES = ConfigModule.of(
            MistralAiConfig.class,
            "forage.mistralai.max.retries",
            "Maximum number of retry attempts",
            "Max Retries",
            null,
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule LOG_REQUESTS_AND_RESPONSES = ConfigModule.of(
            MistralAiConfig.class,
            "forage.mistralai.log.requests.and.responses",
            "Enable request and response logging",
            "Log Requests/Responses",
            null,
            "boolean",
            false,
            ConfigTag.ADVANCED);

    static {
        initModules(
                MistralAiConfigEntries.class,
                API_KEY,
                MODEL_NAME,
                TEMPERATURE,
                MAX_TOKENS,
                TOP_P,
                RANDOM_SEED,
                TIMEOUT,
                MAX_RETRIES,
                LOG_REQUESTS_AND_RESPONSES);
    }
}
