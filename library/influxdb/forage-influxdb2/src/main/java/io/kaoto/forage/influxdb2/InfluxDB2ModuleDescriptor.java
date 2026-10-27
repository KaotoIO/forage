package io.kaoto.forage.influxdb2;

import io.kaoto.forage.core.common.ForageModuleDescriptor;
import com.influxdb.client.InfluxDBClient;

public class InfluxDB2ModuleDescriptor implements ForageModuleDescriptor<InfluxDB2Config, InfluxDB2Provider> {
    @Override
    public String modulePrefix() {
        return "influxdb2";
    }

    @Override
    public InfluxDB2Config createConfig(String prefix) {
        return new InfluxDB2Config(prefix);
    }

    @Override
    public Class<InfluxDB2Provider> providerClass() {
        return InfluxDB2Provider.class;
    }

    @Override
    public String resolveProviderClassName(InfluxDB2Config config) {
        return InfluxDB2Provider.class.getName();
    }

    @Override
    public String defaultBeanName() {
        return "influxdb2";
    }

    @Override
    public Class<?> primaryBeanClass() {
        return InfluxDBClient.class;
    }

    @Override
    public boolean transactionEnabled(InfluxDB2Config config) {
        return false;
    }

    @Override
    public String destroyMethodName(String prefix) {
        return "close";
    }
}
