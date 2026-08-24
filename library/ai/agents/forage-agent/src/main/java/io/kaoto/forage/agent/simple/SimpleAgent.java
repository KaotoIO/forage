package io.kaoto.forage.agent.simple;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.camel.component.langchain4j.agent.api.Agent;
import org.apache.camel.component.langchain4j.agent.api.AgentConfiguration;
import org.apache.camel.component.langchain4j.agent.api.AiAgentBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.agent.factory.ConfigurationAware;
import io.kaoto.forage.agent.factory.ForageAgentConfiguration;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;

/**
 * Simple implementation of an AI agent that provides basic chat functionality.
 *
 * <p>This class is a singleton shared across threads — it is created once by {@link io.kaoto.forage.agent.AgentCreator}
 * and registered in the Camel registry. The {@link #configure(AgentConfiguration)} method is called
 * exactly once during creation; the configuration is immutable for the agent's lifetime.
 *
 * <p>The {@link AiServices} proxy is built once and cached. A {@link DelegatingToolProvider} decouples
 * the cached proxy from the per-exchange {@link ToolProvider} that Camel creates on every exchange.
 * This relies on LangChain4j calling {@code provideTools()} synchronously on the same thread that
 * calls {@code chat()} (verified in {@code ToolService.java} lines 370-379, LangChain4j 1.11.0).
 */
public class SimpleAgent implements Agent, ConfigurationAware {
    private static final Logger LOG = LoggerFactory.getLogger(SimpleAgent.class);

    // Set once by configure() during agent creation — immutable after startup
    private AgentConfiguration configuration;

    private final DelegatingToolProvider delegatingToolProvider = new DelegatingToolProvider();
    private final ReentrantLock buildLock = new ReentrantLock();

    private volatile ForageAgentWithMemory cachedMemoryService;
    private volatile ForageAgentWithoutMemory cachedNoMemoryService;

    public SimpleAgent() {}

    @Override
    public void configure(AgentConfiguration configuration) {
        this.configuration = configuration;

        if (configuration.getCustomTools() != null
                && !configuration.getCustomTools().isEmpty()) {
            LOG.warn(
                    "customTools configured on AgentConfiguration but not yet supported by Forage SimpleAgent (see issue #83)");
        }
        if (configuration.getMcpClients() != null
                && !configuration.getMcpClients().isEmpty()) {
            LOG.warn(
                    "mcpClients configured on AgentConfiguration but not yet supported by Forage SimpleAgent (see issue #83)");
        }
    }

    private boolean hasMemory() {
        return configuration.getChatMemoryProvider() != null;
    }

    @Override
    public Result<String> chat(AiAgentBody<?> aiAgentBody, ToolProvider toolProvider) {
        LOG.debug("Chatting using ForageAgent");

        Content content = aiAgentBody.getContent();
        String systemMessage = aiAgentBody.getSystemMessage();
        String userMessage = aiAgentBody.getUserMessage();

        if (hasMemory()) {
            LOG.debug("Chatting with memory");
            ForageAgentWithMemory agentService = getOrCreateService(toolProvider, ForageAgentWithMemory.class);
            Object memoryId = aiAgentBody.getMemoryId();

            ToolProvider previous = delegatingToolProvider.swapDelegate(toolProvider);
            try {
                if (content != null && systemMessage != null) {
                    return agentService.chat(memoryId, userMessage, List.of(content), systemMessage);
                } else if (content != null) {
                    return agentService.chat(memoryId, userMessage, List.of(content));
                } else if (systemMessage != null) {
                    return agentService.chat(memoryId, userMessage, systemMessage);
                } else {
                    return agentService.chat(memoryId, userMessage);
                }
            } finally {
                delegatingToolProvider.restoreDelegate(previous);
            }
        } else {
            LOG.debug("Chatting without memory");
            ForageAgentWithoutMemory agentService = getOrCreateService(toolProvider, ForageAgentWithoutMemory.class);

            ToolProvider previous = delegatingToolProvider.swapDelegate(toolProvider);
            try {
                if (content != null && systemMessage != null) {
                    return agentService.chat(userMessage, List.of(content), systemMessage);
                } else if (content != null) {
                    return agentService.chat(userMessage, List.of(content));
                } else if (systemMessage != null) {
                    return agentService.chat(userMessage, systemMessage);
                } else {
                    return agentService.chat(userMessage);
                }
            } finally {
                delegatingToolProvider.restoreDelegate(previous);
            }
        }
    }

