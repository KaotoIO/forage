package io.kaoto.forage.core;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.core.util.config.PlaceholderTestConfig;
import io.kaoto.forage.core.util.config.UnresolvedPlaceholderException;

import static io.kaoto.forage.core.util.config.TestSecretPropertiesFunction.SECRET;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plain Camel with the Camel Main lifecycle (as used by Camel JBang): Forage creates its beans while the
 * CamelContext is built, before Camel Main applies the camel.vault.* properties.
 */
class CamelMainPlaceholderTest {

    private static final String PASSWORD_KEY =
            PlaceholderTestConfig.PlaceholderTestConfigEntries.PASSWORD.propertyName();

    private Main main;

    @AfterEach
    void reset() {
        if (main != null) {
            main.stop();
        }
        System.clearProperty(PASSWORD_KEY);
        ConfigStore.getInstance().reload();
    }

    private Main newMain() {
        main = new Main();
        main.setPropertyPlaceholderLocations("classpath:placeholder-main-test.properties");
        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:region").setBody().constant("{{forage-test:region}}");
            }
        });
        return main;
    }

    @Test
    void forageBeanAndRoutesResolveTheSameVaultBackedFunction() {
        System.setProperty(
                PASSWORD_KEY,
                "{{forage-test:region}}:{{forage-test:default-credentials-provider}}:{{forage-test:db-password}}");

        newMain().start();

        assertThat(main.getCamelContext().getRegistry().lookupByName(PlaceholderTestBeanFactory.BEAN_NAME))
                .isEqualTo("eu-south-1:true:" + SECRET);
        // The function Forage started early is configured as Camel Main configures it for routes
        assertThat(main.getCamelContext().createProducerTemplate().requestBody("direct:region", "", String.class))
                .isEqualTo("eu-south-1");
    }

    @Test
    void unresolvablePlaceholderFailsTheStartup() {
        System.setProperty(PASSWORD_KEY, "{{forage-test:missing}}");

        Main failing = newMain();
        assertThatThrownBy(failing::start).satisfies(e -> assertThat(e)
                .hasStackTraceContaining(UnresolvedPlaceholderException.class.getName())
                .hasStackTraceContaining(PASSWORD_KEY));
    }
}
