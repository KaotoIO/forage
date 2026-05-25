package io.kaoto.forage.springboot.messaging.springrabbitmq.metrics;

import org.springframework.amqp.rabbit.connection.AbstractConnectionFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import io.kaoto.forage.springboot.messaging.springrabbitmq.ForageSpringRabbitMQAutoConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import com.rabbitmq.client.ConnectionFactory;

/**
 * Auto-configuration for metrics on all available RabbitMQ {@link ConnectionFactory connection factories}.
 *
 * <p>When Micrometer is present on the classpath, this configuration automatically
 * enables metrics collection for all Spring AMQP connection factories.
 *
 * @since 1.4
 */
@AutoConfiguration(after = ForageSpringRabbitMQAutoConfiguration.class)
@ConditionalOnClass({ConnectionFactory.class, AbstractConnectionFactory.class, MeterRegistry.class})
@ConditionalOnBean({org.springframework.amqp.rabbit.connection.ConnectionFactory.class, MeterRegistry.class})
public class ForageRabbitMetricsAutoConfiguration {

    @Bean
    static ForageRabbitConnectionFactoryMetricsPostProcessor forageRabbitConnectionFactoryMetricsPostProcessor(
            ApplicationContext applicationContext) {
        return new ForageRabbitConnectionFactoryMetricsPostProcessor(applicationContext);
    }
}
