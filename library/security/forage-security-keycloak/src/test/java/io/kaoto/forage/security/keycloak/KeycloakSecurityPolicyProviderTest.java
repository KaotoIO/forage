package io.kaoto.forage.security.keycloak;

import org.apache.camel.component.keycloak.security.KeycloakSecurityPolicy;
import org.apache.camel.spi.AuthorizationPolicy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.Resources.SYSTEM_PROPERTIES;

@DisplayName("KeycloakSecurityPolicyProvider Tests")
@ResourceLock(SYSTEM_PROPERTIES)
class KeycloakSecurityPolicyProviderTest {

    @Nested
    @DisplayName("Provider Identity Tests")
    class ProviderIdentityTests {

        @Test
        @DisplayName("Should return correct provider name")
        void shouldReturnCorrectProviderName() {
            KeycloakSecurityPolicyProvider provider = new KeycloakSecurityPolicyProvider();

            assertThat(provider.name()).isEqualTo("keycloak");
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
                if (provider instanceof KeycloakSecurityPolicyProvider) {
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
        @DisplayName("Should create policy with required configuration")
        void shouldCreatePolicyWithRequiredConfiguration() {
            System.setProperty("forage.keycloak.server.url", "https://keycloak.example.com");
            System.setProperty("forage.keycloak.realm", "myrealm");
            System.setProperty("forage.keycloak.client.id", "myclient");
            System.setProperty("forage.keycloak.client.secret", "mysecret");

            try {
                KeycloakSecurityPolicyProvider provider = new KeycloakSecurityPolicyProvider();
                AuthorizationPolicy policy = provider.create(null);

                assertThat(policy).isNotNull();
                assertThat(policy).isInstanceOf(KeycloakSecurityPolicy.class);

                KeycloakSecurityPolicy keycloakPolicy = (KeycloakSecurityPolicy) policy;
                assertThat(keycloakPolicy.getServerUrl()).isEqualTo("https://keycloak.example.com");
                assertThat(keycloakPolicy.getRealm()).isEqualTo("myrealm");
                assertThat(keycloakPolicy.getClientId()).isEqualTo("myclient");
            } finally {
                System.clearProperty("forage.keycloak.server.url");
                System.clearProperty("forage.keycloak.realm");
                System.clearProperty("forage.keycloak.client.id");
                System.clearProperty("forage.keycloak.client.secret");
            }
        }

        @Test
        @DisplayName("Should create policy with roles configured")
        void shouldCreatePolicyWithRoles() {
            System.setProperty("forage.keycloak.server.url", "https://keycloak.example.com");
            System.setProperty("forage.keycloak.realm", "myrealm");
            System.setProperty("forage.keycloak.client.id", "myclient");
            System.setProperty("forage.keycloak.client.secret", "mysecret");
            System.setProperty("forage.keycloak.required.roles", "admin,user");
            System.setProperty("forage.keycloak.all.roles.required", "true");

            try {
                KeycloakSecurityPolicyProvider provider = new KeycloakSecurityPolicyProvider();
                AuthorizationPolicy policy = provider.create(null);

                KeycloakSecurityPolicy keycloakPolicy = (KeycloakSecurityPolicy) policy;
                assertThat(keycloakPolicy.getRequiredRoles()).isEqualTo("admin,user");
                assertThat(keycloakPolicy.isAllRolesRequired()).isTrue();
            } finally {
                System.clearProperty("forage.keycloak.server.url");
                System.clearProperty("forage.keycloak.realm");
                System.clearProperty("forage.keycloak.client.id");
                System.clearProperty("forage.keycloak.client.secret");
                System.clearProperty("forage.keycloak.required.roles");
                System.clearProperty("forage.keycloak.all.roles.required");
            }
        }

        @Test
        @DisplayName("Should create policy with token introspection enabled")
        void shouldCreatePolicyWithTokenIntrospection() {
            System.setProperty("forage.keycloak.server.url", "https://keycloak.example.com");
            System.setProperty("forage.keycloak.realm", "myrealm");
            System.setProperty("forage.keycloak.client.id", "myclient");
            System.setProperty("forage.keycloak.client.secret", "mysecret");
            System.setProperty("forage.keycloak.use.token.introspection", "true");
            System.setProperty("forage.keycloak.introspection.cache.enabled", "true");
            System.setProperty("forage.keycloak.introspection.cache.ttl", "60000");

            try {
                KeycloakSecurityPolicyProvider provider = new KeycloakSecurityPolicyProvider();
                AuthorizationPolicy policy = provider.create(null);

                KeycloakSecurityPolicy keycloakPolicy = (KeycloakSecurityPolicy) policy;
                assertThat(keycloakPolicy.isUseTokenIntrospection()).isTrue();
                assertThat(keycloakPolicy.isIntrospectionCacheEnabled()).isTrue();
                assertThat(keycloakPolicy.getIntrospectionCacheTtl()).isEqualTo(60L);
            } finally {
                System.clearProperty("forage.keycloak.server.url");
                System.clearProperty("forage.keycloak.realm");
                System.clearProperty("forage.keycloak.client.id");
                System.clearProperty("forage.keycloak.client.secret");
                System.clearProperty("forage.keycloak.use.token.introspection");
                System.clearProperty("forage.keycloak.introspection.cache.enabled");
                System.clearProperty("forage.keycloak.introspection.cache.ttl");
            }
        }
    }
}
