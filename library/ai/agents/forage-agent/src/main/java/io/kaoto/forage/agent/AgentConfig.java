package io.kaoto.forage.agent;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import io.kaoto.forage.core.util.config.AbstractConfig;

import static io.kaoto.forage.agent.AgentConfigEntries.API_KEY;
import static io.kaoto.forage.agent.AgentConfigEntries.BASE_URL;
import static io.kaoto.forage.agent.AgentConfigEntries.DEFAULT_RAG_MAX_RESULTS;
import static io.kaoto.forage.agent.AgentConfigEntries.DEFAULT_RAG_MIN_SCORE;
import static io.kaoto.forage.agent.AgentConfigEntries.DEPLOYMENT_NAME;
import static io.kaoto.forage.agent.AgentConfigEntries.EMBEDDING_MODEL_BASE_URL;
import static io.kaoto.forage.agent.AgentConfigEntries.EMBEDDING_MODEL_MAX_RETRIES;
import static io.kaoto.forage.agent.AgentConfigEntries.EMBEDDING_MODEL_MODEL_NAME;
import static io.kaoto.forage.agent.AgentConfigEntries.EMBEDDING_MODEL_TIMEOUT;
import static io.kaoto.forage.agent.AgentConfigEntries.EMBEDDING_STORE_FILE_SOURCE;
import static io.kaoto.forage.agent.AgentConfigEntries.EMBEDDING_STORE_MAX_SIZE;
import static io.kaoto.forage.agent.AgentConfigEntries.EMBEDDING_STORE_OVERLAP_SIZE;
import static io.kaoto.forage.agent.AgentConfigEntries.ENDPOINT;
import static io.kaoto.forage.agent.AgentConfigEntries.FEATURES;
import static io.kaoto.forage.agent.AgentConfigEntries.LOG_REQUESTS;
import static io.kaoto.forage.agent.AgentConfigEntries.LOG_RESPONSES;
import static io.kaoto.forage.agent.AgentConfigEntries.MAX_TOKENS;
import static io.kaoto.forage.agent.AgentConfigEntries.MEMORY_INFINISPAN_CACHE_NAME;
import static io.kaoto.forage.agent.AgentConfigEntries.MEMORY_INFINISPAN_SERVER_LIST;
import static io.kaoto.forage.agent.AgentConfigEntries.MEMORY_KIND;
import static io.kaoto.forage.agent.AgentConfigEntries.MEMORY_MAX_MESSAGES;
import static io.kaoto.forage.agent.AgentConfigEntries.MEMORY_REDIS_HOST;
import static io.kaoto.forage.agent.AgentConfigEntries.MEMORY_REDIS_PASSWORD;
import static io.kaoto.forage.agent.AgentConfigEntries.MEMORY_REDIS_PORT;
import static io.kaoto.forage.agent.AgentConfigEntries.MODEL_KIND;
import static io.kaoto.forage.agent.AgentConfigEntries.MODEL_NAME;
import static io.kaoto.forage.agent.AgentConfigEntries.TEMPERATURE;
import static io.kaoto.forage.agent.AgentConfigEntries.TIMEOUT;
import static io.kaoto.forage.agent.AgentConfigEntries.TOP_K;
import static io.kaoto.forage.agent.AgentConfigEntries.TOP_P;

/**
 * Unified configuration class for agent factory.
 *
 * <p>This configuration class manages all agent settings under the unified {@code agent.*}
 * namespace, following the same pattern as JDBC and JMS configurations.
 *
 * <p>Supports both default configurations and prefixed configurations for multi-agent scenarios.
 * Prefixes are auto-detected from properties like {@code myagent.agent.model.kind}.
 */
public class AgentConfig extends AbstractConfig {

    public AgentConfig() {
        this(null);
    }

    public AgentConfig(String prefix) {
        super(prefix, AgentConfigEntries.class);
    }

    @Override
    public String name() {
        return "forage-agent-factory";
    }

    // Core configuration

    public String modelKind() {
        return get(MODEL_KIND).orElse(null);
    }

