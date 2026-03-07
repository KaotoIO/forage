package io.kaoto.forage.quarkus.agent;

import io.kaoto.forage.agent.AgentConfig;
import io.kaoto.forage.agent.AgentModuleDescriptor;
import io.kaoto.forage.core.common.ForageModuleDescriptor;
import io.kaoto.forage.core.common.ForageQuarkusConfigSourceAdapter;

/**
 * SmallRye {@link io.smallrye.config.ConfigSourceFactory} that translates Forage agent properties
 * into Quarkus langchain4j properties at config bootstrap time.
 *
 * <p>Delegates all logic to {@link ForageQuarkusConfigSourceAdapter} using the {@link AgentModuleDescriptor}.
 *
 * <p>Registered via {@code META-INF/services/io.smallrye.config.ConfigSourceFactory}.
 *
 * @since 1.1
 */
public class ForageAgentConfigSourceFactory extends ForageQuarkusConfigSourceAdapter<AgentConfig> {

    @Override
    protected ForageModuleDescriptor<AgentConfig, ?> descriptor() {
        return new AgentModuleDescriptor();
    }
}
