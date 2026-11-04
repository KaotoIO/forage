package io.kaoto.forage.policy.schedule;

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
 * Configuration entries for the schedule route policy.
 *
 * <p>Configuration follows the pattern:
 * {@code forage.route.policy.<routeId>.schedule.<option>}
 *
 * @since 1.0
 */
public final class ScheduleRoutePolicyConfigEntries extends ConfigEntries {

    /**
     * The base configuration prefix for route policy configuration.
     */
    public static final String CONFIG_PREFIX = "forage.route.policy";

    /*
     * Catalog entries for the schedule options. The route ID is part of the property name, so these modules
     * only document the properties in the Forage catalog, where tooling resolves the {routeId} placeholder.
     * At runtime the options are read through the per-route factory methods below, which reuse their
     * metadata.
     */

    /** Catalog entry for {@code forage.route.policy.<routeId>.schedule.start-time}. */
    public static final ConfigModule START_TIME = ConfigModule.of(
            ScheduleRoutePolicyConfig.class,
            "forage.route.policy.{routeId}.schedule.start-time",
            "Daily start time in HH:mm format",
            "Start Time",
            null,
            "string",
            false,
            ConfigTag.COMMON);

    /** Catalog entry for {@code forage.route.policy.<routeId>.schedule.stop-time}. */
    public static final ConfigModule STOP_TIME = ConfigModule.of(
            ScheduleRoutePolicyConfig.class,
            "forage.route.policy.{routeId}.schedule.stop-time",
            "Daily stop time in HH:mm format",
            "Stop Time",
            null,
            "string",
            false,
            ConfigTag.COMMON);

    /** Catalog entry for {@code forage.route.policy.<routeId>.schedule.timezone}. */
    public static final ConfigModule TIMEZONE = ConfigModule.of(
            ScheduleRoutePolicyConfig.class,
            "forage.route.policy.{routeId}.schedule.timezone",
            "Timezone for scheduling (e.g., America/New_York)",
            "Timezone",
            null,
            "string",
            false,
            ConfigTag.COMMON);

    /** Catalog entry for {@code forage.route.policy.<routeId>.schedule.cron}. */
    public static final ConfigModule CRON = ConfigModule.of(
            ScheduleRoutePolicyConfig.class,
            "forage.route.policy.{routeId}.schedule.cron",
            "Cron expression for schedule (alternative to start/stop times)",
            "Cron Expression",
            null,
            "string",
            false,
            ConfigTag.COMMON);

    /** Catalog entry for {@code forage.route.policy.<routeId>.schedule.days-of-week}. */
    public static final ConfigModule DAYS_OF_WEEK = ConfigModule.of(
            ScheduleRoutePolicyConfig.class,
            "forage.route.policy.{routeId}.schedule.days-of-week",
            "Comma-separated days of week (MON,TUE,WED,THU,FRI,SAT,SUN)",
            "Days of Week",
            null,
            "string",
            false,
            ConfigTag.COMMON);

    private static final Map<ConfigModule, ConfigEntry> CONFIG_MODULES = new ConcurrentHashMap<>();

    private ScheduleRoutePolicyConfigEntries() {}

    /*
     * The per-route factory methods take the config prefix the route policy factory passes to
     * ScheduleRoutePolicyProvider.create(), forage.route.policy.<routeId>.schedule, like the flip policy.
     */

    /**
     * Creates a ConfigModule for the start-time configuration with the given config prefix.
     *
     * @param configPrefix the full config prefix (e.g., forage.route.policy.routeId.schedule)
     * @return a ConfigModule for the start-time configuration
     */
    public static ConfigModule startTime(String configPrefix) {
        return withName(START_TIME, configPrefix + ".start-time");
    }

    /**
     * Creates a ConfigModule for the stop-time configuration with the given config prefix.
     *
     * @param configPrefix the full config prefix (e.g., forage.route.policy.routeId.schedule)
     * @return a ConfigModule for the stop-time configuration
     */
    public static ConfigModule stopTime(String configPrefix) {
        return withName(STOP_TIME, configPrefix + ".stop-time");
    }

    /**
     * Creates a ConfigModule for the timezone configuration with the given config prefix.
     *
     * @param configPrefix the full config prefix (e.g., forage.route.policy.routeId.schedule)
     * @return a ConfigModule for the timezone configuration
     */
    public static ConfigModule timezone(String configPrefix) {
        return withName(TIMEZONE, configPrefix + ".timezone");
    }

    /**
     * Creates a ConfigModule for the cron configuration with the given config prefix.
     *
     * @param configPrefix the full config prefix (e.g., forage.route.policy.routeId.schedule)
     * @return a ConfigModule for the cron configuration
     */
    public static ConfigModule cron(String configPrefix) {
        return withName(CRON, configPrefix + ".cron");
    }

    /**
     * Creates a ConfigModule for the days-of-week configuration with the given config prefix.
     *
     * @param configPrefix the full config prefix (e.g., forage.route.policy.routeId.schedule)
     * @return a ConfigModule for the days-of-week configuration
     */
    public static ConfigModule daysOfWeek(String configPrefix) {
        return withName(DAYS_OF_WEEK, configPrefix + ".days-of-week");
    }

    private static ConfigModule withName(ConfigModule catalogEntry, String name) {
        return ConfigModule.of(
                ScheduleRoutePolicyConfig.class,
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
        if (name.contains(".schedule.")) {
            return name.substring(name.lastIndexOf(".schedule.") + 10);
        }
        return name;
    }

    public static void register(String configPrefix) {
        if (configPrefix != null) {
            CONFIG_MODULES.put(startTime(configPrefix), ConfigEntry.fromModule());
            CONFIG_MODULES.put(stopTime(configPrefix), ConfigEntry.fromModule());
            CONFIG_MODULES.put(timezone(configPrefix), ConfigEntry.fromModule());
            CONFIG_MODULES.put(cron(configPrefix), ConfigEntry.fromModule());
            CONFIG_MODULES.put(daysOfWeek(configPrefix), ConfigEntry.fromModule());
        }
    }

    public static void loadOverrides(String configPrefix) {
        if (configPrefix != null) {
            loadForModule(startTime(configPrefix));
            loadForModule(stopTime(configPrefix));
            loadForModule(timezone(configPrefix));
            loadForModule(cron(configPrefix));
            loadForModule(daysOfWeek(configPrefix));
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
