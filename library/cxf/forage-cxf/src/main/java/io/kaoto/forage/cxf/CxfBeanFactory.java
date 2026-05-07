package io.kaoto.forage.cxf;

import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.core.annotations.FactoryType;
import io.kaoto.forage.core.annotations.ForageFactory;
import io.kaoto.forage.core.common.BeanFactory;
import io.kaoto.forage.core.common.ServiceLoaderHelper;
import io.kaoto.forage.core.cxf.CxfEndpointProvider;
import io.kaoto.forage.core.util.config.ConfigHelper;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.cxf.common.CxfCommonExportHelper;
import io.kaoto.forage.cxf.common.CxfConfig;

@ForageFactory(
        value = "CXF Web Service",
        components = {"camel-cxf"},
        description = "Creates CXF SOAP endpoint beans for web service integration",
        type = FactoryType.CXF_ENDPOINT,
        autowired = true,
        configClass = CxfConfig.class)
public class CxfBeanFactory implements BeanFactory {
    private final Logger LOG = LoggerFactory.getLogger(CxfBeanFactory.class);

    private CamelContext camelContext;
    private static final String DEFAULT_BEAN_NAME = "cxfEndpoint";

    @Override
    public void cleanup() {
        CxfConfig config = new CxfConfig();
        Set<String> prefixes =
                ConfigStore.getInstance().readPrefixes(config, ConfigHelper.getNamedPropertyRegexp("cxf"));

        for (String name : prefixes) {
            camelContext.getRegistry().unbind(name);
        }
        camelContext.getRegistry().unbind(DEFAULT_BEAN_NAME);
    }

    @Override
    public void configure() {
        CxfConfig config = new CxfConfig();
        Set<String> prefixes =
                ConfigStore.getInstance().readPrefixes(config, ConfigHelper.getNamedPropertyRegexp("cxf"));

        if (!prefixes.isEmpty()) {
            for (String name : prefixes) {
                try {
                    if (camelContext.getRegistry().lookupByName(name) == null) {
                        CxfConfig cxfConfig = new CxfConfig(name);
                        Object endpoint = newCxfEndpoint(cxfConfig, name);
                        if (endpoint != null) {
                            applyCamelContext(endpoint);
                            camelContext.getRegistry().bind(name, endpoint);
                        }
                    }
                } catch (Exception ex) {
                    LOG.error("Failed to configure CXF endpoint '{}': {}", name, ex.getMessage(), ex);
                }
            }
        } else {
            try {
                if (camelContext.getRegistry().lookupByName(DEFAULT_BEAN_NAME) == null) {
                    CxfConfig cxfConfig = new CxfConfig();
                    Object endpoint = newCxfEndpoint(cxfConfig, null);
                    if (endpoint != null) {
                        applyCamelContext(endpoint);
                        camelContext.getRegistry().bind(DEFAULT_BEAN_NAME, endpoint);
                    } else {
                        throw new IllegalArgumentException(
                                "No matching CxfEndpointProvider found for configured cxf.kind");
                    }
                }
            } catch (Exception ex) {
                LOG.error(ex.getMessage(), ex);
            }
        }
    }

    private Object newCxfEndpoint(CxfConfig cxfConfig, String name) {
        final String providerClass = CxfCommonExportHelper.transformCxfKindIntoProviderClass(cxfConfig.cxfKind());
        LOG.info("Creating CXF endpoint of type {}", providerClass);

        final List<ServiceLoader.Provider<CxfEndpointProvider>> providers = findProviders(CxfEndpointProvider.class);

        final ServiceLoader.Provider<CxfEndpointProvider> provider =
                ServiceLoaderHelper.findProviderByClassName(providers, providerClass);

        if (provider == null) {
            LOG.warn(
                    "CXF endpoint '{}' has no provider for {}", name != null ? name : DEFAULT_BEAN_NAME, providerClass);
            return null;
        }

        return provider.get().create(name);
    }

    // setCamelContext() applies deferred properties — all config must be set before this call
    private void applyCamelContext(Object endpoint) {
        if (endpoint instanceof CamelContextAware contextAware) {
            contextAware.setCamelContext(camelContext);
        }
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
