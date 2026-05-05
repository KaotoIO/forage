package io.kaoto.forage.security.shiro;

import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.camel.component.shiro.security.ShiroSecurityPolicy;
import org.apache.camel.spi.AuthorizationPolicy;
import org.apache.shiro.authz.Permission;
import org.apache.shiro.authz.permission.WildcardPermission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.core.annotations.ForageBean;
import io.kaoto.forage.core.security.SecurityPolicyProvider;

/**
 * Provider for creating Apache Shiro security policies.
 *
 * <p>This provider creates instances of {@link ShiroSecurityPolicy} using
 * configuration values managed by {@link ShiroSecurityPolicyConfig}.
 *
 * <p><strong>Configuration:</strong>
 * <ul>
 *   <li>forage.shiro.ini.resource.path: Path to the Shiro INI file (required)</li>
 *   <li>forage.shiro.passphrase: Base64-encoded passphrase for token encryption</li>
 *   <li>forage.shiro.always.reauthenticate: Re-authenticate on every exchange (default: false)</li>
 *   <li>forage.shiro.roles: Comma-separated required roles</li>
 *   <li>forage.shiro.all.roles.required: All roles must be present (default: false)</li>
 *   <li>forage.shiro.permissions: Comma-separated required permissions</li>
 *   <li>forage.shiro.all.permissions.required: All permissions must be present (default: false)</li>
 * </ul>
 *
 * @see ShiroSecurityPolicyConfig
 * @see ShiroSecurityPolicy
 * @since 1.3
 */
@ForageBean(
        value = "shiro",
        components = {"camel-shiro"},
        feature = "Security Policy",
        description = "Apache Shiro security authorization policy")
public class ShiroSecurityPolicyProvider implements SecurityPolicyProvider {
    private static final Logger LOG = LoggerFactory.getLogger(ShiroSecurityPolicyProvider.class);

    @Override
    public String name() {
        return "shiro";
    }

    @Override
    public AuthorizationPolicy create(String id) {
        LOG.debug("Creating Shiro security policy with id: {}", id);

        ShiroSecurityPolicyConfig config = new ShiroSecurityPolicyConfig(id);
        String iniResourcePath = config.iniResourcePath();

        ShiroSecurityPolicy policy;
        if (config.passphrase().isPresent()) {
            byte[] passPhrase = Base64.getDecoder().decode(config.passphrase().get());
            policy = new ShiroSecurityPolicy(iniResourcePath, passPhrase);
        } else {
            policy = new ShiroSecurityPolicy(iniResourcePath);
        }

        policy.setAlwaysReauthenticate(config.alwaysReauthenticate());

        List<String> roles = config.roles();
        if (!roles.isEmpty()) {
            policy.setRolesList(roles);
            policy.setAllRolesRequired(config.allRolesRequired());
        }

        List<String> permissions = config.permissions();
        if (!permissions.isEmpty()) {
            List<Permission> permissionObjects =
                    permissions.stream().map(WildcardPermission::new).collect(Collectors.toList());
            policy.setPermissionsList(permissionObjects);
            policy.setAllPermissionsRequired(config.allPermissionsRequired());
        }

        LOG.info(
                "Created Shiro security policy from INI '{}' with {} role(s) and {} permission(s)",
                iniResourcePath,
                roles.size(),
                permissions.size());

        return policy;
    }
}
