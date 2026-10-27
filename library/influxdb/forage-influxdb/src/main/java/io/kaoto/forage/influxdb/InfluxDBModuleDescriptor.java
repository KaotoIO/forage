package io.kaoto.forage.influxdb;

import org.influxdb.InfluxDB;
import io.kaoto.forage.core.common.ForageModuleDescriptor;

public class InfluxDBModuleDescriptor implements ForageModuleDescriptor<InfluxDBConfig, InfluxDBProvider> {
    @Override
    public String modulePrefix() {
        return "influxdb";
    }

    @Override
    public InfluxDBConfig createConfig(String prefix) {
        return new InfluxDBConfig(prefix);
    }

    @Override
    public Class<InfluxDBProvider> providerClass() {
        return InfluxDBProvider.class;
    }

    @Override
    public String resolveProviderClassName(InfluxDBConfig config) {
        return InfluxDBProvider.class.getName();
    }

    @Override
    public String defaultBeanName() {
        return "influxdb";
    }

    @Override
    public Class<?> primaryBeanClass() {
        return InfluxDB.class;
    }

    @Override
    public boolean transactionEnabled(InfluxDBConfig config) {
        return false;
    }

    @Override
    public String destroyMethodName(String prefix) {
        return "close";
    }
}
