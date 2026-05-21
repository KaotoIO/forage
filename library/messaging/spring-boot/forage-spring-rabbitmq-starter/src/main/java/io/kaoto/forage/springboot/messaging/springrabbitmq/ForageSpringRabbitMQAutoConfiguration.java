package io.kaoto.forage.springboot.messaging.springrabbitmq;

import java.util.List;
import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import io.kaoto.forage.core.annotations.FactoryType;
import io.kaoto.forage.core.annotations.FactoryVariant;
import io.kaoto.forage.core.annotations.ForageFactory;
import io.kaoto.forage.core.messaging.SpringRabbitMQConnectionFactoryProvider;
import io.kaoto.forage.core.util.config.MissingConfigException;
import io.kaoto.forage.messaging.spring.rabbitmq.common.SpringRabbitMQConfig;
import io.kaoto.forage.messaging.spring.rabbitmq.common.SpringRabbitMQConstants;
import io.kaoto.forage.messaging.spring.rabbitmq.common.SpringRabbitMQModuleDescriptor;
import io.kaoto.forage.springboot.common.ForageSpringBootModuleAdapter;

/**
 * Auto-configuration for Forage Spring RabbitMQ CachingConnectionFactory creation.
 * Automatically creates ConnectionFactory beans from Spring RabbitMQ configuration properties,
 * supporting both single and multi-instance (prefixed) configurations.
 *
 * <p>Named/prefixed connection factories (e.g., {@code forage.mq1.spring.rabbitmq.host})
 * are registered dynamically by {@link ForageSpringBootModuleAdapter} using the
 * {@link SpringRabbitMQModuleDescriptor}.
 *
 * @since 1.4
 */
@ForageFactory(
        value = "Spring RabbitMQ Connection (Spring Boot)",
        components = {"camel-spring-rabbitmq"},
        description = "Auto-configured Spring RabbitMQ CachingConnectionFactory for Spring Boot",
        type = FactoryType.SPRING_RABBITMQ_CONNECTION_FACTORY,
        autowired = true,
        configClass = SpringRabbitMQConfig.class,
        variant = FactoryVariant.SPRING_BOOT)
@AutoConfiguration(before = RabbitAutoConfiguration.class)
public class ForageSpringRabbitMQAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ForageSpringRabbitMQAutoConfiguration.class);

    /**
     * Registers the generic module adapter that discovers prefixed Spring RabbitMQ
     * configurations and registers them as proper bean definitions using the
     * {@link SpringRabbitMQModuleDescriptor}.
     */
    @Bean
    static ForageSpringBootModuleAdapter<SpringRabbitMQConfig, SpringRabbitMQConnectionFactoryProvider>
            forageSpringRabbitMQModuleAdapter(Environment environment) {
        return new ForageSpringBootModuleAdapter<>(new SpringRabbitMQModuleDescriptor(), environment);
    }

    /**
     * Fallback ConnectionFactory bean created when no named/prefixed configurations are found
     * and default (unprefixed) Spring RabbitMQ properties exist.
     *
     * <p>This bean is only registered when:
     * <ul>
     *   <li>No "rabbitConnectionFactory" bean already exists (e.g., from the module adapter's prefix discovery)</li>
     *   <li>The {@code forage.spring.rabbitmq.host} property is configured</li>
     * </ul>
     *
     * @param forageSpringRabbitMQModuleAdapter injected to ensure the adapter runs first and discovers
     *                                          prefixed configurations before this fallback bean is evaluated.
     *                                          The parameter is not used in the method body - it's a Spring Boot
     *                                          dependency ordering pattern that guarantees proper initialization sequence.
     */
    @Bean(SpringRabbitMQConstants.DEFAULT_BEAN_NAME)
    @ConditionalOnMissingBean(name = SpringRabbitMQConstants.DEFAULT_BEAN_NAME)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            prefix = "forage." + SpringRabbitMQConstants.MODULE_PREFIX,
            name = "host")
    public ConnectionFactory forageDefaultRabbitConnectionFactory(
            ForageSpringBootModuleAdapter<SpringRabbitMQConfig, SpringRabbitMQConnectionFactoryProvider>
                    forageSpringRabbitMQModuleAdapter) {
        SpringRabbitMQConfig config = new SpringRabbitMQConfig();

        // Validate required property before logging
        if (config.host() == null || config.host().trim().isEmpty()) {
            throw new MissingConfigException("Spring RabbitMQ host is required but not configured");
        }

        List<ServiceLoader.Provider<SpringRabbitMQConnectionFactoryProvider>> providers =
                ServiceLoader.load(SpringRabbitMQConnectionFactoryProvider.class).stream()
                        .toList();

        if (providers.isEmpty()) {
            throw new IllegalStateException("No SpringRabbitMQConnectionFactoryProvider found on classpath. "
                    + "Ensure forage-spring-rabbitmq-common is a dependency.");
        }

        log.info(
                "Creating default Spring RabbitMQ ConnectionFactory using provider: {}",
                providers.get(0).type().getName());
        SpringRabbitMQConnectionFactoryProvider provider = providers.get(0).get();
        ConnectionFactory connectionFactory = provider.create(null);

        log.info("Registered default Spring RabbitMQ ConnectionFactory bean");
        return connectionFactory;
    }
}
