package io.kaoto.forage.core.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.SimpleEventNotifierSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.core.util.config.ConfigHelper;
import io.kaoto.forage.core.util.config.ConfigStore;

/**
 * Owns named closeable clients and releases retired generations once all routes have been removed.
 * Partial reloads retain clients because dynamic sends may still hold cached producers.
 * Module descriptors supply discovery metadata; subclasses provide client and endpoint access.
 *
 * @param <T> the client type
 */
public abstract class AbstractClientBeanFactory<T extends AutoCloseable> implements BeanFactory {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractClientBeanFactory.class);
    private final ForageModuleDescriptor<?, ?> descriptor;
    private CamelContext camelContext;

    private final Map<String, T> clients = new LinkedHashMap<>();
    private final List<T> retiredClients = new ArrayList<>();

    private final SimpleEventNotifierSupport routeListener = new SimpleEventNotifierSupport() {
        @Override
        public boolean isEnabled(CamelEvent event) {
            return event instanceof CamelEvent.RouteRemovedEvent;
        }

        @Override
        public void notify(CamelEvent event) {
            synchronized (AbstractClientBeanFactory.this) {
                // Route services have shut down; any remaining routes may still hold retired clients.
                CamelEvent.RouteRemovedEvent removed = (CamelEvent.RouteRemovedEvent) event;
                if (camelContext.getRoutes().stream().allMatch(route -> route == removed.getRoute())) {
                    closeRetiredClients();
                }
            }
        }
    };

    protected AbstractClientBeanFactory(ForageModuleDescriptor<?, ?> descriptor) {
        this.descriptor = descriptor;
    }

    protected abstract T createClient(String prefix);

    protected abstract void clearComponentClient(T client);

    protected abstract boolean usesClient(Endpoint endpoint, T client);

    @Override
    public synchronized void configure() {
        var config = descriptor.createConfig(null);
        Set<String> prefixes = ConfigStore.getInstance()
                .readPrefixes(config, ConfigHelper.getNamedPropertyRegexp(descriptor.modulePrefix()));
        Map<String, String> names = new LinkedHashMap<>();
        if (!prefixes.isEmpty()) {
            prefixes.stream().sorted().forEach(name -> names.put(name, name));
        } else if (!ConfigStore.getInstance()
                .readPrefixes(config, ConfigHelper.getDefaultPropertyRegexp(descriptor.modulePrefix()))
                .isEmpty()) {
            names.put(descriptor.defaultBeanName(), null);
        }
        Map<String, T> pending = new LinkedHashMap<>();
        try {
            for (Map.Entry<String, String> entry : names.entrySet()) {
                Object existing = camelContext.getRegistry().lookupByName(entry.getKey());
                if (existing == null) {
                    pending.put(entry.getKey(), createClient(entry.getValue()));
                } else if (!descriptor.primaryBeanClass().isInstance(existing)) {
                    throw new IllegalStateException("Bean '" + entry.getKey() + "' is not a "
                            + descriptor.primaryBeanClass().getName());
                }
            }
        } catch (RuntimeException e) {
            pending.values().forEach(this::closeClient);
            throw e;
        }
        pending.forEach((name, client) -> camelContext.getRegistry().bind(name, client));
        clients.putAll(pending);
        if (!clients.isEmpty()
                && !camelContext.getManagementStrategy().getEventNotifiers().contains(routeListener)) {
            camelContext.getManagementStrategy().addEventNotifier(routeListener);
        }
    }

    @Override
    public synchronized void cleanup() {
        // Routes, including dynamic sends, may still hold clients until the old routes are removed.
        clients.forEach((name, client) -> {
            if (camelContext.getRegistry().lookupByName(name) == client) {
                camelContext.getRegistry().unbind(name);
            }
            clearComponentClient(client);
            retiredClients.add(client);
        });
        clients.clear();
        if (camelContext.getRoutes().isEmpty()) {
            closeRetiredClients();
        }
    }

    @Override
    public synchronized void stop() {
        cleanup();
        closeRetiredClients();
        camelContext.getManagementStrategy().removeEventNotifier(routeListener);
    }

    private void closeRetiredClients() {
        retiredClients.forEach(this::closeClient);
        retiredClients.clear();
    }

    private void closeClient(T client) {
        try {
            // Prevent new routes from reusing cached endpoints backed by a closed client.
            for (var endpoint : camelContext.getEndpoints().stream()
                    .filter(candidate -> usesClient(candidate, client))
                    .toList()) {
                camelContext.removeEndpoint(endpoint);
            }
            client.close();
        } catch (Exception e) {
            LOG.warn("Failed to close {} client", descriptor.modulePrefix(), e);
        }
    }

    @Override
    public void setCamelContext(CamelContext context) {
        this.camelContext = context;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }
}
