package io.kaoto.forage.models.chat.ollama;

import io.kaoto.forage.core.util.config.ConfigEntries;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigTag;

public final class OllamaConfigEntries extends ConfigEntries {
    public static final ConfigModule BASE_URL = ConfigModule.of(
            OllamaConfig.class,
            "forage.ollama.base.url",
            "The base URL of the Ollama server",
            "Base URL",
            "http://localhost:11434",
            "string",
            false,
            ConfigTag.COMMON);
    public static final ConfigModule MODEL_NAME = ConfigModule.of(
            OllamaConfig.class,
            "forage.ollama.model.name",
            "The Ollama model to use",
            "Model Name",
            "llama3",
            "string",
            false,
            ConfigTag.COMMON);
    public static final ConfigModule TEMPERATURE = ConfigModule.of(
            OllamaConfig.class,
            "forage.ollama.temperature",
            "Temperature for response randomness (0.0-2.0)",
            "Temperature",
            null,
            "double",
            false,
            ConfigTag.COMMON);
    public static final ConfigModule TOP_K = ConfigModule.of(
            OllamaConfig.class,
            "forage.ollama.top.k",
            "Top-K sampling parameter",
            "Top K",
            null,
            "integer",
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule TOP_P = ConfigModule.of(
            OllamaConfig.class,
            "forage.ollama.top.p",
            "Top-P (nucleus) sampling parameter (0.0-1.0)",
            "Top P",
            null,
            "double",
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule MIN_P = ConfigModule.of(
            OllamaConfig.class,
            "forage.ollama.min.p",
            "Minimum probability threshold (0.0-1.0)",
            "Min P",
            null,
            "double",
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule NUM_CTX = ConfigModule.of(
            OllamaConfig.class,
            "forage.ollama.num.ctx",
            "Context window size",
            "Context Size",
            null,
            "integer",
            false,
            ConfigTag.COMMON);
    public static final ConfigModule LOG_REQUESTS = ConfigModule.of(
            OllamaConfig.class,
            "forage.ollama.log.requests",
            "Enable request logging",
            "Log Requests",
            null,
            "boolean",
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule LOG_RESPONSES = ConfigModule.of(
            OllamaConfig.class,
            "forage.ollama.log.responses",
            "Enable response logging",
            "Log Responses",
            null,
            "boolean",
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule TIMEOUT = ConfigModule.of(
            OllamaConfig.class,
            "forage.ollama.timeout",
            "Request timeout duration in ISO-8601 format (e.g. PT120S for 120 seconds)",
            "Timeout",
            null,
            "string",
            false,
            ConfigTag.COMMON);

    static {
        initModules(
                OllamaConfigEntries.class,
                BASE_URL,
                MODEL_NAME,
                TEMPERATURE,
                TOP_K,
                TOP_P,
                MIN_P,
                NUM_CTX,
                LOG_REQUESTS,
                LOG_RESPONSES,
                TIMEOUT);
    }
}
