package io.kaoto.forage.core.common;

import java.util.Map;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.core.util.config.PlaceholderTestConfig;
import io.kaoto.forage.core.util.config.UnresolvedPlaceholderException;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Quarkus: Forage translates its properties into Quarkus configuration consumed by Quarkus extensions,
 * where Camel placeholder functions cannot be resolved.
 */
class ForageQuarkusConfigSourceAdapterPlaceholderTest {

    private static final String PASSWORD_KEY =
            PlaceholderTestConfig.PlaceholderTestConfigEntries.PASSWORD.propertyName();
    private static final String QUARKUS_PASSWORD = "quarkus.datasource.password";

    @AfterEach
    void reset() {
        System.clearProperty(PASSWORD_KEY);
        ConfigStore.getInstance().reload();
    }

    @Test
    void secretStoreFunctionFailsTheTranslationWithThePropertyName() {
        System.setProperty(PASSWORD_KEY, "{{secret:orders-db/password}}");

        assertThatThrownBy(() -> new SmallRyeConfigBuilder()
                        .withSources(new TestConfigSourceAdapter())
                        .build())
                .hasStackTraceContaining(UnresolvedPlaceholderException.class.getName())
                .hasStackTraceContaining(PASSWORD_KEY)
                .hasStackTraceContaining("Quarkus")
                .hasStackTraceContaining("{{env:NAME}}");
    }

    @Test
    void environmentPlaceholdersAreStillTranslated() {
        // Translation also runs at build time, where runtime variables are not set
        String value = "{{env:FORAGE_TEST_NONEXISTENT_12345}}{{sys:user.name}}";
        System.setProperty(PASSWORD_KEY, value);

        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new TestConfigSourceAdapter())
                .build();

        assertThat(config.getConfigValue(QUARKUS_PASSWORD).getRawValue())
                .isEqualTo("{{env:FORAGE_TEST_NONEXISTENT_12345}}" + System.getProperty("user.name"));
    }

    static class TestConfigSourceAdapter extends ForageQuarkusConfigSourceAdapter<PlaceholderTestConfig> {
        @Override
        protected ForageModuleDescriptor<PlaceholderTestConfig, ?> descriptor() {
            return new TestDescriptor();
        }
    }

    static class TestDescriptor implements ForageModuleDescriptor<PlaceholderTestConfig, BeanProvider<Object>> {

        @Override
        public String modulePrefix() {
            return "placeholdertest";
        }

        @Override
        public PlaceholderTestConfig createConfig(String prefix) {
            return new PlaceholderTestConfig(prefix);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Class<BeanProvider<Object>> providerClass() {
            return (Class<BeanProvider<Object>>) (Class<?>) BeanProvider.class;
        }

        @Override
        public String resolveProviderClassName(PlaceholderTestConfig config) {
            return null;
        }

        @Override
        public String defaultBeanName() {
            return "placeholderTest";
        }

        @Override
        public Class<?> primaryBeanClass() {
            return String.class;
        }

        @Override
        public boolean transactionEnabled(PlaceholderTestConfig config) {
            return false;
        }

        @Override
        public Map<String, String> translateProperties(String prefix, PlaceholderTestConfig config) {
            return Map.of(QUARKUS_PASSWORD, config.password().orElse(""));
        }
    }
}
