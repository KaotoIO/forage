package io.kaoto.forage.policy.schedule;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.core.util.config.AbstractConfig;
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
        return ConfigStore.getInstance()
                .get(ScheduleRoutePolicyConfigEntries.startTime(super.prefix()))
                .map(this::parseTime)
                .orElse(null);
    }

    /**
     * Returns the stop time for the schedule.
     *
     * @return the stop time, or null if not configured
     */
    public LocalTime stopTime() {
        return ConfigStore.getInstance()
                .get(ScheduleRoutePolicyConfigEntries.stopTime(super.prefix()))
                .map(this::parseTime)
                .orElse(null);
    }

    /**
     * Returns the timezone for the schedule.
     *
     * @return the timezone, or system default if not configured
     */
    public ZoneId timezone() {
        return ConfigStore.getInstance()
                .get(ScheduleRoutePolicyConfigEntries.timezone(super.prefix()))
                .map(this::parseZoneId)
                .orElse(ZoneId.systemDefault());
    }

    /**
     * Returns the cron expression for the schedule.
     *
     * @return the cron expression, or null if not configured
     */
    public String cronExpression() {
        return ConfigStore.getInstance()
                .get(ScheduleRoutePolicyConfigEntries.cron(super.prefix()))
                .orElse(null);
    }

    /**
     * Returns the days of week for the schedule.
     *
     * @return the days of week, or all days if not configured
     */
    public Set<DayOfWeek> daysOfWeek() {
        return ConfigStore.getInstance()
                .get(ScheduleRoutePolicyConfigEntries.daysOfWeek(super.prefix()))
                .map(this::parseDaysOfWeek)
                .orElse(EnumSet.allOf(DayOfWeek.class));
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
            try {
                days.add(DayOfWeek.valueOf(day.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                LOG.warn("Invalid day of week '{}'. Ignoring.", day.trim());
            }
        }
        return days.isEmpty() ? EnumSet.allOf(DayOfWeek.class) : days;
    }
}
