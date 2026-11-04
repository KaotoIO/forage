package io.kaoto.forage.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import org.apache.camel.CamelContext;
import org.apache.camel.spi.ContextServicePlugin;
import org.apache.camel.support.LifecycleStrategySupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.core.common.BeanFactory;
import io.kaoto.forage.core.common.RuntimeManagedBeanFactories;
import io.kaoto.forage.core.util.config.CamelPlaceholderFunctionResolver;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.core.util.config.PlaceholderFunctionResolver;
import io.kaoto.forage.core.util.config.PlaceholderResolutionDeferredException;
import io.kaoto.forage.core.util.config.PlaceholderResolver;
import io.kaoto.forage.core.util.config.UnresolvedPlaceholderException;
import io.kaoto.forage.core.util.config.UnsupportedPlaceholderFunctionResolver;

public class ForageContextServicePlugin implements ContextServicePlugin {
    private static final Logger LOG = LoggerFactory.getLogger(ForageContextServicePlugin.class);
    private static final String QUARKUS_PACKAGE = "org.apache.camel.quarkus.";

    private final List<BeanFactory> factories = new ArrayList<>();
    private PlaceholderFunctionResolver functionResolver;

    @Override
    public void load(CamelContext camelContext) {
        installPlaceholderFunctionResolver(camelContext);

        ClassLoader classLoader = camelContext.getApplicationContextClassLoader();
        Set<String> runtimeManaged = runtimeManagedFactories(classLoader);

        List<UnresolvedPlaceholderException> unresolved = new ArrayList<>();
        ServiceLoader.load(BeanFactory.class, classLoader).stream().forEach(provider -> {
            if (runtimeManaged.contains(provider.type().getName())) {
                LOG.debug(
                        "Skipping bean factory {}: its beans are registered by the runtime",
                        provider.type().getName());
                return;
            }
            try {
                BeanFactory beanFactory = provider.get();
                beanFactory.setCamelContext(camelContext);
                beanFactory.configure();
                factories.add(beanFactory);
                LOG.debug(
                        "Successfully configured bean factory: {}",
                        beanFactory.getClass().getName());
            } catch (Exception e) {
                UnresolvedPlaceholderException placeholder = findCause(e, UnresolvedPlaceholderException.class);
                if (placeholder != null) {
                    // A misconfiguration: the bean would get a literal {{...}} value
                    unresolved.add(placeholder);
                } else if (findCause(e, PlaceholderResolutionDeferredException.class) != null) {
                    // The runtime creates the bean later, once the CamelContext is ready (Spring Boot)
                    LOG.debug("Bean factory {} deferred: {}", provider.type().getName(), e.getMessage());
                } else {
                    LOG.warn(
                            "Failed to configure bean factory: {}",
                            provider.type().getName(),
                            e);
                }
            }
        });

        if (!unresolved.isEmpty()) {
            failStartup(camelContext, unresolved);
        }
    }

    /**
     * Plugins cannot abort the CamelContext build (Camel logs and ignores their exceptions), so the
     * startup fails when the CamelContext initializes instead, before any route uses a missing bean.
     */
    private static void failStartup(CamelContext camelContext, List<UnresolvedPlaceholderException> unresolved) {
        UnresolvedPlaceholderException failure = unresolved.get(0);
        for (int i = 1; i < unresolved.size(); i++) {
            failure.addSuppressed(unresolved.get(i));
        }
        camelContext.addLifecycleStrategy(new LifecycleStrategySupport() {
            @Override
            public void onContextInitializing(CamelContext context) {
                throw failure;
            }
        });
    }

    /**
     * Makes Forage resolve placeholder functions ({@code {{secret:...}}}, {@code {{aws:...}}}, ...) through
     * this CamelContext. Spring Boot installs its own resolver, backed by the CamelContext bean, before any
     * CamelContext exists; Quarkus cannot resolve them (see {@link UnsupportedPlaceholderFunctionResolver#QUARKUS}).
     */
    private void installPlaceholderFunctionResolver(CamelContext camelContext) {
        PlaceholderFunctionResolver installed = PlaceholderResolver.installedFunctionResolver();
        if (isQuarkus(camelContext)) {
            functionResolver = UnsupportedPlaceholderFunctionResolver.QUARKUS;
        } else if (installed == null || installed instanceof CamelPlaceholderFunctionResolver) {
            functionResolver = new CamelPlaceholderFunctionResolver(camelContext);
        } else {
            return;
        }
        PlaceholderResolver.install(functionResolver);
    }

    private static boolean isQuarkus(CamelContext camelContext) {
        for (Class<?> type = camelContext.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().startsWith(QUARKUS_PACKAGE)) {
                return true;
            }
        }
        return false;
    }

    private static <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return null;
    }

    private static Set<String> runtimeManagedFactories(ClassLoader classLoader) {
        Set<String> names = new HashSet<>();
        for (RuntimeManagedBeanFactories managed : ServiceLoader.load(RuntimeManagedBeanFactories.class, classLoader)) {
            names.addAll(managed.beanFactoryClassNames());
        }
        return names;
    }

    /**
     * Called by Camel when the context is stopping. Lets factories release resources that must
     * only be freed at end of life (unlike {@link BeanFactory#cleanup()}, which is followed by a
     * re-{@link BeanFactory#configure()} during dev-mode reload).
     */
    @Override
    public void unload(CamelContext camelContext) {
        for (BeanFactory factory : factories) {
            try {
                factory.stop();
            } catch (Exception e) {
                LOG.warn("Failed to stop bean factory: {}", factory.getClass().getName(), e);
            }
        }
        factories.clear();
        if (functionResolver != null) {
            PlaceholderResolver.uninstall(functionResolver);
            functionResolver = null;
        }
    }

    /**
     * Called by Camel's route watcher reload strategy before routes are reloaded in dev mode.
     * Refreshes Forage beans so they pick up updated property values from disk.
     *
     * <p>The reload cycle is:
     * <ol>
     *   <li><strong>Cleanup</strong> — call {@link BeanFactory#cleanup()} on all factories</li>
     *   <li><strong>Clear config</strong> — call {@link ConfigStore#reload()} to clear cached values</li>
     *   <li><strong>Reconfigure</strong> — call {@link BeanFactory#configure()} on all factories</li>
     * </ol>
     */
    @Override
    public void onReload(CamelContext camelContext) {
        if (factories.isEmpty()) {
            return;
        }

        LOG.info("Forage property change detected, reloading beans...");

        // Phase 1: cleanup
        for (BeanFactory factory : factories) {
            try {
                factory.cleanup();
            } catch (Exception e) {
                LOG.warn(
                        "Failed to cleanup bean factory: {}", factory.getClass().getName(), e);
            }
        }

        // Phase 2: clear config cache
        LOG.debug("Clearing ConfigStore and ConfigHelper caches");
        ConfigStore.getInstance().reload();

        // Phase 3: reconfigure with fresh values from disk
        for (BeanFactory factory : factories) {
            try {
                factory.configure();
                LOG.info("Reloaded bean factory: {}", factory.getClass().getName());
            } catch (Exception e) {
                LOG.warn("Failed to reload bean factory: {}", factory.getClass().getName(), e);
            }
        }
    }
}
