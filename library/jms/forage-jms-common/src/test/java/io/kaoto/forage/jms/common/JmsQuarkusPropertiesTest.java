package io.kaoto.forage.jms.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.camel.tooling.model.Strings;
import io.kaoto.forage.core.jta.store.ObjectStoreSettings;
import io.kaoto.forage.core.jta.store.ObjectStoreType;
import io.kaoto.forage.core.util.config.ConfigEntries;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.core.util.config.MissingConfigException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link JmsModuleDescriptor#translateProperties} built from {@link JmsModuleDescriptor#quarkusProperties()} gives
 * the same map as before (a verbatim copy of the previous code), for every catalog key set.
 */
class JmsQuarkusPropertiesTest {

    private final List<String> set = new ArrayList<>();

    @AfterEach
    void tearDown() {
        set.forEach(System::clearProperty);
        set.clear();
        ConfigStore.getInstance().reload();
    }

    @Test
    void sameMapAsBeforeWithEveryKeySet() {
        for (String kind : new String[] {"artemis", "ibmmq"}) {
            for (String name : new String[] {null, "broker1"}) {
                setAll(name, kind);
                assertThat(assertSame(name)).containsKey("quarkus.transaction-manager.enable-recovery");
                tearDown();
            }
        }
    }

    @Test
    void sameMapAsBeforeWithRequiredKeysOnly() {
        for (String name : new String[] {null, "broker1"}) {
            String p = name == null ? "forage.jms." : "forage." + name + ".jms.";
            set(p + "kind", "artemis");
            set(p + "broker.url", "tcp://h:61616");
            assertSame(name);
        }
    }

    @Test
    void everyListedKeyIsAConfigEntry() {
        List<String> names = modules().stream().map(ConfigModule::name).toList();
        assertThat(new JmsModuleDescriptor().quarkusProperties())
                .allSatisfy(p -> assertThat(names).contains("forage.jms." + p.forageKey()));
    }

    /** An ignored key is a config entry, not mapped, and does not change the translated map. */
    @Test
    void ignoredKeysDoNotChangeTheMap() {
        JmsModuleDescriptor descriptor = new JmsModuleDescriptor();
        List<String> names = modules().stream().map(ConfigModule::name).toList();
        for (String key : descriptor.quarkusIgnoredProperties()) {
            assertThat(names).contains("forage.jms." + key);
            assertThat(descriptor.quarkusProperties())
                    .noneMatch(p -> p.forageKey().equals(key));
            set("forage.jms.kind", "artemis");
            set("forage.jms.broker.url", "tcp://h:61616");
            Map<String, String> without = descriptor.translateProperties(null, new ConnectionFactoryConfig());
            set("forage.jms." + key, "12345");
            ConfigStore.getInstance().reload();
            assertThat(descriptor.translateProperties(null, new ConnectionFactoryConfig()))
                    .isEqualTo(without);
            tearDown();
        }
    }

    private Map<String, String> assertSame(String name) {
        ConnectionFactoryConfig config =
                name == null ? new ConnectionFactoryConfig() : new ConnectionFactoryConfig(name);
        Map<String, String> now = new JmsModuleDescriptor().translateProperties(name, config);
        assertThat(now).isEqualTo(legacy(name, config));
        assertThat(now).isNotEmpty();
        return now;
    }

    /** Every jms catalog key, with a value its type accepts. */
    private void setAll(String name, String kind) {
        for (ConfigModule module : modules()) {
            String key = module.name().substring("forage.jms.".length());
            String value =
                    switch (module.type() == null ? "string" : module.type()) {
                        case "integer", "long" -> "7";
                        case "boolean" -> "true";
                        default -> "v-" + key;
                    };
            if (key.equals("kind")) {
                value = kind;
            } else if (key.equals("transaction.object.store.type")) {
                value = "file-system";
            }
            set((name == null ? "forage.jms." : "forage." + name + ".jms.") + key, value);
        }
    }

    private static List<ConfigModule> modules() {
        new ConnectionFactoryConfig();
        return ConfigEntries.getModules(ConnectionFactoryConfigEntries.class).keySet().stream()
                .filter(m -> m.name().startsWith("forage.jms."))
                .toList();
    }

    private void set(String key, String value) {
        System.setProperty(key, value);
        set.add(key);
    }

