package io.kaoto.forage.springboot.security;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import io.kaoto.forage.core.annotations.FactoryType;
import io.kaoto.forage.core.annotations.FactoryVariant;
import io.kaoto.forage.core.annotations.ForageFactory;

/**
 * Registers the configured Forage security policies ({@code shiroPolicy}, {@code keycloakPolicy},
 * {@code springSecurityPolicy}) as Spring beans, so that the Spring context owns their lifecycle and routes can
 * reference them with {@code policy.ref}.
 */
@ForageFactory(
        value = "Security Policy (Spring Boot)",
        components = {"camel-keycloak", "camel-shiro", "camel-spring-security"},
        description = "Security policies registered as Spring beans",
        type = FactoryType.SECURITY_POLICY,
        autowired = true,
        variant = FactoryVariant.SPRING_BOOT)
@AutoConfiguration
@Import(ForageSecurityPolicyBeanRegistrar.class)
public class ForageSecurityPolicyAutoConfiguration {

    /**
     * Wires the Spring Security policy with the application's authentication and authorization managers.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.apache.camel.component.spring.security.SpringSecurityAuthorizationPolicy")
    static class SpringSecurityPolicyConfiguration {

        @Bean
        static BeanPostProcessor forageSpringSecurityPolicyWiring(ConfigurableListableBeanFactory beanFactory) {
            return new ForageSpringSecurityPolicyWiring(beanFactory);
        }
    }
}
