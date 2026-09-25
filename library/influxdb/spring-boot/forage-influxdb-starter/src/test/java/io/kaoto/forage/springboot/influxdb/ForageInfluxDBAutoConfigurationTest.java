package io.kaoto.forage.springboot.influxdb;

import org.influxdb.InfluxDB;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.influxdb.InfluxDBProvider;
import io.kaoto.forage.springboot.common.ForageEnvironmentPostProcessor;
import io.kaoto.forage.springboot.common.SpringConfigResolver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ForageInfluxDBAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> new ForageEnvironmentPostProcessor()
                    .postProcessEnvironment(context.getEnvironment(), new SpringApplication()))
            .withConfiguration(AutoConfigurations.of(ForageInfluxDBAutoConfiguration.class));

    @AfterEach
    void reset() {
        ConfigStore.getInstance().unregisterResolver(SpringConfigResolver.class);
        ConfigStore.getInstance().reload();
    }

    @Test
    void staysInactiveWithoutProperties() {
        runner.run(context -> assertThat(context).doesNotHaveBean(InfluxDB.class));
    }

    @Test
    void createsDefaultClientAndClosesIt() {
        InfluxDB client = mock(InfluxDB.class);
        try (MockedConstruction<InfluxDBProvider> providers = mockConstruction(
                InfluxDBProvider.class, (provider, ignored) -> when(provider.create(nullable(String.class)))
                        .thenReturn(client))) {
            runner.withPropertyValues("forage.influxdb.url=http://localhost:8086")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context.getBean("influxdb")).isSameAs(client);
                    });
            verify(client).close();
        }
    }

    @Test
    void createsNamedClientsAndClosesBoth() {
        InfluxDB alpha = mock(InfluxDB.class);
        InfluxDB beta = mock(InfluxDB.class);
        try (MockedConstruction<InfluxDBProvider> providers =
                mockConstruction(InfluxDBProvider.class, (provider, ignored) -> {
                    when(provider.create("alpha")).thenReturn(alpha);
                    when(provider.create("beta")).thenReturn(beta);
                })) {
            runner.withPropertyValues(
                            "forage.alpha.influxdb.url=http://alpha:8086", "forage.beta.influxdb.url=http://beta:8086")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context.getBean("alpha")).isSameAs(alpha);
                        assertThat(context.getBean("beta")).isSameAs(beta);
                        assertThat(context.getBean("influxdb")).isSameAs(alpha);
                    });
            verify(alpha).close();
            verify(beta).close();
        }
    }

    @Test
    void preservesUserBean() {
        InfluxDB external = mock(InfluxDB.class);
        runner.withBean("influxdb", InfluxDB.class, () -> external)
                .withPropertyValues("forage.influxdb.url=http://localhost:8086")
                .run(context -> assertThat(context.getBean("influxdb")).isSameAs(external));
    }

    @Test
    void readsApplicationPropertiesThroughForageBridge() {
        runner.withPropertyValues("forage.influxdb.url=http://localhost:8086").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(InfluxDB.class);
        });
    }
}
