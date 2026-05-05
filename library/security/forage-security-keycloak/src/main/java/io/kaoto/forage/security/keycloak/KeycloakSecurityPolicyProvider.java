package io.kaoto.forage.security.keycloak;

import org.apache.camel.component.keycloak.security.KeycloakSecurityPolicy;
import org.apache.camel.spi.AuthorizationPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.core.annotations.ForageBean;
import io.kaoto.forage.core.security.SecurityPolicyProvider;

/**
 * Provider for creating Keycloak security policies.
 *
 * <p>This provider creates instances of {@link KeycloakSecurityPolicy} using
 * configuration values managed by {@link KeycloakSecurityPolicyConfig}.
 *
 * <p><strong>Configuration:</strong>
 * <ul>
 *   <li>forage.keycloak.server.url: Keycloak server URL (required)</li>
 *   <li>forage.keycloak.realm: Keycloak realm name (required)</li>
 *   <li>forage.keycloak.client.id: Keycloak client ID (required)</li>
 *   <li>forage.keycloak.client.secret: Keycloak client secret (required)</li>
 *   <li>forage.keycloak.required.roles: Comma-separated required roles</li>
 *   <li>forage.keycloak.all.roles.required: All roles must be present (default: false)</li>
 *   <li>forage.keycloak.required.permissions: Comma-separated required permissions</li>
 *   <li>forage.keycloak.all.permissions.required: All permissions must be present (default: false)</li>
 *   <li>forage.keycloak.use.token.introspection: Use token introspection (default: false)</li>
 *   <li>forage.keycloak.introspection.cache.enabled: Cache introspection results (default: false)</li>
 *   <li>forage.keycloak.introspection.cache.ttl: Introspection cache TTL in ms (default: 300000)</li>
 *   <li>forage.keycloak.validate.issuer: Validate token issuer (default: true)</li>
 *   <li>forage.keycloak.auto.fetch.public.key: Auto-fetch public key (default: true)</li>
 * </ul>
 *
 * @see KeycloakSecurityPolicyConfig
 * @see KeycloakSecurityPolicy
 * @since 1.3
 */
@ForageBean(
        value = "keycloak",
        components = {"camel-keycloak"},
        feature = "Security Policy",
        description = "Keycloak security authorization policy")
public class KeycloakSecurityPolicyProvider implements SecurityPolicyProvider {
    private static final Logger LOG = LoggerFactory.getLogger(KeycloakSecurityPolicyProvider.class);

    @Override
    public String name() {
        return "keycloak";
    }

    @Override
    public AuthorizationPolicy create(String id) {
        LOG.debug("Creating Keycloak security policy with id: {}", id);

        KeycloakSecurityPolicyConfig config = new KeycloakSecurityPolicyConfig(id);

        KeycloakSecurityPolicy policy = new KeycloakSecurityPolicy(
                config.serverUrl(), config.realm(), config.clientId(), config.clientSecret());

        config.requiredRoles().ifPresent(policy::setRequiredRoles);
        policy.setAllRolesRequired(config.allRolesRequired());

        config.requiredPermissions().ifPresent(policy::setRequiredPermissions);
        policy.setAllPermissionsRequired(config.allPermissionsRequired());

        policy.setUseTokenIntrospection(config.useTokenIntrospection());
        policy.setIntrospectionCacheEnabled(config.introspectionCacheEnabled());
        policy.setIntrospectionCacheTtl(config.introspectionCacheTtl());
        policy.setValidateIssuer(config.validateIssuer());
        policy.setAutoFetchPublicKey(config.autoFetchPublicKey());

        LOG.info("Created Keycloak security policy for realm '{}' on server '{}'", config.realm(), config.serverUrl());

        return policy;
    }
}
