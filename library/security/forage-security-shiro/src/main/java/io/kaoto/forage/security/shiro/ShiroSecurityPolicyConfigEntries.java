package io.kaoto.forage.security.shiro;

import io.kaoto.forage.core.util.config.ConfigEntries;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigTag;

/**
 * Configuration entries for the Shiro security policy provider.
 *
 * @since 1.3
 */
public final class ShiroSecurityPolicyConfigEntries extends ConfigEntries {

    public static final ConfigModule INI_RESOURCE_PATH = ConfigModule.of(
            ShiroSecurityPolicyConfig.class,
            "forage.shiro.ini.resource.path",
            "Path to the Shiro INI configuration file",
            "INI Resource Path",
            null,
            "string",
            true,
            ConfigTag.COMMON);

    public static final ConfigModule PASSPHRASE = ConfigModule.of(
            ShiroSecurityPolicyConfig.class,
            "forage.shiro.passphrase",
            "Base64-encoded passphrase for Shiro security token encryption",
            "Passphrase",
            null,
            "string",
            false,
            ConfigTag.SECURITY);

    public static final ConfigModule ALWAYS_REAUTHENTICATE = ConfigModule.of(
            ShiroSecurityPolicyConfig.class,
            "forage.shiro.always.reauthenticate",
            "Whether to re-authenticate on every exchange",
            "Always Re-authenticate",
            "false",
            "boolean",
            false,
            ConfigTag.COMMON);

    public static final ConfigModule ROLES = ConfigModule.of(
            ShiroSecurityPolicyConfig.class,
            "forage.shiro.roles",
            "Comma-separated list of required roles",
            "Roles",
            null,
            "string",
            false,
            ConfigTag.COMMON);

    public static final ConfigModule ALL_ROLES_REQUIRED = ConfigModule.of(
            ShiroSecurityPolicyConfig.class,
            "forage.shiro.all.roles.required",
            "Whether all roles must be present (true) or any role suffices (false)",
            "All Roles Required",
            "false",
            "boolean",
            false,
            ConfigTag.COMMON);

    public static final ConfigModule PERMISSIONS = ConfigModule.of(
            ShiroSecurityPolicyConfig.class,
            "forage.shiro.permissions",
            "Comma-separated list of required permission strings",
            "Permissions",
            null,
            "string",
            false,
            ConfigTag.COMMON);

    public static final ConfigModule ALL_PERMISSIONS_REQUIRED = ConfigModule.of(
            ShiroSecurityPolicyConfig.class,
            "forage.shiro.all.permissions.required",
            "Whether all permissions must be present (true) or any permission suffices (false)",
            "All Permissions Required",
            "false",
            "boolean",
            false,
            ConfigTag.COMMON);

    static {
        initModules(
                ShiroSecurityPolicyConfigEntries.class,
                INI_RESOURCE_PATH,
                PASSPHRASE,
                ALWAYS_REAUTHENTICATE,
                ROLES,
                ALL_ROLES_REQUIRED,
                PERMISSIONS,
                ALL_PERMISSIONS_REQUIRED);
    }
}
