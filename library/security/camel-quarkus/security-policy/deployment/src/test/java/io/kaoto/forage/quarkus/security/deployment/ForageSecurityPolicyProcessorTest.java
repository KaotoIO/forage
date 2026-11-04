package io.kaoto.forage.quarkus.security.deployment;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.apache.camel.component.shiro.security.ShiroSecurityPolicy;
import org.apache.camel.quarkus.core.deployment.spi.CamelRuntimeBeanBuildItem;
import org.apache.camel.spi.AuthorizationPolicy;
import io.kaoto.forage.core.common.RuntimeType;
import io.kaoto.forage.core.security.SecurityPolicyModuleDescriptor;
import io.kaoto.forage.core.security.SecurityPolicyProvider;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.quarkus.security.ForageSecurityPolicyRecorder;
import io.kaoto.forage.security.keycloak.KeycloakSecurityPolicyProvider;
import io.kaoto.forage.security.shiro.ShiroSecurityPolicyConfig;
import io.kaoto.forage.security.shiro.ShiroSecurityPolicyModuleDescriptor;
import io.kaoto.forage.security.shiro.ShiroSecurityPolicyProvider;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.configuration.ConfigurationException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ForageSecurityPolicyProcessorTest {

    private static final String INI_PATH = "forage.shiro.ini.resource.path";

    @AfterEach
    void reset() {
        System.clearProperty(INI_PATH);
        ConfigStore.getInstance().reload();
    }

    @Test
    void selectsTheConfiguredProvidersOnly() {
        System.setProperty(INI_PATH, "classpath:forage-test-shiro.ini");

        List<SecurityPolicyProvider> configured = ForageSecurityPolicyProcessor.configured(
                List.of(new ShiroSecurityPolicyProvider(), new KeycloakSecurityPolicyProvider()));

        assertThat(configured).singleElement().isInstanceOf(ShiroSecurityPolicyProvider.class);
    }

    @Test
    void registersTheConfiguredPoliciesForCreationAtRuntime() {
        System.setProperty(INI_PATH, "classpath:forage-test-shiro.ini");
        ForageSecurityPolicyRecorder recorder = mock(ForageSecurityPolicyRecorder.class);
        ShutdownContextBuildItem shutdown = new ShutdownContextBuildItem();
        RuntimeValue<AuthorizationPolicy> policy = new RuntimeValue<>(new ShiroSecurityPolicy());
        when(recorder.createPolicy(ShiroSecurityPolicyProvider.class.getName(), shutdown))
                .thenReturn(policy);
        List<CamelRuntimeBeanBuildItem> beans = new ArrayList<>();

        new ForageSecurityPolicyProcessor().registerPolicies(recorder, shutdown, beans::add);

        assertThat(beans).singleElement().satisfies(bean -> {
            assertThat(bean.getName()).isEqualTo("shiroPolicy");
            assertThat(bean.getType()).isEqualTo(ShiroSecurityPolicy.class.getName());
            assertThat(bean.getValue()).contains(policy);
        });
        verify(recorder).createPolicy(ShiroSecurityPolicyProvider.class.getName(), shutdown);
    }

    @Test
    void selectsNothingWithoutConfiguration() {
        assertThat(ForageSecurityPolicyProcessor.configured(
                        List.of(new ShiroSecurityPolicyProvider(), new KeycloakSecurityPolicyProvider())))
                .isEmpty();
    }

    @Test
    void ignoresProvidersWithoutDescriptor() {
        assertThat(ForageSecurityPolicyProcessor.configured(List.of(new UndescribedProvider())))
                .isEmpty();
    }

    @Test
    void failsTheBuildForConfiguredProvidersThatDoNotSupportQuarkus() {
        assertThatThrownBy(() -> ForageSecurityPolicyProcessor.configured(List.of(new SpringOnlyProvider())))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("spring-only security policy")
                .hasMessageContaining("not supported on Quarkus");
    }

    @Test
    void includesTheClasspathIniFileInTheNativeImage() {
        System.setProperty(INI_PATH, "classpath:forage-test-shiro.ini");

        assertThat(ForageSecurityPolicyProcessor.nativeResources(new ShiroSecurityPolicyModuleDescriptor()))
                .containsExactly("forage-test-shiro.ini");
    }

    @Test
    void doesNotIncludeIniFilesOutsideTheClasspath() {
        System.setProperty(INI_PATH, "file:/etc/forage/shiro.ini");

        assertThat(ForageSecurityPolicyProcessor.nativeResources(new ShiroSecurityPolicyModuleDescriptor()))
                .isEmpty();
    }

    public static class UndescribedProvider implements SecurityPolicyProvider {
        @Override
        public String name() {
            return "undescribed";
        }

        @Override
        public AuthorizationPolicy create(String id) {
            return new ShiroSecurityPolicy();
        }
    }

    public static class SpringOnlyProvider extends UndescribedProvider {
        @Override
        public String name() {
            return "spring-only";
        }

        @Override
        public SecurityPolicyModuleDescriptor<?> descriptor() {
            return new ShiroSecurityPolicyModuleDescriptor() {
                @Override
                public boolean isConfigured() {
                    return true;
                }

                @Override
                public Set<RuntimeType> supportedRuntimes() {
                    return EnumSet.of(RuntimeType.MAIN, RuntimeType.SPRING_BOOT);
                }

                @Override
                public Set<String> runtimeResources(ShiroSecurityPolicyConfig config) {
                    return Set.of();
                }
            };
        }
    }
}
