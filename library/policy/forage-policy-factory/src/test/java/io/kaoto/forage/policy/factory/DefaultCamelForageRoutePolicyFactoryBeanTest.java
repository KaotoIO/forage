package io.kaoto.forage.policy.factory;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import io.kaoto.forage.core.ForageContextServicePlugin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DefaultCamelForageRoutePolicyFactoryBean.
 */
@DisplayName("DefaultCamelForageRoutePolicyFactoryBean Tests")
class DefaultCamelForageRoutePolicyFactoryBeanTest {

    private CamelContext camelContext;

    @AfterEach
    void tearDown() throws Exception {
        System.clearProperty("forage.route.policy.enabled");
        if (camelContext != null) {
            camelContext.close();
        }
    }

    /**
     * Creates a CamelContext with the given enabled property already set.
     * The property must be set BEFORE context creation because
     * {@link ForageContextServicePlugin} auto-discovers and configures
     * BeanFactory implementations (including the route policy factory)
     * during CamelContext initialization.
     */
    private CamelContext createContextWithEnabledProperty(String value) {
        System.setProperty("forage.route.policy.enabled", value);
        return new DefaultCamelContext();
    }

    @Nested
    @DisplayName("Configure Tests")
    class ConfigureTests {

        @Test
        @DisplayName("Should register factory when enabled (default)")
        void shouldRegisterFactoryWhenEnabledByDefault() {
            camelContext = createContextWithEnabledProperty("true");

            DefaultCamelForageRoutePolicyFactoryBean bean = new DefaultCamelForageRoutePolicyFactoryBean();
            bean.setCamelContext(camelContext);

            int factoriesBefore = camelContext.getRoutePolicyFactories().size();
            bean.configure();
            int factoriesAfter = camelContext.getRoutePolicyFactories().size();

            assertThat(factoriesAfter).isEqualTo(factoriesBefore + 1);
            assertThat(camelContext.getRoutePolicyFactories())
                    .anyMatch(f -> f instanceof DefaultCamelForageRoutePolicyFactory);
        }

        @Test
        @DisplayName("Should not register factory when disabled")
        void shouldNotRegisterFactoryWhenDisabled() {
            camelContext = createContextWithEnabledProperty("false");

            DefaultCamelForageRoutePolicyFactoryBean bean = new DefaultCamelForageRoutePolicyFactoryBean();
            bean.setCamelContext(camelContext);

            int factoriesBefore = camelContext.getRoutePolicyFactories().size();
            bean.configure();
            int factoriesAfter = camelContext.getRoutePolicyFactories().size();

            assertThat(factoriesAfter).isEqualTo(factoriesBefore);
            assertThat(camelContext.getRoutePolicyFactories())
                    .noneMatch(f -> f instanceof DefaultCamelForageRoutePolicyFactory);
        }

        @Test
        @DisplayName("Should register factory when explicitly enabled")
        void shouldRegisterFactoryWhenExplicitlyEnabled() {
            camelContext = createContextWithEnabledProperty("true");

            DefaultCamelForageRoutePolicyFactoryBean bean = new DefaultCamelForageRoutePolicyFactoryBean();
            bean.setCamelContext(camelContext);

            int factoriesBefore = camelContext.getRoutePolicyFactories().size();
            bean.configure();
            int factoriesAfter = camelContext.getRoutePolicyFactories().size();

            assertThat(factoriesAfter).isEqualTo(factoriesBefore + 1);
        }
    }

    @Nested
    @DisplayName("CamelContext Tests")
    class CamelContextTests {

        @Test
        @DisplayName("Should store and return CamelContext")
        void shouldStoreAndReturnCamelContext() {
            camelContext = new DefaultCamelContext();
            DefaultCamelForageRoutePolicyFactoryBean bean = new DefaultCamelForageRoutePolicyFactoryBean();

            bean.setCamelContext(camelContext);

            assertThat(bean.getCamelContext()).isSameAs(camelContext);
        }

        @Test
        @DisplayName("Should return null before CamelContext is set")
        void shouldReturnNullBeforeCamelContextIsSet() {
            DefaultCamelForageRoutePolicyFactoryBean bean = new DefaultCamelForageRoutePolicyFactoryBean();

            assertThat(bean.getCamelContext()).isNull();
        }
    }
}
