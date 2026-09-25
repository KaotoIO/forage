package io.kaoto.forage.influxdb2;

import org.apache.camel.Endpoint;
import org.apache.camel.component.influxdb2.InfluxDb2Component;
import org.apache.camel.component.influxdb2.InfluxDb2Endpoint;
import io.kaoto.forage.core.annotations.FactoryType;
import io.kaoto.forage.core.annotations.ForageFactory;
import io.kaoto.forage.core.common.AbstractClientBeanFactory;
import com.influxdb.client.InfluxDBClient;

@ForageFactory(
        value = "InfluxDB 2 Client",
        components = {"camel-influxdb2"},
        description = "Creates InfluxDB 2 client beans",
        type = FactoryType.INFLUXDB2_CLIENT,
        autowired = true,
        configClass = InfluxDB2Config.class)
public class InfluxDB2BeanFactory extends AbstractClientBeanFactory<InfluxDBClient> {
    public InfluxDB2BeanFactory() {
        super(new InfluxDB2ModuleDescriptor());
    }

    @Override
    protected InfluxDBClient createClient(String prefix) {
        return new InfluxDB2Provider().create(prefix);
    }

    @Override
    protected void clearComponentClient(InfluxDBClient client) {
        if (getCamelContext().hasComponent("influxdb2") instanceof InfluxDb2Component component
                && component.getInfluxDBClient() == client) {
            component.setInfluxDBClient(null);
        }
    }

    @Override
    protected boolean usesClient(Endpoint endpoint, InfluxDBClient client) {
        return endpoint instanceof InfluxDb2Endpoint typed && typed.getInfluxDBClient() == client;
    }
}
