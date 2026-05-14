package io.kaoto.forage.messaging.spring.rabbitmq;

import java.util.Set;
import org.apache.camel.CamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import io.kaoto.forage.core.annotations.FactoryType;
import io.kaoto.forage.core.annotations.ForageFactory;
import io.kaoto.forage.core.common.BeanFactory;
import io.kaoto.forage.core.util.config.ConfigHelper;
import io.kaoto.forage.core.util.config.ConfigStore;

@ForageFactory(
        value = "Spring RabbitMQ Connection",
        components = {"camel-spring-rabbitmq"},
        description = "Creates Spring RabbitMQ CachingConnectionFactory beans for AMQP messaging",
        type = FactoryType.SPRING_RABBITMQ_CONNECTION_FACTORY,
        autowired = true,
        configClass = SpringRabbitMQConfig.class)
public class SpringRabbitMQBeanFactory implements BeanFactory {
    private static final Logger LOG = LoggerFactory.getLogger(SpringRabbitMQBeanFactory.class);

    private CamelContext camelContext;
    private static final String DEFAULT_BEAN_NAME = "rabbitConnectionFactory";

    @Override
    public void cleanup() {
        SpringRabbitMQConfig config = new SpringRabbitMQConfig();
        Set<String> prefixes =
                ConfigStore.getInstance().readPrefixes(config, ConfigHelper.getNamedPropertyRegexp("spring.rabbitmq"));

        for (String name : prefixes) {
            camelContext.getRegistry().unbind(name);
        }
        camelContext.getRegistry().unbind(DEFAULT_BEAN_NAME);
    }

    @Override
    public void configure() {
        SpringRabbitMQConfig config = new SpringRabbitMQConfig();
        Set<String> prefixes =
                ConfigStore.getInstance().readPrefixes(config, ConfigHelper.getNamedPropertyRegexp("spring.rabbitmq"));

        if (!prefixes.isEmpty()) {
            for (String name : prefixes) {
                if (camelContext
                                .getRegistry()
                                .lookupByNameAndType(
                                        name, org.springframework.amqp.rabbit.connection.ConnectionFactory.class)
                        == null) {
                    try {
                        SpringRabbitMQConfig namedConfig = new SpringRabbitMQConfig(name);
                        CachingConnectionFactory connectionFactory = createConnectionFactory(namedConfig);
                        camelContext.getRegistry().bind(name, connectionFactory);
                    } catch (Exception e) {
                        LOG.error("Failed to create RabbitMQ connection factory for: {}", name, e);
                    }
                }
            }
        } else {
            try {
                if (camelContext
                                .getRegistry()
                                .lookupByNameAndType(
                                        DEFAULT_BEAN_NAME,
                                        org.springframework.amqp.rabbit.connection.ConnectionFactory.class)
                        == null) {
                    CachingConnectionFactory connectionFactory = createConnectionFactory(config);
                    camelContext.getRegistry().bind(DEFAULT_BEAN_NAME, connectionFactory);
                }
            } catch (Exception e) {
                LOG.error(e.getMessage(), e);
            }
        }
    }

    private CachingConnectionFactory createConnectionFactory(SpringRabbitMQConfig config) {
        LOG.info(
                "Creating CachingConnectionFactory - Host: {}, Port: {}, Username: {}, VirtualHost: {}, "
                        + "ChannelCacheSize: {}, CacheMode: {}",
                config.host(),
                config.port(),
                config.username(),
                config.virtualHost(),
                config.channelCacheSize(),
                config.cacheMode());

        com.rabbitmq.client.ConnectionFactory rabbitConnectionFactory = new com.rabbitmq.client.ConnectionFactory();
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
            cachingConnectionFactory.setCacheMode(CachingConnectionFactory.CacheMode.CHANNEL);
        }

        LOG.info("CachingConnectionFactory created successfully");
        return cachingConnectionFactory;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }
}
