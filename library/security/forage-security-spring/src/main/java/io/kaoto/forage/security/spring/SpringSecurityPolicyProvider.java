package io.kaoto.forage.security.spring;

import org.apache.camel.component.spring.security.SpringSecurityAuthorizationPolicy;
import org.apache.camel.spi.AuthorizationPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.core.annotations.ForageBean;
import io.kaoto.forage.core.security.SecurityPolicyProvider;

/**
 * Provider for creating Spring Security authorization policies.
 *
 * <p>This provider creates instances of {@link SpringSecurityAuthorizationPolicy}
 * using configuration values managed by {@link SpringSecurityPolicyConfig}.
 *
 * <p>The created policy is configured with basic properties from the Forage
 * configuration system. An {@code AuthenticationManager} and
 * {@code AuthorizationManager} must be provided separately (e.g., via Spring Boot
 * auto-configuration or programmatic setup) before the policy can be used at runtime.
 *
 * <p><strong>Configuration:</strong>
 * <ul>
 *   <li>forage.spring.security.id: Policy bean identifier (default: springSecurityPolicy)</li>
 *   <li>forage.spring.security.always.reauthenticate: Re-authenticate on every exchange (default: false)</li>
 *   <li>forage.spring.security.use.thread.security.context: Use thread-local SecurityContext (default: true)</li>
 * </ul>
 *
 * @see SpringSecurityPolicyConfig
 * @see SpringSecurityAuthorizationPolicy
 * @since 1.3
 */
@ForageBean(
        value = "spring-security",
        components = {"camel-spring-security"},
        feature = "Security Policy",
        description = "Spring Security authorization policy")
public class SpringSecurityPolicyProvider implements SecurityPolicyProvider {
    private static final Logger LOG = LoggerFactory.getLogger(SpringSecurityPolicyProvider.class);

    @Override
    public String name() {
        return "spring-security";
    }

    @Override
    public AuthorizationPolicy create(String id) {
        LOG.debug("Creating Spring Security authorization policy with id: {}", id);

        SpringSecurityPolicyConfig config = new SpringSecurityPolicyConfig(id);

        SpringSecurityAuthorizationPolicy policy = new SpringSecurityAuthorizationPolicy();
        policy.setId(config.id());
        policy.setAlwaysReauthenticate(config.alwaysReauthenticate());
        policy.setUseThreadSecurityContext(config.useThreadSecurityContext());

        LOG.info(
                "Created Spring Security authorization policy '{}' (alwaysReauthenticate={}, useThreadSecurityContext={})",
                config.id(),
                config.alwaysReauthenticate(),
                config.useThreadSecurityContext());
        LOG.warn(
                "Spring Security policy '{}' requires an AuthenticationManager and AuthorizationManager to be set"
                        + " before use (e.g., via Spring Boot auto-configuration or manual wiring)",
                config.id());

        return policy;
    }
}
