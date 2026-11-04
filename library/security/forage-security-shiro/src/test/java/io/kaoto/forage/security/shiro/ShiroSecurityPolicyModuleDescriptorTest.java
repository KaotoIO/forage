package io.kaoto.forage.security.shiro;

import org.apache.camel.component.shiro.security.ShiroSecurityPolicy;
import io.kaoto.forage.core.common.RuntimeType;
import io.kaoto.forage.core.util.config.ConfigStore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.Resources.SYSTEM_PROPERTIES;

@ResourceLock(SYSTEM_PROPERTIES)
class ShiroSecurityPolicyModuleDescriptorTest {

    private static final String INI_PATH = "forage.shiro.ini.resource.path";

    private final ShiroSecurityPolicyModuleDescriptor descriptor = new ShiroSecurityPolicyModuleDescriptor();

    // Values loaded by other test classes stay in the store until it is reloaded
    @BeforeEach
    @AfterEach
    void reset() {
        System.clearProperty(INI_PATH);
        System.clearProperty("forage.shiro.roles");
        ConfigStore.getInstance().reload();
    }

    @Test
    void describesTheProviderBean() {
        assertThat(descriptor.defaultBeanName()).isEqualTo(new ShiroSecurityPolicyProvider().beanName());
        assertThat(descriptor.primaryBeanClass()).isEqualTo(ShiroSecurityPolicy.class);
        assertThat(descriptor.supportedRuntimes()).containsExactlyInAnyOrder(RuntimeType.values());
    }

    @Test
    void isConfiguredByAnyShiroProperty() {
        assertThat(descriptor.isConfigured()).isFalse();

        System.setProperty("forage.shiro.roles", "admin");

        assertThat(descriptor.isConfigured()).isTrue();
    }

    @Test
    void listsTheIniFileLoadedFromTheClasspath() {
        System.setProperty(INI_PATH, "classpath:/security/shiro.ini");

        assertThat(descriptor.runtimeResources(descriptor.createConfig(null))).containsExactly("security/shiro.ini");
    }

    @Test
    void listsNoIniFileOutsideTheClasspath() {
        System.setProperty(INI_PATH, "file:/etc/shiro.ini");

        assertThat(descriptor.runtimeResources(descriptor.createConfig(null))).isEmpty();
    }

    @Test
    void listsNoIniFileWhenTheLocationIsMissing() {
        System.setProperty("forage.shiro.roles", "admin");

        assertThat(descriptor.runtimeResources(descriptor.createConfig(null))).isEmpty();
    }
}