    // previous JmsModuleDescriptor.translateProperties, verbatim
    private static Map<String, String> legacy(String prefix, ConnectionFactoryConfig config) {
        Map<String, String> props = new HashMap<>();
        String named = prefix;

        if ("artemis".equals(config.jmsKind())) {
            String property = "quarkus.artemis.";
            if (!Strings.isNullOrEmpty(named)) {
                property += "\"" + named + "\".";
                // Disable the default (unnamed) configuration when only named configs are
                // used, preventing "url is not set" / "devservices is activated" errors
                addIfNotEmpty(props, "quarkus.artemis.enabled", "false");
                addIfNotEmpty(props, "quarkus.artemis.devservices.enabled", "false");
            } else {
                addIfNotEmpty(props, "quarkus.artemis.enabled", "true");
            }
            addIfNotEmpty(props, property + "password", config.password());
            addIfNotEmpty(props, property + "username", config.username());
            addIfNotEmpty(props, property + "url", config.brokerUrl());

            addIfNotEmpty(props, "quarkus.pooled-jms.max-connections", config::maxConnections);
            addIfNotEmpty(props, "quarkus.pooled-jms.max-sessions-per-connection", config::maxSessionsPerConnection);
            // pooled-jms expects both timeouts in milliseconds - pass the configured millis through unchanged
            addIfNotEmpty(props, "quarkus.pooled-jms.connection-idle-timeout", config::idleTimeoutMillis);
            addIfNotEmpty(props, "quarkus.pooled-jms.connection-check-interval", config::expiryTimeoutMillis);
            addIfNotEmpty(props, "quarkus.pooled-jms.block-if-session-pool-is-full", config::blockIfFull);
            addIfNotEmpty(
                    props,
                    "quarkus.pooled-jms.block-if-session-pool-is-full-timeout",
                    config::blockIfFullTimeoutMillis);

            if (config.transactionEnabled()) {
                addIfNotEmpty(props, "quarkus.pooled-jms.transaction", "xa");
                addTransactionManagerProperties(props, config);
            }
        } else if ("ibmmq".equals(config.jmsKind())) {
            // The IBM MQ ConnectionFactory is created via recorder, so no connection properties
            // are translated. The Narayana transaction manager is still owned by
            // quarkus-narayana-jta, so the transaction-manager properties must be translated:
            // without enable-recovery the Quarkus recovery service never starts and the recovery
            // helper registered by the recorder path would stay buffered forever (#432).
            if (config.transactionEnabled()) {
                addTransactionManagerProperties(props, config);
            }
        } else {
            throw new IllegalArgumentException(
                    "`%s` Jms kind is not supported by Quarkus runtime.".formatted(config.jmsKind()));
        }

        return props;
    }

    private static void addTransactionManagerProperties(Map<String, String> props, ConnectionFactoryConfig config) {
        addIfNotEmpty(
                props,
                "quarkus.transaction-manager.default-transaction-timeout",
                config.transactionTimeoutSeconds() + "S");
        addIfNotEmpty(props, "quarkus.transaction-manager.node-name", config::transactionNodeId);
        addIfNotEmpty(
                props,
                "quarkus.transaction-manager.xa-resource-orphan-filters",
                config::transactionXaResourceOrphanFilters);
        addIfNotEmpty(props, "quarkus.transaction-manager.recovery-modules", config::transactionRecoveryModules);
        addIfNotEmpty(props, "quarkus.transaction-manager.expiry-scanners", config::transactionExpiryScanners);
        addIfNotEmpty(
                props, "quarkus.transaction-manager.object-store.directory", config::transactionObjectStoreDirectory);
        addIfNotEmpty(props, "quarkus.transaction-manager.object-store.type", config::transactionObjectStoreType);
        addIfNotEmpty(props, "quarkus.transaction-manager.enable-recovery", config::transactionEnableRecovery);
        addJdbcObjectStoreProperties(props, config.transactionObjectStoreSettings());
    }

    /**
     * The {@code jdbc} object store keeps the transaction log in the referenced datasource, which
     * quarkus-narayana-jta only accepts with its transaction integration disabled. The datasource
     * itself is a Forage JDBC configuration ({@code forage.<name>.jdbc.*}), translated by the JDBC
     * module.
     */
    private static void addJdbcObjectStoreProperties(Map<String, String> props, ObjectStoreSettings settings) {
        if (settings.storeType() != ObjectStoreType.JDBC) {
            return;
        }
        String name = settings.dataSource();
        if (name == null) {
            throw new MissingConfigException("The transaction object store type is 'jdbc' (" + settings.typeProperty()
                    + ") but " + settings.dataSourceProperty() + " is not set. Set it to the name of a non-XA"
                    + " Forage datasource (forage.<name>.jdbc.*) that holds the transaction log table.");
        }
        props.put("quarkus.transaction-manager.object-store.datasource", name);
        props.put("quarkus.transaction-manager.object-store.create-table", String.valueOf(settings.createTable()));
        props.put("quarkus.transaction-manager.object-store.drop-table", String.valueOf(settings.dropTable()));
        addIfNotEmpty(props, "quarkus.transaction-manager.object-store.table-prefix", settings.tablePrefix());
        props.put("quarkus.datasource.\"" + name + "\".jdbc.transactions", "disabled");
    }

    private static void addIfNotEmpty(Map<String, String> config, String key, String value) {
        if (value != null && !Strings.isNullOrEmpty(value)) {
            config.put(key, value);
        }
    }

    private static void addIfNotEmpty(Map<String, String> config, String key, Supplier<?> method) {
        Object value = method.get();
        String stringValue = String.valueOf(value);
        if (value != null && !Strings.isNullOrEmpty(stringValue)) {
            config.put(key, stringValue);
        }
    }
}
