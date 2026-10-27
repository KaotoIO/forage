package io.kaoto.forage.core.security;

import org.apache.camel.spi.AuthorizationPolicy;
import io.kaoto.forage.core.common.BeanProvider;

/**
 * Provider interface for creating security policy instances.
 *
 * <p>Implementations are discovered via ServiceLoader and create configured
 * {@link AuthorizationPolicy} instances based on the configuration ID
 * passed to the create method.
 *
 * <p><strong>Configuration:</strong>
 * Configuration properties follow the pattern:
 * {@code forage.<technology>.<property>}
 *
 * <p><strong>Usage:</strong>
 * <pre>{@code
 * @ForageBean(value = "shiro", components = {"camel-shiro"}, ...)
 * public class ShiroSecurityPolicyProvider implements SecurityPolicyProvider {
 *     @Override
 *     public String name() {
 *         return "shiro";
 *     }
 *
 *     @Override
 *     public AuthorizationPolicy create(String id) {
 *         ShiroSecurityPolicyConfig config = new ShiroSecurityPolicyConfig(id);
 *         return createPolicy(config);
 *     }
 * }
 * }</pre>
 *
 * @see BeanProvider
 * @see AuthorizationPolicy
 * @since 1.3
 */
public interface SecurityPolicyProvider extends BeanProvider<AuthorizationPolicy> {

    /**
     * Returns the security policy provider name.
     *
     * <p>This name is used to identify the provider and should match
     * the value specified in the @ForageBean annotation.
     *
     * @return the provider name (e.g., "shiro", "spring-security")
     */
    String name();

    /**
     * Returns the registry name of the provider's default policy bean.
     *
     * <p>Providers whose technology name is not a suitable Camel bean name may override this
     * method. For example, a provider named {@code spring-security} can expose the conventional
     * bean name {@code springSecurityPolicy}.
     *
     * @return the registry name for the default policy bean
     */
    default String beanName() {
        return name() + "Policy";
    }

    /**
     * Creates an AuthorizationPolicy with the given configuration ID.
     *
     * <p>The ID is used to load provider-specific configuration from
     * the ConfigStore, enabling multiple named policy instances.
     *
     * @param id the configuration ID for this policy instance, or null for default
     * @return a configured AuthorizationPolicy instance
     */
    @Override
    AuthorizationPolicy create(String id);
}
