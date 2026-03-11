package io.kaoto.forage.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import org.apache.camel.component.langchain4j.agent.api.Agent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.agent.factory.ConfigurationAware;
import io.kaoto.forage.core.ai.ChatMemoryBeanProvider;
import io.kaoto.forage.core.ai.EmbeddingModelAware;
import io.kaoto.forage.core.ai.EmbeddingModelProvider;
import io.kaoto.forage.core.ai.EmbeddingStoreAware;
import io.kaoto.forage.core.ai.EmbeddingStoreProvider;
import io.kaoto.forage.core.ai.ModelProvider;
import io.kaoto.forage.core.ai.RetrievalAugmentorProvider;
import io.kaoto.forage.core.annotations.ForageBean;
import io.kaoto.forage.core.guardrails.InputGuardrailProvider;
import io.kaoto.forage.core.guardrails.OutputGuardrailProvider;
import io.kaoto.forage.core.util.config.ConfigHelper;
import io.kaoto.forage.core.util.config.ConfigStore;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.store.embedding.EmbeddingStore;

/**
 * Reusable utility for creating Agent instances from configuration.
 *
 * <p>Extracted from {@link AgentBeanFactory} so that both the plain Camel factory,
 * the Quarkus recorder, and the Spring Boot auto-configuration can share the same
 * agent composition logic.
 */
public final class AgentCreator {

    private static final Logger LOG = LoggerFactory.getLogger(AgentCreator.class);
    public static final String DEFAULT_AGENT = "agent";
    private static final String FEATURE_MEMORY = "memory";

    private AgentCreator() {}

    /**
     * Creates an Agent using the full pipeline: ChatModel + Memory + RAG + Guardrails.
     */
    public static Agent createAgent(AgentConfig config, String name, ClassLoader classLoader) {
        String modelKind = config.modelKind();
        if (modelKind == null) {
            LOG.warn("No model kind configured for agent '{}'", name);
            return null;
        }

        LOG.info("Creating agent '{}' with model kind: {}", name, modelKind);

        ChatModel chatModel = createChatModel(config, modelKind, name, classLoader);
        if (chatModel == null) {
            LOG.warn("Failed to create chat model for agent '{}'", name);
            return null;
        }

        return createAgent(config, name, classLoader, chatModel);
    }

    /**
     * Creates an Agent with a pre-created ChatModel (e.g., from Quarkus CDI).
     */
    public static Agent createAgent(AgentConfig config, String name, ClassLoader classLoader, ChatModel chatModel) {
        String modelKind = config.modelKind();
        if (modelKind == null) {
            LOG.warn("No model kind configured for agent '{}'", name);
            return null;
        }

        ChatMemoryProvider chatMemoryProvider = null;
        if (config.hasFeature(FEATURE_MEMORY)) {
            String memoryKind = config.memoryKind();
            if (memoryKind != null) {
                chatMemoryProvider = createMemoryProvider(config, memoryKind, classLoader);
            } else {
                chatMemoryProvider = createDefaultMemoryProvider(config);
            }
        }

        Agent agent = findAndCreateAgent(classLoader);
        if (agent == null) {
            LOG.warn("No Agent implementation found in classpath");
            return null;
        }

        if (agent instanceof ConfigurationAware configurationAware) {
            ForageAgentConfiguration agentConfiguration = new ForageAgentConfiguration();
            agentConfiguration.withChatModel(chatModel).withChatMemoryProvider(chatMemoryProvider);

            // Only create embedding/RAG pipeline when embedding properties are configured
            if (config.hasEmbeddingConfig()) {
                EmbeddingModel embeddingModel = createEmbeddingModel(config, modelKind, name, classLoader);
                EmbeddingStore<TextSegment> embeddingStore =
                        createEmbeddingStore(config, modelKind, name, classLoader, embeddingModel);

                RetrievalAugmentor retrievalAugmentor =
                        createRetrievalAugmentor(config, modelKind, name, classLoader, embeddingModel, embeddingStore);

                if (retrievalAugmentor != null) {
                    agentConfiguration.withRetrievalAugmentor(retrievalAugmentor);
                }
            }

            List<InputGuardrail> inputGuardrails = loadInputGuardrails(name, classLoader);
            if (!inputGuardrails.isEmpty()) {
                agentConfiguration.withInputGuardrails(inputGuardrails);
                LOG.info("Configured {} input guardrails for agent '{}'", inputGuardrails.size(), name);
            }

            List<OutputGuardrail> outputGuardrails = loadOutputGuardrails(name, classLoader);
            if (!outputGuardrails.isEmpty()) {
                agentConfiguration.withOutputGuardrails(outputGuardrails);
                LOG.info("Configured {} output guardrails for agent '{}'", outputGuardrails.size(), name);
            }

            configurationAware.configure(agentConfiguration);
        }

        return agent;
    }

