package io.kaoto.forage.security.spring;

import io.kaoto.forage.core.util.config.AbstractConfig;

import static io.kaoto.forage.security.spring.SpringSecurityPolicyConfigEntries.ALWAYS_REAUTHENTICATE;
import static io.kaoto.forage.security.spring.SpringSecurityPolicyConfigEntries.ID;
import static io.kaoto.forage.security.spring.SpringSecurityPolicyConfigEntries.USE_THREAD_SECURITY_CONTEXT;

/**
 * Configuration class for the Spring Security authorization policy.
 *
 * <p>Supports configuration of:
 * <ul>
 *   <li>id: Bean identifier for the policy (default: springSecurityPolicy)</li>
 *   <li>always.reauthenticate: Whether to re-authenticate on every exchange (default: false)</li>
 *   <li>use.thread.security.context: Whether to use thread-local SecurityContext (default: true)</li>
 * </ul>
 *
 * <p>Note: An {@code AuthenticationManager} bean must be provided separately
 * (e.g., via Spring Boot auto-configuration) for the policy to function at runtime.
 *
 * @since 1.3
 */
public class SpringSecurityPolicyConfig extends AbstractConfig {

    public SpringSecurityPolicyConfig() {
        this(null);
    }

    public SpringSecurityPolicyConfig(String prefix) {
        super(prefix, SpringSecurityPolicyConfigEntries.class);
    }

    @Override
    public String name() {
        return "forage-security-spring";
    }

    public String id() {
        return get(ID).orElse(ID.defaultValue());
    }

    public boolean alwaysReauthenticate() {
        return get(ALWAYS_REAUTHENTICATE).map(Boolean::parseBoolean).orElse(false);
    }

    public boolean useThreadSecurityContext() {
        return get(USE_THREAD_SECURITY_CONTEXT).map(Boolean::parseBoolean).orElse(true);
    }
}
