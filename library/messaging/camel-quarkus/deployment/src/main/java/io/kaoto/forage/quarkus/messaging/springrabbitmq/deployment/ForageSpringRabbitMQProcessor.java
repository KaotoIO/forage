package io.kaoto.forage.quarkus.messaging.springrabbitmq.deployment;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.camel.quarkus.core.deployment.spi.CamelRegistryBuildItem;
import org.jboss.logging.Logger;
import io.kaoto.forage.core.annotations.FactoryType;
import io.kaoto.forage.core.annotations.FactoryVariant;
import io.kaoto.forage.core.annotations.ForageFactory;
import io.kaoto.forage.core.util.config.ConfigHelper;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.messaging.spring.rabbitmq.common.SpringRabbitMQConfig;
import io.kaoto.forage.messaging.spring.rabbitmq.common.SpringRabbitMQConstants;
import io.kaoto.forage.messaging.spring.rabbitmq.common.SpringRabbitMQModuleDescriptor;
import io.kaoto.forage.quarkus.messaging.springrabbitmq.ForageSpringRabbitMQRecorder;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;

/**
 * Quarkus deployment processor for Forage Spring RabbitMQ.
 *
 * <p>Discovers named and default RabbitMQ connection configurations at build time
 * and registers CachingConnectionFactory beans via the recorder.
 *
 * @since 1.4
 */
@ForageFactory(
        value = "Spring RabbitMQ Connection (Quarkus)",
        components = {"camel-spring-rabbitmq"},
        description = "Native Spring RabbitMQ CachingConnectionFactory for Quarkus with compile-time optimization",
        type = FactoryType.SPRING_RABBITMQ_CONNECTION_FACTORY,
        autowired = true,
        configClass = SpringRabbitMQConfig.class,
        variant = FactoryVariant.QUARKUS,
        runtimeDependencies = {"mvn:org.apache.camel.quarkus:camel-quarkus-spring-rabbitmq"})
public class ForageSpringRabbitMQProcessor {

    private static final Logger LOG = Logger.getLogger(ForageSpringRabbitMQProcessor.class);
    private static final String FEATURE = "forage-spring-rabbitmq";
    private static final SpringRabbitMQModuleDescriptor DESCRIPTOR = new SpringRabbitMQModuleDescriptor();

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(value = ExecutionTime.STATIC_INIT)
    void registerIbmMqConnectionFactory(
            ForageSpringRabbitMQRecorder recorder, CamelRegistryBuildItem camelRegistryBuildItem) {
        LOG.debug("ForageSpringRabbitMQProcessor.registerRabbitConnectionFactories() called at build time");
        SpringRabbitMQConfig defaultConfig = DESCRIPTOR.createConfig(null);
        Set<String> named = ConfigStore.getInstance()
                .readPrefixes(defaultConfig, ConfigHelper.getNamedPropertyRegexp(DESCRIPTOR.modulePrefix()));

        Map<String, SpringRabbitMQConfig> configs;
        if (!named.isEmpty()) {
            configs = named.stream().collect(Collectors.toMap(n -> n, DESCRIPTOR::createConfig));
        } else {
            // Check if default (unprefixed) properties exist before creating a default config
            Set<String> defaultPrefixes = ConfigStore.getInstance()
                    .readPrefixes(defaultConfig, ConfigHelper.getDefaultPropertyRegexp(DESCRIPTOR.modulePrefix()));
            if (!defaultPrefixes.isEmpty()) {
                configs = Collections.singletonMap((String) null, defaultConfig);
            } else {
                LOG.debug("No Forage Spring RabbitMQ configuration found, skipping ConnectionFactory discovery");
                return;
            }
        }

        for (Map.Entry<String, SpringRabbitMQConfig> entry : configs.entrySet()) {
            LOG.infof(
                    "Recording Spring RabbitMQ connection factory for bean: %s",
                    entry.getKey() == null ? SpringRabbitMQConstants.DEFAULT_BEAN_NAME : entry.getKey());

            // create connection factory
            recorder.createRabbitConnectionFactory(entry.getKey(), camelRegistryBuildItem.getRegistry());
        }
    }
}
