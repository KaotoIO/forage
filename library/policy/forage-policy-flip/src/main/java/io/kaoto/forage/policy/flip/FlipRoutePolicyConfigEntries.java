package io.kaoto.forage.policy.flip;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import io.kaoto.forage.core.util.config.ConfigEntries;
import io.kaoto.forage.core.util.config.ConfigEntry;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.core.util.config.ConfigTag;

/**
 * Configuration entries for the flip route policy.
 *
 * <p>Configuration follows the pattern:
 * {@code forage.route.policy.<routeId>.flip.<option>}
 *
 * @since 1.0
 */
public final class FlipRoutePolicyConfigEntries extends ConfigEntries {

    /**
     * The base configuration prefix for route policy configuration.
     */
    public static final String CONFIG_PREFIX = "forage.route.policy";

    /*
     * Catalog entries for the flip options. The route ID is part of the property name, so these modules only
     * document the properties in the Forage catalog, where tooling resolves the {routeId} placeholder. At
     * runtime the options are read through pairedRoute(String) and initiallyActive(String), which reuse
     * their metadata.
     */

    /** Catalog entry for {@code forage.route.policy.<routeId>.flip.paired-route}. */
    public static final ConfigModule PAIRED_ROUTE = ConfigModule.of(
            FlipRoutePolicyConfig.class,
            "forage.route.policy.{routeId}.flip.paired-route",
            "ID of the paired route to flip with",
            "Paired Route",
            null,
            "string",
            true,
            ConfigTag.COMMON);

    /** Catalog entry for {@code forage.route.policy.<routeId>.flip.initially-active}. */
    public static final ConfigModule INITIALLY_ACTIVE = ConfigModule.of(
            FlipRoutePolicyConfig.class,
            "forage.route.policy.{routeId}.flip.initially-active",
            "Whether this route should be active initially",
            "Initially Active",
            "true",
            "boolean",
            false,
            ConfigTag.COMMON);

    private static final Map<ConfigModule, ConfigEntry> CONFIG_MODULES = new ConcurrentHashMap<>();

    private FlipRoutePolicyConfigEntries() {}

    /**
     * Creates a ConfigModule for the paired-route configuration with the given config prefix.
     *
     * @param configPrefix the full config prefix (e.g., forage.route.policy.routeId.flip)
     * @return a ConfigModule for the paired-route configuration
     */
    public static ConfigModule pairedRoute(String configPrefix) {
        return withName(PAIRED_ROUTE, configPrefix + ".paired-route");
    }

    /**
     * Creates a ConfigModule for the initially-active configuration with the given config prefix.
     *
     * @param configPrefix the full config prefix (e.g., forage.route.policy.routeId.flip)
     * @return a ConfigModule for the initially-active configuration
     */
    public static ConfigModule initiallyActive(String configPrefix) {
        return withName(INITIALLY_ACTIVE, configPrefix + ".initially-active");
    }

    private static ConfigModule withName(ConfigModule catalogEntry, String name) {
        return ConfigModule.of(
                FlipRoutePolicyConfig.class,
                name,
                catalogEntry.description(),
                catalogEntry.label(),
                catalogEntry.defaultValue(),
                catalogEntry.type(),
                catalogEntry.required(),
                catalogEntry.configTag());
    }

    public static Map<ConfigModule, ConfigEntry> entries() {
        return Collections.unmodifiableMap(CONFIG_MODULES);
    }

    public static Optional<ConfigModule> find(String configPrefix, String name) {
        if (configPrefix == null) {
            return Optional.empty();
        }
        String fullName = configPrefix + "." + extractPropertyName(name);
        return CONFIG_MODULES.keySet().stream()
                .filter(m -> m.name().equals(fullName))
                .findFirst();
    }

    private static String extractPropertyName(String name) {
        // Extract the property name from various input formats
        if (name.contains(".flip.")) {
            return name.substring(name.lastIndexOf(".flip.") + 6);
        }
        return name;
    }

    public static void register(String configPrefix) {
        if (configPrefix != null) {
            CONFIG_MODULES.put(pairedRoute(configPrefix), ConfigEntry.fromModule());
            CONFIG_MODULES.put(initiallyActive(configPrefix), ConfigEntry.fromModule());
        }
    }

    public static void loadOverrides(String configPrefix) {
        if (configPrefix != null) {
            loadForModule(pairedRoute(configPrefix));
            loadForModule(initiallyActive(configPrefix));
        }
    }

    private static void loadForModule(ConfigModule module) {
        // Check environment variable
        String envName = module.envName();
        if (envName != null) {
            String envValue = System.getenv(envName);
            if (envValue != null) {
                ConfigStore.getInstance().set(module, envValue);
                return;
            }
        }

        // Check system property
        String propName = module.propertyName();
        if (propName != null) {
            String propValue = System.getProperty(propName);
            if (propValue != null) {
                ConfigStore.getInstance().set(module, propValue);
            }
        }
    }
}
