package io.kaoto.forage.security.spring;

import io.kaoto.forage.core.util.config.ConfigEntries;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigTag;

/**
 * Configuration entries for the Spring Security authorization policy provider.
 *
 * @since 1.3
 */
public final class SpringSecurityPolicyConfigEntries extends ConfigEntries {

    public static final ConfigModule ID = ConfigModule.of(
            SpringSecurityPolicyConfig.class,
            "forage.spring.security.id",
            "The unique identifier for the Spring Security authorization policy bean",
            "Policy ID",
            "springSecurityPolicy",
            "string",
            false,
            ConfigTag.COMMON);

    public static final ConfigModule ALWAYS_REAUTHENTICATE = ConfigModule.of(
            SpringSecurityPolicyConfig.class,
            "forage.spring.security.always.reauthenticate",
            "Whether to re-authenticate on every exchange",
            "Always Re-authenticate",
            "false",
            "boolean",
            false,
            ConfigTag.COMMON);

    public static final ConfigModule USE_THREAD_SECURITY_CONTEXT = ConfigModule.of(
            SpringSecurityPolicyConfig.class,
            "forage.spring.security.use.thread.security.context",
            "Whether to use the thread-local SecurityContext for authentication",
            "Use Thread Security Context",
            "true",
            "boolean",
            false,
            ConfigTag.COMMON);

    static {
        initModules(SpringSecurityPolicyConfigEntries.class, ID, ALWAYS_REAUTHENTICATE, USE_THREAD_SECURITY_CONTEXT);
    }
}
