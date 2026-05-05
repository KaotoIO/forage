package io.kaoto.forage.security.shiro;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import io.kaoto.forage.core.util.config.AbstractConfig;

import static io.kaoto.forage.security.shiro.ShiroSecurityPolicyConfigEntries.ALL_PERMISSIONS_REQUIRED;
import static io.kaoto.forage.security.shiro.ShiroSecurityPolicyConfigEntries.ALL_ROLES_REQUIRED;
import static io.kaoto.forage.security.shiro.ShiroSecurityPolicyConfigEntries.ALWAYS_REAUTHENTICATE;
import static io.kaoto.forage.security.shiro.ShiroSecurityPolicyConfigEntries.INI_RESOURCE_PATH;
import static io.kaoto.forage.security.shiro.ShiroSecurityPolicyConfigEntries.PASSPHRASE;
import static io.kaoto.forage.security.shiro.ShiroSecurityPolicyConfigEntries.PERMISSIONS;
import static io.kaoto.forage.security.shiro.ShiroSecurityPolicyConfigEntries.ROLES;

/**
 * Configuration class for the Shiro security policy.
 *
 * <p>Supports configuration of:
 * <ul>
 *   <li>ini.resource.path: Path to the Shiro INI file (required)</li>
 *   <li>passphrase: Base64-encoded passphrase for token encryption</li>
 *   <li>always.reauthenticate: Whether to re-authenticate on every exchange</li>
 *   <li>roles: Comma-separated required roles</li>
 *   <li>all.roles.required: Whether all roles must be present</li>
 *   <li>permissions: Comma-separated required permissions</li>
 *   <li>all.permissions.required: Whether all permissions must be present</li>
 * </ul>
 *
 * @since 1.3
 */
public class ShiroSecurityPolicyConfig extends AbstractConfig {

    public ShiroSecurityPolicyConfig() {
        this(null);
    }

    public ShiroSecurityPolicyConfig(String prefix) {
        super(prefix, ShiroSecurityPolicyConfigEntries.class);
    }

    @Override
    public String name() {
        return "forage-security-shiro";
    }

    public String iniResourcePath() {
        return getRequired(INI_RESOURCE_PATH, "Missing Shiro INI resource path configuration");
    }

    public Optional<String> passphrase() {
        return get(PASSPHRASE);
    }

    public boolean alwaysReauthenticate() {
        return get(ALWAYS_REAUTHENTICATE).map(Boolean::parseBoolean).orElse(false);
    }

    public List<String> roles() {
        return get(ROLES)
                .map(r -> Arrays.stream(r.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    public boolean allRolesRequired() {
        return get(ALL_ROLES_REQUIRED).map(Boolean::parseBoolean).orElse(false);
    }

    public List<String> permissions() {
        return get(PERMISSIONS)
                .map(p -> Arrays.stream(p.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    public boolean allPermissionsRequired() {
        return get(ALL_PERMISSIONS_REQUIRED).map(Boolean::parseBoolean).orElse(false);
    }
}
