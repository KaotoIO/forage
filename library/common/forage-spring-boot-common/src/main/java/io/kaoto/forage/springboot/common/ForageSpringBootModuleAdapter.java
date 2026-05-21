package io.kaoto.forage.springboot.common;

import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.core.env.Environment;
import io.kaoto.forage.core.common.AuxiliaryBeanDescriptor;
import io.kaoto.forage.core.common.BeanProvider;
import io.kaoto.forage.core.common.ForageModuleDescriptor;
import io.kaoto.forage.core.common.ServiceLoaderHelper;
import io.kaoto.forage.core.util.config.Config;
import io.kaoto.forage.core.util.config.ConfigHelper;
import io.kaoto.forage.core.util.config.ConfigStore;

/**
 * Generic Spring Boot adapter that consumes any {@link ForageModuleDescriptor} to dynamically
 * register beans via {@link BeanDefinitionRegistryPostProcessor}.
 *
 * <p>This replaces per-module post-processors (e.g., {@code ForageJdbcBeanDefinitionRegistryPostProcessor},
 * {@code ForageJmsBeanDefinitionRegistryPostProcessor}) with a single reusable class.
 * Adding a new Forage module only requires writing a descriptor — no new post-processor needed.
 *
 * @param <C> the module's configuration type
 * @param <P> the module's provider type
 * @since 1.1
 */
