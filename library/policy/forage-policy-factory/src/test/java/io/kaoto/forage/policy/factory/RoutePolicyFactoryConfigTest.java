package io.kaoto.forage.policy.factory;

import java.util.Optional;
import io.kaoto.forage.core.util.config.ConfigStore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RoutePolicyFactoryConfig Tests")
class RoutePolicyFactoryConfigTest {

    @BeforeEach
    void setUp() {
        ConfigStore.getInstance().reload();
    }

    @AfterEach
    void tearDown() {
        ConfigStore.getInstance().reload();
    }

    @Test
    @DisplayName("Should store per-route policy names via register")
    void shouldStorePerRoutePolicyNamesViaRegister() {
        RoutePolicyFactoryConfig config = new RoutePolicyFactoryConfig();

        config.register("forage.route.policy.route-3104.name", "flip");

        Optional<String> policyNames = config.getPolicyNames("route-3104");

        assertThat(policyNames).isPresent();
        assertThat(policyNames.get()).isEqualTo("flip");
    }

    @Test
    @DisplayName("Should handle multiple routes via register")
    void shouldHandleMultipleRoutesViaRegister() {
        RoutePolicyFactoryConfig config = new RoutePolicyFactoryConfig();

        config.register("forage.route.policy.route-3104.name", "flip");
        config.register("forage.route.policy.route-2528.name", "schedule");

        assertThat(config.getPolicyNames("route-3104")).hasValue("flip");
        assertThat(config.getPolicyNames("route-2528")).hasValue("schedule");
    }

    @Test
    @DisplayName("Should handle comma-separated policy names via register")
    void shouldHandleCommaSeparatedPolicyNames() {
        RoutePolicyFactoryConfig config = new RoutePolicyFactoryConfig();

        config.register("forage.route.policy.myRoute.name", "flip,schedule");

        assertThat(config.getPolicyNames("myRoute")).hasValue("flip,schedule");
    }

    @Test
    @DisplayName("Should return empty for unconfigured route")
    void shouldReturnEmptyForUnconfiguredRoute() {
        RoutePolicyFactoryConfig config = new RoutePolicyFactoryConfig();

        Optional<String> policyNames = config.getPolicyNames("unknown-route");

        assertThat(policyNames).isEmpty();
    }

    @Test
    @DisplayName("Should not treat non-name properties as policy names")
    void shouldNotTreatNonNamePropertiesAsPolicyNames() {
        RoutePolicyFactoryConfig config = new RoutePolicyFactoryConfig();

        // Only register a flip config property, not a .name property
        config.register("forage.route.policy.route-5000.flip.initially-active", "true");

        // route-5000 should have no policy name since we only set flip config, not .name
        assertThat(config.getPolicyNames("route-5000")).isEmpty();
    }
}
