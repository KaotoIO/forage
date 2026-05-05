package io.kaoto.forage.security.spring;

import org.apache.camel.component.spring.security.SpringSecurityAuthorizationPolicy;
import org.apache.camel.spi.AuthorizationPolicy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.Resources.SYSTEM_PROPERTIES;

@DisplayName("SpringSecurityPolicyProvider Tests")
@ResourceLock(SYSTEM_PROPERTIES)
class SpringSecurityPolicyProviderTest {

    @Nested
    @DisplayName("Provider Identity Tests")
    class ProviderIdentityTests {

        @Test
        @DisplayName("Should return correct provider name")
        void shouldReturnCorrectProviderName() {
            SpringSecurityPolicyProvider provider = new SpringSecurityPolicyProvider();

            assertThat(provider.name()).isEqualTo("spring-security");
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
                if (provider instanceof SpringSecurityPolicyProvider) {
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
        @DisplayName("Should create policy with default configuration")
        void shouldCreatePolicyWithDefaultConfiguration() {
            SpringSecurityPolicyProvider provider = new SpringSecurityPolicyProvider();

            AuthorizationPolicy policy = provider.create(null);

            assertThat(policy).isNotNull();
            assertThat(policy).isInstanceOf(SpringSecurityAuthorizationPolicy.class);
        }

        @Test
        @DisplayName("Should create policy with default ID")
        void shouldCreatePolicyWithDefaultId() {
            SpringSecurityPolicyProvider provider = new SpringSecurityPolicyProvider();

            AuthorizationPolicy policy = provider.create(null);

            SpringSecurityAuthorizationPolicy springPolicy = (SpringSecurityAuthorizationPolicy) policy;
            assertThat(springPolicy.getId()).isEqualTo("springSecurityPolicy");
        }

        @Test
        @DisplayName("Should create policy with custom ID via system property")
        void shouldCreatePolicyWithCustomId() {
            System.setProperty("forage.spring.security.id", "myCustomPolicy");

            try {
                SpringSecurityPolicyProvider provider = new SpringSecurityPolicyProvider();
                AuthorizationPolicy policy = provider.create(null);

                SpringSecurityAuthorizationPolicy springPolicy = (SpringSecurityAuthorizationPolicy) policy;
                assertThat(springPolicy.getId()).isEqualTo("myCustomPolicy");
            } finally {
                System.clearProperty("forage.spring.security.id");
            }
        }

        @Test
        @DisplayName("Should create policy with always reauthenticate configured")
        void shouldCreatePolicyWithAlwaysReauthenticate() {
            System.setProperty("forage.spring.security.always.reauthenticate", "true");

            try {
                SpringSecurityPolicyProvider provider = new SpringSecurityPolicyProvider();
                AuthorizationPolicy policy = provider.create(null);

                SpringSecurityAuthorizationPolicy springPolicy = (SpringSecurityAuthorizationPolicy) policy;
                assertThat(springPolicy.isAlwaysReauthenticate()).isTrue();
            } finally {
                System.clearProperty("forage.spring.security.always.reauthenticate");
            }
        }

        @Test
        @DisplayName("Should create policy with thread security context disabled")
        void shouldCreatePolicyWithThreadSecurityContextDisabled() {
            System.setProperty("forage.spring.security.use.thread.security.context", "false");

            try {
                SpringSecurityPolicyProvider provider = new SpringSecurityPolicyProvider();
                AuthorizationPolicy policy = provider.create(null);

                SpringSecurityAuthorizationPolicy springPolicy = (SpringSecurityAuthorizationPolicy) policy;
                assertThat(springPolicy.isUseThreadSecurityContext()).isFalse();
            } finally {
                System.clearProperty("forage.spring.security.use.thread.security.context");
            }
        }
    }
}
