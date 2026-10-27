package io.kaoto.forage.agent.factory;

import io.kaoto.forage.core.util.config.ConfigEntries;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigTag;

/**
 * Configuration entries definition for individual agent factory configurations.
 *
 * <p>This class defines all configuration modules and entries used by {@link AgentFactoryConfig}
 * to manage individual agent settings. It provides the configuration infrastructure for
 * single-agent scenarios and individual agent instances within multi-agent setups.
 *
 * <p>Supports both default configurations and named/prefixed configurations for multi-instance
 * scenarios through the standard Camel Forage configuration pattern.
 */
public final class AgentFactoryConfigEntries extends ConfigEntries {
    public static final String FEATURE_MEMORY = "memory";
    public static final ConfigModule PROVIDER_MODEL_FACTORY_CLASS = ConfigModule.of(
            AgentFactoryConfig.class,
            "forage.provider.model.factory.class",
            "Fully qualified class name of the model provider factory",
            "Model Factory Class",
            null,
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule PROVIDER_FEATURES = ConfigModule.of(
            AgentFactoryConfig.class,
            "forage.provider.features",
            "Comma-separated list of agent features to enable (e.g., memory)",
            "Features",
            null,
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.COMMON);
    public static final ConfigModule PROVIDER_FEATURES_MEMORY_FACTORY_CLASS = ConfigModule.of(
            AgentFactoryConfig.class,
            "forage.provider.features.memory.factory.class",
            "Fully qualified class name of the chat memory factory",
            "Memory Factory Class",
            null,
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule PROVIDER_AGENT_CLASS = ConfigModule.of(
            AgentFactoryConfig.class,
            "forage.provider.agent.class",
            "Fully qualified class name of the agent factory implementation",
            "Agent Class",
            null,
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule GUARDRAILS_INPUT = ConfigModule.of(
            AgentFactoryConfig.class,
            "forage.guardrails.input",
            "Comma-separated list of input guardrail names (@ForageBean values, e.g., pii-detector,keyword-filter)",
            "Input Guardrails",
            null,
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.COMMON);
    public static final ConfigModule GUARDRAILS_OUTPUT = ConfigModule.of(
            AgentFactoryConfig.class,
            "forage.guardrails.output",
            "Comma-separated list of output guardrail names (@ForageBean values, e.g., sensitive-data,output-length)",
            "Output Guardrails",
            null,
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.COMMON);

    static {
        initModules(
                AgentFactoryConfigEntries.class,
                PROVIDER_MODEL_FACTORY_CLASS,
                PROVIDER_FEATURES,
                PROVIDER_FEATURES_MEMORY_FACTORY_CLASS,
                PROVIDER_AGENT_CLASS,
                GUARDRAILS_INPUT,
                GUARDRAILS_OUTPUT);
    }
}
