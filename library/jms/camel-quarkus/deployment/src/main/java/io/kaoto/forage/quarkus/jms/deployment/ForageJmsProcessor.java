package io.kaoto.forage.quarkus.jms.deployment;

import jakarta.jms.ConnectionFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.camel.quarkus.core.deployment.spi.CamelRuntimeBeanBuildItem;
import org.jboss.logging.Logger;
import io.kaoto.forage.core.annotations.FactoryType;
import io.kaoto.forage.core.annotations.FactoryVariant;
import io.kaoto.forage.core.annotations.ForageFactory;
import io.kaoto.forage.core.util.config.ConfigHelper;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.jms.common.ConnectionFactoryConfig;
import io.kaoto.forage.jms.common.JmsModuleDescriptor;
import io.kaoto.forage.quarkus.jms.ForageJmsRecorder;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.runtime.RuntimeValue;

@ForageFactory(
        value = "JMS Connection (Quarkus)",
        components = {"camel-jms"},
        description = "Native JMS ConnectionFactory for Quarkus with compile-time optimization",
        type = FactoryType.CONNECTION_FACTORY,
        autowired = true,
        configClass = ConnectionFactoryConfig.class,
        variant = FactoryVariant.QUARKUS,
        runtimeDependencies = {"mvn:org.apache.camel.quarkus:camel-quarkus-jms"})
public class ForageJmsProcessor {

    private static final Logger LOG = Logger.getLogger(ForageJmsProcessor.class);
    private static final String FEATURE = "forage-jms";
    private static final JmsModuleDescriptor DESCRIPTOR = new JmsModuleDescriptor();

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(value = ExecutionTime.STATIC_INIT)
    void registerIbmMqConnectionFactory(ForageJmsRecorder recorder, BuildProducer<CamelRuntimeBeanBuildItem> beans) {

        ConnectionFactoryConfig defaultConfig = DESCRIPTOR.createConfig(null);
        Set<String> named = ConfigStore.getInstance()
                .readPrefixes(defaultConfig, ConfigHelper.getNamedPropertyRegexp(DESCRIPTOR.modulePrefix()));

        Map<String, ConnectionFactoryConfig> configs = named.isEmpty()
                ? Collections.singletonMap((String) null, defaultConfig)
                : named.stream().collect(Collectors.toMap(n -> n, DESCRIPTOR::createConfig));

        for (Map.Entry<String, ConnectionFactoryConfig> entry : configs.entrySet()) {
            if ("ibmmq".equals(entry.getValue().jmsKind())) {
                LOG.info("Recording IBMMMQ connection factory for url: "
                        + entry.getValue().brokerUrl());
                // create connection factory
                RuntimeValue<ConnectionFactory> connectionFactory =
                        recorder.createIbmMQConnectionFactory(entry.getKey());
                if (connectionFactory != null) {
                    beans.produce(new CamelRuntimeBeanBuildItem(
                            Optional.ofNullable(entry.getValue().name()).orElse(entry.getKey()),
                            ConnectionFactory.class.getName(),
                            connectionFactory));
                }
            }
        }
    }
}
