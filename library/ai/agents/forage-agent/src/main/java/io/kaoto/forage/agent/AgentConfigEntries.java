package io.kaoto.forage.agent;

import io.kaoto.forage.core.util.config.ConfigEntries;
import io.kaoto.forage.core.util.config.ConfigEntry;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigTag;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified configuration entries for agent factory.
 *
 * <p>This class defines all configuration modules under the unified {@code agent.*} namespace,
 * following the same pattern as JDBC ({@code jdbc.*}) and JMS ({@code jms.*}).
 *
 * <p>Example configuration:
 * <pre>
 * # Single agent (no prefix)
 * forage.agent.model.kind=ollama
 * forage.agent.base.url=http://localhost:11434
 * forage.agent.model.name=llama3
 *
 * # Multiple agents (with prefix, auto-detected)
 * forage.google.agent.model.kind=google-gemini
 * forage.google.agent.features=memory
 * forage.google.agent.memory.kind=message-window
 * forage.google.agent.api.key=your-api-key
 * forage.google.agent.model.name=gemini-2.0-flash
 *
 * forage.ollama.agent.model.kind=ollama
 * forage.ollama.agent.base.url=http://localhost:11434
 * forage.ollama.agent.model.name=llama3
 * </pre>
 */
public final class AgentConfigEntries extends ConfigEntries {

    // Core agent configuration
    public static final ConfigModule MODEL_KIND = ConfigModule.of(
            AgentConfig.class,
            "forage.agent.model.kind",
            "The model provider kind (e.g., ollama, openai, google-gemini, azure-openai, anthropic)",
            "Model Kind",
            null,
            "bean-name",
            true,
            ConfigTag.COMMON);

    public static final ConfigModule FEATURES = ConfigModule.of(
            AgentConfig.class,
            "forage.agent.features",
            "Comma-separated list of enabled features (e.g., memory)",
            "Features",
            null,
            "string",
            false,
            ConfigTag.COMMON);

    public static final ConfigModule MEMORY_KIND = ConfigModule.of(
            AgentConfig.class,
            "forage.agent.memory.kind",
            "The memory provider kind (e.g., message-window, redis, infinispan)",
            "Memory Kind",
            null,
            "bean-name",
            false,
            ConfigTag.COMMON);

    // Common model configuration (shared across providers)
    public static final ConfigModule API_KEY = ConfigModule.of(
            AgentConfig.class,
            "forage.agent.api.key",
            "API key for authentication with the model provider",
            "API Key",
            null,
            "password",
            false,
            ConfigTag.SECURITY);

    public static final ConfigModule BASE_URL = ConfigModule.of(
            AgentConfig.class,
            "forage.agent.base.url",
            "Base URL for the model provider API",
            "Base URL",
            null,
            "string",
            false,
            ConfigTag.COMMON);

    public static final ConfigModule MODEL_NAME = ConfigModule.of(
            AgentConfig.class,
            "forage.agent.model.name",
            "The specific model name to use",
            "Model Name",
            null,
            "string",
            false,
            ConfigTag.COMMON);

    public static final ConfigModule TEMPERATURE = ConfigModule.of(
            AgentConfig.class,
            "forage.agent.temperature",
            "Temperature for response randomness (0.0-2.0)",
            "Temperature",
            null,
            "double",
            false,
            ConfigTag.COMMON);

