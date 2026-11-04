package io.kaoto.forage.core.security;

import java.util.EnumSet;
import java.util.Set;
import org.apache.camel.spi.AuthorizationPolicy;
import io.kaoto.forage.core.common.ForageModuleDescriptor;
import io.kaoto.forage.core.common.RuntimeType;
import io.kaoto.forage.core.util.config.Config;
import io.kaoto.forage.core.util.config.ConfigHelper;
import io.kaoto.forage.core.util.config.ConfigStore;

/**
 * Describes a {@link SecurityPolicyProvider} to the runtime adapters (Spring Boot, Quarkus), so that they can
 * detect its configuration and register its policy bean without provider-specific code.
 *
 * <p>The provider's default (unprefixed) configuration is registered under {@link #defaultBeanName()}, which must
 * match {@link SecurityPolicyProvider#beanName()}.
 *
 * @param <C> the provider's configuration type
 * @since 1.6.3
 */
public abstract class SecurityPolicyModuleDescriptor<C extends Config>
        implements ForageModuleDescriptor<C, SecurityPolicyProvider> {

    @Override
    public Class<SecurityPolicyProvider> providerClass() {
        return SecurityPolicyProvider.class;
    }

    @Override
    public Class<?> primaryBeanClass() {
        return AuthorizationPolicy.class;
    }

    @Override
    public boolean transactionEnabled(C config) {
        return false;
    }

    /**
     * Returns whether the provider's default configuration ({@code forage.<module-prefix>.*}) is present in any
     * configuration source. Runtime adapters register a policy bean only for configured providers.
     *
     * @return {@code true} if a policy bean should be registered
     */
    public boolean isConfigured() {
        return !ConfigStore.getInstance()
                .readPrefixes(createConfig(null), ConfigHelper.getDefaultPropertyRegexp(modulePrefix()))
                .isEmpty();
    }

    /**
     * Returns the runtimes the provider's policy works on. A runtime adapter refuses configured providers that do not
     * support it, instead of registering a policy that would fail at runtime.
     *
     * @return the supported runtimes; all of them by default
     */
    public Set<RuntimeType> supportedRuntimes() {
        return EnumSet.allOf(RuntimeType.class);
    }

    /**
     * Returns the classpath resources the policy reads when it is created, without a {@code classpath:} scheme
     * (for example a Shiro INI file). A Quarkus native image must include them.
     *
     * @param config the provider's default configuration
     * @return the resource paths; empty by default
     */
    public Set<String> runtimeResources(C config) {
        return Set.of();
    }

    /**
     * Returns the classpath resource path for a {@code classpath:} location, or {@code null} for other locations.
     *
     * @param location a resource location, possibly {@code null}
     * @return the resource path without the scheme and leading slash, or {@code null}
     */
    protected static String classpathResource(String location) {
        if (location == null || !location.startsWith("classpath:")) {
            return null;
        }
        String path = location.substring("classpath:".length());
        return path.startsWith("/") ? path.substring(1) : path;
    }
}
