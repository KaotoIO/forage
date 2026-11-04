package io.kaoto.forage.security.spring;

import org.apache.camel.component.spring.security.SpringSecurityAuthorizationPolicy;
import io.kaoto.forage.core.common.RuntimeType;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SpringSecurityPolicyModuleDescriptorTest {

    private final SpringSecurityPolicyModuleDescriptor descriptor = new SpringSecurityPolicyModuleDescriptor();

    @Test
    void describesTheProviderBean() {
        assertThat(descriptor.defaultBeanName()).isEqualTo(new SpringSecurityPolicyProvider().beanName());
        assertThat(descriptor.primaryBeanClass()).isEqualTo(SpringSecurityAuthorizationPolicy.class);
        assertThat(descriptor.modulePrefix()).isEqualTo("spring.security");
    }

    @Test
    void isNotSupportedOnQuarkus() {
        assertThat(descriptor.supportedRuntimes()).containsExactlyInAnyOrder(RuntimeType.MAIN, RuntimeType.SPRING_BOOT);
    }

    @Test
    void needsNoPropertyToBeRegistered() {
        assertThat(descriptor.isConfigured()).isTrue();
    }
}
