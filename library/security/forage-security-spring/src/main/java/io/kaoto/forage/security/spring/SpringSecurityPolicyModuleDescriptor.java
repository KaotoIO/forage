package io.kaoto.forage.security.spring;

import java.util.EnumSet;
import java.util.Set;
import org.apache.camel.component.spring.security.SpringSecurityAuthorizationPolicy;
import io.kaoto.forage.core.common.RuntimeType;
import io.kaoto.forage.core.security.SecurityPolicyModuleDescriptor;

/**
 * Describes the Spring Security policy provider to the runtime adapters.
 *
 * <p>Spring Security is not available on Quarkus. On Spring Boot the policy is wired with the application's
 * {@code AuthenticationManager} and {@code AuthorizationManager} by the Forage security policy starter.
 *
 * @since 1.6.3
 */
public class SpringSecurityPolicyModuleDescriptor extends SecurityPolicyModuleDescriptor<SpringSecurityPolicyConfig> {

    public static final String DEFAULT_BEAN_NAME = "springSecurityPolicy";

    @Override
    public String modulePrefix() {
        return "spring.security";
    }

    @Override
    public SpringSecurityPolicyConfig createConfig(String prefix) {
        return new SpringSecurityPolicyConfig(prefix);
    }

    @Override
    public String resolveProviderClassName(SpringSecurityPolicyConfig config) {
        return SpringSecurityPolicyProvider.class.getName();
    }

    @Override
    public String defaultBeanName() {
        return DEFAULT_BEAN_NAME;
    }

    @Override
    public Class<?> primaryBeanClass() {
        return SpringSecurityAuthorizationPolicy.class;
    }

    /**
     * Every property of the Spring Security policy has a default, so the policy is registered as soon as the module is
     * on the classpath, as on plain Camel.
     */
    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public Set<RuntimeType> supportedRuntimes() {
        return EnumSet.of(RuntimeType.MAIN, RuntimeType.SPRING_BOOT);
    }
}
