package io.kaoto.forage.agent;

import java.util.HashMap;
import java.util.Map;
import org.apache.camel.component.langchain4j.agent.api.Agent;
import io.kaoto.forage.core.ai.ModelProvider;
import io.kaoto.forage.core.common.ForageModuleDescriptor;

/**
 * Module descriptor for Forage Agent. Captures agent-specific knowledge:
 * prefix discovery, provider resolution, and Quarkus property translation.
 *
 * @since 1.1
 */
public class AgentModuleDescriptor implements ForageModuleDescriptor<AgentConfig, ModelProvider> {

    @Override
    public String modulePrefix() {
        return "agent";
    }

    @Override
    public AgentConfig createConfig(String prefix) {
        return prefix == null ? new AgentConfig() : new AgentConfig(prefix);
    }

    @Override
    public Class<ModelProvider> providerClass() {
        return ModelProvider.class;
    }

    @Override
    public String resolveProviderClassName(AgentConfig config) {
        String modelKind = config.modelKind();
        if (modelKind == null) {
            return null;
        }
        return AgentCreator.getProviderConfigPrefix(modelKind);
    }

    @Override
    public String defaultBeanName() {
        return "agent";
    }

    @Override
    public Class<?> primaryBeanClass() {
        return Agent.class;
    }

    @Override
    public boolean transactionEnabled(AgentConfig config) {
        return false;
    }

    private static final Map<String, String> QUARKUS_EXTENSION_CLASSES = Map.of(
            "ollama", "io.quarkiverse.langchain4j.ollama.runtime.config.LangChain4jOllamaConfig",
            "openai", "io.quarkiverse.langchain4j.openai.runtime.config.LangChain4jOpenAiConfig",
            "anthropic", "io.quarkiverse.langchain4j.anthropic.runtime.config.LangChain4jAnthropicConfig",
            "google-gemini", "io.quarkiverse.langchain4j.ai.runtime.gemini.config.LangChain4jAiGeminiConfig",
            "azure-openai", "io.quarkiverse.langchain4j.azure.openai.runtime.config.LangChain4jAzureOpenAiConfig",
            "mistral-ai", "io.quarkiverse.langchain4j.mistralai.runtime.config.LangChain4jMistralAiConfig",
            "hugging-face", "io.quarkiverse.langchain4j.huggingface.runtime.config.LangChain4jHuggingFaceConfig",
            "watsonx-ai", "io.quarkiverse.langchain4j.watsonx.runtime.config.LangChain4jWatsonxConfig",
            "bedrock", "io.quarkiverse.langchain4j.bedrock.runtime.config.LangChain4jBedrockConfig");

    @Override
    public Map<String, String> translateProperties(String prefix, AgentConfig config) {
        String modelKind = config.modelKind();
        if (modelKind == null) {
            return Map.of();
        }

        // Only translate when the corresponding quarkus-langchain4j extension is on
        // the classpath. Otherwise Forage creates the ChatModel via ServiceLoader and
        // the translated properties would produce "Unrecognized configuration key" warnings.
        String extensionClass = QUARKUS_EXTENSION_CLASSES.get(modelKind);
        if (extensionClass != null) {
            try {
                Class.forName(extensionClass, false, Thread.currentThread().getContextClassLoader());
            } catch (ClassNotFoundException e) {
                return Map.of();
            }
        }

        return switch (modelKind) {
            case "ollama" -> translateOllamaProperties(config);
            case "openai" -> translateOpenAiProperties(config);
            case "anthropic" -> translateAnthropicProperties(config);
            case "google-gemini" -> translateGeminiProperties(config);
            case "azure-openai" -> translateAzureOpenAiProperties(config);
            case "mistral-ai" -> translateMistralAiProperties(config);
            case "hugging-face" -> translateHuggingFaceProperties(config);
            case "watsonx-ai" -> translateWatsonxProperties(config);
            case "bedrock" -> translateBedrockProperties(config);
            default -> Map.of();
        };
    }

