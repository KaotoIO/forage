package io.kaoto.forage.security.shiro;

import org.apache.camel.spi.AuthorizationPolicy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.Resources.SYSTEM_PROPERTIES;

@DisplayName("ShiroSecurityPolicyProvider Tests")
@ResourceLock(SYSTEM_PROPERTIES)
class ShiroSecurityPolicyProviderTest {

    @Nested
    @DisplayName("Provider Identity Tests")
    class ProviderIdentityTests {

        @Test
        @DisplayName("Should return correct provider name")
        void shouldReturnCorrectProviderName() {
            ShiroSecurityPolicyProvider provider = new ShiroSecurityPolicyProvider();

            assertThat(provider.name()).isEqualTo("shiro");
        }
    }

    @Nested
    @DisplayName("ServiceLoader Tests")
    class ServiceLoaderTests {

        @Test
        @DisplayName("Should be discoverable via ServiceLoader")
        void shouldBeDiscoverableViaServiceLoader() {
            java.util.ServiceLoader<io.kaoto.forage.core.security.SecurityPolicyProvider> loader =
                    java.util.ServiceLoader.load(io.kaoto.forage.core.security.SecurityPolicyProvider.class);

            boolean found = false;
            for (io.kaoto.forage.core.security.SecurityPolicyProvider provider : loader) {
                if (provider instanceof ShiroSecurityPolicyProvider) {
                    found = true;
                    break;
                }
            }

            assertThat(found).isTrue();
        }
    }

    @Nested
    @DisplayName("Policy Creation Tests")
    class PolicyCreationTests {

        @Test
        @DisplayName("Should create policy with INI resource path via system property")
        void shouldCreatePolicyWithIniResourcePath() {
            String propertyKey = "forage.shiro.ini.resource.path";
            System.setProperty(propertyKey, "classpath:shiro.ini");

            try {
                ShiroSecurityPolicyProvider provider = new ShiroSecurityPolicyProvider();
                AuthorizationPolicy policy = provider.create(null);

                assertThat(policy).isNotNull();
                assertThat(policy).isInstanceOf(org.apache.camel.component.shiro.security.ShiroSecurityPolicy.class);
            } finally {
                System.clearProperty(propertyKey);
            }
        }

        @Test
        @DisplayName("Should create policy with roles configured")
        void shouldCreatePolicyWithRoles() {
            System.setProperty("forage.shiro.ini.resource.path", "classpath:shiro.ini");
            System.setProperty("forage.shiro.roles", "admin,user");
            System.setProperty("forage.shiro.all.roles.required", "true");

            try {
                ShiroSecurityPolicyProvider provider = new ShiroSecurityPolicyProvider();
                AuthorizationPolicy policy = provider.create(null);

                assertThat(policy).isNotNull();
                org.apache.camel.component.shiro.security.ShiroSecurityPolicy shiroPolicy =
                        (org.apache.camel.component.shiro.security.ShiroSecurityPolicy) policy;
                assertThat(shiroPolicy.getRolesList()).containsExactly("admin", "user");
                assertThat(shiroPolicy.isAllRolesRequired()).isTrue();
            } finally {
                System.clearProperty("forage.shiro.ini.resource.path");
                System.clearProperty("forage.shiro.roles");
                System.clearProperty("forage.shiro.all.roles.required");
            }
        }

        @Test
        @DisplayName("Should create policy with permissions configured")
        void shouldCreatePolicyWithPermissions() {
            System.setProperty("forage.shiro.ini.resource.path", "classpath:shiro.ini");
            System.setProperty("forage.shiro.permissions", "zone1:readonly:*");

            try {
                ShiroSecurityPolicyProvider provider = new ShiroSecurityPolicyProvider();
                AuthorizationPolicy policy = provider.create(null);

                assertThat(policy).isNotNull();
                org.apache.camel.component.shiro.security.ShiroSecurityPolicy shiroPolicy =
                        (org.apache.camel.component.shiro.security.ShiroSecurityPolicy) policy;
                assertThat(shiroPolicy.getPermissionsList()).hasSize(1);
            } finally {
                System.clearProperty("forage.shiro.ini.resource.path");
                System.clearProperty("forage.shiro.permissions");
            }
        }

        @Test
        @DisplayName("Should create policy with always reauthenticate")
        void shouldCreatePolicyWithAlwaysReauthenticate() {
            System.setProperty("forage.shiro.ini.resource.path", "classpath:shiro.ini");
            System.setProperty("forage.shiro.always.reauthenticate", "true");

            try {
                ShiroSecurityPolicyProvider provider = new ShiroSecurityPolicyProvider();
                AuthorizationPolicy policy = provider.create(null);

                assertThat(policy).isNotNull();
                org.apache.camel.component.shiro.security.ShiroSecurityPolicy shiroPolicy =
                        (org.apache.camel.component.shiro.security.ShiroSecurityPolicy) policy;
                assertThat(shiroPolicy.isAlwaysReauthenticate()).isTrue();
            } finally {
                System.clearProperty("forage.shiro.ini.resource.path");
                System.clearProperty("forage.shiro.always.reauthenticate");
            }
        }
    }
}
