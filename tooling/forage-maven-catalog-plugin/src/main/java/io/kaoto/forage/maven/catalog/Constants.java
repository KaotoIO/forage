package io.kaoto.forage.maven.catalog;

public final class Constants {

    private Constants() {
        // Utility class - prevent instantiation
    }

    /**
     * Fully qualified name of the ForageBean annotation.
     */
    public static final String FORAGE_BEAN_FQN = "io.kaoto.forage.core.annotations.ForageBean";

    /**
     * Fully qualified name of the ForageFactory annotation.
     */
    public static final String FORAGE_FACTORY_FQN = "io.kaoto.forage.core.annotations.ForageFactory";

    /**
     * Fully qualified name of the InternalConfig annotation, which marks a ConfigModule field as hidden.
     */
    public static final String INTERNAL_CONFIG_FQN = "io.kaoto.forage.core.annotations.InternalConfig";

    /**
     * Fully qualified name of the ConfigModule class.
     */
    public static final String CONFIG_MODULE_FQN = "io.kaoto.forage.core.util.config.ConfigModule";

    /**
     * Fully qualified name of the default Config interface.
     * Used to detect when a factory/bean uses the default (no-op) config.
     */
    public static final String DEFAULT_CONFIG_FQN = "io.kaoto.forage.core.util.config.Config";

    public static final String FORAGE_GROUP_ID = "io.kaoto.forage";
}
