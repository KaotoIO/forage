package io.kaoto.forage.influxdb2;

import java.util.List;
import java.util.ServiceLoader;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.influxdb2.InfluxDb2Component;
import org.apache.camel.impl.DefaultCamelContext;
import io.kaoto.forage.core.common.BeanFactory;
import io.kaoto.forage.core.util.config.ConfigStore;
import com.influxdb.client.BucketsQuery;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.OrganizationsQuery;
import com.influxdb.client.domain.Bucket;
import com.influxdb.client.domain.Organization;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InfluxDB2BeanFactoryTest {
    private final DefaultCamelContext context = new DefaultCamelContext();
    private final InfluxDB2BeanFactory factory = new InfluxDB2BeanFactory();

    InfluxDB2BeanFactoryTest() {
        factory.setCamelContext(context);
    }

    @AfterEach
    void reset() {
        factory.stop();
        context.stop();
        System.clearProperty("forage.influxdb2.url");
        System.clearProperty("forage.alpha.influxdb2.url");
        System.clearProperty("forage.beta.influxdb2.url");
        ConfigStore.getInstance().reload();
    }

    @Test
    void discoversFactoryAndProvider() {
        assertThat(ServiceLoader.load(BeanFactory.class).stream().map(ServiceLoader.Provider::type))
                .contains(InfluxDB2BeanFactory.class);
        assertThat(ServiceLoader.load(InfluxDB2Provider.class).findFirst()).isPresent();
    }

    @Test
    void staysInactiveWithoutConfiguration() {
        factory.configure();
        assertThat(context.getRegistry().findByType(InfluxDBClient.class)).isEmpty();
    }

    @Test
    void createsDefaultOnceAndClosesAtShutdown() {
        System.setProperty("forage.influxdb2.url", "http://localhost:8086");
        InfluxDBClient client = mock(InfluxDBClient.class);
        try (MockedConstruction<InfluxDB2Provider> providers = mockConstruction(
                InfluxDB2Provider.class, (provider, ignored) -> when(provider.create(nullable(String.class)))
                        .thenReturn(client))) {
            factory.configure();
            factory.configure();
            assertThat(providers.constructed()).hasSize(1);
            assertThat(context.getRegistry().lookupByName("influxdb2")).isSameAs(client);
            factory.stop();
            factory.stop();
            verify(client).close();
            assertThat(context.getRegistry().lookupByName("influxdb2")).isNull();
        }
    }

    @Test
    void createsDistinctNamedClientsAndClosesUnusedClientsDuringCleanup() {
        System.setProperty("forage.alpha.influxdb2.url", "http://alpha:8086");
        System.setProperty("forage.beta.influxdb2.url", "http://beta:8086");
        InfluxDBClient alpha = mock(InfluxDBClient.class);
        InfluxDBClient beta = mock(InfluxDBClient.class);
        try (MockedConstruction<InfluxDB2Provider> providers =
                mockConstruction(InfluxDB2Provider.class, (provider, ignored) -> {
                    when(provider.create("alpha")).thenReturn(alpha);
                    when(provider.create("beta")).thenReturn(beta);
                })) {
            factory.configure();
            assertThat(context.getRegistry().lookupByName("alpha")).isSameAs(alpha);
            assertThat(context.getRegistry().lookupByName("beta")).isSameAs(beta);
            InfluxDb2Component component = new InfluxDb2Component();
            component.setInfluxDBClient(alpha);
            context.addComponent("influxdb2", component);
            factory.cleanup();
            assertThat(context.getRegistry().lookupByName("alpha")).isNull();
            assertThat(context.getRegistry().lookupByName("beta")).isNull();
            assertThat(component.getInfluxDBClient()).isNull();
            verify(alpha).close();
            verify(beta).close();
            factory.cleanup();
            factory.stop();
            verify(alpha).close();
            verify(beta).close();
        }
    }

    @Test
    void releasesEachGenerationAfterAllRoutesAreRemoved() throws Exception {
        System.setProperty("forage.influxdb2.url", "http://localhost:8086");
        List<InfluxDBClient> generations = List.of(
                mock(InfluxDBClient.class, RETURNS_DEEP_STUBS),
                mock(InfluxDBClient.class, RETURNS_DEEP_STUBS),
                mock(InfluxDBClient.class, RETURNS_DEEP_STUBS));
        Organization organization = mock(Organization.class);
        when(organization.getId()).thenReturn("org-id");
        when(organization.getName()).thenReturn("acme");
        for (InfluxDBClient client : generations) {
            when(client.getOrganizationsApi().findOrganizations(any(OrganizationsQuery.class)))
                    .thenReturn(List.of(organization));
            when(client.getBucketsApi().findBuckets(any(BucketsQuery.class)))
                    .thenReturn(List.of(new Bucket().name("metrics")));
        }
        String endpointUri = "influxdb2:influxdb2?org=acme&bucket=metrics&autoCreateOrg=false&autoCreateBucket=false";
        try (MockedConstruction<InfluxDB2Provider> providers = mockConstruction(
                InfluxDB2Provider.class, (provider, construction) -> when(provider.create(nullable(String.class)))
                        .thenReturn(generations.get(construction.getCount() - 1)))) {
            factory.configure();
            context.start();
            for (int cycle = 0; cycle < 2; cycle++) {
                InfluxDBClient oldClient = generations.get(cycle);
                context.addRoutes(new RouteBuilder() {
                    @Override
                    public void configure() {
                        from("direct:static").routeId("static").to(endpointUri);
                        from("direct:dynamic").routeId("dynamic").toD("${header.destination}");
                    }
                });
                var oldEndpoint = context.getEndpoint(endpointUri);
                try (var producer = context.createProducerTemplate()) {
                    producer.sendBodyAndHeader(
                            "direct:dynamic",
                            com.influxdb.client.write.Point.measurement("temperature")
                                    .addField("value", 21),
                            "destination",
                            endpointUri);
                    factory.cleanup();
                    factory.cleanup();
                    factory.configure();
                    verify(oldClient, never()).close();
                    context.getRouteController().stopRoute("static");
                    context.removeRoute("static");
                    // A surviving dynamic route may still hold a cached producer for the old client.
                    producer.sendBodyAndHeader(
                            "direct:dynamic",
                            com.influxdb.client.write.Point.measurement("temperature")
                                    .addField("value", 21),
                            "destination",
                            endpointUri);
                    verify(oldClient, never()).close();
                    context.getRouteController().stopRoute("dynamic");
                    verify(oldClient, never()).close();
                    context.removeRoute("dynamic");
                }
                verify(oldClient).close();
                verify(generations.get(cycle + 1), never()).close();
                assertThat(context.getEndpoints()).noneMatch(endpoint -> endpoint == oldEndpoint);
            }
            factory.stop();
            factory.stop();
            generations.forEach(client -> verify(client).close());
        }
    }

    @Test
    void preservesExternalClientAndRegistryReplacement() {
        System.setProperty("forage.influxdb2.url", "http://localhost:8086");
        InfluxDBClient external = mock(InfluxDBClient.class);
        context.getRegistry().bind("influxdb2", external);
        factory.configure();
        factory.cleanup();
        factory.stop();
        assertThat(context.getRegistry().lookupByName("influxdb2")).isSameAs(external);
        verify(external, never()).close();
    }

    @Test
    void rejectsNameCollisionWithoutReplacingIt() {
        System.setProperty("forage.influxdb2.url", "http://localhost:8086");
        context.getRegistry().bind("influxdb2", "other bean");
        assertThatThrownBy(factory::configure).isInstanceOf(IllegalStateException.class);
        assertThat(context.getRegistry().lookupByName("influxdb2")).isEqualTo("other bean");
    }

    @Test
    void closesPendingClientsWhenAnotherConfigurationFails() {
        System.setProperty("forage.alpha.influxdb2.url", "http://alpha:8086");
        System.setProperty("forage.beta.influxdb2.url", "http://beta:8086");
        InfluxDBClient alpha = mock(InfluxDBClient.class);
        try (MockedConstruction<InfluxDB2Provider> providers =
                mockConstruction(InfluxDB2Provider.class, (provider, ignored) -> {
                    when(provider.create("alpha")).thenReturn(alpha);
                    when(provider.create("beta")).thenThrow(new IllegalArgumentException("Invalid beta configuration"));
                })) {
            assertThatThrownBy(factory::configure).isInstanceOf(IllegalArgumentException.class);
            verify(alpha).close();
            assertThat(context.getRegistry().findByType(InfluxDBClient.class)).isEmpty();
        }
    }
}
