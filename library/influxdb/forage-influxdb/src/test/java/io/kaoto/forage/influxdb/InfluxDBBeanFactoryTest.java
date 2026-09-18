package io.kaoto.forage.influxdb;

import java.util.ServiceLoader;
import org.apache.camel.component.influxdb.InfluxDbComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.influxdb.InfluxDB;
import io.kaoto.forage.core.common.BeanFactory;
import io.kaoto.forage.core.util.config.ConfigStore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InfluxDBBeanFactoryTest {
    private final DefaultCamelContext context = new DefaultCamelContext();
    private final InfluxDBBeanFactory factory = new InfluxDBBeanFactory();

    InfluxDBBeanFactoryTest() {
        factory.setCamelContext(context);
    }

    @AfterEach
    void reset() {
        factory.stop();
        context.stop();
        System.clearProperty("forage.influxdb.url");
        System.clearProperty("forage.alpha.influxdb.url");
        System.clearProperty("forage.beta.influxdb.url");
        ConfigStore.getInstance().reload();
    }

    @Test
    void discoversFactoryAndProvider() {
        assertThat(ServiceLoader.load(BeanFactory.class).stream().map(ServiceLoader.Provider::type))
                .contains(InfluxDBBeanFactory.class);
        assertThat(ServiceLoader.load(InfluxDBProvider.class).findFirst()).isPresent();
    }

    @Test
    void staysInactiveWithoutConfiguration() {
        factory.configure();
        assertThat(context.getRegistry().findByType(InfluxDB.class)).isEmpty();
    }

    @Test
    void createsDefaultOnceAndClosesAtShutdown() {
        System.setProperty("forage.influxdb.url", "http://localhost:8086");
        InfluxDB client = mock(InfluxDB.class);
        try (MockedConstruction<InfluxDBProvider> providers = mockConstruction(
                InfluxDBProvider.class, (provider, ignored) -> when(provider.create(nullable(String.class)))
                        .thenReturn(client))) {
            factory.configure();
            factory.configure();
            assertThat(providers.constructed()).hasSize(1);
            assertThat(context.getRegistry().lookupByName("influxdb")).isSameAs(client);
            factory.stop();
            factory.stop();
            verify(client).close();
            assertThat(context.getRegistry().lookupByName("influxdb")).isNull();
        }
    }

    @Test
    void createsDistinctNamedClientsAndKeepsRetiredClientsAliveUntilShutdown() {
        System.setProperty("forage.alpha.influxdb.url", "http://alpha:8086");
        System.setProperty("forage.beta.influxdb.url", "http://beta:8086");
        InfluxDB alpha = mock(InfluxDB.class);
        InfluxDB beta = mock(InfluxDB.class);
        try (MockedConstruction<InfluxDBProvider> providers =
                mockConstruction(InfluxDBProvider.class, (provider, ignored) -> {
                    when(provider.create("alpha")).thenReturn(alpha);
                    when(provider.create("beta")).thenReturn(beta);
                })) {
            factory.configure();
            assertThat(context.getRegistry().lookupByName("alpha")).isSameAs(alpha);
            assertThat(context.getRegistry().lookupByName("beta")).isSameAs(beta);
            InfluxDbComponent component = new InfluxDbComponent();
            component.setInfluxDB(alpha);
            context.addComponent("influxdb", component);
            factory.cleanup();
            assertThat(context.getRegistry().lookupByName("alpha")).isNull();
            assertThat(context.getRegistry().lookupByName("beta")).isNull();
            assertThat(component.getInfluxDB()).isNull();
            verify(alpha, never()).close();
            verify(beta, never()).close();
            factory.stop();
            verify(alpha).close();
            verify(beta).close();
        }
    }

    @Test
    void preservesExternalClientAndRegistryReplacement() {
        System.setProperty("forage.influxdb.url", "http://localhost:8086");
        InfluxDB external = mock(InfluxDB.class);
        context.getRegistry().bind("influxdb", external);
        factory.configure();
        factory.cleanup();
        factory.stop();
        assertThat(context.getRegistry().lookupByName("influxdb")).isSameAs(external);
        verify(external, never()).close();
    }

    @Test
    void rejectsNameCollisionWithoutReplacingIt() {
        System.setProperty("forage.influxdb.url", "http://localhost:8086");
        context.getRegistry().bind("influxdb", "other bean");
        assertThatThrownBy(factory::configure).isInstanceOf(IllegalStateException.class);
        assertThat(context.getRegistry().lookupByName("influxdb")).isEqualTo("other bean");
    }

    @Test
    void closesPendingClientsWhenAnotherConfigurationFails() {
        System.setProperty("forage.alpha.influxdb.url", "http://alpha:8086");
        System.setProperty("forage.beta.influxdb.url", "http://beta:8086");
        InfluxDB alpha = mock(InfluxDB.class);
        try (MockedConstruction<InfluxDBProvider> providers =
                mockConstruction(InfluxDBProvider.class, (provider, ignored) -> {
                    when(provider.create("alpha")).thenReturn(alpha);
                    when(provider.create("beta")).thenThrow(new IllegalArgumentException("Invalid beta configuration"));
                })) {
            assertThatThrownBy(factory::configure).isInstanceOf(IllegalArgumentException.class);
            verify(alpha).close();
            assertThat(context.getRegistry().findByType(InfluxDB.class)).isEmpty();
        }
    }
}
