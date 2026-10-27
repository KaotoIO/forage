package io.kaoto.forage.quarkus.influxdb;

import io.kaoto.forage.core.common.ForageModuleDescriptor;
import io.kaoto.forage.core.common.ForageQuarkusConfigSourceAdapter;
import io.kaoto.forage.influxdb.InfluxDBConfig;
import io.kaoto.forage.influxdb.InfluxDBModuleDescriptor;

public class ForageInfluxDBConfigSourceFactory extends ForageQuarkusConfigSourceAdapter<InfluxDBConfig> {
    @Override
    protected ForageModuleDescriptor<InfluxDBConfig, ?> descriptor() {
        return new InfluxDBModuleDescriptor();
    }
}
