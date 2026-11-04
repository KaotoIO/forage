package io.kaoto.forage.quarkus.security.tls.deployment;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.apache.camel.quarkus.core.deployment.spi.CamelRuntimeBeanBuildItem;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.jboss.logging.Logger;
import io.kaoto.forage.core.annotations.FactoryType;
import io.kaoto.forage.core.annotations.FactoryVariant;
import io.kaoto.forage.core.annotations.ForageFactory;
import io.kaoto.forage.core.common.ForageQuarkusConfigSourceAdapter;
import io.kaoto.forage.core.util.config.ConfigHelper;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.quarkus.security.tls.ForageTlsRecorder;
import io.kaoto.forage.security.tls.TlsConfig;
import io.kaoto.forage.security.tls.TlsModuleDescriptor;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;

/**
 * Registers an {@link SSLContextParameters} bean per TLS profile in the Camel registry.
 *
 * <p>Which profiles exist is decided during the build, from the {@code forage.tls.*} and
 * {@code forage.<name>.tls.*} properties; the parameters are created at runtime initialization, from the runtime
 * configuration. For native images, the keystores and truststores loaded from the classpath and the Forage
 * properties file are included in the image.
 */
@ForageFactory(
        value = "TLS Context (Quarkus)",
        components = {},
        description = "SSLContextParameters created at Quarkus runtime initialization",
        type = FactoryType.SSL_CONTEXT_PARAMETERS,
        defaultBeanName = TlsModuleDescriptor.DEFAULT_BEAN_NAME,
        autowired = true,
        configClass = TlsConfig.class,
        variant = FactoryVariant.QUARKUS)
public class ForageTlsProcessor {
    private static final Logger LOG = Logger.getLogger(ForageTlsProcessor.class);

    private static final String FEATURE = "forage-security-tls";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void registerSslContextParameters(ForageTlsRecorder recorder, BuildProducer<CamelRuntimeBeanBuildItem> beans) {
        profiles().forEach((name, prefix) -> {
            beans.produce(new CamelRuntimeBeanBuildItem(
                    name, SSLContextParameters.class.getName(), recorder.createSslContextParameters(prefix)));
            LOG.debugf("Registering SSLContextParameters bean '%s'", name);
        });
    }

    @BuildStep
    void registerNativeResources(BuildProducer<NativeImageResourceBuildItem> resources) {
        Map<String, String> profiles = profiles();
        if (profiles.isEmpty()) {
            return;
        }
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Set<String> paths = new LinkedHashSet<>();
        String propertiesFile = new TlsConfig().name() + ".properties";
        if (classLoader.getResource(propertiesFile) != null) {
            paths.add(propertiesFile);
        }
        for (String prefix : profiles.values()) {
            TlsConfig config = new TlsConfig(prefix);
            addClasspathResource(config.keystorePath(), classLoader, paths);
            addClasspathResource(config.truststorePath(), classLoader, paths);
        }
        if (!paths.isEmpty()) {
            resources.produce(new NativeImageResourceBuildItem(paths.toArray(String[]::new)));
        }
    }

    /**
     * Returns the profiles creating a bean, by bean name: the named profiles when there are any, otherwise the
     * default profile, as {@code TlsBeanFactory} does. The value is the configuration prefix ({@code null} for the
     * default profile).
     */
    static Map<String, String> profiles() {
        TlsModuleDescriptor descriptor = new TlsModuleDescriptor();
        TlsConfig defaultConfig = descriptor.createConfig(null);
        Set<String> prefixes = new HashSet<>(ConfigStore.getInstance()
                .readPrefixes(defaultConfig, ConfigHelper.getNamedPropertyRegexp(descriptor.modulePrefix())));
        prefixes.addAll(ForageQuarkusConfigSourceAdapter.getDiscoveredPrefixes(descriptor.modulePrefix()));

        Map<String, String> profiles = new LinkedHashMap<>();
        if (!prefixes.isEmpty()) {
            for (String prefix : prefixes.stream().sorted().toList()) {
                if (descriptor.createsBean(descriptor.createConfig(prefix))) {
                    profiles.put(prefix, prefix);
                }
            }
        } else if (descriptor.createsBean(defaultConfig)) {
            profiles.put(descriptor.defaultBeanName(), null);
        }
        return profiles;
    }

    /**
     * Adds a store loaded from the classpath: a {@code classpath:} location, or a location without a scheme, which
     * Camel resolves from the classpath, when it exists there.
     */
    static void addClasspathResource(String location, ClassLoader classLoader, Set<String> paths) {
        if (location == null) {
            return;
        }
        String path;
        if (location.startsWith("classpath:")) {
            path = location.substring("classpath:".length());
        } else if (!location.contains(":")) {
            path = location;
        } else {
            return;
        }
        path = path.startsWith("/") ? path.substring(1) : path;
        if (classLoader.getResource(path) != null) {
            paths.add(path);
        } else if (location.startsWith("classpath:")) {
            LOG.warnf("TLS store '%s' was not found on the classpath", location);
        }
    }
}
