package io.kaoto.forage.quarkus.agent.deployment;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.camel.component.langchain4j.agent.api.Agent;
import org.apache.camel.quarkus.core.deployment.spi.CamelContextBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelRuntimeBeanBuildItem;
import org.jboss.logging.Logger;
import io.kaoto.forage.agent.AgentConfig;
import io.kaoto.forage.agent.AgentModuleDescriptor;
import io.kaoto.forage.core.annotations.FactoryType;
import io.kaoto.forage.core.annotations.FactoryVariant;
import io.kaoto.forage.core.annotations.ForageFactory;
import io.kaoto.forage.core.util.config.ConfigHelper;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.quarkus.agent.ForageAgentRecorder;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.runtime.RuntimeValue;

@ForageFactory(
        value = "Agent (Quarkus)",
        components = {"camel-langchain4j-agent"},
        description = "AI Agent for Quarkus with native ChatModel integration via quarkus-langchain4j",
        type = FactoryType.AGENT,
        autowired = true,
        configClass = AgentConfig.class,
        variant = FactoryVariant.QUARKUS)
public class ForageAgentProcessor {

    private static final Logger LOG = Logger.getLogger(ForageAgentProcessor.class);
    private static final String FEATURE = "forage-agent";
    private static final AgentModuleDescriptor DESCRIPTOR = new AgentModuleDescriptor();

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void discoverAgents(BuildProducer<ForageAgentBuildItem> agents) {
        AgentConfig defaultConfig = DESCRIPTOR.createConfig(null);
        Set<String> prefixes = ConfigStore.getInstance()
                .readPrefixes(defaultConfig, ConfigHelper.getNamedPropertyRegexp(DESCRIPTOR.modulePrefix()));

        Map<String, AgentConfig> configs = prefixes.isEmpty()
                ? Collections.singletonMap(DESCRIPTOR.defaultBeanName(), DESCRIPTOR.createConfig(null))
                : prefixes.stream().collect(Collectors.toMap(n -> n, DESCRIPTOR::createConfig));

        for (Map.Entry<String, AgentConfig> entry : configs.entrySet()) {
            if (entry.getValue().modelKind() != null) {
                agents.produce(new ForageAgentBuildItem(entry.getKey(), entry.getValue()));
            }
        }
    }

    @BuildStep
    @Record(value = ExecutionTime.RUNTIME_INIT)
    void registerAgents(
            CamelContextBuildItem context,
            ForageAgentRecorder recorder,
            List<ForageAgentBuildItem> agents,
            BuildProducer<CamelRuntimeBeanBuildItem> beans) {

        for (ForageAgentBuildItem agentItem : agents) {
            String name = agentItem.getName();
            LOG.infof(
                    "Recording Agent bean '%s' with model kind: %s",
                    name, agentItem.getConfig().modelKind());

            RuntimeValue<Agent> agent = recorder.createAgent(name, context.getCamelContext());
            if (agent != null) {
                beans.produce(new CamelRuntimeBeanBuildItem(name, Agent.class.getName(), agent));
            }
        }
    }
}