    // quarkus.langchain4j.ollama.*
    // chat-model: model-id, temperature, top-p, top-k
    private Map<String, String> translateOllamaProperties(AgentConfig config) {
        Map<String, String> props = new HashMap<>();
        String qp = quarkusPrefix("ollama");

        putIfNotNull(props, qp + "base-url", config.baseUrl());
        putIfNotNull(props, qp + "chat-model.model-id", config.modelName());
        putIfNotNull(props, qp + "chat-model.temperature", config.temperature());
        putIfNotNull(props, qp + "chat-model.top-p", config.topP());
        putIfNotNull(props, qp + "chat-model.top-k", config.topK());
        addLoggingProperties(props, qp, config);

        return props;
    }

    // quarkus.langchain4j.openai.*
    // chat-model: model-name, temperature, top-p, max-tokens
    private Map<String, String> translateOpenAiProperties(AgentConfig config) {
        return translateStandardModelProperties("openai", config);
    }

    // quarkus.langchain4j.anthropic.*
    // chat-model: model-name, temperature, top-p, top-k, max-tokens
    private Map<String, String> translateAnthropicProperties(AgentConfig config) {
        Map<String, String> props = new HashMap<>();
        String qp = quarkusPrefix("anthropic");

        putIfNotNull(props, qp + "base-url", config.baseUrl());
        putIfNotNull(props, qp + "api-key", config.apiKey());
        putIfNotNull(props, qp + "chat-model.model-name", config.modelName());
        putIfNotNull(props, qp + "chat-model.temperature", config.temperature());
        putIfNotNull(props, qp + "chat-model.top-p", config.topP());
        putIfNotNull(props, qp + "chat-model.top-k", config.topK());
        putIfNotNull(props, qp + "chat-model.max-tokens", config.maxTokens());
        addLoggingProperties(props, qp, config);

        return props;
    }

    // quarkus.langchain4j.ai.gemini.*
    // chat-model: model-id, temperature, top-p, top-k, max-output-tokens
    private Map<String, String> translateGeminiProperties(AgentConfig config) {
        Map<String, String> props = new HashMap<>();
        String qp = quarkusPrefix("ai.gemini");

        putIfNotNull(props, qp + "api-key", config.apiKey());
        putIfNotNull(props, qp + "base-url", config.baseUrl());
        putIfNotNull(props, qp + "chat-model.model-id", config.modelName());
        putIfNotNull(props, qp + "chat-model.temperature", config.temperature());
        putIfNotNull(props, qp + "chat-model.top-p", config.topP());
        putIfNotNull(props, qp + "chat-model.top-k", config.topK());
        putIfNotNull(props, qp + "chat-model.max-output-tokens", config.maxTokens());
        addLoggingProperties(props, qp, config);

        return props;
    }

    // quarkus.langchain4j.azure-openai.*
    // Uses endpoint + deployment-name instead of base-url + model-name
    // chat-model: temperature, top-p, max-tokens
    private Map<String, String> translateAzureOpenAiProperties(AgentConfig config) {
        Map<String, String> props = new HashMap<>();
        String qp = quarkusPrefix("azure-openai");

        putIfNotNull(props, qp + "api-key", config.apiKey());
        putIfNotNull(props, qp + "endpoint", config.endpoint());
        putIfNotNull(props, qp + "deployment-name", config.deploymentName());
        putIfNotNull(props, qp + "chat-model.temperature", config.temperature());
        putIfNotNull(props, qp + "chat-model.top-p", config.topP());
        putIfNotNull(props, qp + "chat-model.max-tokens", config.maxTokens());
        addLoggingProperties(props, qp, config);

        return props;
    }

    // quarkus.langchain4j.mistralai.*
    // chat-model: model-name, temperature, top-p, max-tokens
    private Map<String, String> translateMistralAiProperties(AgentConfig config) {
        return translateStandardModelProperties("mistralai", config);
    }

