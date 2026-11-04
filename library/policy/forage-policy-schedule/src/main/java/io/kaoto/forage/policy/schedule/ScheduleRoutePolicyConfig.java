package io.kaoto.forage.policy.schedule;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.core.util.config.AbstractConfig;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigStore;

/**
 * Configuration class for the schedule route policy.
 *
 * <p>Supports configuration of:
 * <ul>
 *   <li>start-time: Daily start time (HH:mm format)</li>
 *   <li>stop-time: Daily stop time (HH:mm format)</li>
 *   <li>timezone: Timezone for scheduling</li>
 *   <li>cron: Cron expression (alternative to start/stop times)</li>
 *   <li>days-of-week: Active days (MON,TUE,WED,THU,FRI,SAT,SUN)</li>
 * </ul>
 *
 * @since 1.0
 */
public class ScheduleRoutePolicyConfig extends AbstractConfig {
    private static final Logger LOG = LoggerFactory.getLogger(ScheduleRoutePolicyConfig.class);

    public ScheduleRoutePolicyConfig() {
        this(null);
    }

    /**
     * Creates the configuration of one route's schedule policy.
     *
     * @param prefix the config prefix the route policy factory passes to the provider,
     *        {@code forage.route.policy.<routeId>.schedule}
     */
    public ScheduleRoutePolicyConfig(String prefix) {
        super(prefix, ScheduleRoutePolicyConfigEntries.class);
    }

    @Override
    public String name() {
        return "forage-policy-schedule";
    }

    /**
     * Returns the start time for the schedule.
     *
     * @return the start time, or null if not configured
     */
    public LocalTime startTime() {
        return value(ScheduleRoutePolicyConfigEntries::startTime)
                .map(this::parseTime)
                .orElse(null);
    }

    /**
     * Returns the stop time for the schedule.
     *
     * @return the stop time, or null if not configured
     */
    public LocalTime stopTime() {
        return value(ScheduleRoutePolicyConfigEntries::stopTime)
                .map(this::parseTime)
                .orElse(null);
    }

    /**
     * Returns the timezone for the schedule.
     *
     * @return the timezone, or system default if not configured
     */
    public ZoneId timezone() {
        return value(ScheduleRoutePolicyConfigEntries::timezone)
                .map(this::parseZoneId)
                .orElse(ZoneId.systemDefault());
    }

    /**
     * Returns the cron expression for the schedule.
     *
     * @return the cron expression, or null if not configured
     */
    public String cronExpression() {
        return value(ScheduleRoutePolicyConfigEntries::cron).orElse(null);
    }

    /**
     * Returns the days of week for the schedule.
     *
     * @return the days of week, or all days if not configured
     */
    public Set<DayOfWeek> daysOfWeek() {
        return value(ScheduleRoutePolicyConfigEntries::daysOfWeek)
                .map(this::parseDaysOfWeek)
                .orElse(EnumSet.allOf(DayOfWeek.class));
    }

    /**
     * Reads an option of this route's schedule like the flip policy does: from environment variables,
     * system properties and the runtime configuration, falling back to the per-route properties that
     * the route policy factory registered from its properties file.
     */
    private Optional<String> value(Function<String, ConfigModule> option) {
        if (super.prefix() == null) {
            return Optional.empty();
        }
        ConfigModule module = option.apply(super.prefix());
        ConfigStore.getInstance().load(module);
        return ConfigStore.getInstance().get(module).or(() -> ConfigStore.getInstance()
                .getDirect(module.propertyName()));
    }

    private LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException e) {
            LOG.warn("Invalid time format '{}', expected HH:mm. Using default.", value);
            return null;
        }
    }

    private ZoneId parseZoneId(String value) {
        try {
            return ZoneId.of(value);
        } catch (Exception e) {
            LOG.warn("Invalid timezone '{}'. Using system default.", value);
            return ZoneId.systemDefault();
        }
    }

    private Set<DayOfWeek> parseDaysOfWeek(String value) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (String day : value.split(",")) {
            Optional<DayOfWeek> parsed = parseDayOfWeek(day.trim());
            if (parsed.isPresent()) {
                days.add(parsed.get());
            } else {
                LOG.warn("Invalid day of week '{}'. Ignoring.", day.trim());
            }
        }
        return days.isEmpty() ? EnumSet.allOf(DayOfWeek.class) : days;
    }

    /**
     * Accepts the documented three-letter abbreviations (MON, TUE, ...) and full names (MONDAY, ...),
     * in any case.
     */
    private static Optional<DayOfWeek> parseDayOfWeek(String day) {
        String name = day.toUpperCase(Locale.ROOT);
        return Arrays.stream(DayOfWeek.values())
                .filter(candidate -> candidate.name().equals(name)
                        || (name.length() == 3 && candidate.name().startsWith(name)))
                .findFirst();
    }
}
