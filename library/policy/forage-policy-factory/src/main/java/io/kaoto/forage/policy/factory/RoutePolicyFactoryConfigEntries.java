package io.kaoto.forage.policy.factory;

import io.kaoto.forage.core.util.config.ConfigEntries;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigTag;

/**
 * Configuration entries for the route policy factory.
 *
 * <p>This class defines the configuration modules for the factory itself,
 * primarily the policy name assignment for each route. The configuration
 * follows the pattern: {@code forage.route.policy.<routeId>.name}
 *
 * @since 1.0
 */
public final class RoutePolicyFactoryConfigEntries extends ConfigEntries {

    /**
     * The base configuration prefix for route policy configuration.
     */
    public static final String CONFIG_PREFIX = "forage.route.policy";

    /**
     * Configuration module for enabling/disabling the route policy factory.
     *
     * <p>When set to false, no route policies will be created by this factory.
     * Default is true (enabled).
     */
    public static final ConfigModule ENABLED = ConfigModule.of(
            RoutePolicyFactoryConfig.class,
            CONFIG_PREFIX + ".enabled",
            "Enable or disable the route policy factory",
            "Enabled",
            "false",
            "boolean",
            false,
            ConfigTag.COMMON);

    static {
        initModules(RoutePolicyFactoryConfigEntries.class, ENABLED);
    }

    /**
     * Creates a ConfigModule for the policy name of a specific route.
     *
     * @param routeId the route ID
     * @return a ConfigModule for the policy name configuration
     */
    public static ConfigModule policyNameModule(String routeId) {
        return ConfigModule.of(
                RoutePolicyFactoryConfig.class,
                CONFIG_PREFIX + "." + routeId + ".name",
                "Comma-separated list of policy names for route " + routeId,
                "Policy Names",
                null,
                "string",
                false,
                ConfigTag.COMMON);
    }
}
