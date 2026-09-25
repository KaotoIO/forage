package io.kaoto.forage.jms;

import jakarta.jms.ConnectionFactory;

import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import org.apache.camel.CamelContext;
import org.apache.camel.component.jms.JmsComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.jta.JtaTransactionManager;
import io.kaoto.forage.core.annotations.ConditionalBean;
import io.kaoto.forage.core.annotations.ConditionalBeanGroup;
import io.kaoto.forage.core.annotations.FactoryType;
import io.kaoto.forage.core.annotations.ForageFactory;
import io.kaoto.forage.core.common.BeanFactory;
import io.kaoto.forage.core.common.ServiceLoaderHelper;
import io.kaoto.forage.core.jms.ConnectionFactoryProvider;
import io.kaoto.forage.core.jta.MandatoryJtaTransactionPolicy;
import io.kaoto.forage.core.jta.NeverJtaTransactionPolicy;
import io.kaoto.forage.core.jta.NotSupportedJtaTransactionPolicy;
import io.kaoto.forage.core.jta.RequiredJtaTransactionPolicy;
import io.kaoto.forage.core.jta.RequiresNewJtaTransactionPolicy;
import io.kaoto.forage.core.jta.SupportsJtaTransactionPolicy;
import io.kaoto.forage.core.jta.recovery.ForageRecoveryService;
import io.kaoto.forage.core.util.config.ConfigHelper;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.jms.common.ConnectionFactoryCommonExportHelper;
import io.kaoto.forage.jms.common.ConnectionFactoryConfig;
import io.kaoto.forage.jms.common.PooledConnectionFactory;
import io.kaoto.forage.jms.common.transactions.JmsJtaTransactionSupport;

@ForageFactory(
        value = "JMS Connection",
        components = {"camel-jms"},
        description = "Creates JMS ConnectionFactory beans for message broker integration with transaction support",
        type = FactoryType.CONNECTION_FACTORY,
        autowired = true,
        configClass = ConnectionFactoryConfig.class,
        conditionalBeans = {
            @ConditionalBeanGroup(
                    id = "jta-transaction-policies",
                    description = "JTA Transaction Policy beans for Camel transacted routes",
                    configEntry = "forage.jms.transaction.enabled",
                    runtimeDependencies = {
                        "quarkus:mvn:io.quarkus:quarkus-narayana-jta",
                        "quarkus:mvn:io.quarkiverse.messaginghub:quarkus-pooled-jms"
                    },
                    beans = {
                        @ConditionalBean(
                                name = "PROPAGATION_REQUIRED",
                                javaType = "org.apache.camel.spi.TransactedPolicy",
                                description =
                                        "Starts a new transaction if none exists, otherwise joins the existing one"),
                        @ConditionalBean(
                                name = "MANDATORY",
                                javaType = "org.apache.camel.spi.TransactedPolicy",
                                description = "Requires an existing transaction, throws exception if none exists"),
                        @ConditionalBean(
                                name = "NEVER",
                                javaType = "org.apache.camel.spi.TransactedPolicy",
                                description = "Must execute without a transaction, throws exception if one exists"),
                        @ConditionalBean(
                                name = "NOT_SUPPORTED",
                                javaType = "org.apache.camel.spi.TransactedPolicy",
                                description = "Suspends any existing transaction and executes without one"),
                        @ConditionalBean(
                                name = "REQUIRES_NEW",
                                javaType = "org.apache.camel.spi.TransactedPolicy",
                                description = "Always starts a new transaction, suspending any existing one"),
                        @ConditionalBean(
                                name = "SUPPORTS",
                                javaType = "org.apache.camel.spi.TransactedPolicy",
                                description = "Joins existing transaction if present, otherwise runs without one"),
                        @ConditionalBean(
                                name = "jtaTransactionManager",
                                javaType = "org.springframework.transaction.jta.JtaTransactionManager",
                                description = "Spring JtaTransactionManager wrapping the Narayana transaction manager, "
                                        + "set on the Camel JMS component so consumers receive within a JTA "
                                        + "transaction and XA sessions enlist")
                    }),
            @ConditionalBeanGroup(
                    id = "jms-connection-pool",
                    description = "Connection pooling for JMS connections",
                    configEntry = "forage.jms.pool.enabled",
                    runtimeDependencies = {"quarkus:mvn:io.quarkiverse.messaginghub:quarkus-pooled-jms"},
                    beans = {})
        })
