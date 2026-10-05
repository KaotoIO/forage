package io.kaoto.forage.jms.ibmmq;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.XAConnectionFactory;
import javax.net.ssl.SSLContext;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import io.kaoto.forage.core.annotations.ForageBean;
import io.kaoto.forage.jms.common.ConnectionFactoryConfig;
import io.kaoto.forage.jms.common.PooledConnectionFactory;
import io.kaoto.forage.jms.common.SslContextHelper;
import com.ibm.mq.jakarta.jms.MQConnectionFactory;
import com.ibm.mq.jakarta.jms.MQXAConnectionFactory;
import com.ibm.msg.client.jakarta.jms.JmsConstants;
import com.ibm.msg.client.jakarta.wmq.common.CommonConstants;

@ForageBean(
        value = "ibmmq",
        components = {"camel-jms"},
        description = "IBM MQ message broker",
        feature = "jakarta.jms.ConnectionFactory",
        configClass = IbmMqConfig.class,
        runtimeDependencies = {
            "quarkus:mvn:org.apache.camel.quarkus:camel-quarkus-jta",
            "quarkus:mvn:io.quarkiverse.messaginghub:quarkus-pooled-jms"
        })
public class IbmMqJms extends PooledConnectionFactory {

    @Override
    protected ConnectionFactory createConnectionFactory(ConnectionFactoryConfig config) {
        try {
            MQConnectionFactory connectionFactory = new MQConnectionFactory();
            IbmMqConfig mqConfig = new IbmMqConfig(config.configPrefix());
            configureConnectionFactory(connectionFactory, config, mqConfig);
            return connectionFactory;
        } catch (JMSException e) {
            throw new RuntimeException("Failed to create IBM MQ ConnectionFactory", e);
        }
    }

    @Override
    protected XAConnectionFactory createXAConnectionFactory(ConnectionFactoryConfig config) {
        try {
            MQXAConnectionFactory xaConnectionFactory = new MQXAConnectionFactory();
            IbmMqConfig mqConfig = new IbmMqConfig(config.configPrefix());
            configureConnectionFactory(xaConnectionFactory, config, mqConfig);
            return xaConnectionFactory;
        } catch (JMSException e) {
            throw new RuntimeException("Failed to create IBM MQ XAConnectionFactory", e);
        }
    }

    private void configureConnectionFactory(
            MQConnectionFactory connectionFactory, ConnectionFactoryConfig config, IbmMqConfig mqConfig)
            throws JMSException {

        String ccdtUrl = mqConfig.ccdtUrl();
        if (ccdtUrl != null && !ccdtUrl.isEmpty()) {
            try {
                URL parsed;
                try {
                    parsed = new URL(ccdtUrl);
                } catch (MalformedURLException ignored) {
                    parsed = Path.of(ccdtUrl).toUri().toURL();
                }
                connectionFactory.setCCDTURL(parsed);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid IBM MQ CCDT URL or file path: " + ccdtUrl, e);
            }
        } else {
            String brokerUrl = config.brokerUrl();
            String[] parts = parseBrokerUrl(brokerUrl);

            connectionFactory.setHostName(parts[0]);
            connectionFactory.setPort(Integer.parseInt(parts[1]));
            connectionFactory.setChannel(parts[2]);
            connectionFactory.setQueueManager(parts[3]);
        }

        connectionFactory.setTransportType(CommonConstants.WMQ_CM_CLIENT);

        if (config.username() != null) {
            connectionFactory.setStringProperty(JmsConstants.USERID, config.username());
        }
        if (config.password() != null) {
            connectionFactory.setStringProperty(JmsConstants.PASSWORD, config.password());
        }
        if (config.clientId() != null) {
            connectionFactory.setClientID(config.clientId());
        }

        if (config.sslEnabled()) {
            SSLContext sslContext = SslContextHelper.createSslContext(config);
            if (sslContext != null) {
                connectionFactory.setSSLSocketFactory(sslContext.getSocketFactory());
            }
        }

        String cipherSuite = mqConfig.sslCipherSuite();
        if (cipherSuite != null) {
            connectionFactory.setSSLCipherSuite(cipherSuite);
        }

        String peerName = mqConfig.sslPeerName();
        if (peerName != null) {
            connectionFactory.setSSLPeerName(peerName);
        }

        String reconnectOption = mqConfig.reconnectOption();
        if (reconnectOption != null && !"NO".equalsIgnoreCase(reconnectOption)) {
            int option =
                    switch (reconnectOption.toUpperCase()) {
                        case "YES" -> CommonConstants.WMQ_CLIENT_RECONNECT;
                        case "QMGR" -> CommonConstants.WMQ_CLIENT_RECONNECT_Q_MGR;
                        case "DISABLED" -> CommonConstants.WMQ_CLIENT_RECONNECT_DISABLED;
                        default ->
                            throw new IllegalArgumentException("Invalid reconnect option: " + reconnectOption
                                    + ". Expected one of: NO, YES, QMGR, DISABLED");
                    };
            connectionFactory.setClientReconnectOptions(option);
            connectionFactory.setClientReconnectTimeout(mqConfig.reconnectTimeout());
        }

        Integer shareConv = mqConfig.shareConversations();
        if (shareConv != null) {
            connectionFactory.setShareConvAllowed(shareConv);
        }
    }

    private String[] parseBrokerUrl(String brokerUrl) {
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

        String[] pathParts = path.substring(1).split("/");
        if (pathParts.length < 2) {
            throw new IllegalArgumentException(
                    "Invalid IBM MQ broker URL: expected both channel and queue manager in path. Expected: mq://host:port/channel/queueManager");
        }
        return pathParts;
    }
}