    /**
     * Detects agent prefixes from properties.
     */
    public static Set<String> detectPrefixes(ClassLoader classLoader) {
        ConfigStore.getInstance().setClassLoader(classLoader);
        AgentConfig defaultConfig = new AgentConfig();
        return ConfigStore.getInstance().readPrefixes(defaultConfig, ConfigHelper.getNamedPropertyRegexp("agent"));
    }

    /**
     * Checks if default (non-prefixed) agent configuration exists.
     */
    public static boolean hasDefaultConfig(ClassLoader classLoader) {
        ConfigStore.getInstance().setClassLoader(classLoader);
        AgentConfig defaultConfig = new AgentConfig();
        return !ConfigStore.getInstance()
                .readPrefixes(defaultConfig, ConfigHelper.getDefaultPropertyRegexp("agent"))
                .isEmpty();
    }

    static ChatModel createChatModel(AgentConfig config, String modelKind, String agentName, ClassLoader classLoader) {
        List<ServiceLoader.Provider<ModelProvider>> providers = findModelProviders(classLoader);

        for (ServiceLoader.Provider<ModelProvider> provider : providers) {
            Class<? extends ModelProvider> providerClass = provider.type();
            ForageBean annotation = providerClass.getAnnotation(ForageBean.class);
            if (annotation != null && annotation.value().equals(modelKind)) {
                LOG.debug("Found model provider for kind '{}': {}", modelKind, providerClass.getName());
                ModelProvider modelProvider = provider.get();

                String providerPrefix = getProviderConfigPrefix(modelKind);
                String prefix = DEFAULT_AGENT.equals(agentName) ? null : agentName;

                List<String> setKeys = new ArrayList<>();
                setSystemPropertyIfNotNull(setKeys, prefix, providerPrefix, "api.key", config.apiKey());
                setSystemPropertyIfNotNull(setKeys, prefix, providerPrefix, "model.name", config.modelName());
                setSystemPropertyIfNotNull(setKeys, prefix, providerPrefix, "base.url", config.baseUrl());
                setSystemPropertyIfNotNull(setKeys, prefix, providerPrefix, "temperature", config.temperature());
                setSystemPropertyIfNotNull(setKeys, prefix, providerPrefix, "max.tokens", config.maxTokens());
                setSystemPropertyIfNotNull(setKeys, prefix, providerPrefix, "top.p", config.topP());
                setSystemPropertyIfNotNull(setKeys, prefix, providerPrefix, "top.k", config.topK());
                setSystemPropertyIfNotNull(setKeys, prefix, providerPrefix, "endpoint", config.endpoint());
                setSystemPropertyIfNotNull(setKeys, prefix, providerPrefix, "deployment.name", config.deploymentName());
                setSystemPropertyIfNotNull(setKeys, prefix, providerPrefix, "log.requests", config.logRequests());
                setSystemPropertyIfNotNull(setKeys, prefix, providerPrefix, "log.responses", config.logResponses());

                try {
                    return modelProvider.create(prefix);
                } finally {
                    clearSystemProperties(setKeys);
                }
            }
        }

        LOG.warn("No chat model provider found for kind: {}", modelKind);
        return null;
    }

