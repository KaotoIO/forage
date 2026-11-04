package io.kaoto.forage.policy.schedule;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.spi.RouteController;
import org.apache.camel.support.RoutePolicySupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A route policy that activates/deactivates routes based on a time schedule.
 *
 * <p>This policy supports:
 * <ul>
 *   <li>Fixed time windows (start-time to stop-time)</li>
 *   <li>Timezone-aware scheduling</li>
 *   <li>Day-of-week filtering</li>
 *   <li>Cron expressions (future enhancement)</li>
 * </ul>
 *
 * <p><strong>Behavior:</strong>
 * <ul>
 *   <li>Route starts when current time enters the schedule window</li>
 *   <li>Route stops when current time exits the schedule window</li>
 *   <li>Schedule is checked periodically</li>
 * </ul>
 *
 * @since 1.0
 */
public class ForageScheduleRoutePolicy extends RoutePolicySupport {
    private static final Logger LOG = LoggerFactory.getLogger(ForageScheduleRoutePolicy.class);

    private static final long CHECK_INTERVAL_SECONDS = 60;

    private final LocalTime startTime;
    private final LocalTime stopTime;
    private final ZoneId timezone;
    private final Set<DayOfWeek> daysOfWeek;
    private final String cronExpression;

    private ScheduledFuture<?> scheduledTask;
    private Route route;

    /**
     * Creates a new ForageScheduleRoutePolicy with the given configuration.
     *
     * @param config the schedule configuration
     */
    public ForageScheduleRoutePolicy(ScheduleRoutePolicyConfig config) {
        this.startTime = config.startTime();
        this.stopTime = config.stopTime();
        this.timezone = config.timezone();
        this.daysOfWeek = config.daysOfWeek();
        this.cronExpression = config.cronExpression();
    }

    /**
     * Creates a new ForageScheduleRoutePolicy with explicit parameters.
     *
     * @param startTime the daily start time
     * @param stopTime the daily stop time
     * @param timezone the timezone for scheduling
     * @param daysOfWeek the active days of week
     */
    public ForageScheduleRoutePolicy(
            LocalTime startTime, LocalTime stopTime, ZoneId timezone, Set<DayOfWeek> daysOfWeek) {
        this.startTime = startTime;
        this.stopTime = stopTime;
        this.timezone = timezone;
        this.daysOfWeek = daysOfWeek;
        this.cronExpression = null;
    }

    // Package-private accessors to the resolved schedule, for tests

    LocalTime startTime() {
        return startTime;
    }

    LocalTime stopTime() {
        return stopTime;
    }

    ZoneId timezone() {
        return timezone;
    }

    Set<DayOfWeek> daysOfWeek() {
        return daysOfWeek;
    }

    String cronExpression() {
        return cronExpression;
    }

    @Override
    public void onInit(Route route) {
        LOG.info("Initializing schedule route policy for route {}", route.getId());
        super.onInit(route);
        this.route = route;

        LOG.info(
                "Initializing schedule policy for route '{}': startTime={}, stopTime={}, timezone={}, daysOfWeek={}",
                route.getId(),
                startTime,
                stopTime,
                timezone,
                daysOfWeek);
    }

    @Override
    public void onStart(Route route) {
        LOG.info("Starting schedule route policy for route {}", route.getId());
        super.onStart(route);

        // Schedule periodic checks
        ScheduledExecutorService executor = route.getCamelContext()
                .getExecutorServiceManager()
                .newScheduledThreadPool(this, "ScheduleRoutePolicy-" + route.getId(), 1);

        scheduledTask = executor.scheduleAtFixedRate(
                this::checkSchedule, CHECK_INTERVAL_SECONDS, CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);

        // Check immediately
        checkSchedule();
    }

    @Override
    public void onStop(Route route) {
        super.onStop(route);

        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            scheduledTask = null;
        }

        LOG.info("Stopped schedule policy for route '{}'", route.getId());
    }

    @Override
    public void onExchangeBegin(Route route, Exchange exchange) {
        // No action needed
    }

    @Override
    public void onExchangeDone(Route route, Exchange exchange) {
        // No action needed
    }

    private void checkSchedule() {
        if (route == null) {
            return;
        }

        try {
            boolean shouldBeActive = isWithinSchedule();
            boolean isActive = isRouteStarted();

            if (shouldBeActive && !isActive) {
                LOG.info("Route '{}' entering schedule window, starting route", route.getId());
                startRoute();
            } else if (!shouldBeActive && isActive) {
                LOG.info("Route '{}' exiting schedule window, stopping route", route.getId());
                stopRoute();
            }
        } catch (Exception e) {
            LOG.warn("Error checking schedule for route '{}': {}", route.getId(), e.getMessage());
            LOG.debug("Schedule check error details", e);
        }
    }

    private boolean isWithinSchedule() {
        if (cronExpression != null) {
            // TODO: Implement cron expression evaluation
            LOG.warn("Cron expressions are not yet supported. Route will always be active.");
            return true;
        }

        if (startTime == null || stopTime == null) {
            // No schedule configured, always active
            return true;
        }

        ZonedDateTime now = ZonedDateTime.now(timezone);
        LocalTime currentTime = now.toLocalTime();
        DayOfWeek currentDay = now.getDayOfWeek();

        // Check day of week
        if (!daysOfWeek.contains(currentDay)) {
            LOG.debug("Route '{}' not active on {}", route.getId(), currentDay);
            return false;
        }

        // Check time window
        boolean withinWindow;
        if (startTime.isBefore(stopTime)) {
            // Normal case: start < stop (e.g., 09:00 to 17:00)
            withinWindow = !currentTime.isBefore(startTime) && currentTime.isBefore(stopTime);
        } else {
            // Overnight case: start > stop (e.g., 22:00 to 06:00)
            withinWindow = !currentTime.isBefore(startTime) || currentTime.isBefore(stopTime);
        }

        LOG.debug(
                "Route '{}' schedule check: currentTime={}, startTime={}, stopTime={}, withinWindow={}",
                route.getId(),
                currentTime,
                startTime,
                stopTime,
                withinWindow);

        return withinWindow;
    }

    private boolean isRouteStarted() {
        try {
            RouteController controller = route.getCamelContext().getRouteController();
            return controller.getRouteStatus(route.getId()).isStarted();
        } catch (Exception e) {
            return false;
        }
    }

    private void startRoute() {
        try {
            RouteController controller = route.getCamelContext().getRouteController();
            controller.startRoute(route.getId());
            LOG.info("Started route '{}' according to schedule", route.getId());
        } catch (Exception e) {
            LOG.warn("Failed to start route '{}': {}", route.getId(), e.getMessage());
        }
    }

    private void stopRoute() {
        try {
            RouteController controller = route.getCamelContext().getRouteController();
            controller.stopRoute(route.getId());
            LOG.info("Stopped route '{}' according to schedule", route.getId());
        } catch (Exception e) {
            LOG.warn("Failed to stop route '{}': {}", route.getId(), e.getMessage());
        }
    }
}
