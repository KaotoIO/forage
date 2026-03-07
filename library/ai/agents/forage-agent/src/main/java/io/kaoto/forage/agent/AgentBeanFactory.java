package io.kaoto.forage.agent;

import java.util.Set;
import org.apache.camel.CamelContext;
import org.apache.camel.component.langchain4j.agent.api.Agent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.core.annotations.FactoryType;
import io.kaoto.forage.core.annotations.ForageFactory;
import io.kaoto.forage.core.common.BeanFactory;
import io.kaoto.forage.core.util.config.ConfigStore;

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

    @Override
    public void configure() {
        ClassLoader cl = camelContext.getApplicationContextClassLoader();
        Set<String> prefixes = AgentCreator.detectPrefixes(cl);

        if (!prefixes.isEmpty()) {
            LOG.info("Detected agent prefixes: {}", prefixes);
            configureMultiAgent(prefixes, cl);
        } else if (AgentCreator.hasDefaultConfig(cl)) {
            LOG.info("Detected default agent configuration");
            configureDefaultAgent(cl);
        } else {
            LOG.debug("No agent configuration found, skipping agent registration");
        }
    }

    private void configureMultiAgent(Set<String> prefixes, ClassLoader cl) {
        for (String agentName : prefixes) {
            if (camelContext.getRegistry().lookupByNameAndType(agentName, Agent.class) == null) {
                try {
                    AgentConfig agentConfig = new AgentConfig(agentName);
                    Agent agent = AgentCreator.createAgent(agentConfig, agentName, cl);
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

    private void configureDefaultAgent(ClassLoader cl) {
        if (camelContext.getRegistry().lookupByNameAndType(AgentCreator.DEFAULT_AGENT, Agent.class) == null) {
            try {
                AgentConfig agentConfig = new AgentConfig();
                Agent agent = AgentCreator.createAgent(agentConfig, AgentCreator.DEFAULT_AGENT, cl);
                if (agent != null) {
                    camelContext.getRegistry().bind(AgentCreator.DEFAULT_AGENT, agent);
                    LOG.info("Registered default Agent bean with name: {}", AgentCreator.DEFAULT_AGENT);
                }
            } catch (Exception e) {
                LOG.warn("Failed to create default agent: {}", e.getMessage());
                LOG.debug("Agent creation exception details", e);
            }
        }
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
