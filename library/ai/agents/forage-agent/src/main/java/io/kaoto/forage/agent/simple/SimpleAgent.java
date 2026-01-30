package io.kaoto.forage.agent.simple;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolProvider;
import io.kaoto.forage.agent.ForageAgentConfiguration;
import io.kaoto.forage.agent.factory.ConfigurationAware;
import java.util.List;
import org.apache.camel.component.langchain4j.agent.api.Agent;
import org.apache.camel.component.langchain4j.agent.api.AgentConfiguration;
import org.apache.camel.component.langchain4j.agent.api.AiAgentBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple implementation of an AI agent that provides basic chat functionality
 */
public class SimpleAgent implements Agent, ConfigurationAware {
    private static final Logger LOG = LoggerFactory.getLogger(SimpleAgent.class);

    private AgentConfiguration configuration;

    // Cached AI service instances to avoid recreating proxies on every request
    private ForageAgentWithMemory cachedMemoryService;
    private ForageAgentWithoutMemory cachedNoMemoryService;
    private ToolProvider lastToolProvider;

    public SimpleAgent() {}

    @Override
    public void configure(AgentConfiguration configuration) {
        this.configuration = configuration;
    }

    private boolean hasMemory() {
        return configuration.getChatMemoryProvider() != null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String chat(AiAgentBody<?> aiAgentBody, ToolProvider toolProvider) {
        LOG.debug("Chatting using ForageAgent");

        if (hasMemory()) {
            LOG.debug("Chatting with memory");
            ForageAgentWithMemory agentService = createAiAgentService(toolProvider, ForageAgentWithMemory.class);

            return aiAgentBody.getSystemMessage() != null
                    ? agentService.chat(
                            aiAgentBody.getMemoryId(), aiAgentBody.getUserMessage(), aiAgentBody.getSystemMessage())
                    : agentService.chat(aiAgentBody.getMemoryId(), aiAgentBody.getUserMessage());
        } else {
            LOG.debug("Chatting without memory");
            ForageAgentWithoutMemory agentService = createAiAgentService(toolProvider, ForageAgentWithoutMemory.class);

            if (aiAgentBody.getContent() != null) {
                Content content = aiAgentBody.getContent();
                return agentService.chat(aiAgentBody.getUserMessage(), List.of(content));
            }

            return aiAgentBody.getSystemMessage() != null
                    ? agentService.chat(aiAgentBody.getUserMessage(), aiAgentBody.getSystemMessage())
                    : agentService.chat(aiAgentBody.getUserMessage());
        }
    }

    /**
     * Create AI service with a single universal tool that handles multiple Camel routes and Memory Provider.
     * Services are cached to avoid recreating proxies on every request when the toolProvider is unchanged.
     */
    @SuppressWarnings("unchecked")
    private <T> T createAiAgentService(ToolProvider toolProvider, Class<T> clazz) {
        // Check if we can return a cached instance
        if (toolProvider == lastToolProvider) {
            if (clazz == ForageAgentWithMemory.class && cachedMemoryService != null) {
                LOG.debug("Reusing cached ForageAgentWithMemory service");
                return (T) cachedMemoryService;
            } else if (clazz == ForageAgentWithoutMemory.class && cachedNoMemoryService != null) {
                LOG.debug("Reusing cached ForageAgentWithoutMemory service");
                return (T) cachedNoMemoryService;
            }
        } else {
            // Tool provider changed, invalidate cache
            cachedMemoryService = null;
            cachedNoMemoryService = null;
            lastToolProvider = toolProvider;
        }

        LOG.info("Creating new {} service", clazz.getSimpleName());
        AiServices<T> builder = AiServices.builder(clazz).chatModel(configuration.getChatModel());

        if (hasMemory()) {
            builder = builder.chatMemoryProvider(configuration.getChatMemoryProvider());
        }

        // Apache Camel Tool Provider
        if (toolProvider != null) {
            builder.toolProvider(toolProvider);
        }

        // RAG
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

        // Cache the service
        if (clazz == ForageAgentWithMemory.class) {
            cachedMemoryService = (ForageAgentWithMemory) service;
        } else if (clazz == ForageAgentWithoutMemory.class) {
            cachedNoMemoryService = (ForageAgentWithoutMemory) service;
        }

        return service;
    }
}
