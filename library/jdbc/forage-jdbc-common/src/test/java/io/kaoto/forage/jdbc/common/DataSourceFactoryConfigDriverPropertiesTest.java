package io.kaoto.forage.jdbc.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import io.kaoto.forage.core.util.config.ConfigResolver;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.core.util.config.PropertyFileLocator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.Resources.SYSTEM_PROPERTIES;

@ResourceLock(SYSTEM_PROPERTIES)
class DataSourceFactoryConfigDriverPropertiesTest {

    private final List<String> systemProperties = new ArrayList<>();
    private final List<String> storedKeys = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        systemProperties.forEach(System::clearProperty);
        storedKeys.forEach(key -> ConfigStore.getInstance().setDirect(key, null));
        ConfigStore.getInstance().unregisterResolver(MapResolver.class);
    }

    @Test
    void readsNamedDriverPropertiesFromSystemProperties() {
        setSystemProperty("forage.dpNamed.jdbc.properties.sslConnection", "true");
        setSystemProperty("forage.dpNamed.jdbc.properties.oracle.jdbc.ReadTimeout", "5000");
        setSystemProperty("forage.dpOther.jdbc.properties.sslConnection", "false");

        assertThat(new DataSourceFactoryConfig("dpNamed").driverProperties())
                .containsExactly(Map.entry("oracle.jdbc.ReadTimeout", "5000"), Map.entry("sslConnection", "true"));
    }

    @Test
    void defaultDatasourceDoesNotSeeNamedDriverProperties() {
        setSystemProperty("forage.dpNamedOnly.jdbc.properties.sslConnection", "true");

        assertThat(new DataSourceFactoryConfig().driverProperties()).doesNotContainKey("sslConnection");
        assertThat(new DataSourceFactoryConfig("dpNamedOnly").driverProperties())
                .containsEntry("sslConnection", "true");
    }

    @Test
    void propertiesFileKeysAreKeptAndOverriddenByResolversAndSystemProperties() {
        DataSourceFactoryConfig config = new DataSourceFactoryConfig("dpFile");
        // What the properties-file loader does for each key in forage-datasource-factory.properties
        register(config, "forage.dpFile.jdbc.properties.fromFile", "file");
        register(config, "forage.dpFile.jdbc.properties.overriddenByResolver", "file");
        register(config, "forage.dpFile.jdbc.properties.overriddenBySystem", "file");
        ConfigStore.getInstance()
                .registerResolver(new MapResolver(Map.of(
                        "forage.dpFile.jdbc.properties.overriddenByResolver", "resolver",
                        "forage.dpFile.jdbc.properties.overriddenBySystem", "resolver",
                        "forage.dpFile.jdbc.properties.fromResolver", "resolver")));
        setSystemProperty("forage.dpFile.jdbc.properties.overriddenBySystem", "system");

        assertThat(config.driverProperties())
                .containsExactly(
                        Map.entry("fromFile", "file"),
                        Map.entry("fromResolver", "resolver"),
                        Map.entry("overriddenByResolver", "resolver"),
                        Map.entry("overriddenBySystem", "system"));
    }

    @Test
    void valuesResolvePlaceholders() {
        setSystemProperty("dp.test.truststore.password", "s3cret");
        DataSourceFactoryConfig config = new DataSourceFactoryConfig("dpPlaceholder");
        register(config, "forage.dpPlaceholder.jdbc.properties.fromFile", "{{sys:dp.test.truststore.password}}");
        setSystemProperty(
                "forage.dpPlaceholder.jdbc.properties.sslTrustStorePassword", "{{sys:dp.test.truststore.password}}");

        assertThat(config.driverProperties())
                .containsEntry("fromFile", "s3cret")
                .containsEntry("sslTrustStorePassword", "s3cret");
    }

    @Test
    void driverPropertyKeysAreNotTreatedAsConfigModules() {
        assertThat(DataSourceFactoryConfig.isDriverPropertyKey("forage.jdbc.properties.sslConnection"))
                .isTrue();
        assertThat(DataSourceFactoryConfig.isDriverPropertyKey("forage.ds1.jdbc.properties.oracle.jdbc.ReadTimeout"))
                .isTrue();
        assertThat(DataSourceFactoryConfig.isDriverPropertyKey("forage.jdbc.properties"))
                .isFalse();
        assertThat(DataSourceFactoryConfig.isDriverPropertyKey("forage.ds1.jdbc.url"))
                .isFalse();
    }

    @Test
    void emptyWhenNothingIsConfigured() {
        assertThat(new DataSourceFactoryConfig("dpNone").driverProperties()).isEmpty();
    }

    private void register(DataSourceFactoryConfig config, String key, String value) {
        config.register(key, value);
        storedKeys.add(key);
    }

    private void setSystemProperty(String key, String value) {
        System.setProperty(key, value);
        systemProperties.add(key);
    }

    /** Stands in for a runtime configuration source such as the Spring Environment. */
    private static final class MapResolver implements ConfigResolver {
        private final Map<String, String> values;

        MapResolver(Map<String, String> values) {
            this.values = values;
        }

        @Override
        public Optional<String> resolve(String propertyName) {
            return Optional.ofNullable(values.get(propertyName));
        }

        @Override
        public Set<String> discoverPrefixes(String regexp) {
            return values.keySet().stream()
                    .map(key -> PropertyFileLocator.pattern(regexp).matcher(key))
                    .filter(Matcher::matches)
                    .map(matcher -> matcher.group(1))
                    .collect(Collectors.toSet());
        }

        @Override
        public int priority() {
            return 10;
        }
    }
}