    public static final ConfigModule MAX_TOKENS = ConfigModule.of(
            AgentConfig.class,
            "forage.agent.max.tokens",
            "Maximum number of tokens in the response",
            "Max Tokens",
            null,
            "integer",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule TOP_P = ConfigModule.of(
            AgentConfig.class,
            "forage.agent.top.p",
            "Top-P (nucleus) sampling parameter (0.0-1.0)",
            "Top P",
            null,
            "double",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule TOP_K = ConfigModule.of(
            AgentConfig.class,
            "forage.agent.top.k",
            "Top-K sampling parameter",
            "Top K",
            null,
            "integer",
            false,
            ConfigTag.ADVANCED);

    // Azure OpenAI specific
    public static final ConfigModule ENDPOINT = ConfigModule.of(
            AgentConfig.class,
            "forage.agent.endpoint",
            "Azure OpenAI resource endpoint URL",
            "Endpoint",
            null,
            "string",
            false,
            ConfigTag.COMMON);

    public static final ConfigModule DEPLOYMENT_NAME = ConfigModule.of(
            AgentConfig.class,
            "forage.agent.deployment.name",
            "Azure OpenAI deployment name",
            "Deployment Name",
            null,
            "string",
            false,
            ConfigTag.COMMON);

    // Logging
    public static final ConfigModule LOG_REQUESTS = ConfigModule.of(
            AgentConfig.class,
            "forage.agent.log.requests",
            "Enable request logging",
            "Log Requests",
            null,
            "boolean",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule LOG_RESPONSES = ConfigModule.of(
            AgentConfig.class,
            "forage.agent.log.responses",
            "Enable response logging",
            "Log Responses",
            null,
            "boolean",
            false,
            ConfigTag.ADVANCED);

    // Memory configuration
    public static final ConfigModule MEMORY_MAX_MESSAGES = ConfigModule.of(
            AgentConfig.class,
            "forage.agent.memory.max.messages",
            "Maximum number of messages to retain in memory",
            "Max Messages",
            "20",
            "integer",
            false,
            ConfigTag.COMMON);

    // Redis memory
    public static final ConfigModule MEMORY_REDIS_HOST = ConfigModule.of(
            AgentConfig.class,
            "forage.agent.memory.redis.host",
            "Redis server hostname",
            "Redis Host",
            "localhost",
            "string",
            false,
            ConfigTag.COMMON);

    public static final ConfigModule MEMORY_REDIS_PORT = ConfigModule.of(
            AgentConfig.class,
            "forage.agent.memory.redis.port",
            "Redis server port",
            "Redis Port",
            "6379",
            "integer",
            false,
            ConfigTag.COMMON);

    public static final ConfigModule MEMORY_REDIS_PASSWORD = ConfigModule.of(
            AgentConfig.class,
            "forage.agent.memory.redis.password",
            "Redis authentication password",
            "Redis Password",
            null,
            "password",
            false,
            ConfigTag.SECURITY);

    // Infinispan memory
    public static final ConfigModule MEMORY_INFINISPAN_SERVER_LIST = ConfigModule.of(
            AgentConfig.class,
            "forage.agent.memory.infinispan.server-list",
            "Comma-separated list of Infinispan servers",
            "Server List",
            "localhost:11222",
            "string",
            false,
            ConfigTag.COMMON);

    public static final ConfigModule MEMORY_INFINISPAN_CACHE_NAME = ConfigModule.of(
            AgentConfig.class,
            "forage.agent.memory.infinispan.cache-name",
            "Infinispan cache name for storing messages",
            "Cache Name",
            "chat-memory",
            "string",
            false,
            ConfigTag.COMMON);

    // EMBEDDING STORE

    public static final ConfigModule EMBEDDING_STORE_FILE_SOURCE = ConfigModule.of(
            AgentConfig.class,
            "forage.agent.in.memory.store.file.source",
            "Path to a file to be loaded into store.",
            "File source",
            null,
            "string",
            true,
            ConfigTag.COMMON);

    public static final ConfigModule EMBEDDING_STORE_MAX_SIZE = ConfigModule.of(
            AgentConfig.class,
            "forage.agent.in.memory.store.max.size",
            "The maximum size of the segment, defined in characters.",
            "Max size",
            null,
            "int",
            false,
            ConfigTag.COMMON);

    public static final ConfigModule EMBEDDING_STORE_OVERLAP_SIZE = ConfigModule.of(
            AgentConfig.class,
            "forage.agent.in.memory.store.overlap.size",
            "The maximum size of the overlap, defined in characters.",
            "Overlap size",
            null,
            "int",
            false,
            ConfigTag.COMMON);

    // RAG

    // embedding model
    public static final ConfigModule EMBEDDING_MODEL_API_KEY = ConfigModule.of(
            AgentConfig.class,
            "forage.agent.api.key",
            "API key for authentication with the model provider",
            "API Key",
            null,
            "password",
            false,
            ConfigTag.SECURITY);

    public static final ConfigModule EMBEDDING_MODEL_BASE_URL = ConfigModule.of(
            AgentConfig.class,
            "forage.agent.base.url",
            "Base URL for the model provider API",
            "Base URL",
            null,
            "string",
            false,
            ConfigTag.COMMON);

    public static final ConfigModule EMBEDDING_MODEL_MODEL_NAME = ConfigModule.of(
            AgentConfig.class,
            "forage.agent.embedding.model.name",
            "The specific model name to use",
            "Model Name",
            null,
            "string",
            false,
            ConfigTag.COMMON);
    //    public static final ConfigModule EMBEDDING_MODEL_CUSTOM_HEADERS = ConfigModule.of(
    //            AgentConfig.class,
    //            "forage.agent.embedding.model.custom.headers",
    //            "todo",
    //            "Custom Headers",
    //            null,
    //            "map",
    //            false,
    //            ConfigTag.COMMON);
    public static final ConfigModule EMBEDDING_MODEL_TIMEOUT = ConfigModule.of(
            AgentConfig.class,
            "forage.agent.embedding.model.timeout",
            "Used for the HttpClientBuilder that will be used to communicate with Ollama",
            "Timeout",
            null,
            "Duration",
            false,
            ConfigTag.COMMON);
    public static final ConfigModule EMBEDDING_MODEL_MAX_RETRIES = ConfigModule.of(
            AgentConfig.class,
            "forage.agent.embedding.model.max.retries",
            "Used for the HttpClientBuilder that will be used to communicate with Ollama",
            "Max retries",
            null,
            "int",
            false,
            ConfigTag.COMMON);

    // rag

    public static final ConfigModule DEFAULT_RAG_MAX_RESULTS = ConfigModule.of(
            AgentConfig.class,
            "forage.agent.rag.max.results",
            "The maximum number of Contents to retrieve.",
            "Max results",
            null,
            "int",
            false,
            ConfigTag.COMMON);
    public static final ConfigModule DEFAULT_RAG_MIN_SCORE = ConfigModule.of(
            AgentConfig.class,
            "forage.agent.rag.min.score",
            "The minimum relevance score for the returned Contents.",
            "Min score",
            null,
            "double",
            false,
            ConfigTag.COMMON);

    private static final Map<ConfigModule, ConfigEntry> CONFIG_MODULES = new ConcurrentHashMap<>();

    static {
        init();
    }

    static void init() {
        // Core
        CONFIG_MODULES.put(MODEL_KIND, ConfigEntry.fromModule());
        CONFIG_MODULES.put(FEATURES, ConfigEntry.fromModule());
        CONFIG_MODULES.put(MEMORY_KIND, ConfigEntry.fromModule());

        // Common model config
        CONFIG_MODULES.put(API_KEY, ConfigEntry.fromModule());
        CONFIG_MODULES.put(BASE_URL, ConfigEntry.fromModule());
        CONFIG_MODULES.put(MODEL_NAME, ConfigEntry.fromModule());
        CONFIG_MODULES.put(TEMPERATURE, ConfigEntry.fromModule());
        CONFIG_MODULES.put(MAX_TOKENS, ConfigEntry.fromModule());
        CONFIG_MODULES.put(TOP_P, ConfigEntry.fromModule());
        CONFIG_MODULES.put(TOP_K, ConfigEntry.fromModule());

        // Azure specific
        CONFIG_MODULES.put(ENDPOINT, ConfigEntry.fromModule());
        CONFIG_MODULES.put(DEPLOYMENT_NAME, ConfigEntry.fromModule());

        // Logging
        CONFIG_MODULES.put(LOG_REQUESTS, ConfigEntry.fromModule());
        CONFIG_MODULES.put(LOG_RESPONSES, ConfigEntry.fromModule());

        // Memory
        CONFIG_MODULES.put(MEMORY_MAX_MESSAGES, ConfigEntry.fromModule());
        CONFIG_MODULES.put(MEMORY_REDIS_HOST, ConfigEntry.fromModule());
        CONFIG_MODULES.put(MEMORY_REDIS_PORT, ConfigEntry.fromModule());
        CONFIG_MODULES.put(MEMORY_REDIS_PASSWORD, ConfigEntry.fromModule());
        CONFIG_MODULES.put(MEMORY_INFINISPAN_SERVER_LIST, ConfigEntry.fromModule());
        CONFIG_MODULES.put(MEMORY_INFINISPAN_CACHE_NAME, ConfigEntry.fromModule());

        // embedding store
        CONFIG_MODULES.put(EMBEDDING_STORE_FILE_SOURCE, ConfigEntry.fromModule());
        CONFIG_MODULES.put(EMBEDDING_STORE_MAX_SIZE, ConfigEntry.fromModule());
        CONFIG_MODULES.put(EMBEDDING_STORE_OVERLAP_SIZE, ConfigEntry.fromModule());

        // RAG
        CONFIG_MODULES.put(EMBEDDING_MODEL_API_KEY, ConfigEntry.fromModule());
        CONFIG_MODULES.put(EMBEDDING_MODEL_BASE_URL, ConfigEntry.fromModule());
        CONFIG_MODULES.put(EMBEDDING_MODEL_MODEL_NAME, ConfigEntry.fromModule());
        //        CONFIG_MODULES.put(EMBEDDING_MODEL_CUSTOM_HEADERS, ConfigEntry.fromModule());
        CONFIG_MODULES.put(EMBEDDING_MODEL_TIMEOUT, ConfigEntry.fromModule());
        CONFIG_MODULES.put(EMBEDDING_MODEL_MAX_RETRIES, ConfigEntry.fromModule());

        CONFIG_MODULES.put(DEFAULT_RAG_MAX_RESULTS, ConfigEntry.fromModule());
        CONFIG_MODULES.put(DEFAULT_RAG_MIN_SCORE, ConfigEntry.fromModule());
    }

    public static Map<ConfigModule, ConfigEntry> entries() {
        return Collections.unmodifiableMap(CONFIG_MODULES);
    }

    public static Optional<ConfigModule> find(String prefix, String name) {
        return find(CONFIG_MODULES, prefix, name);
    }

    public static void register(String prefix) {
        if (prefix != null) {
            for (Map.Entry<ConfigModule, ConfigEntry> entry : entries().entrySet()) {
                ConfigModule configModule = entry.getKey().asNamed(prefix);
                CONFIG_MODULES.put(configModule, ConfigEntry.fromModule());
            }
        }
    }

    public static void loadOverrides(String prefix) {
        load(CONFIG_MODULES, prefix);
    }
}
