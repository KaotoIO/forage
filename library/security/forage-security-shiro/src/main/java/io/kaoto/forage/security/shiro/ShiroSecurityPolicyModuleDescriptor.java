package io.kaoto.forage.security.shiro;

import java.util.Set;
import org.apache.camel.component.shiro.security.ShiroSecurityPolicy;
import io.kaoto.forage.core.security.SecurityPolicyModuleDescriptor;
import io.kaoto.forage.core.util.config.MissingConfigException;

/**
 * Describes the Shiro security policy provider to the Spring Boot and Quarkus adapters.
 *
 * @since 1.6.3
 */
public class ShiroSecurityPolicyModuleDescriptor extends SecurityPolicyModuleDescriptor<ShiroSecurityPolicyConfig> {

    public static final String DEFAULT_BEAN_NAME = "shiroPolicy";

    @Override
    public String modulePrefix() {
        return "shiro";
    }

    @Override
    public ShiroSecurityPolicyConfig createConfig(String prefix) {
        return new ShiroSecurityPolicyConfig(prefix);
    }

    @Override
    public String resolveProviderClassName(ShiroSecurityPolicyConfig config) {
        return ShiroSecurityPolicyProvider.class.getName();
    }

    @Override
    public String defaultBeanName() {
        return DEFAULT_BEAN_NAME;
    }

    @Override
    public Class<?> primaryBeanClass() {
        return ShiroSecurityPolicy.class;
    }

    /**
     * Returns the INI file when it is loaded from the classpath ({@code classpath:} location).
     */
    @Override
    public Set<String> runtimeResources(ShiroSecurityPolicyConfig config) {
        try {
            String resource = classpathResource(config.iniResourcePath());
            return resource == null ? Set.of() : Set.of(resource);
        } catch (MissingConfigException e) {
            // Reported when the policy is created
            return Set.of();
        }
    }
}