public class ConnectionFactoryBeanFactory implements BeanFactory {
    private final Logger LOG = LoggerFactory.getLogger(ConnectionFactoryBeanFactory.class);

    private CamelContext camelContext;
    private static final String DEFAULT_CONNECTION_FACTORY = "connectionFactory";
    private static final String JTA_TRANSACTION_MANAGER = "jtaTransactionManager";
    private static final String JMS_TRANSACTION_MANAGER_CUSTOMIZER = "forageJmsTransactionManagerCustomizer";

    @Override
    public void cleanup() {
        ConnectionFactoryConfig config = new ConnectionFactoryConfig();
        Set<String> prefixes =
                ConfigStore.getInstance().readPrefixes(config, ConfigHelper.getNamedPropertyRegexp("jms"));

        for (String name : prefixes) {
            closeAndUnbind(name);
            camelContext.removeComponent(name);
        }
        closeAndUnbind(DEFAULT_CONNECTION_FACTORY);

        // Drop stale XA recovery helpers; configure() registers fresh ones right after,
        // so the recovery manager itself keeps running across dev-mode reloads.
        deregisterRecoveryHelpers(prefixes);

        // Unbind JTA transaction policies and transaction manager wiring if they were registered
        if (anyTransactionEnabled(config, prefixes)) {
            for (String name : List.of(
                    "PROPAGATION_REQUIRED",
                    "MANDATORY",
                    "NEVER",
                    "NOT_SUPPORTED",
                    "REQUIRES_NEW",
                    "SUPPORTS",
                    JTA_TRANSACTION_MANAGER,
                    JMS_TRANSACTION_MANAGER_CUSTOMIZER)) {
                camelContext.getRegistry().unbind(name);
            }
        }
    }

    @Override
    public void stop() {
        ConnectionFactoryConfig config = new ConnectionFactoryConfig();
        Set<String> prefixes =
                ConfigStore.getInstance().readPrefixes(config, ConfigHelper.getNamedPropertyRegexp("jms"));

        deregisterRecoveryHelpers(prefixes);
        // The recovery manager is shared with the JDBC module: the last module out terminates it.
        ForageRecoveryService.getInstance().stopIfNoRegistrations();
    }

    private void deregisterRecoveryHelpers(Set<String> prefixes) {
        for (String name : prefixes) {
            ForageRecoveryService.getInstance().deregisterHelpers(PooledConnectionFactory.recoveryKey(name));
        }
        ForageRecoveryService.getInstance()
                .deregisterHelpers(PooledConnectionFactory.recoveryKey(DEFAULT_CONNECTION_FACTORY));
    }

    private void closeAndUnbind(String name) {
        // Note: we intentionally do NOT close AutoCloseable resources here.
        // Camel components cache references at the component level.
        // The old resource is unbound and will be GC'd after the component is reset and routes reloaded.
        camelContext.getRegistry().unbind(name);
    }