    static EmbeddingModel createEmbeddingModel(
            AgentConfig config, String modelKind, String agentName, ClassLoader classLoader) {
        List<ServiceLoader.Provider<EmbeddingModelProvider>> providers = findEmbeddingModelProviders(classLoader);

        for (ServiceLoader.Provider<EmbeddingModelProvider> provider : providers) {
            Class<? extends EmbeddingModelProvider> providerClass = provider.type();
            ForageBean annotation = providerClass.getAnnotation(ForageBean.class);
            if (annotation != null && annotation.value().equals(modelKind)) {
                LOG.debug("Found embedding model provider for kind '{}': {}", modelKind, providerClass.getName());
                EmbeddingModelProvider modelProvider = provider.get();

                String providerPrefix = getProviderConfigPrefix(modelKind);
                String prefix = DEFAULT_AGENT.equals(agentName) ? null : agentName;

                List<String> setKeys = new ArrayList<>();
                setSystemPropertyIfNotNull(
                        setKeys, prefix, providerPrefix, "embedding.model.name", config.embeddingModelName());
                setSystemPropertyIfNotNull(
                        setKeys, prefix, providerPrefix, "embedding.model.timeout", config.embeddingModelTimeout());
                setSystemPropertyIfNotNull(
                        setKeys, prefix, providerPrefix, "embedding.max.retries", config.embeddingModelMaxRetries());
                setSystemPropertyIfNotNull(
                        setKeys, prefix, providerPrefix, "embedding.base.url", config.embeddingModelBaseUrl());

                try {
                    return modelProvider.create(prefix);
                } finally {
                    clearSystemProperties(setKeys);
                }
            }
        }

        LOG.debug("No embedding model provider found for kind: {}", modelKind);
        return null;
    }

    static EmbeddingStore<TextSegment> createEmbeddingStore(
            AgentConfig config, String modelKind, String agentName, ClassLoader classLoader, EmbeddingModel model) {
        List<ServiceLoader.Provider<EmbeddingStoreProvider>> providers = findEmbeddingStoreProviders(classLoader);

        if (!providers.isEmpty()) {
            ServiceLoader.Provider<EmbeddingStoreProvider> provider = providers.get(0);
            Class<? extends EmbeddingStoreProvider> providerClass = provider.type();
            LOG.debug("Found embedding store provider for kind '{}': {}", modelKind, providerClass.getName());
            EmbeddingStoreProvider storeProvider = provider.get();

            String providerPrefix = getProviderConfigPrefix(modelKind);
            String prefix = DEFAULT_AGENT.equals(agentName) ? null : agentName;

            List<String> setKeys = new ArrayList<>();
            setSystemPropertyIfNotNull(setKeys, prefix, "in.memory.store", "file.source", config.fileSource());
            setSystemPropertyIfNotNull(setKeys, prefix, "in.memory.store", "max.size", config.embeddingStoreMaxSize());
            setSystemPropertyIfNotNull(
                    setKeys, prefix, "in.memory.store", "overlap.size", config.embeddingStoreOverlapSize());

            if (storeProvider instanceof EmbeddingModelAware modelAware) {
                modelAware.withEmbeddingModel(model);
            }

            try {
                return storeProvider.create(prefix);
            } finally {
                clearSystemProperties(setKeys);
            }
        }

        LOG.debug("No embedding store model provider found for kind: {}", modelKind);
        return null;
    }

