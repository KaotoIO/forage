package io.kaoto.forage.springboot.cxf;

import java.util.List;
import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import io.kaoto.forage.core.annotations.FactoryType;
import io.kaoto.forage.core.annotations.FactoryVariant;
import io.kaoto.forage.core.annotations.ForageFactory;
import io.kaoto.forage.core.cxf.CxfEndpointProvider;
import io.kaoto.forage.cxf.common.CxfCommonExportHelper;
import io.kaoto.forage.cxf.common.CxfConfig;
import io.kaoto.forage.cxf.common.CxfModuleDescriptor;
import io.kaoto.forage.springboot.common.ForageSpringBootModuleAdapter;

/**
 * Auto-configuration for Forage CXF endpoint creation using ServiceLoader discovery.
 * Automatically creates CXF endpoint beans from configuration properties,
 * supporting both single and multi-instance (prefixed) configurations.
 *
 * <p>Named/prefixed endpoints (e.g., {@code forage.billing.cxf.address}) are registered
 * dynamically by {@link ForageSpringBootModuleAdapter} using the {@link CxfModuleDescriptor}.
 */
@ForageFactory(
        value = "CXF (Spring Boot)",
        variant = FactoryVariant.SPRING_BOOT,
        components = {"camel-cxf"},
        description = "Auto-configured CXF SOAP endpoint for Spring Boot",
        type = FactoryType.CXF_ENDPOINT,
        autowired = true,
        configClass = CxfConfig.class)
@Configuration
public class ForageCxfAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ForageCxfAutoConfiguration.class);

    @Bean
    static ForageSpringBootModuleAdapter<CxfConfig, CxfEndpointProvider> forageCxfModuleAdapter(
            Environment environment) {
        return new ForageSpringBootModuleAdapter<>(new CxfModuleDescriptor(), environment);
    }

    @Bean("cxfEndpoint")
    @ConditionalOnMissingBean(name = "cxfEndpoint")
    @ConditionalOnProperty(prefix = "forage.cxf", name = "kind")
    public Object forageDefaultCxfEndpoint() {
        CxfConfig config = new CxfConfig();
        String kind = config.cxfKind();
        String providerClassName = CxfCommonExportHelper.transformCxfKindIntoProviderClass(kind);

        List<ServiceLoader.Provider<CxfEndpointProvider>> providers =
                ServiceLoader.load(CxfEndpointProvider.class).stream().toList();

        for (ServiceLoader.Provider<CxfEndpointProvider> provider : providers) {
            if (provider.type().getName().equals(providerClassName)) {
                log.info("Creating default CXF endpoint using provider: {}", providerClassName);
                Object endpoint = provider.get().create(null);
                log.info("Registered default CXF endpoint bean");
                return endpoint;
            }
        }

        String available = providers.stream()
                .map(p -> p.type().getName())
                .reduce((a, b) -> a + ", " + b)
                .orElse("none");
        throw new IllegalStateException("No CxfEndpointProvider found for kind '" + kind + "' (expected "
                + providerClassName + "). Available providers: " + available);
    }
}
