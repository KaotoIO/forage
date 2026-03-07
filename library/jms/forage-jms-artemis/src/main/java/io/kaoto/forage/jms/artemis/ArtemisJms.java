package io.kaoto.forage.jms.artemis;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.XAConnectionFactory;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQXAConnectionFactory;
import io.kaoto.forage.core.annotations.ForageBean;
import io.kaoto.forage.jms.common.ConnectionFactoryConfig;
import io.kaoto.forage.jms.common.PooledConnectionFactory;

/**
 * ActiveMQ Artemis implementation extending PooledConnectionFactory.
 * Provides Artemis-specific connection factory configuration.
 */
@ForageBean(
        value = "artemis",
        components = {"camel-jms"},
        description = "ActiveMQ Artemis message broker",
        feature = "jakarta.jms.ConnectionFactory",
        runtimeDependencies = {"quarkus:mvn:io.quarkiverse.artemis:quarkus-artemis-jms"})
public class ArtemisJms extends PooledConnectionFactory {

    @Override
    protected ConnectionFactory createConnectionFactory(ConnectionFactoryConfig config) {
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(config.brokerUrl());

        setupConnection(config, connectionFactory);

        return connectionFactory;
    }

    private static void setupConnection(ConnectionFactoryConfig config, ActiveMQConnectionFactory connectionFactory) {
        if (config.username() != null) {
            connectionFactory.setUser(config.username());
        }
        if (config.password() != null) {
            connectionFactory.setPassword(config.password());
        }
        if (config.clientId() != null) {
            connectionFactory.setClientID(config.clientId());
        }
    }

    @Override
    protected XAConnectionFactory createXAConnectionFactory(ConnectionFactoryConfig config) {
        ActiveMQXAConnectionFactory xaConnectionFactory = new ActiveMQXAConnectionFactory(config.brokerUrl());

        setupConnection(config, xaConnectionFactory);

        return xaConnectionFactory;
    }
}