    /**
     * Returns a cached AI service or builds one on first call.
     */
    @SuppressWarnings("unchecked")
    private <T> T getOrCreateService(ToolProvider toolProvider, Class<T> clazz) {
        // Fast path: volatile read — visible across threads without locking
        if (clazz == ForageAgentWithMemory.class && cachedMemoryService != null) {
            LOG.debug("Reusing cached ForageAgentWithMemory service");
            return (T) cachedMemoryService;
        } else if (clazz == ForageAgentWithoutMemory.class && cachedNoMemoryService != null) {
            LOG.debug("Reusing cached ForageAgentWithoutMemory service");
            return (T) cachedNoMemoryService;
        }

        return buildAiAgentService(clazz);
    }

    /**
     * Builds the {@link AiServices} proxy. Uses {@link ReentrantLock} instead of {@code synchronized}
     * to avoid pinning virtual threads to their carrier thread (Java 21-23).
     */
    @SuppressWarnings("unchecked")
    private <T> T buildAiAgentService(Class<T> clazz) {
        buildLock.lock();
        try {
            // Double-check: another thread may have built it while we waited for the lock
            if (clazz == ForageAgentWithMemory.class && cachedMemoryService != null) {
                return (T) cachedMemoryService;
            } else if (clazz == ForageAgentWithoutMemory.class && cachedNoMemoryService != null) {
                return (T) cachedNoMemoryService;
            }

            LOG.info("Creating new {} service", clazz.getSimpleName());
            AiServices<T> builder = AiServices.builder(clazz).chatModel(configuration.getChatModel());

            if (hasMemory()) {
                builder = builder.chatMemoryProvider(configuration.getChatMemoryProvider());
            }

            builder.toolProvider(delegatingToolProvider);

            if (configuration.getRetrievalAugmentor() != null) {
                builder.retrievalAugmentor(configuration.getRetrievalAugmentor());
            }

            // Input Guardrails - prefer instances over classes
            if (configuration instanceof ForageAgentConfiguration forageConfig && forageConfig.hasInputGuardrails()) {
                List<InputGuardrail> inputGuardrails = forageConfig.getInputGuardrails();
                builder.inputGuardrails(inputGuardrails.toArray(new InputGuardrail[0]));
                LOG.debug("Using {} input guardrail instances", inputGuardrails.size());
            } else if (configuration.getInputGuardrailClasses() != null
                    && !configuration.getInputGuardrailClasses().isEmpty()) {
                builder.inputGuardrailClasses((List) configuration.getInputGuardrailClasses());
            }

            // Output Guardrails - prefer instances over classes
            if (configuration instanceof ForageAgentConfiguration forageConfig && forageConfig.hasOutputGuardrails()) {
                List<OutputGuardrail> outputGuardrails = forageConfig.getOutputGuardrails();
                builder.outputGuardrails(outputGuardrails.toArray(new OutputGuardrail[0]));
                LOG.debug("Using {} output guardrail instances", outputGuardrails.size());
            } else if (configuration.getOutputGuardrailClasses() != null
                    && !configuration.getOutputGuardrailClasses().isEmpty()) {
                builder.outputGuardrailClasses((List) configuration.getOutputGuardrailClasses());
            }

            T service = builder.build();

            // volatile write — visible to all threads on next read
            if (clazz == ForageAgentWithMemory.class) {
                cachedMemoryService = (ForageAgentWithMemory) service;
            } else if (clazz == ForageAgentWithoutMemory.class) {
                cachedNoMemoryService = (ForageAgentWithoutMemory) service;
            }

            return service;
        } finally {
            buildLock.unlock();
        }
    }

    /**
     * Wraps a {@link ThreadLocal} {@link ToolProvider} delegate so the {@link AiServices} proxy can be built once
     * and reused across exchanges while the underlying tool provider is bound per-thread on each call.
     *
     * <p>This relies on LangChain4j's {@code ToolService} calling {@code provideTools()} synchronously
     * on the same thread that calls {@code chat()} — verified in {@code ToolService.java} lines 370-379
     * (LangChain4j 1.11.0). No async executors or thread pools are involved in tool provider resolution.
     *
     * <p>The delegate is swapped before each {@code chat()} call and restored in a {@code finally} block
     * to support nested calls (e.g. a tool route that itself invokes the agent) and to prevent stale
     * references on reused platform threads.
     */
    private static class DelegatingToolProvider implements ToolProvider {
        private final ThreadLocal<ToolProvider> delegate = new ThreadLocal<>();

        ToolProvider swapDelegate(ToolProvider toolProvider) {
            ToolProvider previous = delegate.get();
            delegate.set(toolProvider);
            return previous;
        }

        void restoreDelegate(ToolProvider previous) {
            if (previous == null) {
                delegate.remove();
            } else {
                delegate.set(previous);
            }
        }

        @Override
        public ToolProviderResult provideTools(ToolProviderRequest request) {
            ToolProvider current = delegate.get();
            if (current == null) {
                return ToolProviderResult.builder().build();
            }
            return current.provideTools(request);
        }
    }
}
