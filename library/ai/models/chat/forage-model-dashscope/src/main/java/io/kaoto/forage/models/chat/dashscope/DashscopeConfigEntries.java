package io.kaoto.forage.models.chat.dashscope;

import io.kaoto.forage.core.util.config.ConfigEntries;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigTag;

public final class DashscopeConfigEntries extends ConfigEntries {
    public static final ConfigModule API_KEY = ConfigModule.of(
            DashscopeConfig.class,
            "forage.dashscope.api.key",
            "Alibaba Dashscope API key for authentication",
            "API Key",
            null,
            "password",
            true,
            ConfigTag.COMMON);
    public static final ConfigModule MODEL_NAME = ConfigModule.of(
            DashscopeConfig.class,
            "forage.dashscope.model.name",
            "Qwen model name (e.g., qwen-turbo, qwen-plus, qwen-max, qwen-max-longcontext)",
            "Model Name",
            "qwen-turbo",
            "string",
            false,
            ConfigTag.COMMON);
    public static final ConfigModule TEMPERATURE = ConfigModule.of(
            DashscopeConfig.class,
            "forage.dashscope.temperature",
            "Temperature for response generation (0.0-2.0): lower values are more deterministic, higher values are more creative",
            "Temperature",
            null,
            "double",
            false,
            ConfigTag.COMMON);
    public static final ConfigModule MAX_TOKENS = ConfigModule.of(
            DashscopeConfig.class,
            "forage.dashscope.max.tokens",
            "Maximum number of tokens in the model's response",
            "Max Tokens",
            null,
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.COMMON);
    public static final ConfigModule TOP_P = ConfigModule.of(
            DashscopeConfig.class,
            "forage.dashscope.top.p",
            "Top-p (nucleus sampling) probability threshold (0.0-1.0)",
            "Top P",
            null,
            ConfigModule.TYPE_DOUBLE,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule TOP_K = ConfigModule.of(
            DashscopeConfig.class,
            "forage.dashscope.top.k",
            "Top-k sampling parameter: limits the model to consider only the top-k most probable tokens",
            "Top K",
            null,
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule REPETITION_PENALTY = ConfigModule.of(
            DashscopeConfig.class,
            "forage.dashscope.repetition.penalty",
            "Repetition penalty for discouraging token repetition (0.0-2.0)",
            "Repetition Penalty",
            null,
            ConfigModule.TYPE_DOUBLE,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule SEED = ConfigModule.of(
            DashscopeConfig.class,
            "forage.dashscope.seed",
            "Seed for deterministic response generation (same seed + same input = similar output)",
            "Seed",
            null,
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule ENABLE_SEARCH = ConfigModule.of(
            DashscopeConfig.class,
            "forage.dashscope.enable.search",
            "Enable web search functionality to provide more up-to-date information",
            "Enable Search",
            null,
            "boolean",
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule TIMEOUT = ConfigModule.of(
            DashscopeConfig.class,
            "forage.dashscope.timeout",
            "Request timeout in seconds",
            "Timeout",
            null,
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule MAX_RETRIES = ConfigModule.of(
            DashscopeConfig.class,
            "forage.dashscope.max.retries",
            "Maximum number of retry attempts for failed requests",
            "Max Retries",
            null,
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule LOG_REQUESTS_AND_RESPONSES = ConfigModule.of(
            DashscopeConfig.class,
            "forage.dashscope.log.requests.and.responses",
            "Enable logging of requests and responses (warning: may log sensitive data)",
            "Log Requests",
            null,
            "boolean",
            false,
            ConfigTag.ADVANCED);

    static {
        initModules(
                DashscopeConfigEntries.class,
                API_KEY,
                MODEL_NAME,
                TEMPERATURE,
                MAX_TOKENS,
                TOP_P,
                TOP_K,
                REPETITION_PENALTY,
                SEED,
                ENABLE_SEARCH,
                TIMEOUT,
                MAX_RETRIES,
                LOG_REQUESTS_AND_RESPONSES);
    }
}
