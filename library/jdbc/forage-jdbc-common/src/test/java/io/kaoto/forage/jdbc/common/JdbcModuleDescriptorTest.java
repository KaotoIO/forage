package io.kaoto.forage.jdbc.common;

import java.util.Map;
import io.kaoto.forage.core.util.config.ConfigStore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class JdbcModuleDescriptorTest {

    @BeforeEach
    void setUp() {
        System.setProperty("forage.jdbc.db.kind", "postgresql");
        System.setProperty("forage.jdbc.url", "jdbc:postgresql://localhost/test");
        System.setProperty("forage.jdbc.username", "user");
        System.setProperty("forage.jdbc.password", "pass");
        System.setProperty("forage.jdbc.pool.min.size", "2");
        System.setProperty("forage.jdbc.pool.max.size", "20");
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("forage.jdbc.db.kind");
        System.clearProperty("forage.jdbc.url");
        System.clearProperty("forage.jdbc.username");
        System.clearProperty("forage.jdbc.password");
        System.clearProperty("forage.jdbc.pool.min.size");
        System.clearProperty("forage.jdbc.pool.max.size");
        // Constructing DataSourceFactoryConfig caches the system-property values (including
        // forage.jdbc.transaction.enabled=true from unsetOptionalPropertiesAreNotTranslated)
        // in the singleton ConfigStore; clearing the system properties alone leaves those
        // stale values behind for later tests in the same JVM
        ConfigStore.getInstance().reload();
    }

    @Test
    void maxSizeComesFromMaxSizeNotMinSize() {
        DataSourceFactoryConfig config = new DataSourceFactoryConfig();
        Map<String, String> props = new JdbcModuleDescriptor().translateProperties(null, config);

        assertThat(props.get("quarkus.datasource.\"dataSource\".jdbc.min-size")).isEqualTo("2");
        assertThat(props.get("quarkus.datasource.\"dataSource\".jdbc.max-size")).isEqualTo("20");
    }

    @Test
    void unsetOptionalPropertiesAreNotTranslated() {
        System.setProperty("forage.jdbc.transaction.enabled", "true");
        try {
            DataSourceFactoryConfig config = new DataSourceFactoryConfig();
            Map<String, String> props = new JdbcModuleDescriptor().translateProperties(null, config);

            // Optional properties without a configured value must be absent from the
            // translated map, not present as null or the literal string "null"
            assertThat(props).doesNotContainKey("quarkus.transaction-manager.node-name");
            assertThat(props).doesNotContainKey("quarkus.transaction-manager.object-store.datasource");
            assertThat(props.values()).noneMatch(v -> v == null || v.isEmpty() || "null".equals(v));
        } finally {
            System.clearProperty("forage.jdbc.transaction.enabled");
        }
    }

    @Test
    void driverPropertiesBecomeQuotedAdditionalJdbcProperties() {
        System.setProperty("forage.jdbc.properties.sslConnection", "true");
        System.setProperty("forage.jdbc.properties.oracle.jdbc.ReadTimeout", "5000");
        try {
            DataSourceFactoryConfig config = new DataSourceFactoryConfig();
            Map<String, String> props = new JdbcModuleDescriptor().translateProperties(null, config);

            assertThat(props)
                    .containsEntry(
                            "quarkus.datasource.\"dataSource\".jdbc.additional-jdbc-properties.\"sslConnection\"",
                            "true")
                    .containsEntry(
                            "quarkus.datasource.\"dataSource\".jdbc.additional-jdbc-properties.\"oracle.jdbc.ReadTimeout\"",
                            "5000");
        } finally {
            System.clearProperty("forage.jdbc.properties.sslConnection");
            System.clearProperty("forage.jdbc.properties.oracle.jdbc.ReadTimeout");
        }
    }
}
