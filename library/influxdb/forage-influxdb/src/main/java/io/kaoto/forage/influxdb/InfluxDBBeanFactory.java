package io.kaoto.forage.influxdb;

import org.apache.camel.Endpoint;
import org.apache.camel.component.influxdb.InfluxDbComponent;
import org.apache.camel.component.influxdb.InfluxDbEndpoint;
import org.influxdb.InfluxDB;
import io.kaoto.forage.core.annotations.FactoryType;
import io.kaoto.forage.core.annotations.ForageFactory;
import io.kaoto.forage.core.common.AbstractClientBeanFactory;

@ForageFactory(
        value = "InfluxDB 1 Client",
        components = {"camel-influxdb"},
        description = "Creates InfluxDB 1 client beans",
        type = FactoryType.INFLUXDB_CLIENT,
        autowired = true,
        configClass = InfluxDBConfig.class)
public class InfluxDBBeanFactory extends AbstractClientBeanFactory<InfluxDB> {
    public InfluxDBBeanFactory() {
        super(new InfluxDBModuleDescriptor());
    }

    @Override
    protected InfluxDB createClient(String prefix) {
        return new InfluxDBProvider().create(prefix);
    }

    @Override
    protected void clearComponentClient(InfluxDB client) {
        if (getCamelContext().hasComponent("influxdb") instanceof InfluxDbComponent component
                && component.getInfluxDB() == client) {
            component.setInfluxDB(null);
        }
    }

    @Override
    protected boolean usesClient(Endpoint endpoint, InfluxDB client) {
        return endpoint instanceof InfluxDbEndpoint typed && typed.getInfluxDB() == client;
    }
}
