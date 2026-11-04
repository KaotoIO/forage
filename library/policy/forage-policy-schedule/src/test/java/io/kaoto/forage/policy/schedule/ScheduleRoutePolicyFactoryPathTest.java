package io.kaoto.forage.policy.schedule;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.RoutePolicy;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.policy.factory.DefaultCamelForageRoutePolicyFactory;
import io.kaoto.forage.policy.factory.RoutePolicyFactoryConfig;
import io.kaoto.forage.policy.factory.RoutePolicyRegistry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Creates the schedule policy the way a Forage project does: the route policy factory reads
 * {@code forage.route.policy.<routeId>.name}, finds {@link ScheduleRoutePolicyProvider} through the
 * ServiceLoader and passes it the {@code forage.route.policy.<routeId>.schedule} config prefix.
 */
@DisplayName("Schedule policy through the route policy factory")
class ScheduleRoutePolicyFactoryPathTest {

    private static final String ROUTE_ID = "night-route";
    private static final String ROUTE_PREFIX = "forage.route.policy." + ROUTE_ID;
    private static final Map<String, String> PROPERTIES = Map.of(
            ROUTE_PREFIX + ".name", "schedule",
            ROUTE_PREFIX + ".schedule.start-time", "22:00",
            ROUTE_PREFIX + ".schedule.stop-time", "06:00",
            ROUTE_PREFIX + ".schedule.timezone", "Europe/Rome",
            ROUTE_PREFIX + ".schedule.days-of-week", "MON,FRI");

    private CamelContext camelContext;

    @BeforeEach
    void setUp() {
        ConfigStore.getInstance().reload();
        camelContext = new DefaultCamelContext();
    }

    @AfterEach
    void tearDown() throws Exception {
        PROPERTIES.keySet().forEach(System::clearProperty);
        ConfigStore.getInstance().reload();
        camelContext.close();
    }

    @Test
    @DisplayName("Should apply the per-route schedule from system properties")
    void shouldApplyPerRouteScheduleFromSystemProperties() {
        PROPERTIES.forEach(System::setProperty);

        RoutePolicy policy = new DefaultCamelForageRoutePolicyFactory().createRoutePolicy(camelContext, ROUTE_ID, null);

        assertConfiguredSchedule(policy);
    }

    @Test
    @DisplayName("Should apply the per-route schedule registered from the factory properties file")
    void shouldApplyPerRouteScheduleRegisteredByFactoryConfig() {
        // RoutePolicyFactoryConfig registers every forage.route.policy.* key it reads from
        // forage-policy-factory.properties, including the schedule options
        RoutePolicyFactoryConfig config = new RoutePolicyFactoryConfig();
        PROPERTIES.forEach(config::register);

        RoutePolicy policy = new DefaultCamelForageRoutePolicyFactory(new RoutePolicyRegistry(), config)
                .createRoutePolicy(camelContext, ROUTE_ID, null);

        assertConfiguredSchedule(policy);
    }

    @Test
    @DisplayName("Should accept full day names in any case")
    void shouldAcceptFullDayNames() {
        PROPERTIES.forEach(System::setProperty);
        System.setProperty(ROUTE_PREFIX + ".schedule.days-of-week", "monday, Friday");

        RoutePolicy policy = new DefaultCamelForageRoutePolicyFactory().createRoutePolicy(camelContext, ROUTE_ID, null);

        assertConfiguredSchedule(policy);
    }

    @Test
    @DisplayName("Should not read the schedule of another route")
    void shouldNotReadScheduleOfAnotherRoute() {
        PROPERTIES.forEach(System::setProperty);
        System.setProperty("forage.route.policy.day-route.name", "schedule");
        try {
            RoutePolicy policy =
                    new DefaultCamelForageRoutePolicyFactory().createRoutePolicy(camelContext, "day-route", null);

            assertThat(policy).isInstanceOfSatisfying(ForageScheduleRoutePolicy.class, schedule -> {
                assertThat(schedule.startTime()).isNull();
                assertThat(schedule.stopTime()).isNull();
            });
        } finally {
            System.clearProperty("forage.route.policy.day-route.name");
        }
    }

    private static void assertConfiguredSchedule(RoutePolicy policy) {
        assertThat(policy).isInstanceOfSatisfying(ForageScheduleRoutePolicy.class, schedule -> {
            assertThat(schedule.startTime()).isEqualTo(LocalTime.of(22, 0));
            assertThat(schedule.stopTime()).isEqualTo(LocalTime.of(6, 0));
            assertThat(schedule.timezone()).isEqualTo(ZoneId.of("Europe/Rome"));
            assertThat(schedule.daysOfWeek()).containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
            assertThat(schedule.cronExpression()).isNull();
        });
    }
}
