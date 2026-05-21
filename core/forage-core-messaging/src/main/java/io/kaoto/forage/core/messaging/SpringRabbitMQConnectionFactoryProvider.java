package io.kaoto.forage.core.messaging;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import io.kaoto.forage.core.common.BeanProvider;

/**
 * Service provider interface for creating Spring RabbitMQ ConnectionFactory instances.
 * Implementations provide broker-specific connection factory configuration.
 *
 * @since 1.4
 */
public interface SpringRabbitMQConnectionFactoryProvider extends BeanProvider<ConnectionFactory> {

    @Override
    default ConnectionFactory create() {
        return create(null);
    }

    @Override
    ConnectionFactory create(String id);
}
