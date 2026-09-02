package io.kaoto.forage.models.chat.anthropic;

import io.kaoto.forage.core.util.config.ConfigEntries;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigTag;

public final class AnthropicConfigEntries extends ConfigEntries {
    public static final ConfigModule API_KEY = ConfigModule.of(
            AnthropicConfig.class,
            "forage.anthropic.api.key",
            "Anthropic API key for authentication",
            "API Key",
            null,
            "password",
            true,
            ConfigTag.COMMON);
    public static final ConfigModule MODEL_NAME = ConfigModule.of(
            AnthropicConfig.class,
            "forage.anthropic.model.name",
            "Claude model name (e.g., claude-sonnet-4-20250514, claude-haiku-4-5-20251001, claude-opus-4-20250115)",
            "Model Name",
            "claude-sonnet-4-20250514",
            "string",
            false,
            ConfigTag.COMMON);
    public static final ConfigModule TEMPERATURE = ConfigModule.of(
            AnthropicConfig.class,
            "forage.anthropic.temperature",
            "Temperature for response generation (0.0-1.0): lower values are more deterministic, higher values are more creative",
            "Temperature",
            null,
            "double",
            false,
            ConfigTag.COMMON);
    public static final ConfigModule MAX_TOKENS = ConfigModule.of(
            AnthropicConfig.class,
            "forage.anthropic.max.tokens",
            "Maximum number of tokens in the model's response",
            "Max Tokens",
            null,
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.COMMON);
    public static final ConfigModule TOP_P = ConfigModule.of(
            AnthropicConfig.class,
            "forage.anthropic.top.p",
            "Top-p (nucleus sampling) probability threshold (0.0-1.0)",
            "Top P",
            null,
            "double",
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule TOP_K = ConfigModule.of(
            AnthropicConfig.class,
            "forage.anthropic.top.k",
            "Top-k sampling parameter: limits the model to consider only the top-k most probable tokens",
            "Top K",
            null,
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule STOP_SEQUENCES = ConfigModule.of(
            AnthropicConfig.class,
            "forage.anthropic.stop.sequences",
            "Comma-separated stop sequences that cause the model to stop generating further tokens",
            "Stop Sequences",
            null,
            "string",
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule TIMEOUT = ConfigModule.of(
            AnthropicConfig.class,
            "forage.anthropic.timeout",
            "Request timeout in seconds",
            "Timeout",
            null,
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule MAX_RETRIES = ConfigModule.of(
            AnthropicConfig.class,
            "forage.anthropic.max.retries",
            "Maximum number of retry attempts for failed requests",
            "Max Retries",
            null,
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule LOG_REQUESTS_AND_RESPONSES = ConfigModule.of(
            AnthropicConfig.class,
            "forage.anthropic.log.requests.and.responses",
            "Enable logging of requests and responses (warning: may log sensitive data)",
            "Log Requests",
            null,
            "boolean",
            false,
            ConfigTag.ADVANCED);

    static {
        initModules(
                AnthropicConfigEntries.class,
                API_KEY,
                MODEL_NAME,
                TEMPERATURE,
                MAX_TOKENS,
                TOP_P,
                TOP_K,
                STOP_SEQUENCES,
                TIMEOUT,
                MAX_RETRIES,
                LOG_REQUESTS_AND_RESPONSES);
    }
}
