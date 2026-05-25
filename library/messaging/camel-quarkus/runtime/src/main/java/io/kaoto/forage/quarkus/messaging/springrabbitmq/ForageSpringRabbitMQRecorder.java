package io.kaoto.forage.quarkus.messaging.springrabbitmq;

import org.apache.camel.spi.Registry;
import org.jboss.logging.Logger;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import io.kaoto.forage.messaging.spring.rabbitmq.common.SpringRabbitMQConfig;
import io.kaoto.forage.messaging.spring.rabbitmq.common.SpringRabbitMQConnectionFactoryHelper;
import io.kaoto.forage.messaging.spring.rabbitmq.common.SpringRabbitMQConstants;
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

    public void createRabbitConnectionFactory(String id, RuntimeValue<Registry> registryRuntimeValue) {
        SpringRabbitMQConfig config = id == null ? new SpringRabbitMQConfig() : new SpringRabbitMQConfig(id);
        CachingConnectionFactory cachingConnectionFactory =
                SpringRabbitMQConnectionFactoryHelper.createCachingConnectionFactory(config);
        registryRuntimeValue
                .getValue()
                .bind(id == null ? SpringRabbitMQConstants.DEFAULT_BEAN_NAME : id, cachingConnectionFactory);
    }
}