    public List<String> features() {
        return get(FEATURES)
                .map(s -> Arrays.stream(s.split(","))
                        .map(String::trim)
                        .filter(token -> !token.isEmpty())
                        .toList())
                .orElse(Collections.emptyList());
    }

    public boolean hasFeature(String feature) {
        return features().contains(feature);
    }

    public String memoryKind() {
        return get(MEMORY_KIND).orElse(null);
    }

    // Common model configuration

    public String apiKey() {
        return get(API_KEY).orElse(null);
    }

    public String baseUrl() {
        return get(BASE_URL).orElse(null);
    }

    public String modelName() {
        return get(MODEL_NAME).orElse(null);
    }

    public Double temperature() {
        return get(TEMPERATURE).map(Double::parseDouble).orElse(null);
    }

    public Integer maxTokens() {
        return get(MAX_TOKENS).map(Integer::parseInt).orElse(null);
    }

    public Double topP() {
        return get(TOP_P).map(Double::parseDouble).orElse(null);
    }

    public Integer topK() {
        return get(TOP_K).map(Integer::parseInt).orElse(null);
    }

    // Azure OpenAI specific

    public String endpoint() {
        return get(ENDPOINT).orElse(null);
    }

    public String deploymentName() {
        return get(DEPLOYMENT_NAME).orElse(null);
    }

    // Logging

    public Boolean logRequests() {
        return get(LOG_REQUESTS).map(Boolean::parseBoolean).orElse(null);
    }

    public Boolean logResponses() {
        return get(LOG_RESPONSES).map(Boolean::parseBoolean).orElse(null);
    }

    public String timeout() {
        return get(TIMEOUT).orElse(null);
    }

    // Memory configuration

    public Integer memoryMaxMessages() {
        return get(MEMORY_MAX_MESSAGES).map(Integer::parseInt).orElse(20);
    }

    // Redis memory

    public String memoryRedisHost() {
        return get(MEMORY_REDIS_HOST).orElse("localhost");
    }

    public Integer memoryRedisPort() {
        return get(MEMORY_REDIS_PORT).map(Integer::parseInt).orElse(6379);
    }

    public String memoryRedisPassword() {
        return get(MEMORY_REDIS_PASSWORD).orElse(null);
    }

    // Infinispan memory

    public String memoryInfinispanServerList() {
        return get(MEMORY_INFINISPAN_SERVER_LIST).orElse("localhost:11222");
    }

    public String memoryInfinispanCacheName() {
        return get(MEMORY_INFINISPAN_CACHE_NAME).orElse("chat-memory");
    }

    // EmbeddingStore

    public boolean hasEmbeddingConfig() {
        return get(EMBEDDING_MODEL_MODEL_NAME).isPresent()
                || get(EMBEDDING_STORE_FILE_SOURCE).isPresent();
    }

    public String fileSource() {
        return get(EMBEDDING_STORE_FILE_SOURCE).orElse(null);
    }

    public Integer embeddingStoreMaxSize() {
        return get(EMBEDDING_STORE_MAX_SIZE).map(Integer::parseInt).orElse(null);
    }

    public Integer embeddingStoreOverlapSize() {
        return get(EMBEDDING_STORE_OVERLAP_SIZE).map(Integer::parseInt).orElse(null);
    }

    // RAG

    public String embeddingModelBaseUrl() {
        return get(EMBEDDING_MODEL_BASE_URL).orElse(BASE_URL.defaultValue());
    }

    public String embeddingModelName() {
        return get(EMBEDDING_MODEL_MODEL_NAME).orElse(MODEL_NAME.defaultValue());
    }

    public Integer embeddingModelMaxRetries() {
        return get(EMBEDDING_MODEL_MAX_RETRIES).map(Integer::parseInt).orElse(null);
    }

    public Duration embeddingModelTimeout() {
        return get(EMBEDDING_MODEL_TIMEOUT).map(Duration::parse).orElse(null);
    }

    public Integer defaultRagMaxResults() {
        return get(DEFAULT_RAG_MAX_RESULTS).map(Integer::parseInt).orElse(null);
    }

    public Double defaultRagMinScore() {
        return get(DEFAULT_RAG_MIN_SCORE).map(Double::parseDouble).orElse(null);
    }
}
