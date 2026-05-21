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
import io.kaoto.forage.messaging.spring.rabbitmq.common.SpringRabbitMQConfig;
import io.kaoto.forage.messaging.spring.rabbitmq.common.SpringRabbitMQConnectionFactoryHelper;

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
            closeAndUnbind(name);
        }
        closeAndUnbind(DEFAULT_BEAN_NAME);
    }

    private void closeAndUnbind(String name) {
        // Note: we intentionally do NOT close AutoCloseable resources here.
        // Camel components cache references at the component level.
        // The old resource is unbound and will be GC'd after the component is reset and routes reloaded.
        camelContext.getRegistry().unbind(name);
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
                        throw new IllegalStateException("Failed to create RabbitMQ connection factory for: " + name, e);
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
                throw new IllegalStateException("Failed to create default RabbitMQ connection factory", e);
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

        CachingConnectionFactory cachingConnectionFactory =
                SpringRabbitMQConnectionFactoryHelper.createCachingConnectionFactory(config);

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
