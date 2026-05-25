package io.kaoto.forage.springboot.messaging.springrabbitmq.health;

import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.actuate.health.HealthContributor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.rabbitmq.client.ConnectionFactory;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link ForageRabbitHealthContributorAutoConfiguration}.
 */
class ForageRabbitHealthContributorAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ForageRabbitHealthContributorAutoConfiguration.class));

    @Test
    void healthIndicatorIsCreatedWhenRabbitTemplateExists() {
        this.contextRunner
                .withUserConfiguration(RabbitTemplateConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(HealthContributor.class);
                    assertThat(context).hasBean("rabbitHealthContributor");
                });
    }

    @Test
    void healthIndicatorIsNotCreatedWhenRabbitTemplateIsMissing() {
        this.contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(HealthContributor.class);
            assertThat(context).doesNotHaveBean("rabbitHealthContributor");
        });
    }

    @Test
    void healthIndicatorIsNotCreatedWhenDisabled() {
        this.contextRunner
                .withUserConfiguration(RabbitTemplateConfiguration.class)
                .withPropertyValues("management.health.rabbit.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(HealthContributor.class);
                    assertThat(context).doesNotHaveBean("rabbitHealthContributor");
                });
    }

    @Test
    void existingHealthIndicatorIsNotReplaced() {
        this.contextRunner
                .withUserConfiguration(RabbitTemplateConfiguration.class, CustomHealthIndicatorConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(HealthContributor.class);
                    assertThat(context).hasBean("rabbitHealthContributor");
                    assertThat(context.getBean("rabbitHealthContributor")).isInstanceOf(CustomHealthContributor.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class RabbitTemplateConfiguration {
        @Bean
        RabbitTemplate rabbitTemplate() {
            ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
            CachingConnectionFactory cachingConnectionFactory = new CachingConnectionFactory(connectionFactory);
            return new RabbitTemplate(cachingConnectionFactory);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomHealthIndicatorConfiguration {
        @Bean
        HealthContributor rabbitHealthContributor() {
            return new CustomHealthContributor();
        }
    }

    static class CustomHealthContributor implements HealthContributor {}
}