    @Override
    public void configure() {

        ConnectionFactoryConfig config = new ConnectionFactoryConfig();
        Set<String> prefixes =
                ConfigStore.getInstance().readPrefixes(config, ConfigHelper.getNamedPropertyRegexp("jms"));

        // Bind JTA policies and transaction manager when the default config OR any
        // discovered prefixed config has transactions enabled (#427)
        JtaTransactionManager jtaTransactionManager = null;
        if (anyTransactionEnabled(config, prefixes)) {
            camelContext.getRegistry().bind("PROPAGATION_REQUIRED", new RequiredJtaTransactionPolicy());
            camelContext.getRegistry().bind("MANDATORY", new MandatoryJtaTransactionPolicy());
            camelContext.getRegistry().bind("NEVER", new NeverJtaTransactionPolicy());
            camelContext.getRegistry().bind("NOT_SUPPORTED", new NotSupportedJtaTransactionPolicy());
            camelContext.getRegistry().bind("REQUIRES_NEW", new RequiresNewJtaTransactionPolicy());
            camelContext.getRegistry().bind("SUPPORTS", new SupportsJtaTransactionPolicy());

            jtaTransactionManager = JmsJtaTransactionSupport.createJtaTransactionManager();
            camelContext.getRegistry().bind(JTA_TRANSACTION_MANAGER, jtaTransactionManager);

            // Apply the ComponentCustomizer to the default "jms" component ONLY when
            // the default (unprefixed) config enables transactions. Named per-broker
            // components are pre-configured at creation time (#433).
            if (config.transactionEnabled()) {
                camelContext
                        .getRegistry()
                        .bind(
                                JMS_TRANSACTION_MANAGER_CUSTOMIZER,
                                JmsJtaTransactionSupport.jmsComponentCustomizer(jtaTransactionManager));
            }
        }

        if (!prefixes.isEmpty()) {
            for (String name : prefixes) {
                ConnectionFactory connectionFactory =
                        camelContext.getRegistry().lookupByNameAndType(name, ConnectionFactory.class);
                if (connectionFactory == null) {
                    ConnectionFactoryConfig cfConfig = new ConnectionFactoryConfig(name);
                    connectionFactory = newConnectionFactory(cfConfig, name);
                    if (connectionFactory != null) {
                        camelContext.getRegistry().bind(name, connectionFactory);
                    } else {
                        LOG.warn("Skipping binding for '{}' because ConnectionFactory creation returned null", name);
                        continue;
                    }
                }

                // Register a per-broker JmsComponent so routes using "name:queue:..."
                // get the correct ConnectionFactory and JTA scoping (#433)
                ConnectionFactoryConfig cfConfig = new ConnectionFactoryConfig(name);
                JtaTransactionManager perBrokerTm = cfConfig.transactionEnabled() ? jtaTransactionManager : null;
                JmsComponent jmsComponent = JmsJtaTransactionSupport.createJmsComponent(connectionFactory, perBrokerTm);
                camelContext.addComponent(name, jmsComponent);
            }
        } else {
            try {
                if (camelContext.getRegistry().lookupByNameAndType(DEFAULT_CONNECTION_FACTORY, ConnectionFactory.class)
                        == null) {
                    final List<ServiceLoader.Provider<ConnectionFactoryProvider>> providers =
                            findProviders(ConnectionFactoryProvider.class);
                    if (providers.size() == 1) {
                        ConnectionFactory connectionFactory = doCreateConnectionFactory(providers.get(0), null);
                        camelContext.getRegistry().bind(DEFAULT_CONNECTION_FACTORY, connectionFactory);
                    } else {
                        throw new IllegalArgumentException(
                                "No ConnectionFactory implementation is present in the classpath");
                    }
                }
            } catch (Exception ex) {
                LOG.error(ex.getMessage(), ex);
            }
        }
    }

    private static boolean anyTransactionEnabled(ConnectionFactoryConfig defaultConfig, Set<String> prefixes) {
        if (defaultConfig.transactionEnabled()) {
            return true;
        }
        return prefixes.stream().anyMatch(p -> new ConnectionFactoryConfig(p).transactionEnabled());
    }

    private synchronized ConnectionFactory newConnectionFactory(
            ConnectionFactoryConfig connectionFactoryConfig, String name) {
        final String connectionFactoryProviderClass =
                ConnectionFactoryCommonExportHelper.transformJmsKindIntoProviderClass(
                        connectionFactoryConfig.jmsKind());
        LOG.info("Creating ConnectionFactory of type {}", connectionFactoryProviderClass);

        final List<ServiceLoader.Provider<ConnectionFactoryProvider>> providers =
                findProviders(ConnectionFactoryProvider.class);

        final ServiceLoader.Provider<ConnectionFactoryProvider> connectionFactoryProvider =
                ServiceLoaderHelper.findProviderByClassName(providers, connectionFactoryProviderClass);

        if (connectionFactoryProvider == null) {
            String available = providers.stream()
                    .map(p -> p.type().getName())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("none");
            throw new IllegalStateException(
                    "No ConnectionFactoryProvider found for kind '%s' (expected %s). Available providers: %s"
                            .formatted(connectionFactoryConfig.jmsKind(), connectionFactoryProviderClass, available));
        }

        return doCreateConnectionFactory(connectionFactoryProvider, name);
    }

    private ConnectionFactory doCreateConnectionFactory(
            ServiceLoader.Provider<ConnectionFactoryProvider> provider, String name) {
        final ConnectionFactoryProvider connectionFactoryProvider = provider.get();
        return connectionFactoryProvider.create(name);
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }
}
