package io.kaoto.forage.springboot.messaging.springrabbitmq.metrics;

import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.rabbitmq.client.ConnectionFactory;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link ForageRabbitMetricsAutoConfiguration}.
 */
class ForageRabbitMetricsAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ForageRabbitMetricsAutoConfiguration.class));

    @Test
    void metricsPostProcessorIsCreatedWhenRequiredBeansExist() {
        this.contextRunner.withUserConfiguration(MetricsConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(ForageRabbitConnectionFactoryMetricsPostProcessor.class);
        });
    }

    @Test
    void metricsPostProcessorIsNotCreatedWhenMeterRegistryIsMissing() {
        this.contextRunner
                .withUserConfiguration(ConnectionFactoryOnlyConfiguration.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ForageRabbitConnectionFactoryMetricsPostProcessor.class);
                });
    }

    @Test
    void metricsPostProcessorIsNotCreatedWhenConnectionFactoryIsMissing() {
        this.contextRunner
                .withUserConfiguration(MeterRegistryOnlyConfiguration.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ForageRabbitConnectionFactoryMetricsPostProcessor.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class MetricsConfiguration {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        org.springframework.amqp.rabbit.connection.ConnectionFactory rabbitConnectionFactory() {
            ConnectionFactory rabbitConnectionFactory = mock(ConnectionFactory.class);
            CachingConnectionFactory cachingConnectionFactory = new CachingConnectionFactory(rabbitConnectionFactory);
            return cachingConnectionFactory;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ConnectionFactoryOnlyConfiguration {
        @Bean
        org.springframework.amqp.rabbit.connection.ConnectionFactory rabbitConnectionFactory() {
            ConnectionFactory rabbitConnectionFactory = mock(ConnectionFactory.class);
            CachingConnectionFactory cachingConnectionFactory = new CachingConnectionFactory(rabbitConnectionFactory);
            return cachingConnectionFactory;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MeterRegistryOnlyConfiguration {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