    static RetrievalAugmentor createRetrievalAugmentor(
            AgentConfig config,
            String modelKind,
            String agentName,
            ClassLoader classLoader,
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore) {
        List<ServiceLoader.Provider<RetrievalAugmentorProvider>> providers =
                findRetrievalAugmentorProviders(classLoader);

        if (!providers.isEmpty()) {
            ServiceLoader.Provider<RetrievalAugmentorProvider> provider = providers.get(0);
            Class<? extends RetrievalAugmentorProvider> providerClass = provider.type();
            LOG.debug("Found retrieval augmentor provider for kind '{}': {}", modelKind, providerClass.getName());
            RetrievalAugmentorProvider retrievalAugmentorProvider = provider.get();

            String providerPrefix = getProviderConfigPrefix(modelKind);
            String prefix = DEFAULT_AGENT.equals(agentName) ? null : agentName;

            List<String> setKeys = new ArrayList<>();
            setSystemPropertyIfNotNull(setKeys, prefix, "rag", "max.results", config.defaultRagMaxResults());
            setSystemPropertyIfNotNull(setKeys, prefix, "rag", "min.score", config.defaultRagMinScore());

            if (retrievalAugmentorProvider instanceof EmbeddingModelAware modelAware) {
                modelAware.withEmbeddingModel(embeddingModel);
            }
            if (retrievalAugmentorProvider instanceof EmbeddingStoreAware storeAware) {
                storeAware.withEmbeddingStore(embeddingStore);
            }

            try {
                return retrievalAugmentorProvider.create(prefix);
            } finally {
                clearSystemProperties(setKeys);
            }
        }

        LOG.debug("No retrieval augmentor provider found for kind: {}", modelKind);
        return null;
    }

    static List<InputGuardrail> loadInputGuardrails(String agentName, ClassLoader classLoader) {
        List<InputGuardrail> guardrails = new ArrayList<>();
        ServiceLoader<InputGuardrailProvider> loader = ServiceLoader.load(InputGuardrailProvider.class, classLoader);
        List<ServiceLoader.Provider<InputGuardrailProvider>> providers =
                loader.stream().toList();

        LOG.info("Found {} input guardrail providers for agent '{}'", providers.size(), agentName);

        for (ServiceLoader.Provider<InputGuardrailProvider> provider : providers) {
            try {
                InputGuardrailProvider guardrailProvider = provider.get();
                String prefix = DEFAULT_AGENT.equals(agentName) ? null : agentName;
                LOG.info(
                        "Creating guardrail from provider {} with prefix '{}'",
                        provider.type().getName(),
                        prefix);
                InputGuardrail guardrail = guardrailProvider.create(prefix);
                if (guardrail != null) {
                    guardrails.add(guardrail);
                    LOG.info("Loaded input guardrail: {}", guardrail.getClass().getName());
                }
            } catch (Exception e) {
                LOG.warn(
                        "Skipping input guardrail provider {}: {}",
                        provider.type().getName(),
                        e.getMessage());
            }
        }

        LOG.info("Total input guardrails loaded: {}", guardrails.size());
        return guardrails;
    }

    static List<OutputGuardrail> loadOutputGuardrails(String agentName, ClassLoader classLoader) {
        List<OutputGuardrail> guardrails = new ArrayList<>();
        ServiceLoader<OutputGuardrailProvider> loader = ServiceLoader.load(OutputGuardrailProvider.class, classLoader);
        List<ServiceLoader.Provider<OutputGuardrailProvider>> providers =
                loader.stream().toList();

        for (ServiceLoader.Provider<OutputGuardrailProvider> provider : providers) {
            try {
                OutputGuardrailProvider guardrailProvider = provider.get();
                String prefix = DEFAULT_AGENT.equals(agentName) ? null : agentName;
                OutputGuardrail guardrail = guardrailProvider.create(prefix);
                if (guardrail != null) {
                    guardrails.add(guardrail);
                    LOG.debug(
                            "Loaded output guardrail: {}", guardrail.getClass().getName());
                }
            } catch (Exception e) {
                LOG.debug(
                        "Skipping output guardrail provider {}: {}",
                        provider.type().getName(),
                        e.getMessage());
            }
        }

        return guardrails;
    }

