package io.kaoto.forage.core;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import io.kaoto.forage.core.util.config.CamelPlaceholderFunctionResolver;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.core.util.config.PlaceholderResolver;
import io.kaoto.forage.core.util.config.PlaceholderTestConfig;
import io.kaoto.forage.core.util.config.UnresolvedPlaceholderException;

import static io.kaoto.forage.core.util.config.TestSecretPropertiesFunction.SECRET;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plain Camel: {@link ForageContextServicePlugin} creates beans while the CamelContext is built, resolving
 * placeholder functions through that CamelContext (see {@link PlaceholderTestBeanFactory}).
 */
class ForageContextServicePluginPlaceholderTest {

    private static final String PASSWORD_KEY =
            PlaceholderTestConfig.PlaceholderTestConfigEntries.PASSWORD.propertyName();

    @AfterEach
    void reset() {
        System.clearProperty(PASSWORD_KEY);
        ConfigStore.getInstance().reload();
    }

    @Test
    void beanCreatedDuringBuildGetsTheResolvedSecret() throws Exception {
        System.setProperty(PASSWORD_KEY, "{{forage-test:db-password}}");

        try (CamelContext camelContext = new DefaultCamelContext(false)) {
            camelContext.build();

            assertThat(PlaceholderResolver.installedFunctionResolver())
                    .isInstanceOfSatisfying(
                            CamelPlaceholderFunctionResolver.class,
                            resolver -> assertThat(resolver.getCamelContext()).isSameAs(camelContext));
            assertThat(camelContext.getRegistry().lookupByName(PlaceholderTestBeanFactory.BEAN_NAME))
                    .isEqualTo(SECRET);

            camelContext.start();
            camelContext.stop();
        }
        assertThat(PlaceholderResolver.installedFunctionResolver()).isNull();
    }

    @Test
    void unresolvablePlaceholderFailsTheStartup() throws Exception {
        System.setProperty(PASSWORD_KEY, "{{forage-test:missing}}");

        try (CamelContext camelContext = new DefaultCamelContext(false)) {
            camelContext.build();
            assertThat(camelContext.getRegistry().lookupByName(PlaceholderTestBeanFactory.BEAN_NAME))
                    .isNull();

            assertThatThrownBy(camelContext::start)
                    .isInstanceOf(UnresolvedPlaceholderException.class)
                    .hasMessageContaining(PASSWORD_KEY)
                    .hasMessageNotContaining("missing");
        }
    }
}
