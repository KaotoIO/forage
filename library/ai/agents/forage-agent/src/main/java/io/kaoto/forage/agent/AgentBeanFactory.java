package io.kaoto.forage.agent;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.kaoto.forage.agent.factory.ConfigurationAware;
import io.kaoto.forage.core.ai.ChatMemoryBeanProvider;
import io.kaoto.forage.core.ai.EmbeddingModelAware;
import io.kaoto.forage.core.ai.EmbeddingModelProvider;
import io.kaoto.forage.core.ai.EmbeddingStoreAware;
import io.kaoto.forage.core.ai.EmbeddingStoreProvider;
import io.kaoto.forage.core.ai.ModelProvider;
import io.kaoto.forage.core.ai.RetrievalAugmentorProvider;
import io.kaoto.forage.core.annotations.FactoryType;
import io.kaoto.forage.core.annotations.ForageBean;
import io.kaoto.forage.core.annotations.ForageFactory;
import io.kaoto.forage.core.common.BeanFactory;
import io.kaoto.forage.core.guardrails.InputGuardrailProvider;
import io.kaoto.forage.core.guardrails.OutputGuardrailProvider;
import io.kaoto.forage.core.util.config.ConfigHelper;
import io.kaoto.forage.core.util.config.ConfigStore;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import org.apache.camel.CamelContext;
import org.apache.camel.component.langchain4j.agent.api.Agent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BeanFactory that registers Agent beans into the CamelContext registry.
 *
 * <p>Uses prefix auto-detection like JDBC and JMS factories. Agent prefixes are
 * detected from properties matching pattern {@code {prefix}.agent.*}.
 *
 * <p>Example configurations:
 * <pre>
 * # Single agent (no prefix needed)
 * agent.model.kind=ollama
 * agent.base.url=http://localhost:11434
 * agent.model.name=llama3
 *
 * # Multiple agents (prefixes auto-detected)
 * google.agent.model.kind=google-gemini
 * google.agent.api.key=your-key
 * google.agent.model.name=gemini-2.0-flash
 *
 * ollama.agent.model.kind=ollama
 * ollama.agent.base.url=http://localhost:11434
 * ollama.agent.model.name=llama3
 * </pre>
 */
@ForageFactory(
        value = "Agent",
        components = {"camel-langchain4j-agent"},
        description =
                "Creates AI agents with configurable chat models and memory providers for LangChain4j integration",
        type = FactoryType.AGENT,
        autowired = true,
        configClass = AgentConfig.class)
public class AgentBeanFactory implements BeanFactory {
    private static final Logger LOG = LoggerFactory.getLogger(AgentBeanFactory.class);

    private CamelContext camelContext;
    private static final String DEFAULT_AGENT = "agent";
    private static final String FEATURE_MEMORY = "memory";

    @Override
    public void configure() {
        AgentConfig defaultConfig = new AgentConfig();

        // Auto-detect prefixes from properties like "google.agent.*", "ollama.agent.*"
        Set<String> prefixes =
                ConfigStore.getInstance().readPrefixes(defaultConfig, ConfigHelper.getNamedPropertyRegexp("agent"));

        if (!prefixes.isEmpty()) {
            LOG.info("Detected agent prefixes: {}", prefixes);
            configureMultiAgent(prefixes);
        } else {
            // Check if there's a default (non-prefixed) agent configuration
            Set<String> defaultPrefixes = ConfigStore.getInstance()
                    .readPrefixes(defaultConfig, ConfigHelper.getDefaultPropertyRegexp("agent"));
            if (!defaultPrefixes.isEmpty()) {
                LOG.info("Detected default agent configuration");
                configureDefaultAgent();
            } else {
                LOG.debug("No agent configuration found, skipping agent registration");
            }
        }
    }

