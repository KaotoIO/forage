package io.kaoto.forage.springboot.messaging.springrabbitmq.health;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.actuate.amqp.RabbitHealthIndicator;
import org.springframework.boot.actuate.autoconfigure.health.CompositeHealthContributorConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.actuate.health.HealthContributor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import io.kaoto.forage.springboot.messaging.springrabbitmq.ForageSpringRabbitMQAutoConfiguration;

/**
 * Auto-configuration for Forage RabbitMQ health indicators.
 *
 * <p>Automatically creates health contributors for all {@link RabbitTemplate} beans
 * when Spring Boot Actuator is present and health indicators are enabled.
 *
 * <p><strong>Relationship with Spring Boot's default RabbitMQ health indicator:</strong>
 * This auto-configuration reuses Spring Boot's {@link RabbitHealthIndicator} implementation,
 * providing health checking for all Forage-managed {@link RabbitTemplate} beans. It uses
 * {@code @ConditionalOnMissingBean} to avoid conflicts with Spring Boot's default
 * auto-configuration - if Spring Boot's default health indicator is already present, this
 * configuration will back off.
 *
 * @since 1.4
 */
@AutoConfiguration(after = ForageSpringRabbitMQAutoConfiguration.class)
@ConditionalOnClass({RabbitHealthIndicator.class, RabbitTemplate.class})
@ConditionalOnBean(RabbitTemplate.class)
@ConditionalOnEnabledHealthIndicator("rabbit")
public class ForageRabbitHealthContributorAutoConfiguration
        extends CompositeHealthContributorConfiguration<RabbitHealthIndicator, RabbitTemplate> {

    public ForageRabbitHealthContributorAutoConfiguration() {
        super(RabbitHealthIndicator::new);
    }

    @Bean
    @ConditionalOnMissingBean(name = {"rabbitHealthIndicator", "rabbitHealthContributor"})
    public HealthContributor rabbitHealthContributor(ConfigurableListableBeanFactory beanFactory) {
        return createContributor(beanFactory, RabbitTemplate.class);
    }
}
