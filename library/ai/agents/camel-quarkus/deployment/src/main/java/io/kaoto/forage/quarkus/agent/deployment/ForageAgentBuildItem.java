package io.kaoto.forage.quarkus.agent.deployment;

import io.kaoto.forage.agent.AgentConfig;
import io.quarkus.builder.item.MultiBuildItem;

/**
 * Build item carrying a discovered Forage agent prefix and its configuration.
 *
 * @since 1.1
 */
public final class ForageAgentBuildItem extends MultiBuildItem {

    private final String name;
    private final AgentConfig config;

    public ForageAgentBuildItem(String name, AgentConfig config) {
        this.name = name;
        this.config = config;
    }

    public String getName() {
        return name;
    }

    public AgentConfig getConfig() {
        return config;
    }
}
