package io.kaoto.forage.core.security;

import java.util.HashSet;
import java.util.Set;
import org.apache.camel.CamelContext;
import org.apache.camel.spi.AuthorizationPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.core.annotations.FactoryType;
import io.kaoto.forage.core.annotations.ForageFactory;
import io.kaoto.forage.core.common.BeanFactory;

/**
 * Registers configured security policies exposed by {@link SecurityPolicyProvider}s.
 *
 * <p>Providers are named after their security technology (for example {@code shiro}).
 * Their default policy is bound as {@code <provider-name>Policy}, allowing YAML DSL routes
 * to reference it with a {@code policy.ref}.
 */
@ForageFactory(
        value = "Security Policy",
        components = {"camel-keycloak", "camel-shiro", "camel-spring-security"},
        description = "Creates Camel authorization policies for configured security providers",
        type = FactoryType.SECURITY_POLICY,
        autowired = true)
public class SecurityPolicyBeanFactory implements BeanFactory {
    private static final Logger LOG = LoggerFactory.getLogger(SecurityPolicyBeanFactory.class);

    private final Set<String> registeredNames = new HashSet<>();
    private CamelContext camelContext;

    @Override
    public void configure() {
        for (var providerRef : findProviders(SecurityPolicyProvider.class)) {
            SecurityPolicyProvider provider = providerRef.get();
            String beanName = provider.beanName();

            try {
                if (camelContext.getRegistry().lookupByNameAndType(beanName, AuthorizationPolicy.class) == null) {
                    camelContext.getRegistry().bind(beanName, provider.create(null));
                    registeredNames.add(beanName);
                    LOG.info("Registered {} security policy bean '{}'", provider.name(), beanName);
                }
            } catch (Exception e) {
                LOG.warn("Failed to configure {} security policy: {}", provider.name(), e.getMessage(), e);
            }
        }
    }

    @Override
    public void cleanup() {
        for (String name : registeredNames) {
            camelContext.getRegistry().unbind(name);
        }
        registeredNames.clear();
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
