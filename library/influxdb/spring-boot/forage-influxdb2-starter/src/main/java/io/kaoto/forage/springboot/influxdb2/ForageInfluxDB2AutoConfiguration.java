package io.kaoto.forage.springboot.influxdb2;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import io.kaoto.forage.core.annotations.FactoryType;
import io.kaoto.forage.core.annotations.FactoryVariant;
import io.kaoto.forage.core.annotations.ForageFactory;
import io.kaoto.forage.influxdb2.InfluxDB2Config;
import io.kaoto.forage.influxdb2.InfluxDB2ModuleDescriptor;
import io.kaoto.forage.influxdb2.InfluxDB2Provider;
import io.kaoto.forage.springboot.common.ForageSpringBootModuleAdapter;
import com.influxdb.client.InfluxDBClient;

@ForageFactory(
        value = "InfluxDB 2 Client (Spring Boot)",
        components = {"camel-influxdb2"},
        description = "InfluxDB 2 clients for Spring Boot",
        type = FactoryType.INFLUXDB2_CLIENT,
        autowired = true,
        configClass = InfluxDB2Config.class,
        variant = FactoryVariant.SPRING_BOOT)
@AutoConfiguration
public class ForageInfluxDB2AutoConfiguration {
    @Bean
    static ForageSpringBootModuleAdapter<InfluxDB2Config, InfluxDB2Provider> forageInfluxDB2ModuleAdapter(
            Environment environment) {
        return new ForageSpringBootModuleAdapter<>(new InfluxDB2ModuleDescriptor(), environment);
    }

    @Bean(name = "influxdb2", destroyMethod = "close")
    @ConditionalOnMissingBean(name = "influxdb2")
    @ConditionalOnProperty(prefix = "forage.influxdb2", name = "url")
    InfluxDBClient forageInfluxDB2Client(ForageSpringBootModuleAdapter<InfluxDB2Config, InfluxDB2Provider> adapter) {
        return new InfluxDB2Provider().create(null);
    }
}
