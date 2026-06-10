package io.kaoto.forage.springboot.jms;

import jakarta.jms.ConnectionFactory;

import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import org.apache.camel.CamelContext;
import org.apache.camel.component.jms.JmsComponent;
import org.messaginghub.pooled.jms.JmsPoolConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.artemis.autoconfigure.ArtemisAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.transaction.jta.JtaTransactionManager;
import io.kaoto.forage.core.annotations.FactoryType;
import io.kaoto.forage.core.annotations.FactoryVariant;
import io.kaoto.forage.core.annotations.ForageFactory;
import io.kaoto.forage.core.jms.ConnectionFactoryProvider;
import io.kaoto.forage.core.util.config.ConfigHelper;
import io.kaoto.forage.jms.common.ConnectionFactoryConfig;
import io.kaoto.forage.jms.common.JmsModuleDescriptor;
import io.kaoto.forage.jms.common.transactions.JmsJtaTransactionSupport;
import io.kaoto.forage.springboot.common.ForageSpringBootModuleAdapter;
import io.kaoto.forage.springboot.common.SpringPropertyHelper;

/**
 * Auto-configuration for Forage JMS ConnectionFactory creation using ServiceLoader discovery.
 * Automatically creates ConnectionFactory beans from JMS configuration properties,
 * supporting both single and multi-instance (prefixed) configurations.
 *
 * <p>Named/prefixed connection factories (e.g., {@code forage.mq1.jms.url}) are registered
 * dynamically by {@link ForageSpringBootModuleAdapter} using the {@link JmsModuleDescriptor}.
 */
@ForageFactory(
        value = "JMS Connection (Spring Boot)",
        components = {"camel-jms"},
        description = "Auto-configured JMS ConnectionFactory for Spring Boot with transaction management",
        type = FactoryType.CONNECTION_FACTORY,
        autowired = true,
        configClass = ConnectionFactoryConfig.class,
        variant = FactoryVariant.SPRING_BOOT)
@Configuration
@AutoConfigureBefore(ArtemisAutoConfiguration.class)
public class ForageConnectionFactoryAutoConfiguration implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ForageConnectionFactoryAutoConfiguration.class);

    private ConnectionFactory defaultConnectionFactory;

    /**
     * Registers the generic module adapter that discovers prefixed ConnectionFactory
     * configurations and registers them as proper bean definitions using the
     * {@link JmsModuleDescriptor}.
     */
    @Bean
    static ForageSpringBootModuleAdapter<ConnectionFactoryConfig, ConnectionFactoryProvider> forageJmsModuleAdapter(
            Environment environment) {
        return new ForageSpringBootModuleAdapter<>(new JmsModuleDescriptor(), environment);
    }

    /**
     * Fallback ConnectionFactory bean created when no named/prefixed configurations are found
     * and default (unprefixed) JMS properties exist. Uses {@code forage.jms.kind} to select
     * the matching provider when multiple providers are on the classpath.
     *
     * <p>{@code destroyMethod = ""} disables Spring's destroy-method inference:
     * {@link JmsPoolConnectionFactory} exposes {@code stop()} rather than {@code close()}/
     * {@code shutdown()}, so inference would never stop the pool (and a raw, pool-disabled
     * connection factory may have no lifecycle method at all). The pool is instead stopped
     * explicitly in {@link #destroy()} when the context closes.
     */
    @Bean(value = "jmsConnectionFactory", destroyMethod = "")
    @ConditionalOnMissingBean(name = "jmsConnectionFactory")
    @ConditionalOnProperty(prefix = "forage.jms", name = "kind")
    public ConnectionFactory forageDefaultConnectionFactory() {
        ConnectionFactoryConfig config = new ConnectionFactoryConfig();
        String kind = config.jmsKind();
        String providerClassName =
                io.kaoto.forage.jms.common.ConnectionFactoryCommonExportHelper.transformJmsKindIntoProviderClass(kind);

        List<ServiceLoader.Provider<ConnectionFactoryProvider>> providers =
                ServiceLoader.load(ConnectionFactoryProvider.class).stream().toList();

        for (ServiceLoader.Provider<ConnectionFactoryProvider> provider : providers) {
            if (provider.type().getName().equals(providerClassName)) {
                log.info("Creating default ConnectionFactory using provider: {}", providerClassName);
                ConnectionFactory connectionFactory = provider.get().create(null);
                log.info("Registered default ConnectionFactory bean");
                this.defaultConnectionFactory = connectionFactory;
                return connectionFactory;
            }
        }

        String available = providers.stream()
                .map(p -> p.type().getName())
                .reduce((a, b) -> a + ", " + b)
                .orElse("none");
        throw new IllegalStateException("No ConnectionFactoryProvider found for kind '" + kind + "' (expected "
                + providerClassName + "). Available providers: " + available);
    }

    /**
     * Registers per-broker {@link JmsComponent} instances so routes using
     * {@code mq1:queue:...} get the correct ConnectionFactory and JTA scoping (#433).
     * Runs after all singletons are created but before the CamelContext starts.
     */
    @Bean
    @ConditionalOnBean(CamelContext.class)
    SmartInitializingSingleton forageJmsComponentRegistrar(
            CamelContext camelContext, ConfigurableListableBeanFactory beanFactory, Environment environment) {
        return () -> {
            Set<String> prefixes =
                    SpringPropertyHelper.discoverPrefixes(environment, ConfigHelper.getNamedPropertyRegexp("jms"));
            if (prefixes.isEmpty()) {
                return;
            }

            JtaTransactionManager jtaTransactionManager = null;
            try {
                jtaTransactionManager = beanFactory.getBean(JtaTransactionManager.class);
            } catch (Exception e) {
                // no JtaTransactionManager — transactions are not enabled globally
            }

            for (String name : prefixes) {
                ConnectionFactoryConfig cfConfig = new ConnectionFactoryConfig(name);
                ConnectionFactory cf = beanFactory.getBean(name, ConnectionFactory.class);
                JtaTransactionManager perBrokerTm = cfConfig.transactionEnabled() ? jtaTransactionManager : null;
                JmsComponent component = JmsJtaTransactionSupport.createJmsComponent(cf, perBrokerTm);
                camelContext.addComponent(name, component);
                log.info(
                        "Registered per-broker JmsComponent '{}' (transactions={})",
                        name,
                        cfConfig.transactionEnabled());
            }
        };
    }

    /**
     * Stops the default pooled ConnectionFactory when the application context closes.
     * {@link JmsPoolConnectionFactory} has no {@code close()}/{@code shutdown()} method, so
     * Spring's inferred destroy method would never release the pool.
     */
    @Override
    public void destroy() {
        if (defaultConnectionFactory instanceof JmsPoolConnectionFactory pool) {
            log.info("Stopping default pooled ConnectionFactory");
            pool.stop();
        }
    }
}
