package io.kaoto.forage.springboot.influxdb;

import org.influxdb.InfluxDB;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import io.kaoto.forage.core.annotations.FactoryType;
import io.kaoto.forage.core.annotations.FactoryVariant;
import io.kaoto.forage.core.annotations.ForageFactory;
import io.kaoto.forage.influxdb.InfluxDBConfig;
import io.kaoto.forage.influxdb.InfluxDBModuleDescriptor;
import io.kaoto.forage.influxdb.InfluxDBProvider;
import io.kaoto.forage.springboot.common.ForageSpringBootModuleAdapter;

@ForageFactory(
        value = "InfluxDB 1 Client (Spring Boot)",
        components = {"camel-influxdb"},
        description = "InfluxDB 1 clients for Spring Boot",
        type = FactoryType.INFLUXDB_CLIENT,
        autowired = true,
        configClass = InfluxDBConfig.class,
        variant = FactoryVariant.SPRING_BOOT)
@AutoConfiguration
public class ForageInfluxDBAutoConfiguration {
    @Bean
    static ForageSpringBootModuleAdapter<InfluxDBConfig, InfluxDBProvider> forageInfluxDBModuleAdapter(
            Environment environment) {
        return new ForageSpringBootModuleAdapter<>(new InfluxDBModuleDescriptor(), environment);
    }

    @Bean(name = "influxdb", destroyMethod = "close")
    @ConditionalOnMissingBean(name = "influxdb")
    @ConditionalOnProperty(prefix = "forage.influxdb", name = "url")
    InfluxDB forageInfluxDBClient(ForageSpringBootModuleAdapter<InfluxDBConfig, InfluxDBProvider> adapter) {
        return new InfluxDBProvider().create(null);
    }
}
