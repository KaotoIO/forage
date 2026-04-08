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

    private static final Map<ConfigModule, ConfigEntry> CONFIG_MODULES = new ConcurrentHashMap<>();

    private ScheduleRoutePolicyConfigEntries() {}

    /**
     * Creates a ConfigModule for the start-time configuration with the given routeId.
     *
     * @param routeId the route ID
     * @return a ConfigModule for the start-time configuration
     */
    public static ConfigModule startTime(String routeId) {
        String name = CONFIG_PREFIX + "." + routeId + ".schedule.start-time";
        return ConfigModule.of(
                ScheduleRoutePolicyConfig.class,
                name,
                "Daily start time in HH:mm format",
                "Start Time",
                null,
                "string",
                false,
                ConfigTag.COMMON);
    }

    /**
     * Creates a ConfigModule for the stop-time configuration with the given routeId.
     *
     * @param routeId the route ID
     * @return a ConfigModule for the stop-time configuration
     */
    public static ConfigModule stopTime(String routeId) {
        String name = CONFIG_PREFIX + "." + routeId + ".schedule.stop-time";
        return ConfigModule.of(
                ScheduleRoutePolicyConfig.class,
                name,
                "Daily stop time in HH:mm format",
                "Stop Time",
                null,
                "string",
                false,
                ConfigTag.COMMON);
    }

    /**
     * Creates a ConfigModule for the timezone configuration with the given routeId.
     *
     * @param routeId the route ID
     * @return a ConfigModule for the timezone configuration
     */
    public static ConfigModule timezone(String routeId) {
        String name = CONFIG_PREFIX + "." + routeId + ".schedule.timezone";
        return ConfigModule.of(
                ScheduleRoutePolicyConfig.class,
                name,
                "Timezone for scheduling (e.g., America/New_York)",
                "Timezone",
                null,
                "string",
                false,
                ConfigTag.COMMON);
    }

    /**
     * Creates a ConfigModule for the cron configuration with the given routeId.
     *
     * @param routeId the route ID
     * @return a ConfigModule for the cron configuration
     */
    public static ConfigModule cron(String routeId) {
        String name = CONFIG_PREFIX + "." + routeId + ".schedule.cron";
        return ConfigModule.of(
                ScheduleRoutePolicyConfig.class,
                name,
                "Cron expression for schedule (alternative to start/stop times)",
                "Cron Expression",
                null,
                "string",
                false,
                ConfigTag.COMMON);
    }

    /**
     * Creates a ConfigModule for the days-of-week configuration with the given routeId.
     *
     * @param routeId the route ID
     * @return a ConfigModule for the days-of-week configuration
     */
    public static ConfigModule daysOfWeek(String routeId) {
        String name = CONFIG_PREFIX + "." + routeId + ".schedule.days-of-week";
        return ConfigModule.of(
                ScheduleRoutePolicyConfig.class,
                name,
                "Comma-separated days of week (MON,TUE,WED,THU,FRI,SAT,SUN)",
                "Days of Week",
                null,
                "string",
                false,
                ConfigTag.COMMON);
    }

    public static Map<ConfigModule, ConfigEntry> entries() {
        return Collections.unmodifiableMap(CONFIG_MODULES);
    }

    public static Optional<ConfigModule> find(String routeId, String name) {
        if (routeId == null) {
            return Optional.empty();
        }
        String fullName = CONFIG_PREFIX + "." + routeId + ".schedule." + extractPropertyName(name);
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

    public static void register(String routeId) {
        if (routeId != null) {
            CONFIG_MODULES.put(startTime(routeId), ConfigEntry.fromModule());
            CONFIG_MODULES.put(stopTime(routeId), ConfigEntry.fromModule());
            CONFIG_MODULES.put(timezone(routeId), ConfigEntry.fromModule());
            CONFIG_MODULES.put(cron(routeId), ConfigEntry.fromModule());
            CONFIG_MODULES.put(daysOfWeek(routeId), ConfigEntry.fromModule());
        }
    }

    public static void loadOverrides(String routeId) {
        if (routeId != null) {
            loadForModule(startTime(routeId));
            loadForModule(stopTime(routeId));
            loadForModule(timezone(routeId));
            loadForModule(cron(routeId));
            loadForModule(daysOfWeek(routeId));
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
