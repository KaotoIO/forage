package io.kaoto.forage.security.keycloak;

import java.util.Optional;
import io.kaoto.forage.core.util.config.MissingConfigException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("KeycloakSecurityPolicyConfig Tests")
class KeycloakSecurityPolicyConfigTest {

    @Nested
    @DisplayName("Required Configuration Tests")
    class RequiredConfigTests {

        @Test
        @DisplayName("Should return configured server URL")
        void shouldReturnConfiguredServerUrl() {
            TestKeycloakConfig config =
                    new TestKeycloakConfig("https://keycloak.example.com", "myrealm", "myclient", "mysecret");

            assertThat(config.serverUrl()).isEqualTo("https://keycloak.example.com");
        }

        @Test
        @DisplayName("Should throw exception when server URL not configured")
        void shouldThrowExceptionWhenServerUrlNotConfigured() {
            TestKeycloakConfig config = new TestKeycloakConfig(null, "myrealm", "myclient", "mysecret");

            assertThatThrownBy(config::serverUrl)
                    .isInstanceOf(MissingConfigException.class)
                    .hasMessageContaining("server URL");
        }

        @Test
        @DisplayName("Should return configured realm")
        void shouldReturnConfiguredRealm() {
            TestKeycloakConfig config =
                    new TestKeycloakConfig("https://keycloak.example.com", "myrealm", "myclient", "mysecret");

            assertThat(config.realm()).isEqualTo("myrealm");
        }

        @Test
        @DisplayName("Should throw exception when realm not configured")
        void shouldThrowExceptionWhenRealmNotConfigured() {
            TestKeycloakConfig config =
                    new TestKeycloakConfig("https://keycloak.example.com", null, "myclient", "mysecret");

            assertThatThrownBy(config::realm)
                    .isInstanceOf(MissingConfigException.class)
                    .hasMessageContaining("realm");
        }

        @Test
        @DisplayName("Should return configured client ID")
        void shouldReturnConfiguredClientId() {
            TestKeycloakConfig config =
                    new TestKeycloakConfig("https://keycloak.example.com", "myrealm", "myclient", "mysecret");

            assertThat(config.clientId()).isEqualTo("myclient");
        }

        @Test
        @DisplayName("Should throw exception when client ID not configured")
        void shouldThrowExceptionWhenClientIdNotConfigured() {
            TestKeycloakConfig config =
                    new TestKeycloakConfig("https://keycloak.example.com", "myrealm", null, "mysecret");

            assertThatThrownBy(config::clientId)
                    .isInstanceOf(MissingConfigException.class)
                    .hasMessageContaining("client ID");
        }

        @Test
        @DisplayName("Should return configured client secret")
        void shouldReturnConfiguredClientSecret() {
            TestKeycloakConfig config =
                    new TestKeycloakConfig("https://keycloak.example.com", "myrealm", "myclient", "mysecret");

            assertThat(config.clientSecret()).isEqualTo("mysecret");
        }

        @Test
        @DisplayName("Should throw exception when client secret not configured")
        void shouldThrowExceptionWhenClientSecretNotConfigured() {
            TestKeycloakConfig config =
                    new TestKeycloakConfig("https://keycloak.example.com", "myrealm", "myclient", null);

            assertThatThrownBy(config::clientSecret)
                    .isInstanceOf(MissingConfigException.class)
                    .hasMessageContaining("client secret");
        }
    }

    @Nested
    @DisplayName("Optional Configuration Tests")
    class OptionalConfigTests {

        @Test
        @DisplayName("Should return empty when no roles configured")
        void shouldReturnEmptyWhenNoRolesConfigured() {
            TestKeycloakConfig config =
                    new TestKeycloakConfig("https://keycloak.example.com", "myrealm", "myclient", "mysecret");

            assertThat(config.requiredRoles()).isEmpty();
        }

        @Test
        @DisplayName("Should return configured roles")
        void shouldReturnConfiguredRoles() {
            TestKeycloakConfig config = TestKeycloakConfig.withRoles(
                    "https://keycloak.example.com", "myrealm", "myclient", "mysecret", "admin,user");

            assertThat(config.requiredRoles()).contains("admin,user");
        }

        @Test
        @DisplayName("Should return false for all roles required by default")
        void shouldReturnFalseForAllRolesRequiredByDefault() {
            TestKeycloakConfig config =
                    new TestKeycloakConfig("https://keycloak.example.com", "myrealm", "myclient", "mysecret");

            assertThat(config.allRolesRequired()).isFalse();
        }

