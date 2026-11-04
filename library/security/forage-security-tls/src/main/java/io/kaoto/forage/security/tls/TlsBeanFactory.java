package io.kaoto.forage.security.tls;

import java.util.Set;
import org.apache.camel.CamelContext;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.core.annotations.FactoryType;
import io.kaoto.forage.core.annotations.ForageFactory;
import io.kaoto.forage.core.common.BeanFactory;
import io.kaoto.forage.core.util.config.ConfigHelper;
import io.kaoto.forage.core.util.config.ConfigStore;

@ForageFactory(
        value = "TLS Context",
        components = {},
        description = "Creates SSLContextParameters beans for TLS/SSL configuration",
        type = FactoryType.SSL_CONTEXT_PARAMETERS,
        defaultBeanName = "sslContextParameters",
        autowired = true,
        configClass = TlsConfig.class)
public class TlsBeanFactory implements BeanFactory {
    private static final Logger LOG = LoggerFactory.getLogger(TlsBeanFactory.class);

    private CamelContext camelContext;
    private static final String DEFAULT_BEAN_NAME = TlsModuleDescriptor.DEFAULT_BEAN_NAME;

    @Override
    public void cleanup() {
        TlsConfig config = new TlsConfig();
        Set<String> prefixes =
                ConfigStore.getInstance().readPrefixes(config, ConfigHelper.getNamedPropertyRegexp("tls"));

        for (String name : prefixes) {
            camelContext.getRegistry().unbind(name);
        }
        camelContext.getRegistry().unbind(DEFAULT_BEAN_NAME);
    }

    @Override
    public void configure() {
        TlsConfig config = new TlsConfig();
        Set<String> prefixes =
                ConfigStore.getInstance().readPrefixes(config, ConfigHelper.getNamedPropertyRegexp("tls"));

        if (!prefixes.isEmpty()) {
            for (String name : prefixes) {
                try {
                    if (camelContext.getRegistry().lookupByNameAndType(name, SSLContextParameters.class) == null) {
                        TlsConfig tlsConfig = new TlsConfig(name);
                        SSLContextParameters sslCtxParams = createSslContextParameters(tlsConfig);
                        if (sslCtxParams != null) {
                            camelContext.getRegistry().bind(name, sslCtxParams);
                            LOG.info("Registered SSLContextParameters bean '{}'", name);
                        }
                    }
                } catch (Exception ex) {
                    LOG.error("Failed to configure TLS profile '{}': {}", name, ex.getMessage(), ex);
                }
            }
        } else {
            try {
                if (camelContext.getRegistry().lookupByNameAndType(DEFAULT_BEAN_NAME, SSLContextParameters.class)
                        == null) {
                    SSLContextParameters sslCtxParams = createSslContextParameters(config);
                    if (sslCtxParams != null) {
                        camelContext.getRegistry().bind(DEFAULT_BEAN_NAME, sslCtxParams);
                        LOG.info("Registered default SSLContextParameters bean '{}'", DEFAULT_BEAN_NAME);
                    }
                }
            } catch (Exception ex) {
                LOG.error("Failed to configure default TLS profile: {}", ex.getMessage(), ex);
            }
        }
    }

    SSLContextParameters createSslContextParameters(TlsConfig config) {
        return new TlsProvider().create(config);
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
