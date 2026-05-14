package io.kaoto.forage.messaging.spring.rabbitmq;

import io.kaoto.forage.core.util.config.AbstractConfig;

import static io.kaoto.forage.messaging.spring.rabbitmq.SpringRabbitMQConfigEntries.ADDRESSES;
import static io.kaoto.forage.messaging.spring.rabbitmq.SpringRabbitMQConfigEntries.AUTOMATIC_RECOVERY_ENABLED;
import static io.kaoto.forage.messaging.spring.rabbitmq.SpringRabbitMQConfigEntries.CACHE_MODE;
import static io.kaoto.forage.messaging.spring.rabbitmq.SpringRabbitMQConfigEntries.CHANNEL_CACHE_SIZE;
import static io.kaoto.forage.messaging.spring.rabbitmq.SpringRabbitMQConfigEntries.CHANNEL_CHECKOUT_TIMEOUT;
import static io.kaoto.forage.messaging.spring.rabbitmq.SpringRabbitMQConfigEntries.CONNECTION_CACHE_SIZE;
import static io.kaoto.forage.messaging.spring.rabbitmq.SpringRabbitMQConfigEntries.CONNECTION_TIMEOUT;
import static io.kaoto.forage.messaging.spring.rabbitmq.SpringRabbitMQConfigEntries.HOST;
import static io.kaoto.forage.messaging.spring.rabbitmq.SpringRabbitMQConfigEntries.NETWORK_RECOVERY_INTERVAL;
import static io.kaoto.forage.messaging.spring.rabbitmq.SpringRabbitMQConfigEntries.PASSWORD;
import static io.kaoto.forage.messaging.spring.rabbitmq.SpringRabbitMQConfigEntries.PORT;
import static io.kaoto.forage.messaging.spring.rabbitmq.SpringRabbitMQConfigEntries.REQUESTED_HEARTBEAT;
import static io.kaoto.forage.messaging.spring.rabbitmq.SpringRabbitMQConfigEntries.USERNAME;
import static io.kaoto.forage.messaging.spring.rabbitmq.SpringRabbitMQConfigEntries.VIRTUAL_HOST;

public class SpringRabbitMQConfig extends AbstractConfig {

    public SpringRabbitMQConfig() {
        this(null);
    }

    public SpringRabbitMQConfig(String prefix) {
        super(prefix, SpringRabbitMQConfigEntries.class);
    }

    @Override
    public String name() {
        return "forage-spring-rabbitmq";
    }

    public String host() {
        return get(HOST).orElse(HOST.defaultValue());
    }

    public int port() {
        return get(PORT).map(Integer::parseInt).orElse(Integer.parseInt(PORT.defaultValue()));
    }

    public String username() {
        return get(USERNAME).orElse(USERNAME.defaultValue());
    }

    public String password() {
        return get(PASSWORD).orElse(PASSWORD.defaultValue());
    }

    public String virtualHost() {
        return get(VIRTUAL_HOST).orElse(VIRTUAL_HOST.defaultValue());
    }

    public int channelCacheSize() {
        return get(CHANNEL_CACHE_SIZE)
                .map(Integer::parseInt)
                .orElse(Integer.parseInt(CHANNEL_CACHE_SIZE.defaultValue()));
    }

    public String cacheMode() {
        return get(CACHE_MODE).orElse(CACHE_MODE.defaultValue());
    }

    public int connectionCacheSize() {
        return get(CONNECTION_CACHE_SIZE)
                .map(Integer::parseInt)
                .orElse(Integer.parseInt(CONNECTION_CACHE_SIZE.defaultValue()));
    }

    public long channelCheckoutTimeout() {
        return get(CHANNEL_CHECKOUT_TIMEOUT)
                .map(Long::parseLong)
                .orElse(Long.parseLong(CHANNEL_CHECKOUT_TIMEOUT.defaultValue()));
    }

    public int requestedHeartbeat() {
        return get(REQUESTED_HEARTBEAT)
                .map(Integer::parseInt)
                .orElse(Integer.parseInt(REQUESTED_HEARTBEAT.defaultValue()));
    }

    public int connectionTimeout() {
        return get(CONNECTION_TIMEOUT)
                .map(Integer::parseInt)
                .orElse(Integer.parseInt(CONNECTION_TIMEOUT.defaultValue()));
    }

    public String addresses() {
        return get(ADDRESSES).orElse(null);
    }

    public boolean automaticRecoveryEnabled() {
        return get(AUTOMATIC_RECOVERY_ENABLED)
                .map(Boolean::parseBoolean)
                .orElse(Boolean.parseBoolean(AUTOMATIC_RECOVERY_ENABLED.defaultValue()));
    }

    public long networkRecoveryInterval() {
        return get(NETWORK_RECOVERY_INTERVAL)
                .map(Long::parseLong)
                .orElse(Long.parseLong(NETWORK_RECOVERY_INTERVAL.defaultValue()));
    }
}
