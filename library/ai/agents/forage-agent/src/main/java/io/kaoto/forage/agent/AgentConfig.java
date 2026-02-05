package io.kaoto.forage.agent;

import static io.kaoto.forage.agent.AgentConfigEntries.*;

import io.kaoto.forage.core.util.config.Config;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigStore;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Unified configuration class for agent factory.
 *
 * <p>This configuration class manages all agent settings under the unified {@code agent.*}
 * namespace, following the same pattern as JDBC and JMS configurations.
 *
 * <p>Supports both default configurations and prefixed configurations for multi-agent scenarios.
 * Prefixes are auto-detected from properties like {@code myagent.agent.model.kind}.
 */
public class AgentConfig implements Config {

    private final String prefix;

    public AgentConfig() {
        this(null);
    }

    public AgentConfig(String prefix) {
        this.prefix = prefix;

        AgentConfigEntries.register(prefix);
        ConfigStore.getInstance().load(AgentConfig.class, this, this::register);
        AgentConfigEntries.loadOverrides(prefix);
    }

    @Override
    public void register(String name, String value) {
        Optional<ConfigModule> config = AgentConfigEntries.find(prefix, name);
        config.ifPresent(module -> ConfigStore.getInstance().set(module, value));
    }

    @Override
    public String name() {
        return "forage-agent-factory";
    }

    // Core configuration

    public String modelKind() {
        return ConfigStore.getInstance().get(MODEL_KIND.asNamed(prefix)).orElse(null);
    }

    public List<String> features() {
        return ConfigStore.getInstance()
                .get(FEATURES.asNamed(prefix))
                .map(s -> Arrays.asList(s.split(",")))
                .orElse(Collections.emptyList());
    }

    public boolean hasFeature(String feature) {
        return features().contains(feature);
    }

    public String memoryKind() {
        return ConfigStore.getInstance().get(MEMORY_KIND.asNamed(prefix)).orElse(null);
    }

    // Common model configuration

    public String apiKey() {
        return ConfigStore.getInstance().get(API_KEY.asNamed(prefix)).orElse(null);
    }

    public String baseUrl() {
        return ConfigStore.getInstance().get(BASE_URL.asNamed(prefix)).orElse(null);
    }

    public String modelName() {
        return ConfigStore.getInstance().get(MODEL_NAME.asNamed(prefix)).orElse(null);
    }

    public Double temperature() {
        return ConfigStore.getInstance()
                .get(TEMPERATURE.asNamed(prefix))
                .map(Double::parseDouble)
                .orElse(null);
    }

    public Integer maxTokens() {
        return ConfigStore.getInstance()
                .get(MAX_TOKENS.asNamed(prefix))
                .map(Integer::parseInt)
                .orElse(null);
    }

    public Double topP() {
        return ConfigStore.getInstance()
                .get(TOP_P.asNamed(prefix))
                .map(Double::parseDouble)
                .orElse(null);
    }

    public Integer topK() {
        return ConfigStore.getInstance()
                .get(TOP_K.asNamed(prefix))
                .map(Integer::parseInt)
                .orElse(null);
    }

    // Azure OpenAI specific

    public String endpoint() {
        return ConfigStore.getInstance().get(ENDPOINT.asNamed(prefix)).orElse(null);
    }

    public String deploymentName() {
        return ConfigStore.getInstance().get(DEPLOYMENT_NAME.asNamed(prefix)).orElse(null);
    }

    // Logging

    public Boolean logRequests() {
        return ConfigStore.getInstance()
                .get(LOG_REQUESTS.asNamed(prefix))
                .map(Boolean::parseBoolean)
                .orElse(null);
    }

    public Boolean logResponses() {
        return ConfigStore.getInstance()
                .get(LOG_RESPONSES.asNamed(prefix))
                .map(Boolean::parseBoolean)
                .orElse(null);
    }

    // Memory configuration

    public Integer memoryMaxMessages() {
        return ConfigStore.getInstance()
                .get(MEMORY_MAX_MESSAGES.asNamed(prefix))
                .map(Integer::parseInt)
                .orElse(20);
    }

    // Redis memory

    public String memoryRedisHost() {
        return ConfigStore.getInstance().get(MEMORY_REDIS_HOST.asNamed(prefix)).orElse("localhost");
    }

    public Integer memoryRedisPort() {
        return ConfigStore.getInstance()
                .get(MEMORY_REDIS_PORT.asNamed(prefix))
                .map(Integer::parseInt)
                .orElse(6379);
    }

    public String memoryRedisPassword() {
        return ConfigStore.getInstance()
                .get(MEMORY_REDIS_PASSWORD.asNamed(prefix))
                .orElse(null);
    }

    // Infinispan memory

    public String memoryInfinispanServerList() {
        return ConfigStore.getInstance()
                .get(MEMORY_INFINISPAN_SERVER_LIST.asNamed(prefix))
                .orElse("localhost:11222");
    }

    public String memoryInfinispanCacheName() {
        return ConfigStore.getInstance()
                .get(MEMORY_INFINISPAN_CACHE_NAME.asNamed(prefix))
                .orElse("chat-memory");
    }

    // EmbeddingStore

    public String fileSource() {
        return ConfigStore.getInstance()
                .get(EMBEDDING_STORE_FILE_SOURCE.asNamed(prefix))
                .orElse(null);
    }

    public Integer embeddingStoreMaxSize() {
        return ConfigStore.getInstance()
                .get(EMBEDDING_STORE_MAX_SIZE.asNamed(prefix))
                .map(Integer::parseInt)
                .orElse(null);
    }

    public Integer embeddingStoreOverlapSize() {
        return ConfigStore.getInstance()
                .get(EMBEDDING_STORE_OVERLAP_SIZE.asNamed(prefix))
                .map(Integer::parseInt)
                .orElse(null);
    }

    // RAG

    public String embeddingModelBaseUrl() {
        return ConfigStore.getInstance()
                .get(EMBEDDING_MODEL_BASE_URL.asNamed(prefix))
                .orElse(BASE_URL.defaultValue());
    }

    public String embeddingModelName() {
        return ConfigStore.getInstance()
                .get(EMBEDDING_MODEL_MODEL_NAME.asNamed(prefix))
                .orElse(MODEL_NAME.defaultValue());
    }

    public Integer embeddingModelMaxRetries() {
        return ConfigStore.getInstance()
                .get(EMBEDDING_MODEL_MAX_RETRIES.asNamed(prefix))
                .map(Integer::parseInt)
                .orElse(null);
    }

    public Duration embeddingModelTimeout() {
        return ConfigStore.getInstance()
                .get(EMBEDDING_MODEL_TIMEOUT.asNamed(prefix))
                .map(Duration::parse)
                .orElse(null);
    }

    public Integer defaultRagMaxResults() {
        return ConfigStore.getInstance()
                .get(DEFAULT_RAG_MAX_RESULTS.asNamed(prefix))
                .map(Integer::parseInt)
                .orElse(null);
    }

    public Double defaultRagMinScore() {
        return ConfigStore.getInstance()
                .get(DEFAULT_RAG_MIN_SCORE.asNamed(prefix))
                .map(Double::parseDouble)
                .orElse(null);
    }
}
