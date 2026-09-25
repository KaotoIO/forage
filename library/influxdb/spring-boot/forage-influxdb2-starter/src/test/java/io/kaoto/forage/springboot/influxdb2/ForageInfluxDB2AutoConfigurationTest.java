package io.kaoto.forage.springboot.influxdb2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.influxdb2.InfluxDB2Provider;
import io.kaoto.forage.springboot.common.ForageEnvironmentPostProcessor;
import io.kaoto.forage.springboot.common.SpringConfigResolver;
import com.influxdb.client.InfluxDBClient;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ForageInfluxDB2AutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> new ForageEnvironmentPostProcessor()
                    .postProcessEnvironment(context.getEnvironment(), new SpringApplication()))
            .withConfiguration(AutoConfigurations.of(ForageInfluxDB2AutoConfiguration.class));

    @AfterEach
    void reset() {
        ConfigStore.getInstance().unregisterResolver(SpringConfigResolver.class);
        ConfigStore.getInstance().reload();
    }

    @Test
    void staysInactiveWithoutProperties() {
        runner.run(context -> assertThat(context).doesNotHaveBean(InfluxDBClient.class));
    }

    @Test
    void createsDefaultClientAndClosesIt() {
        InfluxDBClient client = mock(InfluxDBClient.class);
        try (MockedConstruction<InfluxDB2Provider> providers = mockConstruction(
                InfluxDB2Provider.class, (provider, ignored) -> when(provider.create(nullable(String.class)))
                        .thenReturn(client))) {
            runner.withPropertyValues("forage.influxdb2.url=http://localhost:8086")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context.getBean("influxdb2")).isSameAs(client);
                    });
            verify(client).close();
        }
    }

    @Test
    void createsNamedClientsAndClosesBoth() {
        InfluxDBClient alpha = mock(InfluxDBClient.class);
        InfluxDBClient beta = mock(InfluxDBClient.class);
        try (MockedConstruction<InfluxDB2Provider> providers =
                mockConstruction(InfluxDB2Provider.class, (provider, ignored) -> {
                    when(provider.create("alpha")).thenReturn(alpha);
                    when(provider.create("beta")).thenReturn(beta);
                })) {
            runner.withPropertyValues(
                            "forage.alpha.influxdb2.url=http://alpha:8086",
                            "forage.beta.influxdb2.url=http://beta:8086")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context.getBean("alpha")).isSameAs(alpha);
                        assertThat(context.getBean("beta")).isSameAs(beta);
                        assertThat(context.getBean("influxdb2")).isSameAs(alpha);
                    });
            verify(alpha).close();
            verify(beta).close();
        }
    }

    @Test
    void preservesUserBean() {
        InfluxDBClient external = mock(InfluxDBClient.class);
        runner.withBean("influxdb2", InfluxDBClient.class, () -> external)
                .withPropertyValues("forage.influxdb2.url=http://localhost:8086")
                .run(context -> assertThat(context.getBean("influxdb2")).isSameAs(external));
    }

    @Test
    void readsApplicationPropertiesThroughForageBridge() {
        runner.withPropertyValues("forage.influxdb2.url=http://localhost:8086", "forage.influxdb2.token=test-token")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(InfluxDBClient.class);
                });
    }
}