    // quarkus.langchain4j.huggingface.*
    // chat-model: inference-endpoint-url, temperature, top-p, top-k, max-new-tokens
    private Map<String, String> translateHuggingFaceProperties(AgentConfig config) {
        Map<String, String> props = new HashMap<>();
        String qp = quarkusPrefix("huggingface");

        putIfNotNull(props, qp + "api-key", config.apiKey());
        putIfNotNull(props, qp + "chat-model.inference-endpoint-url", config.baseUrl());
        putIfNotNull(props, qp + "chat-model.temperature", config.temperature());
        putIfNotNull(props, qp + "chat-model.top-p", config.topP());
        putIfNotNull(props, qp + "chat-model.top-k", config.topK());
        putIfNotNull(props, qp + "chat-model.max-new-tokens", config.maxTokens());
        addLoggingProperties(props, qp, config);

        return props;
    }

    // quarkus.langchain4j.watsonx.*
    // chat-model: model-name, temperature, top-p, max-output-tokens
    private Map<String, String> translateWatsonxProperties(AgentConfig config) {
        Map<String, String> props = new HashMap<>();
        String qp = quarkusPrefix("watsonx");

        putIfNotNull(props, qp + "base-url", config.baseUrl());
        putIfNotNull(props, qp + "api-key", config.apiKey());
        putIfNotNull(props, qp + "chat-model.model-name", config.modelName());
        putIfNotNull(props, qp + "chat-model.temperature", config.temperature());
        putIfNotNull(props, qp + "chat-model.top-p", config.topP());
        putIfNotNull(props, qp + "chat-model.max-output-tokens", config.maxTokens());
        addLoggingProperties(props, qp, config);

        return props;
    }

    // quarkus.langchain4j.bedrock.*
    // chat-model: model-id, temperature, top-p, top-k, max-tokens
    private Map<String, String> translateBedrockProperties(AgentConfig config) {
        Map<String, String> props = new HashMap<>();
        String qp = quarkusPrefix("bedrock");

        putIfNotNull(props, qp + "chat-model.model-id", config.modelName());
        putIfNotNull(props, qp + "chat-model.temperature", config.temperature());
        putIfNotNull(props, qp + "chat-model.top-p", config.topP());
        putIfNotNull(props, qp + "chat-model.top-k", config.topK());
        putIfNotNull(props, qp + "chat-model.max-tokens", config.maxTokens());
        addLoggingProperties(props, qp, config);

        return props;
    }

    private Map<String, String> translateStandardModelProperties(String provider, AgentConfig config) {
        Map<String, String> props = new HashMap<>();
        String qp = quarkusPrefix(provider);

        putIfNotNull(props, qp + "base-url", config.baseUrl());
        putIfNotNull(props, qp + "api-key", config.apiKey());
        putIfNotNull(props, qp + "chat-model.model-name", config.modelName());
        putIfNotNull(props, qp + "chat-model.temperature", config.temperature());
        putIfNotNull(props, qp + "chat-model.top-p", config.topP());
        putIfNotNull(props, qp + "chat-model.max-tokens", config.maxTokens());
        addLoggingProperties(props, qp, config);

        return props;
    }

    private static void addLoggingProperties(Map<String, String> props, String qp, AgentConfig config) {
        putIfNotNull(props, qp + "log-requests", config.logRequests());
        putIfNotNull(props, qp + "log-responses", config.logResponses());
    }

    private static String quarkusPrefix(String provider) {
        // Each model kind maps to a separate Quarkus provider namespace
        // (e.g., quarkus.langchain4j.ollama.*, quarkus.langchain4j.openai.*),
        // so the default (non-named) config is used. Quarkus named configs
        // would only be needed for multiple agents with the same model kind,
        // which is not yet supported.
        return "quarkus.langchain4j." + provider + ".";
    }

    private static void putIfNotNull(Map<String, String> props, String key, Object value) {
        if (value != null) {
            props.put(key, String.valueOf(value));
        }
    }
}