        @Test
        @DisplayName("Should return empty when no permissions configured")
        void shouldReturnEmptyWhenNoPermissionsConfigured() {
            TestKeycloakConfig config =
                    new TestKeycloakConfig("https://keycloak.example.com", "myrealm", "myclient", "mysecret");

            assertThat(config.requiredPermissions()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Advanced Configuration Tests")
    class AdvancedConfigTests {

        @Test
        @DisplayName("Should return false for token introspection by default")
        void shouldReturnFalseForTokenIntrospectionByDefault() {
            TestKeycloakConfig config =
                    new TestKeycloakConfig("https://keycloak.example.com", "myrealm", "myclient", "mysecret");

            assertThat(config.useTokenIntrospection()).isFalse();
        }

        @Test
        @DisplayName("Should return true for validate issuer by default")
        void shouldReturnTrueForValidateIssuerByDefault() {
            TestKeycloakConfig config =
                    new TestKeycloakConfig("https://keycloak.example.com", "myrealm", "myclient", "mysecret");

            assertThat(config.validateIssuer()).isTrue();
        }

        @Test
        @DisplayName("Should return true for auto fetch public key by default")
        void shouldReturnTrueForAutoFetchPublicKeyByDefault() {
            TestKeycloakConfig config =
                    new TestKeycloakConfig("https://keycloak.example.com", "myrealm", "myclient", "mysecret");

            assertThat(config.autoFetchPublicKey()).isTrue();
        }

        @Test
        @DisplayName("Should return default introspection cache TTL")
        void shouldReturnDefaultIntrospectionCacheTtl() {
            TestKeycloakConfig config =
                    new TestKeycloakConfig("https://keycloak.example.com", "myrealm", "myclient", "mysecret");

            assertThat(config.introspectionCacheTtl()).isEqualTo(300000L);
        }
    }

    @Nested
    @DisplayName("Config Name Tests")
    class ConfigNameTests {

        @Test
        @DisplayName("Should return correct config name")
        void shouldReturnCorrectConfigName() {
            KeycloakSecurityPolicyConfig config = new KeycloakSecurityPolicyConfig();

            assertThat(config.name()).isEqualTo("forage-security-keycloak");
        }
    }

    static class TestKeycloakConfig extends KeycloakSecurityPolicyConfig {
        private final String testServerUrl;
        private final String testRealm;
        private final String testClientId;
        private final String testClientSecret;
        private String testRequiredRoles;

        TestKeycloakConfig(String serverUrl, String realm, String clientId, String clientSecret) {
            super();
            this.testServerUrl = serverUrl;
            this.testRealm = realm;
            this.testClientId = clientId;
            this.testClientSecret = clientSecret;
        }

        static TestKeycloakConfig withRoles(
                String serverUrl, String realm, String clientId, String clientSecret, String roles) {
            TestKeycloakConfig config = new TestKeycloakConfig(serverUrl, realm, clientId, clientSecret);
            config.testRequiredRoles = roles;
            return config;
        }

        @Override
        public String serverUrl() {
            if (testServerUrl == null) {
                throw new MissingConfigException("Missing Keycloak server URL configuration");
            }
            return testServerUrl;
        }

        @Override
        public String realm() {
            if (testRealm == null) {
                throw new MissingConfigException("Missing Keycloak realm configuration");
            }
            return testRealm;
        }

        @Override
        public String clientId() {
            if (testClientId == null) {
                throw new MissingConfigException("Missing Keycloak client ID configuration");
            }
            return testClientId;
        }

        @Override
        public String clientSecret() {
            if (testClientSecret == null) {
                throw new MissingConfigException("Missing Keycloak client secret configuration");
            }
            return testClientSecret;
        }

        @Override
        public Optional<String> requiredRoles() {
            return Optional.ofNullable(testRequiredRoles);
        }

        @Override
        public boolean allRolesRequired() {
            return false;
        }

        @Override
        public Optional<String> requiredPermissions() {
            return Optional.empty();
        }

        @Override
        public boolean allPermissionsRequired() {
            return false;
        }

        @Override
        public boolean useTokenIntrospection() {
            return false;
        }

        @Override
        public boolean introspectionCacheEnabled() {
            return false;
        }

        @Override
        public long introspectionCacheTtl() {
            return 300000L;
        }

        @Override
        public boolean validateIssuer() {
            return true;
        }

        @Override
        public boolean autoFetchPublicKey() {
            return true;
        }
    }
}
