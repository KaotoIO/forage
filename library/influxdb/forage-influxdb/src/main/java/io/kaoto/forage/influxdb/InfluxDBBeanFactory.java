package io.kaoto.forage.influxdb;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.camel.CamelContext;
import org.apache.camel.component.influxdb.InfluxDbComponent;
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

    @Override
    public void configure() {
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
    }

    @Override
    public void cleanup() {
        // Routes may still hold these clients during reload. Release them only at shutdown.
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
    }

    @Override
    public void stop() {
        cleanup();
        retiredClients.forEach(this::closeClient);
        retiredClients.clear();
    }

    private void closeClient(InfluxDB client) {
        try {
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
