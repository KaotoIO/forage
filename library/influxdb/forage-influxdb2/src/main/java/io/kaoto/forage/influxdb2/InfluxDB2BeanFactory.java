package io.kaoto.forage.influxdb2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.camel.CamelContext;
import org.apache.camel.component.influxdb2.InfluxDb2Component;
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

    @Override
    public void configure() {
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
    }

    @Override
    public void cleanup() {
        // Routes may still hold these clients during reload. Release them only at shutdown.
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
    }

    @Override
    public void stop() {
        cleanup();
        retiredClients.forEach(this::closeClient);
        retiredClients.clear();
    }

    private void closeClient(InfluxDBClient client) {
        try {
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
