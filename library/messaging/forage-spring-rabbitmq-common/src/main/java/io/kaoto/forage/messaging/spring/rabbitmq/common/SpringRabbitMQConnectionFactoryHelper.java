package io.kaoto.forage.messaging.spring.rabbitmq.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import com.rabbitmq.client.ConnectionFactory;

/**
 * Helper class for creating Spring RabbitMQ CachingConnectionFactory instances.
 * Centralizes the factory creation logic to avoid duplication between different runtime variants.
 *
 * @since 1.4
 */
public final class SpringRabbitMQConnectionFactoryHelper {
    private static final Logger LOG = LoggerFactory.getLogger(SpringRabbitMQConnectionFactoryHelper.class);

    private SpringRabbitMQConnectionFactoryHelper() {}

    /**
     * Creates a CachingConnectionFactory configured from the provided SpringRabbitMQConfig.
     *
     * @param config the configuration to use for creating the connection factory
     * @return a fully configured CachingConnectionFactory
     */
    public static CachingConnectionFactory createCachingConnectionFactory(SpringRabbitMQConfig config) {
        ConnectionFactory rabbitConnectionFactory = new ConnectionFactory();
        rabbitConnectionFactory.setHost(config.host());
        rabbitConnectionFactory.setPort(config.port());
        rabbitConnectionFactory.setUsername(config.username());
        rabbitConnectionFactory.setPassword(config.password());
        rabbitConnectionFactory.setVirtualHost(config.virtualHost());
        rabbitConnectionFactory.setRequestedHeartbeat(config.requestedHeartbeat());
        rabbitConnectionFactory.setConnectionTimeout(config.connectionTimeout());
        rabbitConnectionFactory.setAutomaticRecoveryEnabled(config.automaticRecoveryEnabled());
        rabbitConnectionFactory.setNetworkRecoveryInterval(config.networkRecoveryInterval());

        CachingConnectionFactory cachingConnectionFactory = new CachingConnectionFactory(rabbitConnectionFactory);
        cachingConnectionFactory.setChannelCacheSize(config.channelCacheSize());
        cachingConnectionFactory.setConnectionCacheSize(config.connectionCacheSize());
        cachingConnectionFactory.setChannelCheckoutTimeout(config.channelCheckoutTimeout());

        if (config.addresses() != null) {
            cachingConnectionFactory.setAddresses(config.addresses());
        }

        if ("CONNECTION".equalsIgnoreCase(config.cacheMode())) {
            cachingConnectionFactory.setCacheMode(CachingConnectionFactory.CacheMode.CONNECTION);
        } else {
            if (!"CHANNEL".equalsIgnoreCase(config.cacheMode())) {
                LOG.warn("Unknown cache mode '{}', defaulting to CHANNEL", config.cacheMode());
            }
            cachingConnectionFactory.setCacheMode(CachingConnectionFactory.CacheMode.CHANNEL);
        }

        return cachingConnectionFactory;
    }
}
