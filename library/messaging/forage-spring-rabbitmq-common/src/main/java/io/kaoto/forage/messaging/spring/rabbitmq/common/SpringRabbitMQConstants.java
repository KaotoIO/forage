package io.kaoto.forage.messaging.spring.rabbitmq.common;

/**
 * Constants for Spring RabbitMQ module configuration.
 *
 * <p>Centralized constants for module prefix and default bean naming.
 *
 * @since 1.4
 */
public final class SpringRabbitMQConstants {

    private SpringRabbitMQConstants() {
        // Utility class
    }

    /** Module prefix used in property names: {@code "rabbitmq"} */
    public static final String MODULE_PREFIX = "rabbitmq";

    /** Default bean name for unprefixed configurations: {@code "rabbitConnectionFactory"} */
    public static final String DEFAULT_BEAN_NAME = "rabbitConnectionFactory";
}