public class ForageSpringBootModuleAdapter<C extends Config, P extends BeanProvider<?>>
        implements BeanDefinitionRegistryPostProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(ForageSpringBootModuleAdapter.class);

    private final ForageModuleDescriptor<C, P> descriptor;
    private final Environment environment;

    public ForageSpringBootModuleAdapter(ForageModuleDescriptor<C, P> descriptor, Environment environment) {
        this.descriptor = descriptor;
        this.environment = environment;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        Set<String> prefixes = discoverPrefixes();
        if (!prefixes.isEmpty()) {
            LOG.info("Discovered Forage {} configuration prefixes: {}", descriptor.modulePrefix(), prefixes);
            registerBeans(registry, prefixes);
        } else if (hasDefaultProperties()) {
            // Single (non-prefixed) configuration: primary bean is created by the
            // auto-configuration's @Bean method, but auxiliary beans (aggregation repo,
            // idempotent repo) still need to be registered here.
            LOG.info(
                    "Discovered default Forage {} configuration, registering auxiliary beans",
                    descriptor.modulePrefix());
            registerAuxiliaryBeans(registry, null);
        } else {
            LOG.debug("No Forage {} configuration found", descriptor.modulePrefix());
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // Replace conflicting alias beans registered by framework auto-configs
        // (e.g., Spring Boot's ArtemisAutoConfiguration creates a CachingConnectionFactory
        // as "jmsConnectionFactory" that wraps Forage's already-pooled ConnectionFactory).
        // postProcessBeanFactory runs AFTER all auto-config bean definitions are registered,
        // so this is the reliable point to replace them.
        if (beanFactory instanceof BeanDefinitionRegistry registry) {
            String defaultName = descriptor.defaultBeanName();
            if (registry.containsBeanDefinition(defaultName)) {
                for (String alias : descriptor.defaultBeanAliases()) {
                    if (registry.containsBeanDefinition(alias)) {
                        registry.removeBeanDefinition(alias);
                        LOG.info("Removed conflicting {} bean definition: {}", descriptor.modulePrefix(), alias);
                    }
                    GenericBeanDefinition aliasDef = new GenericBeanDefinition();
                    aliasDef.setBeanClass(descriptor.primaryBeanClass());
                    aliasDef.setInstanceSupplier(() -> beanFactory.getBean(defaultName));
                    registry.registerBeanDefinition(alias, aliasDef);
                    LOG.info(
                            "Registered {} alias bean definition: {} -> {}",
                            descriptor.modulePrefix(),
                            alias,
                            defaultName);
                }
            }
        }
    }

    private boolean hasDefaultProperties() {
        if (SpringPropertyHelper.hasProperties(
                environment, ConfigHelper.getDefaultPropertyRegexp(descriptor.modulePrefix()))) {
            return true;
        }
        // Also check ConfigStore (covers forage-*.properties files not loaded into Spring Environment)
        C defaultConfig = descriptor.createConfig(null);
        return !ConfigStore.getInstance()
                .readPrefixes(defaultConfig, ConfigHelper.getDefaultPropertyRegexp(descriptor.modulePrefix()))
                .isEmpty();
    }

    private Set<String> discoverPrefixes() {
        Set<String> prefixes = SpringPropertyHelper.discoverPrefixes(
                environment, ConfigHelper.getNamedPropertyRegexp(descriptor.modulePrefix()));
        if (!prefixes.isEmpty()) {
            return prefixes;
        }
        // Also check ConfigStore (covers forage-*.properties files not loaded into Spring Environment)
        C defaultConfig = descriptor.createConfig(null);
        return ConfigStore.getInstance()
                .readPrefixes(defaultConfig, ConfigHelper.getNamedPropertyRegexp(descriptor.modulePrefix()));
    }

    private void registerBeans(BeanDefinitionRegistry registry, Set<String> prefixes) {
        LOG.debug("Registering Forage {} beans for prefixes: {}", descriptor.modulePrefix(), prefixes);
        boolean isFirst = true;
        for (String name : prefixes.stream().sorted().toList()) {
            if (!registry.containsBeanDefinition(name)) {
                registerPrimaryBean(registry, name, isFirst);
            } else {
                LOG.debug("Bean '{}' already defined, skipping primary registration", name);
            }
            // Register auxiliary beans for every prefix that has them configured
            registerAuxiliaryBeans(registry, name);
            isFirst = false;
        }
    }

    private void registerPrimaryBean(BeanDefinitionRegistry registry, String name, boolean isFirst) {
        GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
        beanDefinition.setBeanClass(descriptor.primaryBeanClass());
        beanDefinition.setInstanceSupplier(() -> createPrimaryBean(name));
        beanDefinition.setPrimary(isFirst);
        registry.registerBeanDefinition(name, beanDefinition);
        LOG.info("Registered {} bean definition: {}", descriptor.modulePrefix(), name);

        String defaultName = descriptor.defaultBeanName();
        if (isFirst && !name.equals(defaultName)) {
            if (registry.containsBeanDefinition(defaultName)) {
                registry.removeBeanDefinition(defaultName);
                LOG.info("Replaced conflicting {} default bean definition", descriptor.modulePrefix());
            }
            registry.registerAlias(name, defaultName);
            LOG.info("Registered default {} alias: {} -> {}", descriptor.modulePrefix(), defaultName, name);
        }
    }

    private void registerAuxiliaryBeans(BeanDefinitionRegistry registry, String prefix) {
        List<AuxiliaryBeanDescriptor> auxiliaryBeans = descriptor.auxiliaryBeans(prefix);
        for (AuxiliaryBeanDescriptor aux : auxiliaryBeans) {
            if (!registry.containsBeanDefinition(aux.name())) {
                GenericBeanDefinition beanDef = new GenericBeanDefinition();
                beanDef.setBeanClass(aux.type());
                beanDef.setInstanceSupplier(aux.factory());
                registry.registerBeanDefinition(aux.name(), beanDef);
                LOG.info("Registered auxiliary bean definition: {}", aux.name());
            }
        }
    }

    /**
     * Creates the primary bean for the given prefix/name using ServiceLoader discovery.
     * Exposed for use by {@code ImportBeanDefinitionRegistrar} implementations that need
     * to delegate bean creation to the adapter.
     *
     * @param name the configuration prefix or bean name
     * @return the created bean, or null if no provider is found
     */
    public Object createBean(String name) {
        return createPrimaryBean(name);
    }

    private Object createPrimaryBean(String name) {
        C config = descriptor.createConfig(name);
        String providerClassName = descriptor.resolveProviderClassName(config);
        List<ServiceLoader.Provider<P>> providers =
                ServiceLoader.load(descriptor.providerClass()).stream().toList();

        ServiceLoader.Provider<P> provider;
        if (providers.size() == 1) {
            provider = providers.get(0);
        } else {
            provider = ServiceLoaderHelper.findProviderByClassName(providers, providerClassName);
        }

        if (provider == null) {
            throw new IllegalStateException(
                    "No " + descriptor.modulePrefix() + " provider found for class: " + providerClassName);
        }

        return provider.get().create(name);
    }
}
