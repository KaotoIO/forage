package io.kaoto.forage.influxdb;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.camel.CamelContext;
import org.apache.camel.component.influxdb.InfluxDbComponent;
import org.apache.camel.component.influxdb.InfluxDbEndpoint;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.SimpleEventNotifierSupport;
import org.influxdb.InfluxDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.core.annotations.FactoryType;
import io.kaoto.forage.core.annotations.ForageFactory;
import io.kaoto.forage.core.common.BeanFactory;
import io.kaoto.forage.core.util.config.ConfigHelper;
import io.kaoto.forage.core.util.config.ConfigStore;

@ForageFactory(
        value = "InfluxDB 1 Client",
        components = {"camel-influxdb"},
        description = "Creates InfluxDB 1 client beans",
        type = FactoryType.INFLUXDB_CLIENT,
        autowired = true,
        configClass = InfluxDBConfig.class)
public class InfluxDBBeanFactory implements BeanFactory {
    private static final Logger LOG = LoggerFactory.getLogger(InfluxDBBeanFactory.class);
    private CamelContext camelContext;
    private final Map<String, InfluxDB> clients = new LinkedHashMap<>();
    private final List<InfluxDB> retiredClients = new ArrayList<>();

    private final SimpleEventNotifierSupport routeListener = new SimpleEventNotifierSupport() {
        @Override
        public boolean isEnabled(CamelEvent event) {
            return event instanceof CamelEvent.RouteRemovedEvent;
        }

        @Override
        public void notify(CamelEvent event) {
            synchronized (InfluxDBBeanFactory.this) {
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
        InfluxDBConfig config = new InfluxDBConfig();
        Set<String> prefixes =
                ConfigStore.getInstance().readPrefixes(config, ConfigHelper.getNamedPropertyRegexp("influxdb"));
        Map<String, String> names = new LinkedHashMap<>();
        if (!prefixes.isEmpty()) {
            prefixes.stream().sorted().forEach(name -> names.put(name, name));
        } else if (!ConfigStore.getInstance()
                .readPrefixes(config, ConfigHelper.getDefaultPropertyRegexp("influxdb"))
                .isEmpty()) {
            names.put("influxdb", null);
        }
        Map<String, InfluxDB> pending = new LinkedHashMap<>();
        try {
            for (Map.Entry<String, String> entry : names.entrySet()) {
                Object existing = camelContext.getRegistry().lookupByName(entry.getKey());
                if (existing == null) {
                    pending.put(entry.getKey(), new InfluxDBProvider().create(entry.getValue()));
                } else if (!(existing instanceof InfluxDB)) {
                    throw new IllegalStateException("Bean '" + entry.getKey() + "' is not an InfluxDB 1 client");
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
        InfluxDbComponent component =
                camelContext.hasComponent("influxdb") instanceof InfluxDbComponent typed ? typed : null;
        clients.forEach((name, client) -> {
            if (camelContext.getRegistry().lookupByName(name) == client) {
                camelContext.getRegistry().unbind(name);
            }
            if (component != null && component.getInfluxDB() == client) {
                component.setInfluxDB(null);
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

    private void closeClient(InfluxDB client) {
        try {
            // Prevent new routes from reusing cached endpoints backed by a closed client.
            for (var endpoint : camelContext.getEndpoints().stream()
                    .filter(candidate -> candidate instanceof InfluxDbEndpoint typed && typed.getInfluxDB() == client)
                    .toList()) {
                camelContext.removeEndpoint(endpoint);
            }
            client.close();
        } catch (Exception e) {
            LOG.warn("Failed to close InfluxDB 1 client", e);
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
