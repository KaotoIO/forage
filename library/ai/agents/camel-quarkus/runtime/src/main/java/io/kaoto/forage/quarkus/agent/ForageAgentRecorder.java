package io.kaoto.forage.quarkus.agent;

import org.apache.camel.CamelContext;
import org.apache.camel.component.langchain4j.agent.api.Agent;
import org.jboss.logging.Logger;
import io.kaoto.forage.agent.AgentConfig;
import io.kaoto.forage.agent.AgentCreator;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import dev.langchain4j.model.chat.ChatModel;

/**
 * Quarkus recorder that creates Agent beans at runtime.
 *
 * <p>Looks up a ChatModel from the CamelContext registry (placed there by quarkus-langchain4j
 * via CDI and property translation), then composes the Agent with memory, guardrails, and RAG
 * via {@link AgentCreator}.
 *
 * @since 1.1
 */
@Recorder
public class ForageAgentRecorder {
    private static final Logger LOG = Logger.getLogger(ForageAgentRecorder.class);

    public RuntimeValue<Agent> createAgent(String name, RuntimeValue<CamelContext> ctx) {
        CamelContext camelContext = ctx.getValue();
        AgentConfig config = AgentCreator.DEFAULT_AGENT.equals(name) ? new AgentConfig() : new AgentConfig(name);
        ClassLoader cl = camelContext.getApplicationContextClassLoader();

        // Try to look up ChatModel from registry (created by quarkus-langchain4j via property translation)
        ChatModel chatModel = camelContext.getRegistry().lookupByNameAndType(name, ChatModel.class);
        Agent agent;
        if (chatModel != null) {
            LOG.infof("Found ChatModel '%s' in registry, composing Agent with it", name);
            agent = AgentCreator.createAgent(config, name, cl, chatModel);
        } else {
            LOG.infof("No ChatModel '%s' in registry, creating Agent via ServiceLoader", name);
            agent = AgentCreator.createAgent(config, name, cl);
        }

        if (agent != null) {
            return new RuntimeValue<>(agent);
        }
        return null;
    }
}
