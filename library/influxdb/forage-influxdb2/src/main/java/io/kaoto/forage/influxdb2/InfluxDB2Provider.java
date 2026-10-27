package io.kaoto.forage.influxdb2;

import io.kaoto.forage.core.annotations.ForageBean;
import io.kaoto.forage.core.common.BeanProvider;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;

@ForageBean(
        value = "influxdb2",
        components = {"camel-influxdb2"},
        description = "InfluxDB 2 client",
        configClass = InfluxDB2Config.class)
public class InfluxDB2Provider implements BeanProvider<InfluxDBClient> {
    @Override
    public InfluxDBClient create(String id) {
        InfluxDB2Config config = new InfluxDB2Config(id);
        return InfluxDBClientFactory.create(config.url(), config.token().toCharArray());
    }
}
