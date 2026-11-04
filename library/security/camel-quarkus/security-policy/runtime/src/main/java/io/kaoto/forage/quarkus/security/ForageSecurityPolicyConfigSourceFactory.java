package io.kaoto.forage.quarkus.security;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import org.eclipse.microprofile.config.spi.ConfigSource;
import io.kaoto.forage.core.common.ForageModuleDescriptor;
import io.kaoto.forage.core.common.ForageQuarkusConfigSourceAdapter;
import io.kaoto.forage.core.security.SecurityPolicyModuleDescriptor;
import io.kaoto.forage.core.security.SecurityPolicyProvider;
import io.kaoto.forage.core.util.config.Config;
import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;

/**
 * Makes the security policy properties of every provider on the classpath visible to Forage on Quarkus.
 *
 * <p>The policies need no Quarkus properties, so no configuration source is contributed: the
 * {@link ForageQuarkusConfigSourceAdapter} of each provider registers the {@code forage.*} values of all Quarkus
 * configuration sources (profiles, YAML, environment) into the Forage configuration store, where the deployment
 * processor detects the configured providers during the build.
 */
public class ForageSecurityPolicyConfigSourceFactory implements ConfigSourceFactory {

    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context) {
        List<ConfigSource> sources = new ArrayList<>();
        for (SecurityPolicyProvider provider : ServiceLoader.load(
                SecurityPolicyProvider.class, Thread.currentThread().getContextClassLoader())) {
            SecurityPolicyModuleDescriptor<?> descriptor = provider.descriptor();
            if (descriptor != null) {
                adapter(descriptor).getConfigSources(context).forEach(sources::add);
            }
        }
        return sources;
    }

    private static <C extends Config> ForageQuarkusConfigSourceAdapter<C> adapter(
            SecurityPolicyModuleDescriptor<C> descriptor) {
        return new ForageQuarkusConfigSourceAdapter<>() {
            @Override
            protected ForageModuleDescriptor<C, ?> descriptor() {
                return descriptor;
            }
        };
    }
}
