package io.kaoto.forage.springboot.common;

import java.util.concurrent.atomic.AtomicReference;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.PropertiesFunction;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.kaoto.forage.core.util.config.PlaceholderResolutionDeferredException;
import io.kaoto.forage.core.util.config.PlaceholderResolver;
import io.kaoto.forage.core.util.config.UnresolvedPlaceholderException;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ForagePlaceholderFunctionsInitializerTest {

    private static final String KEY = "forage.test.jdbc.password";

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withInitializer(new ForagePlaceholderFunctionsInitializer());

    @Test
    void resolvesThroughTheCamelContextBean() {
        contextRunner.withUserConfiguration(CamelConfiguration.class).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean("forageValue")).isEqualTo("VALUE");
            // Forage bean factories look beans up while the CamelContext bean is created
            assertThat(CamelConfiguration.DURING_CREATION.get())
                    .isInstanceOf(PlaceholderResolutionDeferredException.class)
                    .hasMessageContaining(KEY);
        });
        assertThat(PlaceholderResolver.installedFunctionResolver()).isNull();
    }

    @Test
    void secretStoreFunctionFailsWithoutCamelContext() {
        contextRunner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThatThrownBy(() -> PlaceholderResolver.resolveFunctions(KEY, "{{secret:db/password}}"))
                    .isInstanceOf(UnresolvedPlaceholderException.class)
                    .hasMessageContaining(KEY)
                    .hasMessageContaining("no CamelContext bean");
            assertThat(PlaceholderResolver.resolveFunctions(KEY, "{{name:default}}"))
                    .isEqualTo("{{name:default}}");
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class CamelConfiguration {

        static final AtomicReference<Throwable> DURING_CREATION = new AtomicReference<>();

        @Bean
        CamelContext camelContext() {
            try {
                PlaceholderResolver.resolveFunctions(KEY, "{{upper:value}}");
            } catch (RuntimeException e) {
                DURING_CREATION.set(e);
            }
            CamelContext camelContext = new DefaultCamelContext();
            camelContext.getPropertiesComponent().addPropertiesFunction(new UpperCaseFunction());
            return camelContext;
        }

        @Bean
        String forageValue(CamelContext camelContext) {
            return PlaceholderResolver.resolveFunctions(KEY, "{{upper:value}}");
        }
    }

    static class UpperCaseFunction implements PropertiesFunction {

        @Override
        public String getName() {
            return "upper";
        }

        @Override
        public String apply(String remainder) {
            return remainder.toUpperCase();
        }
    }
}
