package io.kaoto.forage.jms.ibmmq;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.XAConnectionFactory;

import java.net.URI;
import io.kaoto.forage.core.annotations.ForageBean;
import io.kaoto.forage.jms.common.ConnectionFactoryConfig;
import io.kaoto.forage.jms.common.PooledConnectionFactory;
import com.ibm.mq.jakarta.jms.MQConnectionFactory;
import com.ibm.mq.jakarta.jms.MQXAConnectionFactory;
import com.ibm.msg.client.jakarta.wmq.common.CommonConstants;

/**
 * IBM MQ implementation extending PooledConnectionFactory.
 * Provides IBM MQ-specific connection factory configuration.
 */
@ForageBean(
        value = "ibmmq",
        components = {"camel-jms"},
        description = "IBM MQ message broker",
        feature = "jakarta.jms.ConnectionFactory",
        runtimeDependencies = {
            "quarkus:mvn:org.apache.camel.quarkus:camel-quarkus-jta",
            "quarkus:mvn:io.quarkiverse.messaginghub:quarkus-pooled-jms"
        })
public class IbmMqJms extends PooledConnectionFactory {

    @Override
    protected ConnectionFactory createConnectionFactory(ConnectionFactoryConfig config) {
        try {
            MQConnectionFactory connectionFactory = new MQConnectionFactory();
            configureConnectionFactory(connectionFactory, config);
            return connectionFactory;
        } catch (JMSException e) {
            throw new RuntimeException("Failed to create IBM MQ ConnectionFactory", e);
        }
    }

    @Override
    protected XAConnectionFactory createXAConnectionFactory(ConnectionFactoryConfig config) {
        try {
            MQXAConnectionFactory xaConnectionFactory = new MQXAConnectionFactory();
            configureConnectionFactory(xaConnectionFactory, config);
            return xaConnectionFactory;
        } catch (JMSException e) {
            throw new RuntimeException("Failed to create IBM MQ XAConnectionFactory", e);
        }
    }

    private void configureConnectionFactory(MQConnectionFactory connectionFactory, ConnectionFactoryConfig config)
            throws JMSException {
        // Parse broker URL to extract host, port, channel, and queue manager
        // Expected format: mq://host:port/channel/queueManager
        String brokerUrl = config.brokerUrl();
        String[] parts = parseBrokerUrl(brokerUrl);

        connectionFactory.setHostName(parts[0]);
        connectionFactory.setPort(Integer.parseInt(parts[1]));
        connectionFactory.setChannel(parts[2]);
        connectionFactory.setQueueManager(parts[3]);
        connectionFactory.setTransportType(CommonConstants.WMQ_CM_CLIENT);

        if (config.username() != null) {
            connectionFactory.setStringProperty(CommonConstants.USERID, config.username());
        }
        if (config.password() != null) {
            connectionFactory.setStringProperty(CommonConstants.PASSWORD, config.password());
        }
        if (config.clientId() != null) {
            connectionFactory.setClientID(config.clientId());
        }
    }

    private String[] parseBrokerUrl(String brokerUrl) {
        // Expected format: mq://host:port/channel/queueManager
        try {
            URI uri = new URI(brokerUrl);

            String host = uri.getHost();
            if (host == null || host.isEmpty()) {
                throw new IllegalArgumentException(
                        "Invalid IBM MQ broker URL: host is missing. Expected: mq://host:port/channel/queueManager");
            }

            int portNum = uri.getPort();
            String port = portNum > 0 ? String.valueOf(portNum) : "1414";

            final String[] pathParts = extractPathPartFromURI(uri);

            String channel = pathParts[0];
            String queueManager = pathParts[1];
            if (channel.isEmpty() || queueManager.isEmpty()) {
                throw new IllegalArgumentException(
                        "Invalid IBM MQ broker URL: channel and queue manager must be non-empty. Expected: mq://host:port/channel/queueManager");
            }

            return new String[] {host, port, channel, queueManager};
        } catch (java.net.URISyntaxException e) {
            throw new IllegalArgumentException(
                    "Invalid IBM MQ broker URL: failed to parse URI. Expected: mq://host:port/channel/queueManager", e);
        }
    }

    private static String[] extractPathPartFromURI(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException(
                    "Invalid IBM MQ broker URL: channel and queue manager are missing. Expected: mq://host:port/channel/queueManager");
        }

        // Remove leading slash and split
        String[] pathParts = path.substring(1).split("/");
        if (pathParts.length < 2) {
            throw new IllegalArgumentException(
                    "Invalid IBM MQ broker URL: expected both channel and queue manager in path. Expected: mq://host:port/channel/queueManager");
        }
        return pathParts;
    }
}
