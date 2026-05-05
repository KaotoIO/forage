package io.kaoto.forage.security.keycloak;

import io.kaoto.forage.core.util.config.ConfigEntries;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigTag;

/**
 * Configuration entries for the Keycloak security policy provider.
 *
 * @since 1.3
 */
public final class KeycloakSecurityPolicyConfigEntries extends ConfigEntries {

    public static final ConfigModule SERVER_URL = ConfigModule.of(
            KeycloakSecurityPolicyConfig.class,
            "forage.keycloak.server.url",
            "Keycloak server URL",
            "Server URL",
            null,
            "string",
            true,
            ConfigTag.COMMON);

    public static final ConfigModule REALM = ConfigModule.of(
            KeycloakSecurityPolicyConfig.class,
            "forage.keycloak.realm",
            "Keycloak realm name",
            "Realm",
            null,
            "string",
            true,
            ConfigTag.COMMON);

    public static final ConfigModule CLIENT_ID = ConfigModule.of(
            KeycloakSecurityPolicyConfig.class,
            "forage.keycloak.client.id",
            "Keycloak client ID",
            "Client ID",
            null,
            "string",
            true,
            ConfigTag.COMMON);

    public static final ConfigModule CLIENT_SECRET = ConfigModule.of(
            KeycloakSecurityPolicyConfig.class,
            "forage.keycloak.client.secret",
            "Keycloak client secret",
            "Client Secret",
            null,
            "string",
            true,
            ConfigTag.SECURITY);

    public static final ConfigModule REQUIRED_ROLES = ConfigModule.of(
            KeycloakSecurityPolicyConfig.class,
            "forage.keycloak.required.roles",
            "Comma-separated list of required roles",
            "Required Roles",
            null,
            "string",
            false,
            ConfigTag.COMMON);

    public static final ConfigModule ALL_ROLES_REQUIRED = ConfigModule.of(
            KeycloakSecurityPolicyConfig.class,
            "forage.keycloak.all.roles.required",
            "Whether all roles must be present (true) or any role suffices (false)",
            "All Roles Required",
            "false",
            "boolean",
            false,
            ConfigTag.COMMON);

    public static final ConfigModule REQUIRED_PERMISSIONS = ConfigModule.of(
            KeycloakSecurityPolicyConfig.class,
            "forage.keycloak.required.permissions",
            "Comma-separated list of required permissions",
            "Required Permissions",
            null,
            "string",
            false,
            ConfigTag.COMMON);

    public static final ConfigModule ALL_PERMISSIONS_REQUIRED = ConfigModule.of(
            KeycloakSecurityPolicyConfig.class,
            "forage.keycloak.all.permissions.required",
            "Whether all permissions must be present (true) or any permission suffices (false)",
            "All Permissions Required",
            "false",
            "boolean",
            false,
            ConfigTag.COMMON);

    public static final ConfigModule USE_TOKEN_INTROSPECTION = ConfigModule.of(
            KeycloakSecurityPolicyConfig.class,
            "forage.keycloak.use.token.introspection",
            "Whether to use token introspection endpoint for validation",
            "Use Token Introspection",
            "false",
            "boolean",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule INTROSPECTION_CACHE_ENABLED = ConfigModule.of(
            KeycloakSecurityPolicyConfig.class,
            "forage.keycloak.introspection.cache.enabled",
            "Whether to cache token introspection results",
            "Introspection Cache Enabled",
            "false",
            "boolean",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule INTROSPECTION_CACHE_TTL = ConfigModule.of(
            KeycloakSecurityPolicyConfig.class,
            "forage.keycloak.introspection.cache.ttl",
            "Time-to-live for cached introspection results in milliseconds",
            "Introspection Cache TTL",
            "300000",
            "long",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule VALIDATE_ISSUER = ConfigModule.of(
            KeycloakSecurityPolicyConfig.class,
            "forage.keycloak.validate.issuer",
            "Whether to validate the token issuer",
            "Validate Issuer",
            "true",
            "boolean",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule AUTO_FETCH_PUBLIC_KEY = ConfigModule.of(
            KeycloakSecurityPolicyConfig.class,
            "forage.keycloak.auto.fetch.public.key",
            "Whether to automatically fetch the public key from Keycloak",
            "Auto Fetch Public Key",
            "true",
            "boolean",
            false,
            ConfigTag.ADVANCED);

    static {
        initModules(
                KeycloakSecurityPolicyConfigEntries.class,
                SERVER_URL,
                REALM,
                CLIENT_ID,
                CLIENT_SECRET,
                REQUIRED_ROLES,
                ALL_ROLES_REQUIRED,
                REQUIRED_PERMISSIONS,
                ALL_PERMISSIONS_REQUIRED,
                USE_TOKEN_INTROSPECTION,
                INTROSPECTION_CACHE_ENABLED,
                INTROSPECTION_CACHE_TTL,
                VALIDATE_ISSUER,
                AUTO_FETCH_PUBLIC_KEY);
    }
}
