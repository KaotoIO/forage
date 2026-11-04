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
            "forage.route.policy.enabled",
            "Enable or disable the route policy factory",
            "Enabled",
            "true",
            "boolean",
            false,
            ConfigTag.COMMON);

    /**
     * Catalog entry describing the per-route policy selection, {@code forage.route.policy.<routeId>.name}.
     *
     * <p>The route ID is part of the property name, so this module only documents the property in the
     * Forage catalog, where tooling resolves the {@code {routeId}} placeholder. At runtime the policy names
     * are read through {@link #policyNameModule(String)}.
     */
    public static final ConfigModule POLICY_NAME = ConfigModule.ofBeanName(
            RoutePolicyFactoryConfig.class,
            "forage.route.policy.{routeId}.name",
            "Route policy to apply to the route, for example flip or schedule. When several policies are listed, comma-separated, the last one is applied",
            "Route Policy",
            false,
            ConfigTag.COMMON,
            "Route Policy");

    static {
        // POLICY_NAME is a catalog template: the runtime reads per-route names through policyNameModule()
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
