package io.kaoto.forage.agent.factory;

import io.kaoto.forage.core.util.config.ConfigEntries;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigTag;

/**
 * Configuration entries definition for multi-agent orchestration configurations.
 *
 * <p>This class defines all configuration modules and entries used by {@link MultiAgentConfig}
 * to manage multi-agent scenarios and agent ID extraction strategies. It provides the
 * configuration infrastructure for sophisticated multi-agent routing and coordination.
 *
 * <p>These configuration entries enable flexible agent routing strategies, supporting extraction
 * of agent identifiers from various exchange sources (route ID, headers, properties, variables).
 * Each configuration entry supports both default and named/prefixed configurations for
 * multi-instance scenarios.
 */
public final class MultiAgentConfigEntries extends ConfigEntries {
    public static final ConfigModule MULTI_AGENT_NAMES = ConfigModule.of(
            MultiAgentConfig.class,
            "forage.multi.agent.names",
            "Comma-separated list of named agent prefixes for multi-agent setup",
            "Agent Names",
            null,
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.COMMON);
    public static final ConfigModule MULTI_AGENT_ID_SOURCE = ConfigModule.of(
            MultiAgentConfig.class,
            "forage.multi.agent.id.source",
            "Source for extracting agent ID (route-id, header, property, variable)",
            "ID Source",
            null,
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.COMMON);
    public static final ConfigModule MULTI_AGENT_ID_SOURCE_HEADER = ConfigModule.of(
            MultiAgentConfig.class,
            "forage.multi.agent.id.source.header",
            "Exchange header name to extract agent ID from",
            "ID Source Header",
            null,
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule MULTI_AGENT_ID_SOURCE_PROPERTY = ConfigModule.of(
            MultiAgentConfig.class,
            "forage.multi.agent.id.source.property",
            "Exchange property name to extract agent ID from",
            "ID Source Property",
            null,
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule MULTI_AGENT_ID_SOURCE_VARIABLE = ConfigModule.of(
            MultiAgentConfig.class,
            "forage.multi.agent.id.source.variable",
            "Exchange variable name to extract agent ID from",
            "ID Source Variable",
            null,
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.ADVANCED);

    static {
        initModules(
                MultiAgentConfigEntries.class,
                MULTI_AGENT_NAMES,
                MULTI_AGENT_ID_SOURCE,
                MULTI_AGENT_ID_SOURCE_HEADER,
                MULTI_AGENT_ID_SOURCE_PROPERTY,
                MULTI_AGENT_ID_SOURCE_VARIABLE);
    }
}
