package io.kaoto.forage.models.embeddings.ollama;

import io.kaoto.forage.core.util.config.ConfigEntries;
import io.kaoto.forage.core.util.config.ConfigEntry;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigTag;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class OllamaEmbeddingConfigEntries extends ConfigEntries {
    public static final ConfigModule BASE_URL = ConfigModule.of(
            OllamaEmbedddingConfig.class,
            "forage.ollama.embedding.model.base.url",
            "The base URL of the Ollama server",
            "Base URL",
            "http://localhost:11434",
            "string",
            false,
            ConfigTag.COMMON);
    public static final ConfigModule MODEL_NAME = ConfigModule.of(
            OllamaEmbedddingConfig.class,
            "forage.ollama.embedding.model.name",
            "The Ollama model to use",
            "Model Name",
            "llama3",
            "string",
            false,
            ConfigTag.COMMON);
    public static final ConfigModule TIMEOUT = ConfigModule.of(
            OllamaEmbedddingConfig.class,
            "forage.ollama.embedding.model.timeout",
            "Used for the HttpClientBuilder that will be used to communicate with Ollama",
            "Timeout",
            null,
            "Duration",
            false,
            ConfigTag.COMMON);
    public static final ConfigModule MAX_RETRIES = ConfigModule.of(
            OllamaEmbedddingConfig.class,
            "forage.ollama.embedding.model.max.retries",
            "Used for the HttpClientBuilder that will be used to communicate with Ollama",
            "Max retries",
            null,
            "int",
            false,
            ConfigTag.COMMON);
    public static final ConfigModule LOG_REQUESTS = ConfigModule.of(
            OllamaEmbedddingConfig.class,
            "forage.ollama.embedding.model.log.requests",
            "Enable request logging",
            "Log Requests",
            null,
            "boolean",
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule LOG_RESPONSES = ConfigModule.of(
            OllamaEmbedddingConfig.class,
            "forage.ollama.embedding.model.log.responses",
            "Enable response logging",
            "Log Responses",
            null,
            "boolean",
            false,
            ConfigTag.ADVANCED);
    private static final Map<ConfigModule, ConfigEntry> CONFIG_MODULES = new ConcurrentHashMap<>();

    static {
        init();
    }

    static void init() {
        CONFIG_MODULES.put(BASE_URL, ConfigEntry.fromModule());
        CONFIG_MODULES.put(MODEL_NAME, ConfigEntry.fromModule());
        CONFIG_MODULES.put(TIMEOUT, ConfigEntry.fromModule());
        CONFIG_MODULES.put(MAX_RETRIES, ConfigEntry.fromModule());
        CONFIG_MODULES.put(LOG_REQUESTS, ConfigEntry.fromModule());
        CONFIG_MODULES.put(LOG_RESPONSES, ConfigEntry.fromModule());
    }

    public static Map<ConfigModule, ConfigEntry> entries() {
        return Collections.unmodifiableMap(CONFIG_MODULES);
    }

    public static Optional<ConfigModule> find(String prefix, String name) {
        return find(CONFIG_MODULES, prefix, name);
    }

    /**
     * Registers new known configuration if a prefix is provided (otherwise is ignored)
     * @param prefix the prefix to register
     */
    public static void register(String prefix) {
        if (prefix != null) {
            for (Map.Entry<ConfigModule, ConfigEntry> entry : entries().entrySet()) {
                ConfigModule configModule = entry.getKey().asNamed(prefix);
                CONFIG_MODULES.put(configModule, ConfigEntry.fromModule());
            }
        }
    }

    /**
     * Load override configurations (which are defined via environment variables and/or system properties)
     * @param prefix and optional prefix to use
     */
    public static void loadOverrides(String prefix) {
        load(CONFIG_MODULES, prefix);
    }
}
