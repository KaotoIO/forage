package io.kaoto.forage.jms.common;

import jakarta.jms.ConnectionFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.camel.tooling.model.Strings;
import io.kaoto.forage.core.common.ForageModuleDescriptor;
import io.kaoto.forage.core.jms.ConnectionFactoryProvider;

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
    public Class<?> primaryBeanClass() {
        return ConnectionFactory.class;
    }

    @Override
    public boolean transactionEnabled(ConnectionFactoryConfig config) {
        return config.transactionEnabled();
    }

    @Override
    public Map<String, String> translateProperties(String prefix, ConnectionFactoryConfig config) {
        Map<String, String> props = new HashMap<>();
        String named = prefix;

        if ("artemis".equals(config.jmsKind())) {
            String property = "quarkus.artemis.";
            if (!Strings.isNullOrEmpty(named)) {
                property += "\"" + named + "\".";
            }

            addIfNotEmpty(props, "quarkus.artemis.enabled", "true");
            addIfNotEmpty(props, property + "password", config.password());
            addIfNotEmpty(props, property + "username", config.username());
            addIfNotEmpty(props, property + "url", config.brokerUrl());

            addIfNotEmpty(props, "quarkus.pooled-jms.max-connections", config::maxConnections);
            addIfNotEmpty(props, "quarkus.pooled-jms.max-sessions-per-connection", config::maxSessionsPerConnection);
            addSecondsFromMillis(props, "quarkus.pooled-jms.connection-idle-timeout", config::idleTimeoutMillis);
            addIfNotEmpty(props, "quarkus.pooled-jms.connection-check-interval", config::expiryTimeoutMillis);
            addIfNotEmpty(props, "quarkus.pooled-jms.block-if-session-pool-is-full", config::blockIfFull);
            addSecondsFromMillis(
                    props,
                    "quarkus.pooled-jms.block-if-session-pool-is-full-timeout",
                    config::blockIfFullTimeoutMillis);

            if (config.transactionEnabled()) {
                addIfNotEmpty(props, "quarkus.pooled-jms.transaction", "xa");
                addIfNotEmpty(
                        props,
                        "quarkus.transaction-manager.default-transaction-timeout",
                        config.transactionTimeoutSeconds() + "S");
                addIfNotEmpty(props, "quarkus.transaction-manager.node-name", config::transactionNodeId);
                addIfNotEmpty(
                        props,
                        "quarkus.transaction-manager.xa-resource-orphan-filters",
                        config::transactionXaResourceOrphanFilters);
                addIfNotEmpty(
                        props, "quarkus.transaction-manager.recovery-modules", config::transactionRecoveryModules);
                addIfNotEmpty(props, "quarkus.transaction-manager.expiry-scanners", config::transactionExpiryScanners);
                addIfNotEmpty(
                        props,
                        "quarkus.transaction-manager.object-store.directory",
                        config::transactionObjectStoreDirectory);
                addIfNotEmpty(
                        props, "quarkus.transaction-manager.object-store.type", config::transactionObjectStoreType);
                addIfNotEmpty(props, "quarkus.transaction-manager.enable-recovery", config::transactionEnableRecovery);
            }
        } else if (!"ibmmq".equals(config.jmsKind())) {
            // IBM MQ configuration is created via recorder, no property translation needed
            throw new IllegalArgumentException(
                    "`%s` Jms kind is not supported by Quarkus runtime.".formatted(config.jmsKind()));
        }

        return props;
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

    private static void addSecondsFromMillis(Map<String, String> config, String key, Supplier<Long> method) {
        Long value = method.get();
        if (value != null) {
            int intValue = (int) (value / 1000);
            config.put(key, String.valueOf(intValue));
        }
    }
}
