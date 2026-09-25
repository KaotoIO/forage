package io.kaoto.forage.quarkus.influxdb.deployment;

import java.util.HashSet;
import java.util.Set;
import org.apache.camel.quarkus.core.deployment.spi.CamelRuntimeBeanBuildItem;
import org.influxdb.InfluxDB;
import io.kaoto.forage.core.annotations.FactoryType;
import io.kaoto.forage.core.annotations.FactoryVariant;
import io.kaoto.forage.core.annotations.ForageFactory;
import io.kaoto.forage.core.common.ForageQuarkusConfigSourceAdapter;
import io.kaoto.forage.core.util.config.ConfigHelper;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.influxdb.InfluxDBConfig;
import io.kaoto.forage.quarkus.influxdb.ForageInfluxDBRecorder;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;

@ForageFactory(
        value = "InfluxDB 1 Client (Quarkus)",
        components = {"camel-influxdb"},
        description = "InfluxDB 1 clients for Quarkus",
        type = FactoryType.INFLUXDB_CLIENT,
        autowired = true,
        configClass = InfluxDBConfig.class,
        variant = FactoryVariant.QUARKUS,
        runtimeDependencies = {"mvn:org.apache.camel.quarkus:camel-quarkus-influxdb"})
public class ForageInfluxDBProcessor {
    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem("forage-influxdb");
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void registerClients(
            ForageInfluxDBRecorder recorder,
            ShutdownContextBuildItem shutdown,
            BuildProducer<CamelRuntimeBeanBuildItem> beans) {
        InfluxDBConfig config = new InfluxDBConfig();
        Set<String> prefixes = new HashSet<>(
                ConfigStore.getInstance().readPrefixes(config, ConfigHelper.getNamedPropertyRegexp("influxdb")));
        prefixes.addAll(ForageQuarkusConfigSourceAdapter.getDiscoveredPrefixes("influxdb"));
        if (!prefixes.isEmpty()) {
            for (String prefix : prefixes.stream().sorted().toList()) {
                beans.produce(new CamelRuntimeBeanBuildItem(
                        prefix, InfluxDB.class.getName(), recorder.createClient(prefix, shutdown)));
            }
        } else if (!ConfigStore.getInstance()
                .readPrefixes(config, ConfigHelper.getDefaultPropertyRegexp("influxdb"))
                .isEmpty()) {
            beans.produce(new CamelRuntimeBeanBuildItem(
                    "influxdb", InfluxDB.class.getName(), recorder.createClient(null, shutdown)));
        }
    }
}