    private void configureMultiAgent(Set<String> prefixes) {
        for (String agentName : prefixes) {
            if (camelContext.getRegistry().lookupByNameAndType(agentName, Agent.class) == null) {
                try {
                    AgentConfig agentConfig = new AgentConfig(agentName);
                    Agent agent = createAgent(agentConfig, agentName);
                    if (agent != null) {
                        camelContext.getRegistry().bind(agentName, agent);
                        LOG.info("Registered Agent bean with name: {}", agentName);
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to create agent '{}': {}", agentName, e.getMessage());
                    LOG.debug("Agent creation exception details", e);
                }
            }
        }
    }

    private void configureDefaultAgent() {
        if (camelContext.getRegistry().lookupByNameAndType(DEFAULT_AGENT, Agent.class) == null) {
            try {
                AgentConfig agentConfig = new AgentConfig();
                Agent agent = createAgent(agentConfig, DEFAULT_AGENT);
                if (agent != null) {
                    camelContext.getRegistry().bind(DEFAULT_AGENT, agent);
                    LOG.info("Registered default Agent bean with name: {}", DEFAULT_AGENT);
                }
            } catch (Exception e) {
                LOG.warn("Failed to create default agent: {}", e.getMessage());
                LOG.debug("Agent creation exception details", e);
            }
        }
    }

    private Agent createAgent(AgentConfig config, String name) {
        String modelKind = config.modelKind();
        if (modelKind == null) {
            LOG.warn("No model kind configured for agent '{}'", name);
            return null;
        }

        LOG.info("Creating agent '{}' with model kind: {}", name, modelKind);

        // Create chat model
        ChatModel chatModel = createChatModel(config, modelKind, name);
        if (chatModel == null) {
            LOG.warn("Failed to create chat model for agent '{}'", name);
            return null;
        }

        // Create memory provider if enabled
        ChatMemoryProvider chatMemoryProvider = null;
        if (config.hasFeature(FEATURE_MEMORY)) {
            String memoryKind = config.memoryKind();
            if (memoryKind != null) {
                chatMemoryProvider = createMemoryProvider(config, memoryKind);
            } else {
                // Default to message-window memory
                chatMemoryProvider = createDefaultMemoryProvider(config);
            }
        }

        // Find and create agent using ServiceLoader
        Agent agent = findAndCreateAgent();
        if (agent == null) {
            LOG.warn("No Agent implementation found in classpath");
            return null;
        }

        // Configure the agent
        if (agent instanceof ConfigurationAware configurationAware) {
            ForageAgentConfiguration agentConfiguration = new ForageAgentConfiguration();
            agentConfiguration.withChatModel(chatModel).withChatMemoryProvider(chatMemoryProvider);

            EmbeddingModel embeddingModel = createEmbeddingModel(config, modelKind, name);
            EmbeddingStore<TextSegment> embeddingStore = createEmbeddingStore(config, modelKind, name, embeddingModel);

            // Create RetrievalAugmentor
            RetrievalAugmentor retrievalAugmentor =
                    createRetrievalAugmentor(config, modelKind, name, embeddingModel, embeddingStore);

            if (retrievalAugmentor != null) {
                agentConfiguration.withRetrievalAugmentor(retrievalAugmentor);
            }

            // Load input guardrail instances via ServiceLoader
            List<InputGuardrail> inputGuardrails = loadInputGuardrails(name);
            if (!inputGuardrails.isEmpty()) {
                agentConfiguration.withInputGuardrails(inputGuardrails);
                LOG.info("Configured {} input guardrails for agent '{}'", inputGuardrails.size(), name);
            }

            // Load output guardrail instances via ServiceLoader
            List<OutputGuardrail> outputGuardrails = loadOutputGuardrails(name);
            if (!outputGuardrails.isEmpty()) {
                agentConfiguration.withOutputGuardrails(outputGuardrails);
                LOG.info("Configured {} output guardrails for agent '{}'", outputGuardrails.size(), name);
            }

            configurationAware.configure(agentConfiguration);
        }

        return agent;
    }

    private List<InputGuardrail> loadInputGuardrails(String agentName) {
        List<InputGuardrail> guardrails = new ArrayList<>();
        List<ServiceLoader.Provider<InputGuardrailProvider>> providers = findInputGuardrailProviders();

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

    private List<OutputGuardrail> loadOutputGuardrails(String agentName) {
        List<OutputGuardrail> guardrails = new ArrayList<>();
        List<ServiceLoader.Provider<OutputGuardrailProvider>> providers = findOutputGuardrailProviders();

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

    private List<ServiceLoader.Provider<InputGuardrailProvider>> findInputGuardrailProviders() {
        ServiceLoader<InputGuardrailProvider> loader =
                ServiceLoader.load(InputGuardrailProvider.class, camelContext.getApplicationContextClassLoader());
        return loader.stream().toList();
    }

    private List<ServiceLoader.Provider<OutputGuardrailProvider>> findOutputGuardrailProviders() {
        ServiceLoader<OutputGuardrailProvider> loader =
                ServiceLoader.load(OutputGuardrailProvider.class, camelContext.getApplicationContextClassLoader());
        return loader.stream().toList();
    }

    private ChatModel createChatModel(AgentConfig config, String modelKind, String agentName) {
        // Find model provider by kind using ServiceLoader
        List<ServiceLoader.Provider<ModelProvider>> providers = findModelProviders();

        for (ServiceLoader.Provider<ModelProvider> provider : providers) {
            Class<? extends ModelProvider> providerClass = provider.type();
            ForageBean annotation = providerClass.getAnnotation(ForageBean.class);
            if (annotation != null && annotation.value().equals(modelKind)) {
                LOG.debug("Found model provider for kind '{}': {}", modelKind, providerClass.getName());
                ModelProvider modelProvider = provider.get();

                // Create model using unified config
                return createChatModelFromConfig(config, modelKind, modelProvider, agentName);
            }
        }

        LOG.warn("No model provider found for kind: {}", modelKind);
        return null;
    }

    private EmbeddingModel createEmbeddingModel(AgentConfig config, String modelKind, String agentName) {
        // Find model provider by kind using ServiceLoader
        List<ServiceLoader.Provider<EmbeddingModelProvider>> providers = findEmbeddingModelProviders();

        for (ServiceLoader.Provider<EmbeddingModelProvider> provider : providers) {
            Class<? extends EmbeddingModelProvider> providerClass = provider.type();
            ForageBean annotation = providerClass.getAnnotation(ForageBean.class);
            if (annotation != null && annotation.value().equals(modelKind)) {
                LOG.debug("Found embedding model provider for kind '{}': {}", modelKind, providerClass.getName());
                EmbeddingModelProvider modelProvider = provider.get();

                // Create model using unified config
                return createEmbeddingModelFromConfig(config, modelKind, modelProvider, agentName);
            }
        }

        LOG.warn("No model provider found for kind: {}", modelKind);
        return null;
    }

    private EmbeddingStore<TextSegment> createEmbeddingStore(
            AgentConfig config, String modelKind, String agentName, EmbeddingModel model) {
        // Find model provider by kind using ServiceLoader
        List<ServiceLoader.Provider<EmbeddingStoreProvider>> providers = findEmbeddingStoreProviders();

        for (ServiceLoader.Provider<EmbeddingStoreProvider> provider : providers) {
            Class<? extends EmbeddingStoreProvider> providerClass = provider.type();
            LOG.debug("Found embedding store provider for kind '{}': {}", modelKind, providerClass.getName());
            EmbeddingStoreProvider modelProvider = provider.get();

            // Create model using unified config
            return createEmbeddingStoreFromConfig(config, modelKind, modelProvider, agentName, model);
        }

        LOG.warn("No model provider found for kind: {}", modelKind);
        return null;
    }

    private RetrievalAugmentor createRetrievalAugmentor(
            AgentConfig config,
            String modelKind,
            String agentName,
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore) {
        // Find model provider by kind using ServiceLoader
        List<ServiceLoader.Provider<RetrievalAugmentorProvider>> providers = findRetrievalAugmentorProviders();

        for (ServiceLoader.Provider<RetrievalAugmentorProvider> provider : providers) {
            Class<? extends RetrievalAugmentorProvider> providerClass = provider.type();
            LOG.debug("Found retrieval augmentor provider for kind '{}': {}", modelKind, providerClass.getName());
            RetrievalAugmentorProvider retrievalAugmentorProvider = provider.get();

            // Create model using unified config
            return createRetrievalAugmentorFromConfig(
                    config, modelKind, retrievalAugmentorProvider, agentName, embeddingModel, embeddingStore);
        }

        LOG.warn("No model provider found for kind: {}", modelKind);
        return null;
    }

    private ChatModel createChatModelFromConfig(
            AgentConfig config, String modelKind, ModelProvider modelProvider, String agentName) {
        // Map unified agent config values to provider-specific config keys
        // Provider configs expect keys like: {prefix}.{provider}.api.key
        // We have values in: {prefix}.agent.api.key
        // So we need to set system properties that the provider's loadOverrides will pick up

        String providerPrefix = getProviderConfigPrefix(modelKind);
        String prefix = DEFAULT_AGENT.equals(agentName) ? null : agentName;

        // Set provider config values as system properties (provider's loadOverrides will pick these up)
        setSystemPropertyIfNotNull(prefix, providerPrefix, "api.key", config.apiKey());
        setSystemPropertyIfNotNull(prefix, providerPrefix, "model.name", config.modelName());
        setSystemPropertyIfNotNull(prefix, providerPrefix, "base.url", config.baseUrl());
        setSystemPropertyIfNotNull(prefix, providerPrefix, "temperature", config.temperature());
        setSystemPropertyIfNotNull(prefix, providerPrefix, "max.tokens", config.maxTokens());
        setSystemPropertyIfNotNull(prefix, providerPrefix, "top.p", config.topP());
        setSystemPropertyIfNotNull(prefix, providerPrefix, "top.k", config.topK());
        setSystemPropertyIfNotNull(prefix, providerPrefix, "endpoint", config.endpoint());
        setSystemPropertyIfNotNull(prefix, providerPrefix, "deployment.name", config.deploymentName());
        setSystemPropertyIfNotNull(prefix, providerPrefix, "log.requests", config.logRequests());
        setSystemPropertyIfNotNull(prefix, providerPrefix, "log.responses", config.logResponses());

        return modelProvider.create(prefix);
    }

    private EmbeddingModel createEmbeddingModelFromConfig(
            AgentConfig config, String modelKind, EmbeddingModelProvider modelProvider, String agentName) {
        // Map unified agent config values to provider-specific config keys
        // Provider configs expect keys like: {prefix}.{provider}.api.key
        // We have values in: {prefix}.agent.api.key
        // So we need to set system properties that the provider's loadOverrides will pick up

        String providerPrefix = getProviderConfigPrefix(modelKind);
        String prefix = DEFAULT_AGENT.equals(agentName) ? null : agentName;

        // Set provider config values as system properties (provider's loadOverrides will pick these up)
        setSystemPropertyIfNotNull(prefix, providerPrefix, "embedding.model.name", config.embeddingModelName());
        setSystemPropertyIfNotNull(prefix, providerPrefix, "embedding.model.timeout", config.embeddingModelTimeout());
        setSystemPropertyIfNotNull(prefix, providerPrefix, "embedding.max.retries", config.embeddingModelMaxRetries());
        setSystemPropertyIfNotNull(prefix, providerPrefix, "embedding.base.url", config.embeddingModelBaseUrl());

        return modelProvider.create(prefix);
    }

    private EmbeddingStore<TextSegment> createEmbeddingStoreFromConfig(
            AgentConfig config,
            String modelKind,
            EmbeddingStoreProvider embeddingStoreProvider,
            String agentName,
            EmbeddingModel embeddingModel) {
        // Map unified agent config values to provider-specific config keys
        // Provider configs expect keys like: {prefix}.{provider}.api.key
        // We have values in: {prefix}.agent.api.key
        // So we need to set system properties that the provider's loadOverrides will pick up

        String providerPrefix = getProviderConfigPrefix(modelKind);
        String prefix = DEFAULT_AGENT.equals(agentName) ? null : agentName;

        // Set provider config values as system properties (provider's loadOverrides will pick these up)
        setSystemPropertyIfNotNull(null, providerPrefix, "in.memory.store.file.source", config.fileSource());
        setSystemPropertyIfNotNull(null, providerPrefix, "in.memory.store.max.size", config.embeddingStoreMaxSize());
        setSystemPropertyIfNotNull(
                null, providerPrefix, "in.memory.store.overlap.size", config.embeddingStoreOverlapSize());

        if (embeddingStoreProvider instanceof EmbeddingModelAware) {
            ((EmbeddingModelAware) embeddingStoreProvider).withEmbeddingModel(embeddingModel);
        }

        return embeddingStoreProvider.create(prefix);
    }

    private RetrievalAugmentor createRetrievalAugmentorFromConfig(
            AgentConfig config,
            String modelKind,
            RetrievalAugmentorProvider retrievalAugmentorProvider,
            String agentName,
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore) {
        // Map unified agent config values to provider-specific config keys
        // Provider configs expect keys like: {prefix}.{provider}.api.key
        // We have values in: {prefix}.agent.api.key
        // So we need to set system properties that the provider's loadOverrides will pick up

        String providerPrefix = getProviderConfigPrefix(modelKind);
        String prefix = DEFAULT_AGENT.equals(agentName) ? null : agentName;

        setSystemPropertyIfNotNull(null, providerPrefix, "rag.max.results", config.defaultRagMaxResults());
        setSystemPropertyIfNotNull(null, providerPrefix, "rag.min.score", config.defaultRagMinScore());

        if (retrievalAugmentorProvider instanceof EmbeddingModelAware) {
            ((EmbeddingModelAware) retrievalAugmentorProvider).withEmbeddingModel(embeddingModel);
        }
        if (retrievalAugmentorProvider instanceof EmbeddingStoreAware) {
            ((EmbeddingStoreAware) retrievalAugmentorProvider).withEmbeddingStore(embeddingStore);
        }

        return retrievalAugmentorProvider.create(prefix);
    }

    private void setSystemPropertyIfNotNull(String prefix, String providerPrefix, String key, Object value) {
        if (value != null) {
            String fullKey = prefix != null
                    ? "forage." + prefix + "." + providerPrefix + "." + key
                    : "forage." + providerPrefix + "." + key;
            System.setProperty(fullKey, String.valueOf(value));
            LOG.trace("Set system property: {}={}", fullKey, value);
        }
    }

    private String getProviderConfigPrefix(String modelKind) {
        // Map model kind to provider config prefix
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

    private ChatMemoryProvider createMemoryProvider(AgentConfig config, String memoryKind) {
        List<ServiceLoader.Provider<ChatMemoryBeanProvider>> providers = findMChatModelProviders();

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

    private ChatMemoryProvider createDefaultMemoryProvider(AgentConfig config) {
        int maxMessages = config.memoryMaxMessages();
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(maxMessages)
                .build();
    }

    private Agent findAndCreateAgent() {
        List<ServiceLoader.Provider<Agent>> providers = findAgents();
        if (!providers.isEmpty()) {
            return providers.get(0).get();
        }
        return null;
    }

    private List<ServiceLoader.Provider<ModelProvider>> findModelProviders() {
        ServiceLoader<ModelProvider> loader =
                ServiceLoader.load(ModelProvider.class, camelContext.getApplicationContextClassLoader());
        return loader.stream().toList();
    }

    private List<ServiceLoader.Provider<ChatMemoryBeanProvider>> findMChatModelProviders() {
        ServiceLoader<ChatMemoryBeanProvider> loader =
                ServiceLoader.load(ChatMemoryBeanProvider.class, camelContext.getApplicationContextClassLoader());
        return loader.stream().toList();
    }

    private List<ServiceLoader.Provider<EmbeddingModelProvider>> findEmbeddingModelProviders() {
        ServiceLoader<EmbeddingModelProvider> loader =
                ServiceLoader.load(EmbeddingModelProvider.class, camelContext.getApplicationContextClassLoader());
        return loader.stream().toList();
    }

    private List<ServiceLoader.Provider<EmbeddingStoreProvider>> findEmbeddingStoreProviders() {
        ServiceLoader<EmbeddingStoreProvider> loader =
                ServiceLoader.load(EmbeddingStoreProvider.class, camelContext.getApplicationContextClassLoader());
        return loader.stream().toList();
    }

    private List<ServiceLoader.Provider<RetrievalAugmentorProvider>> findRetrievalAugmentorProviders() {
        ServiceLoader<RetrievalAugmentorProvider> loader =
                ServiceLoader.load(RetrievalAugmentorProvider.class, camelContext.getApplicationContextClassLoader());
        return loader.stream().toList();
    }

    private List<ServiceLoader.Provider<Agent>> findAgents() {
        ServiceLoader<Agent> loader = ServiceLoader.load(Agent.class, camelContext.getApplicationContextClassLoader());
        return loader.stream().toList();
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
        ConfigStore.getInstance().setClassLoader(camelContext.getApplicationContextClassLoader());
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }
}
