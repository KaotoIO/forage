package io.kaoto.forage.jms.common;

import jakarta.jms.ConnectionFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.camel.tooling.model.Strings;
import io.kaoto.forage.core.common.ForageModuleDescriptor;
import io.kaoto.forage.core.jms.ConnectionFactoryProvider;
import io.kaoto.forage.core.jta.store.ObjectStoreSettings;
import io.kaoto.forage.core.jta.store.ObjectStoreType;
import io.kaoto.forage.core.util.config.MissingConfigException;

/**
 * Module descriptor for Forage JMS. Captures all JMS-specific knowledge:
 * prefix discovery, provider resolution, and Quarkus property translation.
 *
 * @since 1.1
 */
public class JmsModuleDescriptor implements ForageModuleDescriptor<ConnectionFactoryConfig, ConnectionFactoryProvider> {

    @Override
    public String modulePrefix() {
        return "jms";
    }

    @Override
    public ConnectionFactoryConfig createConfig(String prefix) {
        return prefix == null ? new ConnectionFactoryConfig() : new ConnectionFactoryConfig(prefix);
    }

    @Override
    public Class<ConnectionFactoryProvider> providerClass() {
        return ConnectionFactoryProvider.class;
    }

    @Override
    public String resolveProviderClassName(ConnectionFactoryConfig config) {
        return ConnectionFactoryCommonExportHelper.transformJmsKindIntoProviderClass(config.jmsKind());
    }

    @Override
    public String defaultBeanName() {
        return "connectionFactory";
    }

    @Override
    public List<String> defaultBeanAliases() {
        return List.of("jmsConnectionFactory");
    }

    @Override
    public Class<?> primaryBeanClass() {
        return ConnectionFactory.class;
    }

    @Override
    public boolean transactionEnabled(ConnectionFactoryConfig config) {
        return config.transactionEnabled();
    }

    @Override
    public String destroyMethodName(String prefix) {
        // JmsPoolConnectionFactory is stopped via stop(). When pooling is disabled the provider
        // returns the raw broker ConnectionFactory (e.g., ActiveMQConnectionFactory) which has
        // no stop() method — setting one would make Spring fail at context close.
        return createConfig(prefix).poolEnabled() ? "stop" : "";
    }

    @Override
    public Map<String, String> translateProperties(String prefix, ConnectionFactoryConfig config) {
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
