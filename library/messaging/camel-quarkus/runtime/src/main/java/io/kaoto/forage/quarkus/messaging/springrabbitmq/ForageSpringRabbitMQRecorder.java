package io.kaoto.forage.quarkus.messaging.springrabbitmq;

import org.jboss.logging.Logger;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.messaging.spring.rabbitmq.common.SpringRabbitMQConfig;
import io.kaoto.forage.messaging.spring.rabbitmq.common.SpringRabbitMQConnectionFactoryHelper;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

/**
 * Recorder for creating Spring RabbitMQ CachingConnectionFactory beans at runtime.
 *
 * @since 1.4
 */
@Recorder
public class ForageSpringRabbitMQRecorder {
    private static final Logger LOG = Logger.getLogger(ForageSpringRabbitMQRecorder.class);

    public RuntimeValue<org.springframework.amqp.rabbit.connection.ConnectionFactory> createRabbitConnectionFactory(
            String id) {

        ConfigStore.getInstance().setClassLoader(Thread.currentThread().getContextClassLoader());
        SpringRabbitMQConfig config = id == null ? new SpringRabbitMQConfig() : new SpringRabbitMQConfig(id);

        LOG.debugf(
                "Creating CachingConnectionFactory (id=%s, port=%d, channelCacheSize=%d, cacheMode=%s)",
                id == null ? "<default>" : id, config.port(), config.channelCacheSize(), config.cacheMode());

        CachingConnectionFactory cachingConnectionFactory =
                SpringRabbitMQConnectionFactoryHelper.createCachingConnectionFactory(config);

        LOG.info("CachingConnectionFactory created successfully");
        return new RuntimeValue<>(cachingConnectionFactory);
    }
}
