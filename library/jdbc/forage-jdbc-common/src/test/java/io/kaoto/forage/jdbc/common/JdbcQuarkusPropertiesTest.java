package io.kaoto.forage.jdbc.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.kaoto.forage.core.util.config.ConfigEntries;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigStore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link JdbcModuleDescriptor#translateProperties} built from {@link JdbcModuleDescriptor#quarkusProperties()} gives
 * the same map as before (a verbatim copy of the previous code), for every catalog key set.
 */
class JdbcQuarkusPropertiesTest {

    private final List<String> set = new ArrayList<>();

    @AfterEach
    void tearDown() {
        set.forEach(System::clearProperty);
        ConfigStore.getInstance().reload();
    }

    @Test
    void sameMapAsBeforeWithEveryKeySet() {
        for (String name : new String[] {null, "ds1"}) {
            setAll(name);
            String ds = "quarkus.datasource.\"" + (name == null ? "dataSource" : name) + "\".jdbc.";
            assertThat(assertSame(name))
                    .containsKeys(
                            ds + "transaction-isolation-level",
                            ds + "additional-jdbc-properties.\"oracle.jdbc.ReadTimeout\"");
            tearDown();
            set.clear();
        }
    }

    @Test
    void sameMapAsBeforeWithRequiredKeysOnly() {
        for (String name : new String[] {null, "ds1"}) {
            String p = name == null ? "forage.jdbc." : "forage." + name + ".jdbc.";
            set(p + "db.kind", "mysql");
            set(p + "url", "jdbc:mysql://h/db");
            set(p + "username", "u");
            set(p + "password", "p");
            assertSame(name);
        }
    }

    @Test
    void everyListedKeyIsAConfigEntry() {
        List<String> names = modules().stream().map(ConfigModule::name).toList();
        assertThat(new JdbcModuleDescriptor().quarkusProperties()).allSatisfy(p -> assertThat(names)
                .contains("forage.jdbc." + p.forageKey().replaceAll("\\.$", "")));
    }

    private Map<String, String> assertSame(String name) {
        DataSourceFactoryConfig config =
                name == null ? new DataSourceFactoryConfig() : new DataSourceFactoryConfig(name);
        Map<String, String> now = new JdbcModuleDescriptor().translateProperties(name, config);
        assertThat(now).isEqualTo(legacy(name, config));
        assertThat(now).isNotEmpty();
        return now;
    }

    /** Every jdbc catalog key, with a value its type accepts. */
    private void setAll(String name) {
        for (ConfigModule module : modules()) {
            String key = module.name().substring("forage.jdbc.".length());
            String value =
                    switch (module.type() == null ? "string" : module.type()) {
                        case "integer", "long" -> "7";
                        case "boolean" -> "true";
                        default -> "v-" + key;
                    };
            if (key.equals("db.kind")) {
                value = "postgresql";
            } else if (key.equals("transaction.object.store.type")) {
                value = "file-system";
            } else if (key.equals("properties")) {
                key = "properties.oracle.jdbc.ReadTimeout";
                value = "5000";
            }
            set((name == null ? "forage.jdbc." : "forage." + name + ".jdbc.") + key, value);
        }
    }

    private static List<ConfigModule> modules() {
        new DataSourceFactoryConfig();
        return ConfigEntries.getModules(DataSourceFactoryConfigEntries.class).keySet().stream()
                .filter(m -> m.name().startsWith("forage.jdbc."))
                .toList();
    }

    private void set(String key, String value) {
        System.setProperty(key, value);
        set.add(key);
    }

    // previous JdbcModuleDescriptor.translateProperties, verbatim
    private static Map<String, String> legacy(String prefix, DataSourceFactoryConfig config) {
        Map<String, String> props = new HashMap<>();

        String quarkusPrefix = "quarkus.datasource.";
        String effectivePrefix = prefix != null ? prefix : "dataSource";
        quarkusPrefix += "\"" + effectivePrefix + "\".";

        putIfNotEmpty(props, quarkusPrefix + "db-kind", config.dbKind());
        putIfNotEmpty(props, quarkusPrefix + "password", config.password());
        putIfNotEmpty(props, quarkusPrefix + "username", config.username());
        putIfNotEmpty(props, quarkusPrefix + "jdbc.url", config.jdbcUrl());
        props.put(quarkusPrefix + "jdbc.initial-size", String.valueOf(config.initialSize()));
        props.put(quarkusPrefix + "jdbc.min-size", String.valueOf(config.minSize()));
        props.put(quarkusPrefix + "jdbc.max-size", String.valueOf(config.maxSize()));
        props.put(quarkusPrefix + "jdbc.acquisition-timeout", config.acquisitionTimeoutSeconds() + "S");
        props.put(quarkusPrefix + "jdbc.validation-query-timeout", config.validationTimeoutSeconds() + "S");
        props.put(quarkusPrefix + "jdbc.leak-detection-interval", config.leakTimeoutMinutes() + "M");
        // Quote the key: driver property names can contain dots (e.g. oracle.jdbc.ReadTimeout)
        for (Map.Entry<String, String> driverProperty :
                config.driverProperties().entrySet()) {
            props.put(
                    quarkusPrefix + "jdbc.additional-jdbc-properties.\"" + driverProperty.getKey() + "\"",
                    driverProperty.getValue());
        }

        if (config.transactionEnabled()) {
            props.put(quarkusPrefix + "jdbc.transaction-isolation-level", "READ_COMMITTED");
            props.put(
                    "quarkus.transaction-manager.default-transaction-timeout",
                    config.transactionTimeoutSeconds() + "S");
            putIfNotEmpty(props, "quarkus.transaction-manager.node-name", config.transactionNodeId());
            props.put(
                    "quarkus.transaction-manager.enable-recovery", String.valueOf(config.transactionEnableRecovery()));
            putIfNotEmpty(props, "quarkus.transaction-manager.recovery-modules", config.transactionRecoveryModules());
            putIfNotEmpty(
                    props,
                    "quarkus.transaction-manager.xa-resource-orphan-filters",
                    config.transactionXaResourceOrphanFilters());
            putIfNotEmpty(
                    props,
                    "quarkus.transaction-manager.object-store.directory",
                    config.transactionObjectStoreDirectory());
            putIfNotEmpty(props, "quarkus.transaction-manager.object-store.type", config.transactionObjectStoreType());
            putIfNotEmpty(
                    props,
                    "quarkus.transaction-manager.object-store.datasource",
                    config.transactionObjectStoreDataSource());
            props.put(
                    "quarkus.transaction-manager.object-store.create-table",
                    String.valueOf(config.transactionObjectStoreCreateTable()));
            props.put(
                    "quarkus.transaction-manager.object-store.drop-table",
                    String.valueOf(config.transactionObjectStoreDropTable()));
            putIfNotEmpty(
                    props,
                    "quarkus.transaction-manager.object-store.table-prefix",
                    config.transactionObjectStoreTablePrefix());
            props.putAll(
                    JdbcModuleDescriptor.jdbcObjectStoreDataSourceProperties(config.transactionObjectStoreSettings()));
        }

        return props;
    }

    private static void putIfNotEmpty(Map<String, String> props, String key, String value) {
        if (value != null && !value.isEmpty()) {
            props.put(key, value);
        }
    }
}
