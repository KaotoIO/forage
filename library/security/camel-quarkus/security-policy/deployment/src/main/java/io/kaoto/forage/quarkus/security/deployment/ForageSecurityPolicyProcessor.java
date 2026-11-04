package io.kaoto.forage.quarkus.security.deployment;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import org.apache.camel.quarkus.core.deployment.spi.CamelRuntimeBeanBuildItem;
import org.jboss.logging.Logger;
import io.kaoto.forage.core.annotations.FactoryType;
import io.kaoto.forage.core.annotations.FactoryVariant;
import io.kaoto.forage.core.annotations.ForageFactory;
import io.kaoto.forage.core.common.RuntimeType;
import io.kaoto.forage.core.security.SecurityPolicyModuleDescriptor;
import io.kaoto.forage.core.security.SecurityPolicyProvider;
import io.kaoto.forage.core.util.config.Config;
import io.kaoto.forage.quarkus.security.ForageSecurityPolicyRecorder;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;
import io.quarkus.runtime.configuration.ConfigurationException;

/**
 * Registers the default policy of every configured {@link SecurityPolicyProvider} in the Camel registry.
 *
 * <p>Which providers are configured is decided during the build, from the {@code forage.<provider>.*} properties;
 * the policies themselves are created at runtime initialization, from the runtime configuration. The native image
 * support of the security libraries comes from the Camel Quarkus extensions ({@code camel-quarkus-shiro},
 * {@code camel-quarkus-keycloak}), which the Forage catalog adds for each configured provider. This processor adds
 * what is specific to Forage: the provider services, the Forage properties files and the classpath resources the
 * policies load (for example the Shiro INI file).
 */
@ForageFactory(
        value = "Security Policy (Quarkus)",
        components = {"camel-keycloak", "camel-shiro"},
        description = "Security policies created at Quarkus runtime initialization",
        type = FactoryType.SECURITY_POLICY,
        autowired = true,
        variant = FactoryVariant.QUARKUS)
public class ForageSecurityPolicyProcessor {
    private static final Logger LOG = Logger.getLogger(ForageSecurityPolicyProcessor.class);

    private static final String FEATURE = "forage-security-policy";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    ServiceProviderBuildItem registerProviders() {
        return ServiceProviderBuildItem.allProvidersFromClassPath(SecurityPolicyProvider.class.getName());
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void registerPolicies(
            ForageSecurityPolicyRecorder recorder,
            ShutdownContextBuildItem shutdown,
            BuildProducer<CamelRuntimeBeanBuildItem> beans) {
        for (SecurityPolicyProvider provider : configuredProviders()) {
            SecurityPolicyModuleDescriptor<?> descriptor = provider.descriptor();
            beans.produce(new CamelRuntimeBeanBuildItem(
                    descriptor.defaultBeanName(),
                    descriptor.primaryBeanClass().getName(),
                    recorder.createPolicy(provider.getClass().getName(), shutdown)));
            LOG.debugf("Registering %s security policy bean '%s'", provider.name(), descriptor.defaultBeanName());
        }
    }

    @BuildStep
    void registerNativeResources(BuildProducer<NativeImageResourceBuildItem> resources) {
        List<String> paths = new ArrayList<>();
        for (SecurityPolicyProvider provider : configuredProviders()) {
            paths.addAll(nativeResources(provider.descriptor()));
        }
        if (!paths.isEmpty()) {
            resources.produce(new NativeImageResourceBuildItem(paths));
        }
    }

    private static List<SecurityPolicyProvider> configuredProviders() {
        return configured(ServiceLoader.load(
                SecurityPolicyProvider.class, Thread.currentThread().getContextClassLoader()));
    }

    /**
     * Returns the providers whose default configuration is present.
     *
     * @throws ConfigurationException if a configured provider does not support Quarkus
     */
    static List<SecurityPolicyProvider> configured(Iterable<SecurityPolicyProvider> providers) {
        List<SecurityPolicyProvider> configured = new ArrayList<>();
        for (SecurityPolicyProvider provider : providers) {
            SecurityPolicyModuleDescriptor<?> descriptor = provider.descriptor();
            if (descriptor == null) {
                LOG.warnf(
                        "The %s security policy provider (%s) does not describe its configuration and is not"
                                + " registered on Quarkus",
                        provider.name(), provider.getClass().getName());
            } else if (descriptor.isConfigured()) {
                if (!descriptor.supportedRuntimes().contains(RuntimeType.QUARKUS)) {
                    throw new ConfigurationException("The " + provider.name() + " security policy ("
                            + provider.getClass().getName() + ") is not supported on Quarkus, only on "
                            + descriptor.supportedRuntimes() + ": remove it from the application and use a"
                            + " provider that supports Quarkus, such as shiro or keycloak");
                }
                configured.add(provider);
            }
        }
        return configured;
    }

    /**
     * Returns the Forage properties file and the classpath resources of a provider that exist on the classpath.
     */
    static <C extends Config> Set<String> nativeResources(SecurityPolicyModuleDescriptor<C> descriptor) {
        C config = descriptor.createConfig(null);
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(config.name() + ".properties");
        candidates.addAll(descriptor.runtimeResources(config));

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Set<String> present = new LinkedHashSet<>();
        for (String candidate : candidates) {
            if (classLoader.getResource(candidate) != null) {
                present.add(candidate);
            } else if (!candidate.equals(config.name() + ".properties")) {
                LOG.warnf(
                        "Classpath resource '%s' used by the %s security policy was not found",
                        candidate, config.name());
            }
        }
        return present;
    }
}