    static Agent findAndCreateAgent(ClassLoader classLoader) {
        ServiceLoader<Agent> loader = ServiceLoader.load(Agent.class, classLoader);
        List<ServiceLoader.Provider<Agent>> providers = loader.stream().toList();
        if (!providers.isEmpty()) {
            return providers.get(0).get();
        }
        return null;
    }

    static ChatMemoryProvider createMemoryProvider(AgentConfig config, String memoryKind, ClassLoader classLoader) {
        ServiceLoader<ChatMemoryBeanProvider> loader = ServiceLoader.load(ChatMemoryBeanProvider.class, classLoader);
        List<ServiceLoader.Provider<ChatMemoryBeanProvider>> providers =
                loader.stream().toList();

        for (ServiceLoader.Provider<ChatMemoryBeanProvider> provider : providers) {
            Class<? extends ChatMemoryBeanProvider> providerClass = provider.type();
            ForageBean annotation = providerClass.getAnnotation(ForageBean.class);
            if (annotation != null && annotation.value().equals(memoryKind)) {
                LOG.debug("Found memory provider for kind '{}': {}", memoryKind, providerClass.getName());
                ChatMemoryBeanProvider memoryProvider = provider.get();
                return memoryProvider.create();
            }
        }

        LOG.warn("No memory provider found for kind '{}', using default", memoryKind);
        return createDefaultMemoryProvider(config);
    }

    static ChatMemoryProvider createDefaultMemoryProvider(AgentConfig config) {
        int maxMessages = config.memoryMaxMessages();
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(maxMessages)
                .build();
    }

    static void setSystemPropertyIfNotNull(
            List<String> setKeys, String prefix, String providerPrefix, String key, Object value) {
        if (value != null) {
            String fullKey = prefix != null
                    ? "forage." + prefix + "." + providerPrefix + "." + key
                    : "forage." + providerPrefix + "." + key;
            System.setProperty(fullKey, String.valueOf(value));
            setKeys.add(fullKey);
            LOG.trace("Set system property: {}={}", fullKey, value);
        }
    }

    private static void clearSystemProperties(List<String> keys) {
        for (String key : keys) {
            System.clearProperty(key);
            LOG.trace("Cleared system property: {}", key);
        }
    }

    static String getProviderConfigPrefix(String modelKind) {
        return switch (modelKind) {
            case "google-gemini" -> "google";
            case "azure-openai" -> "azure.openai";
            case "openai" -> "openai";
            case "ollama" -> "ollama";
            case "anthropic" -> "anthropic";
            case "mistral-ai" -> "mistral";
            case "hugging-face" -> "huggingface";
            case "watsonx-ai" -> "watsonx";
            case "local-ai" -> "localai";
            case "dashscope" -> "dashscope";
            default -> modelKind.replace("-", ".");
        };
    }

    private static List<ServiceLoader.Provider<ModelProvider>> findModelProviders(ClassLoader classLoader) {
        ServiceLoader<ModelProvider> loader = ServiceLoader.load(ModelProvider.class, classLoader);
        return loader.stream().toList();
    }

    private static List<ServiceLoader.Provider<EmbeddingModelProvider>> findEmbeddingModelProviders(
            ClassLoader classLoader) {
        ServiceLoader<EmbeddingModelProvider> loader = ServiceLoader.load(EmbeddingModelProvider.class, classLoader);
        return loader.stream().toList();
    }

    private static List<ServiceLoader.Provider<EmbeddingStoreProvider>> findEmbeddingStoreProviders(
            ClassLoader classLoader) {
        ServiceLoader<EmbeddingStoreProvider> loader = ServiceLoader.load(EmbeddingStoreProvider.class, classLoader);
        return loader.stream().toList();
    }

    private static List<ServiceLoader.Provider<RetrievalAugmentorProvider>> findRetrievalAugmentorProviders(
            ClassLoader classLoader) {
        ServiceLoader<RetrievalAugmentorProvider> loader =
                ServiceLoader.load(RetrievalAugmentorProvider.class, classLoader);
        return loader.stream().toList();
    }
}
