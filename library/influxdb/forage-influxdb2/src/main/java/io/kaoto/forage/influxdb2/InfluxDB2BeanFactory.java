package io.kaoto.forage.influxdb2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.camel.CamelContext;
import org.apache.camel.component.influxdb2.InfluxDb2Component;
import org.apache.camel.component.influxdb2.InfluxDb2Endpoint;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.SimpleEventNotifierSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.core.annotations.FactoryType;
import io.kaoto.forage.core.annotations.ForageFactory;
import io.kaoto.forage.core.common.BeanFactory;
import io.kaoto.forage.core.util.config.ConfigHelper;
import io.kaoto.forage.core.util.config.ConfigStore;
import com.influxdb.client.InfluxDBClient;

@ForageFactory(
        value = "InfluxDB 2 Client",
        components = {"camel-influxdb2"},
        description = "Creates InfluxDB 2 client beans",
        type = FactoryType.INFLUXDB2_CLIENT,
        autowired = true,
        configClass = InfluxDB2Config.class)
public class InfluxDB2BeanFactory implements BeanFactory {
    private static final Logger LOG = LoggerFactory.getLogger(InfluxDB2BeanFactory.class);
    private CamelContext camelContext;
    private final Map<String, InfluxDBClient> clients = new LinkedHashMap<>();
    private final List<InfluxDBClient> retiredClients = new ArrayList<>();

    private final SimpleEventNotifierSupport routeListener = new SimpleEventNotifierSupport() {
        @Override
        public boolean isEnabled(CamelEvent event) {
            return event instanceof CamelEvent.RouteRemovedEvent;
        }

        @Override
        public void notify(CamelEvent event) {
            synchronized (InfluxDB2BeanFactory.this) {
                // Route services have shut down; any remaining routes may still hold retired clients.
                CamelEvent.RouteRemovedEvent removed = (CamelEvent.RouteRemovedEvent) event;
                if (camelContext.getRoutes().stream().allMatch(route -> route == removed.getRoute())) {
                    closeRetiredClients();
                }
            }
        }
    };

    @Override
    public synchronized void configure() {
        InfluxDB2Config config = new InfluxDB2Config();
        Set<String> prefixes =
                ConfigStore.getInstance().readPrefixes(config, ConfigHelper.getNamedPropertyRegexp("influxdb2"));
        Map<String, String> names = new LinkedHashMap<>();
        if (!prefixes.isEmpty()) {
            prefixes.stream().sorted().forEach(name -> names.put(name, name));
        } else if (!ConfigStore.getInstance()
                .readPrefixes(config, ConfigHelper.getDefaultPropertyRegexp("influxdb2"))
                .isEmpty()) {
            names.put("influxdb2", null);
        }
        Map<String, InfluxDBClient> pending = new LinkedHashMap<>();
        try {
            for (Map.Entry<String, String> entry : names.entrySet()) {
                Object existing = camelContext.getRegistry().lookupByName(entry.getKey());
                if (existing == null) {
                    pending.put(entry.getKey(), new InfluxDB2Provider().create(entry.getValue()));
                } else if (!(existing instanceof InfluxDBClient)) {
                    throw new IllegalStateException("Bean '" + entry.getKey() + "' is not an InfluxDB 2 client");
                }
            }
        } catch (RuntimeException e) {
            pending.values().forEach(this::closeClient);
            throw e;
        }
        pending.forEach((name, client) -> camelContext.getRegistry().bind(name, client));
        clients.putAll(pending);
        if (!clients.isEmpty()
                && !camelContext.getManagementStrategy().getEventNotifiers().contains(routeListener)) {
            camelContext.getManagementStrategy().addEventNotifier(routeListener);
        }
    }

    @Override
    public synchronized void cleanup() {
        // Routes, including dynamic sends, may still hold clients until the old routes are removed.
        InfluxDb2Component component =
                camelContext.hasComponent("influxdb2") instanceof InfluxDb2Component typed ? typed : null;
        clients.forEach((name, client) -> {
            if (camelContext.getRegistry().lookupByName(name) == client) {
                camelContext.getRegistry().unbind(name);
            }
            if (component != null && component.getInfluxDBClient() == client) {
                component.setInfluxDBClient(null);
            }
            retiredClients.add(client);
        });
        clients.clear();
        if (camelContext.getRoutes().isEmpty()) {
            closeRetiredClients();
        }
    }

    @Override
    public synchronized void stop() {
        cleanup();
        closeRetiredClients();
        camelContext.getManagementStrategy().removeEventNotifier(routeListener);
    }

    private void closeRetiredClients() {
        retiredClients.forEach(this::closeClient);
        retiredClients.clear();
    }

    private void closeClient(InfluxDBClient client) {
        try {
            // Prevent new routes from reusing cached endpoints backed by a closed client.
            for (var endpoint : camelContext.getEndpoints().stream()
                    .filter(candidate ->
                            candidate instanceof InfluxDb2Endpoint typed && typed.getInfluxDBClient() == client)
                    .toList()) {
                camelContext.removeEndpoint(endpoint);
            }
            client.close();
        } catch (Exception e) {
            LOG.warn("Failed to close InfluxDB 2 client", e);
        }
    }

    @Override
    public void setCamelContext(CamelContext context) {
        this.camelContext = context;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }
}
