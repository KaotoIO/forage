package io.kaoto.forage.security.shiro;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import io.kaoto.forage.core.util.config.MissingConfigException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ShiroSecurityPolicyConfig Tests")
class ShiroSecurityPolicyConfigTest {

    @Nested
    @DisplayName("INI Resource Path Tests")
    class IniResourcePathTests {

        @Test
        @DisplayName("Should return configured INI resource path")
        void shouldReturnConfiguredIniResourcePath() {
            TestShiroConfig config = new TestShiroConfig("classpath:shiro.ini");

            assertThat(config.iniResourcePath()).isEqualTo("classpath:shiro.ini");
        }

        @Test
        @DisplayName("Should throw exception when INI resource path not configured")
        void shouldThrowExceptionWhenIniResourcePathNotConfigured() {
            TestShiroConfig config = new TestShiroConfig(null);

            assertThatThrownBy(config::iniResourcePath)
                    .isInstanceOf(MissingConfigException.class)
                    .hasMessageContaining("INI resource path");
        }
    }

    @Nested
    @DisplayName("Passphrase Tests")
    class PassphraseTests {

        @Test
        @DisplayName("Should return empty when passphrase not configured")
        void shouldReturnEmptyWhenPassphraseNotConfigured() {
            TestShiroConfig config = new TestShiroConfig("classpath:shiro.ini");

            assertThat(config.passphrase()).isEmpty();
        }

        @Test
        @DisplayName("Should return passphrase when configured")
        void shouldReturnPassphraseWhenConfigured() {
            TestShiroConfig config = TestShiroConfig.withPassphrase("classpath:shiro.ini", "dGVzdA==");

            assertThat(config.passphrase()).contains("dGVzdA==");
        }
    }

    @Nested
    @DisplayName("Always Reauthenticate Tests")
    class AlwaysReauthenticateTests {

        @Test
        @DisplayName("Should return false by default")
        void shouldReturnFalseByDefault() {
            TestShiroConfig config = new TestShiroConfig("classpath:shiro.ini");

            assertThat(config.alwaysReauthenticate()).isFalse();
        }

        @Test
        @DisplayName("Should return true when configured")
        void shouldReturnTrueWhenConfigured() {
            TestShiroConfig config = TestShiroConfig.withAlwaysReauthenticate("classpath:shiro.ini", true);

            assertThat(config.alwaysReauthenticate()).isTrue();
        }
    }

    @Nested
    @DisplayName("Roles Tests")
    class RolesTests {

        @Test
        @DisplayName("Should return empty list when no roles configured")
        void shouldReturnEmptyListWhenNoRolesConfigured() {
            TestShiroConfig config = new TestShiroConfig("classpath:shiro.ini");

            assertThat(config.roles()).isEmpty();
        }

        @Test
        @DisplayName("Should return configured roles")
        void shouldReturnConfiguredRoles() {
            TestShiroConfig config = TestShiroConfig.withRoles("classpath:shiro.ini", List.of("admin", "user"));

            assertThat(config.roles()).containsExactly("admin", "user");
        }
    }

    @Nested
    @DisplayName("All Roles Required Tests")
    class AllRolesRequiredTests {

        @Test
        @DisplayName("Should return false by default")
        void shouldReturnFalseByDefault() {
            TestShiroConfig config = new TestShiroConfig("classpath:shiro.ini");

            assertThat(config.allRolesRequired()).isFalse();
        }

        @Test
        @DisplayName("Should return true when configured")
        void shouldReturnTrueWhenConfigured() {
            TestShiroConfig config = TestShiroConfig.withAllRolesRequired("classpath:shiro.ini", true);

            assertThat(config.allRolesRequired()).isTrue();
        }
    }

    @Nested
    @DisplayName("Permissions Tests")
    class PermissionsTests {

        @Test
        @DisplayName("Should return empty list when no permissions configured")
        void shouldReturnEmptyListWhenNoPermissionsConfigured() {
            TestShiroConfig config = new TestShiroConfig("classpath:shiro.ini");

            assertThat(config.permissions()).isEmpty();
        }

        @Test
        @DisplayName("Should return configured permissions")
        void shouldReturnConfiguredPermissions() {
            TestShiroConfig config =
                    TestShiroConfig.withPermissions("classpath:shiro.ini", List.of("zone1:readonly:*"));

            assertThat(config.permissions()).containsExactly("zone1:readonly:*");
        }
    }

    @Nested
    @DisplayName("Config Name Tests")
    class ConfigNameTests {

        @Test
        @DisplayName("Should return correct config name")
        void shouldReturnCorrectConfigName() {
            ShiroSecurityPolicyConfig config = new ShiroSecurityPolicyConfig();

            assertThat(config.name()).isEqualTo("forage-security-shiro");
        }
    }

    static class TestShiroConfig extends ShiroSecurityPolicyConfig {
        private final String testIniResourcePath;
        private String testPassphrase;
        private Boolean testAlwaysReauthenticate;
        private List<String> testRoles;
        private Boolean testAllRolesRequired;
        private List<String> testPermissions;
        private Boolean testAllPermissionsRequired;

        TestShiroConfig(String iniResourcePath) {
            super();
            this.testIniResourcePath = iniResourcePath;
        }

        static TestShiroConfig withPassphrase(String iniResourcePath, String passphrase) {
            TestShiroConfig config = new TestShiroConfig(iniResourcePath);
            config.testPassphrase = passphrase;
            return config;
        }

        static TestShiroConfig withAlwaysReauthenticate(String iniResourcePath, boolean alwaysReauthenticate) {
            TestShiroConfig config = new TestShiroConfig(iniResourcePath);
            config.testAlwaysReauthenticate = alwaysReauthenticate;
            return config;
        }

        static TestShiroConfig withRoles(String iniResourcePath, List<String> roles) {
            TestShiroConfig config = new TestShiroConfig(iniResourcePath);
            config.testRoles = roles;
            return config;
        }

        static TestShiroConfig withAllRolesRequired(String iniResourcePath, boolean allRolesRequired) {
            TestShiroConfig config = new TestShiroConfig(iniResourcePath);
            config.testAllRolesRequired = allRolesRequired;
            return config;
        }

        static TestShiroConfig withPermissions(String iniResourcePath, List<String> permissions) {
            TestShiroConfig config = new TestShiroConfig(iniResourcePath);
            config.testPermissions = permissions;
            return config;
        }

        @Override
        public String iniResourcePath() {
            if (testIniResourcePath == null) {
                throw new MissingConfigException("Missing Shiro INI resource path configuration");
            }
            return testIniResourcePath;
        }

        @Override
        public Optional<String> passphrase() {
            return Optional.ofNullable(testPassphrase);
        }

        @Override
        public boolean alwaysReauthenticate() {
            return testAlwaysReauthenticate != null ? testAlwaysReauthenticate : false;
        }

        @Override
        public List<String> roles() {
            return testRoles != null ? testRoles : Collections.emptyList();
        }

        @Override
        public boolean allRolesRequired() {
            return testAllRolesRequired != null ? testAllRolesRequired : false;
        }

        @Override
        public List<String> permissions() {
            return testPermissions != null ? testPermissions : Collections.emptyList();
        }

        @Override
        public boolean allPermissionsRequired() {
            return testAllPermissionsRequired != null ? testAllPermissionsRequired : false;
        }
    }
}
