package io.kaoto.forage.core.util.config;

import java.util.concurrent.atomic.AtomicInteger;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.PropertiesFunction;

import static io.kaoto.forage.core.util.config.PlaceholderTestConfig.PlaceholderTestConfigEntries.PASSWORD;
import static io.kaoto.forage.core.util.config.PlaceholderTestConfig.PlaceholderTestConfigEntries.PROMPT;
import static io.kaoto.forage.core.util.config.TestSecretPropertiesFunction.SECRET;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CamelPlaceholderFunctionResolverTest {

    private static final String PASSWORD_KEY = PASSWORD.propertyName();
    private static final String PROMPT_KEY = PROMPT.propertyName();

    private CamelPlaceholderFunctionResolver resolver;

    @AfterEach
    void reset() throws Exception {
        System.clearProperty(PASSWORD_KEY);
        System.clearProperty(PROMPT_KEY);
        ConfigStore.getInstance().reload();
        if (resolver != null) {
            PlaceholderResolver.uninstall(resolver);
            resolver.getCamelContext().close();
        }
    }

    private PlaceholderTestConfig configWith(CamelContext camelContext, String password) {
        System.setProperty(PASSWORD_KEY, password);
        resolver = new CamelPlaceholderFunctionResolver(camelContext);
        PlaceholderResolver.install(resolver);
        return new PlaceholderTestConfig();
    }

    @Test
    void configValueIsResolvedThroughCamelPlaceholderFunction() {
        PlaceholderTestConfig config = configWith(new DefaultCamelContext(), "{{forage-test:db-password}}");

        assertThat(config.password()).contains(SECRET);
    }

    @Test
    void functionsResolveBeforeTheCamelContextIsBuilt() {
        // Plain Camel creates Forage beans from a ContextServicePlugin, during CamelContext#build(),
        // before Camel Main initializes the PropertiesComponent and applies camel.vault.* properties
        CamelContext camelContext = new DefaultCamelContext(false);
        camelContext.getPropertiesComponent().addLocation("classpath:placeholder-vault-test.properties");

        PlaceholderTestConfig config = configWith(camelContext, "{{forage-test:region}}/{{forage-test:db-password}}");

        assertThat(config.password()).contains("eu-south-1/" + SECRET);
        assertThat(camelContext.getVaultConfiguration().aws().getRegion()).isEqualTo("eu-south-1");
    }

    @Test
    void functionRegisteredOnThePropertiesComponentIsUsed() {
        CamelContext camelContext = new DefaultCamelContext();
        camelContext.getPropertiesComponent().addPropertiesFunction(new UpperCaseFunction(new AtomicInteger()));

        PlaceholderTestConfig config = configWith(camelContext, "{{upper:value}}");

        assertThat(config.password()).contains("VALUE");
    }

    @Test
    void valueIsResolvedOncePerConfigInstance() {
        AtomicInteger calls = new AtomicInteger();
        CamelContext camelContext = new DefaultCamelContext();
        camelContext.getPropertiesComponent().addPropertiesFunction(new UpperCaseFunction(calls));
        PlaceholderTestConfig config = configWith(camelContext, "{{upper:value}}");

        config.password();
        config.password();

        assertThat(calls).hasValue(1);
    }

    @Test
    void placeholdersWithoutFunctionAreLeftUnchanged() {
        String prompt = "You are {{role}}. Answer {{it}}, password {{db.password:changeme}}";
        System.setProperty(PROMPT_KEY, prompt);

        PlaceholderTestConfig config = configWith(new DefaultCamelContext(), "{{forage-test:db-password}}");

        assertThat(config.prompt()).contains(prompt);
        assertThat(config.password()).contains(SECRET);
    }

    @Test
    void functionReturningNoValueFailsWithThePropertyName() {
        PlaceholderTestConfig config = configWith(new DefaultCamelContext(), "literal-part{{forage-test:missing}}");

        assertThatThrownBy(config::password)
                .isInstanceOf(UnresolvedPlaceholderException.class)
                .hasMessageContaining(PASSWORD_KEY)
                .hasMessageContaining("{{forage-test:...}}")
                .hasMessageNotContaining("literal-part")
                .hasMessageNotContaining("missing")
                .satisfies(e -> assertThat(((UnresolvedPlaceholderException) e).getPropertyName())
                        .isEqualTo(PASSWORD_KEY));
    }

    @Test
    void failingFunctionFailsWithItsCause() {
        PlaceholderTestConfig config = configWith(new DefaultCamelContext(), "{{forage-test:boom}}");

        assertThatThrownBy(config::password)
                .isInstanceOf(UnresolvedPlaceholderException.class)
                .hasMessageContaining(PASSWORD_KEY)
                .hasMessageNotContaining("secret store unavailable")
                .cause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("secret store unavailable");
    }

    @Test
    void unavailableSecretStoreFunctionFailsInsteadOfBecomingADefaultValue() {
        // Without camel-aws-secrets-manager, Camel would read {{aws:prod-db#password}} as the key "aws"
        // with the default value "prod-db#password"
        PlaceholderTestConfig config = configWith(new DefaultCamelContext(), "{{aws:prod-db#password}}");

        assertThatThrownBy(config::password)
                .isInstanceOf(UnresolvedPlaceholderException.class)
                .hasMessageContaining(PASSWORD_KEY)
                .hasMessageContaining("camel-aws-secrets-manager")
                .hasMessageNotContaining("prod-db");
    }

    @Test
    void unsetEnvironmentVariableIsLeftUnchanged() {
        String value = "{{env:FORAGE_TEST_NONEXISTENT_12345}}";

        PlaceholderTestConfig config = configWith(new DefaultCamelContext(), value);

        assertThat(config.password()).contains(value);
    }

    @Test
    void ignoreMissingPropertyLeavesUnresolvablePlaceholdersUnchanged() {
        // Camel JBang sets this initial property when exporting
        CamelContext camelContext = new DefaultCamelContext(false);
        camelContext
                .getPropertiesComponent()
                .addInitialProperty("camel.component.properties.ignore-missing-property", "true");
        String value = "{{forage-test:missing}}{{aws:prod}}{{env:FORAGE_TEST_NONEXISTENT_12345}}";

        PlaceholderTestConfig config = configWith(camelContext, value);

        assertThat(config.password()).contains(value);
    }

    @Test
    void resolvedSecretsAreNeverLogged() {
        PlaceholderTestConfig config;
        try (LogCapture logs = LogCapture.start()) {
            config = configWith(new DefaultCamelContext(false), "{{forage-test:db-password}}");
            assertThat(config.password()).contains(SECRET);

            System.setProperty(PROMPT_KEY, SECRET + "{{forage-test:missing}}");
            PlaceholderTestConfig failing = new PlaceholderTestConfig();
            assertThatThrownBy(failing::prompt).isInstanceOf(UnresolvedPlaceholderException.class);

            assertThat(logs.lines()).isNotEmpty().noneMatch(line -> line.contains(SECRET));
        }
    }

    /** Counts its invocations. */
    private record UpperCaseFunction(AtomicInteger calls) implements PropertiesFunction {

        @Override
        public String getName() {
            return "upper";
        }

        @Override
        public String apply(String remainder) {
            calls.incrementAndGet();
            return remainder.toUpperCase();
        }
    }
}
