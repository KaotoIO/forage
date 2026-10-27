package io.kaoto.forage.influxdb;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import io.kaoto.forage.core.annotations.ForageBean;
import io.kaoto.forage.core.common.BeanProvider;
import io.kaoto.forage.core.util.config.MissingConfigException;

@ForageBean(
        value = "influxdb",
        components = {"camel-influxdb"},
        description = "InfluxDB 1 client",
        configClass = InfluxDBConfig.class)
public class InfluxDBProvider implements BeanProvider<InfluxDB> {
    @Override
    public InfluxDB create(String id) {
        InfluxDBConfig config = new InfluxDBConfig(id);
        String url = config.url();
        String username = config.username();
        String password = config.password();
        if (username == null && password == null) {
            return InfluxDBFactory.connect(url);
        }
        if (username == null || password == null) {
            throw new MissingConfigException("InfluxDB 1 username and password must be configured together");
        }
        return InfluxDBFactory.connect(url, username, password);
    }
}
