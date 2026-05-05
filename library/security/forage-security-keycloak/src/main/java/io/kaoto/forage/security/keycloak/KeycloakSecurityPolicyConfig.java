package io.kaoto.forage.security.keycloak;

import java.util.Optional;
import io.kaoto.forage.core.util.config.AbstractConfig;

import static io.kaoto.forage.security.keycloak.KeycloakSecurityPolicyConfigEntries.ALL_PERMISSIONS_REQUIRED;
import static io.kaoto.forage.security.keycloak.KeycloakSecurityPolicyConfigEntries.ALL_ROLES_REQUIRED;
import static io.kaoto.forage.security.keycloak.KeycloakSecurityPolicyConfigEntries.AUTO_FETCH_PUBLIC_KEY;
import static io.kaoto.forage.security.keycloak.KeycloakSecurityPolicyConfigEntries.CLIENT_ID;
import static io.kaoto.forage.security.keycloak.KeycloakSecurityPolicyConfigEntries.CLIENT_SECRET;
import static io.kaoto.forage.security.keycloak.KeycloakSecurityPolicyConfigEntries.INTROSPECTION_CACHE_ENABLED;
import static io.kaoto.forage.security.keycloak.KeycloakSecurityPolicyConfigEntries.INTROSPECTION_CACHE_TTL;
import static io.kaoto.forage.security.keycloak.KeycloakSecurityPolicyConfigEntries.REALM;
import static io.kaoto.forage.security.keycloak.KeycloakSecurityPolicyConfigEntries.REQUIRED_PERMISSIONS;
import static io.kaoto.forage.security.keycloak.KeycloakSecurityPolicyConfigEntries.REQUIRED_ROLES;
import static io.kaoto.forage.security.keycloak.KeycloakSecurityPolicyConfigEntries.SERVER_URL;
import static io.kaoto.forage.security.keycloak.KeycloakSecurityPolicyConfigEntries.USE_TOKEN_INTROSPECTION;
import static io.kaoto.forage.security.keycloak.KeycloakSecurityPolicyConfigEntries.VALIDATE_ISSUER;

/**
 * Configuration class for the Keycloak security policy.
 *
 * <p>Supports configuration of:
 * <ul>
 *   <li>server.url: Keycloak server URL (required)</li>
 *   <li>realm: Keycloak realm name (required)</li>
 *   <li>client.id: Keycloak client ID (required)</li>
 *   <li>client.secret: Keycloak client secret (required)</li>
 *   <li>required.roles: Comma-separated required roles</li>
 *   <li>required.permissions: Comma-separated required permissions</li>
 *   <li>use.token.introspection: Whether to use token introspection</li>
 *   <li>validate.issuer: Whether to validate the token issuer</li>
 *   <li>auto.fetch.public.key: Whether to auto-fetch the public key</li>
 * </ul>
 *
 * @since 1.3
 */
public class KeycloakSecurityPolicyConfig extends AbstractConfig {

    public KeycloakSecurityPolicyConfig() {
        this(null);
    }

    public KeycloakSecurityPolicyConfig(String prefix) {
        super(prefix, KeycloakSecurityPolicyConfigEntries.class);
    }

    @Override
    public String name() {
        return "forage-security-keycloak";
    }

    public String serverUrl() {
        return getRequired(SERVER_URL, "Missing Keycloak server URL configuration");
    }

    public String realm() {
        return getRequired(REALM, "Missing Keycloak realm configuration");
    }

    public String clientId() {
        return getRequired(CLIENT_ID, "Missing Keycloak client ID configuration");
    }

    public String clientSecret() {
        return getRequired(CLIENT_SECRET, "Missing Keycloak client secret configuration");
    }

    public Optional<String> requiredRoles() {
        return get(REQUIRED_ROLES);
    }

    public boolean allRolesRequired() {
        return get(ALL_ROLES_REQUIRED).map(Boolean::parseBoolean).orElse(false);
    }

    public Optional<String> requiredPermissions() {
        return get(REQUIRED_PERMISSIONS);
    }

    public boolean allPermissionsRequired() {
        return get(ALL_PERMISSIONS_REQUIRED).map(Boolean::parseBoolean).orElse(false);
    }

    public boolean useTokenIntrospection() {
        return get(USE_TOKEN_INTROSPECTION).map(Boolean::parseBoolean).orElse(false);
    }

    public boolean introspectionCacheEnabled() {
        return get(INTROSPECTION_CACHE_ENABLED).map(Boolean::parseBoolean).orElse(false);
    }

    public long introspectionCacheTtl() {
        return get(INTROSPECTION_CACHE_TTL).map(Long::parseLong).orElse(300000L);
    }

    public boolean validateIssuer() {
        return get(VALIDATE_ISSUER).map(Boolean::parseBoolean).orElse(true);
    }

    public boolean autoFetchPublicKey() {
        return get(AUTO_FETCH_PUBLIC_KEY).map(Boolean::parseBoolean).orElse(true);
    }
}
