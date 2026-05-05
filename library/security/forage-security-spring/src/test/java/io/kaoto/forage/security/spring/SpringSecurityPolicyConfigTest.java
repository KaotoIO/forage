package io.kaoto.forage.security.spring;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SpringSecurityPolicyConfig Tests")
class SpringSecurityPolicyConfigTest {

    @Nested
    @DisplayName("Policy ID Tests")
    class PolicyIdTests {

        @Test
        @DisplayName("Should return default policy ID")
        void shouldReturnDefaultPolicyId() {
            TestSpringConfig config = new TestSpringConfig();

            assertThat(config.id()).isEqualTo("springSecurityPolicy");
        }

        @Test
        @DisplayName("Should return configured policy ID")
        void shouldReturnConfiguredPolicyId() {
            TestSpringConfig config = TestSpringConfig.withId("myCustomPolicy");

            assertThat(config.id()).isEqualTo("myCustomPolicy");
        }
    }

    @Nested
    @DisplayName("Always Reauthenticate Tests")
    class AlwaysReauthenticateTests {

        @Test
        @DisplayName("Should return false by default")
        void shouldReturnFalseByDefault() {
            TestSpringConfig config = new TestSpringConfig();

            assertThat(config.alwaysReauthenticate()).isFalse();
        }

        @Test
        @DisplayName("Should return true when configured")
        void shouldReturnTrueWhenConfigured() {
            TestSpringConfig config = TestSpringConfig.withAlwaysReauthenticate(true);

            assertThat(config.alwaysReauthenticate()).isTrue();
        }
    }

    @Nested
    @DisplayName("Use Thread Security Context Tests")
    class UseThreadSecurityContextTests {

        @Test
        @DisplayName("Should return true by default")
        void shouldReturnTrueByDefault() {
            TestSpringConfig config = new TestSpringConfig();

            assertThat(config.useThreadSecurityContext()).isTrue();
        }

        @Test
        @DisplayName("Should return false when configured")
        void shouldReturnFalseWhenConfigured() {
            TestSpringConfig config = TestSpringConfig.withUseThreadSecurityContext(false);

            assertThat(config.useThreadSecurityContext()).isFalse();
        }
    }

    @Nested
    @DisplayName("Config Name Tests")
    class ConfigNameTests {

        @Test
        @DisplayName("Should return correct config name")
        void shouldReturnCorrectConfigName() {
            SpringSecurityPolicyConfig config = new SpringSecurityPolicyConfig();

            assertThat(config.name()).isEqualTo("forage-security-spring");
        }
    }

    static class TestSpringConfig extends SpringSecurityPolicyConfig {
        private String testId;
        private Boolean testAlwaysReauthenticate;
        private Boolean testUseThreadSecurityContext;

        TestSpringConfig() {
            super();
        }

        static TestSpringConfig withId(String id) {
            TestSpringConfig config = new TestSpringConfig();
            config.testId = id;
            return config;
        }

        static TestSpringConfig withAlwaysReauthenticate(boolean value) {
            TestSpringConfig config = new TestSpringConfig();
            config.testAlwaysReauthenticate = value;
            return config;
        }

        static TestSpringConfig withUseThreadSecurityContext(boolean value) {
            TestSpringConfig config = new TestSpringConfig();
            config.testUseThreadSecurityContext = value;
            return config;
        }

        @Override
        public String id() {
            return testId != null ? testId : super.id();
        }

        @Override
        public boolean alwaysReauthenticate() {
            return testAlwaysReauthenticate != null ? testAlwaysReauthenticate : super.alwaysReauthenticate();
        }

        @Override
        public boolean useThreadSecurityContext() {
            return testUseThreadSecurityContext != null
                    ? testUseThreadSecurityContext
                    : super.useThreadSecurityContext();
        }
    }
}
