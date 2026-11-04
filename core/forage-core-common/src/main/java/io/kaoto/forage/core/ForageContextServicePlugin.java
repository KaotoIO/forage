package io.kaoto.forage.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import org.apache.camel.CamelContext;
import org.apache.camel.spi.ContextServicePlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.core.common.BeanFactory;
import io.kaoto.forage.core.common.RuntimeManagedBeanFactories;
import io.kaoto.forage.core.util.config.ConfigStore;

public class ForageContextServicePlugin implements ContextServicePlugin {
    private static final Logger LOG = LoggerFactory.getLogger(ForageContextServicePlugin.class);

    private final List<BeanFactory> factories = new ArrayList<>();

    @Override
    public void load(CamelContext camelContext) {
        ClassLoader classLoader = camelContext.getApplicationContextClassLoader();
        Set<String> runtimeManaged = runtimeManagedFactories(classLoader);

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
                LOG.warn("Failed to configure bean factory: {}", provider.type().getName(), e);
            }
        });
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
