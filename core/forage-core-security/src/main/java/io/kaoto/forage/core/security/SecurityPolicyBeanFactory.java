package io.kaoto.forage.core.security;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.camel.CamelContext;
import org.apache.camel.spi.AuthorizationPolicy;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.SimpleEventNotifierSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.core.annotations.BeanReference;
import io.kaoto.forage.core.annotations.FactoryType;
import io.kaoto.forage.core.annotations.ForageFactory;
import io.kaoto.forage.core.common.BeanFactory;

/**
 * Registers configured security policies exposed by {@link SecurityPolicyProvider}s.
 *
 * <p>Providers are named after their security technology (for example {@code shiro}).
 * Their default policy is bound as {@code <provider-name>Policy}, allowing YAML DSL routes
 * to reference it with a {@code policy.ref}.
 *
 * <p>Policies holding resources (for example the Keycloak HTTP clients) implement {@link AutoCloseable}.
 * They are closed when the CamelContext stops. On reload, the replaced policies are closed once no route
 * can still use them, that is when all routes have been removed.
 */
@ForageFactory(
        value = "Security Policy",
        components = {"camel-keycloak", "camel-shiro", "camel-spring-security"},
        description = "Creates Camel authorization policies for configured security providers",
        type = FactoryType.SECURITY_POLICY,
        autowired = true,
        namedInstances = false,
        beanReferences =
                @BeanReference(catalogKind = "processor", name = "policy", option = "ref", optionKind = "attribute"))
public class SecurityPolicyBeanFactory implements BeanFactory {
    private static final Logger LOG = LoggerFactory.getLogger(SecurityPolicyBeanFactory.class);

    private final Map<String, AuthorizationPolicy> registered = new LinkedHashMap<>();
    private final List<AuthorizationPolicy> retired = new ArrayList<>();
    private CamelContext camelContext;

    private final SimpleEventNotifierSupport routeListener = new SimpleEventNotifierSupport() {
        @Override
        public boolean isEnabled(CamelEvent event) {
            return event instanceof CamelEvent.RouteRemovedEvent;
        }

        @Override
        public void notify(CamelEvent event) {
            synchronized (SecurityPolicyBeanFactory.this) {
                // Routes still running may hold retired policies, close them once the last one is gone
                CamelEvent.RouteRemovedEvent removed = (CamelEvent.RouteRemovedEvent) event;
                if (camelContext.getRoutes().stream().allMatch(route -> route == removed.getRoute())) {
                    closeRetired();
                }
            }
        }
    };

    @Override
    public synchronized void configure() {
        for (var providerRef : findProviders(SecurityPolicyProvider.class)) {
            SecurityPolicyProvider provider = providerRef.get();
            SecurityPolicyModuleDescriptor<?> descriptor = provider.descriptor();
            if (descriptor != null && !descriptor.isConfigured()) {
                LOG.debug(
                        "Skipping {} security policy: no forage.{}.* configuration found",
                        provider.name(),
                        descriptor.modulePrefix());
                continue;
            }
            String beanName = provider.beanName();

            try {
                if (camelContext.getRegistry().lookupByNameAndType(beanName, AuthorizationPolicy.class) == null) {
                    AuthorizationPolicy policy = provider.create(null);
                    camelContext.getRegistry().bind(beanName, policy);
                    registered.put(beanName, policy);
                    LOG.info("Registered {} security policy bean '{}'", provider.name(), beanName);
                }
            } catch (Exception e) {
                LOG.warn("Failed to configure {} security policy: {}", provider.name(), e.getMessage(), e);
            }
        }
        if (registered.values().stream().anyMatch(AutoCloseable.class::isInstance)
                && !camelContext.getManagementStrategy().getEventNotifiers().contains(routeListener)) {
            camelContext.getManagementStrategy().addEventNotifier(routeListener);
        }
    }

    @Override
    public synchronized void cleanup() {
        registered.forEach((name, policy) -> {
            if (camelContext.getRegistry().lookupByName(name) == policy) {
                camelContext.getRegistry().unbind(name);
            }
            retired.add(policy);
        });
        registered.clear();
        if (camelContext.getRoutes().isEmpty()) {
            closeRetired();
        }
    }

    @Override
    public synchronized void stop() {
        cleanup();
        closeRetired();
        camelContext.getManagementStrategy().removeEventNotifier(routeListener);
    }

    private void closeRetired() {
        for (AuthorizationPolicy policy : retired) {
            if (policy instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception e) {
                    LOG.warn(
                            "Failed to close security policy {}",
                            policy.getClass().getName(),
                            e);
                }
            }
        }
        retired.clear();
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
