package io.kaoto.forage.springboot.security;

import java.util.Map;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.shiro.security.ShiroSecurityConstants;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.authentication.AuthenticationManager;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.springboot.common.ForageEnvironmentPostProcessor;
import io.kaoto.forage.springboot.common.SpringConfigResolver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs a Camel Spring Boot application with a route secured by the Shiro policy, referenced by name as YAML DSL
 * routes do.
 */
class ForageSecurityPolicyCamelTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> new ForageEnvironmentPostProcessor()
                    .postProcessEnvironment(context.getEnvironment(), new SpringApplication()))
            .withConfiguration(
                    AutoConfigurations.of(CamelAutoConfiguration.class, ForageSecurityPolicyAutoConfiguration.class))
            // The Spring Security policy on the test classpath needs an authentication manager
            .withBean(AuthenticationManager.class, () -> authentication -> authentication)
            .withBean(RoutesBuilder.class, () -> new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:secured").policy("shiroPolicy").setBody(constant("granted"));
                }
            })
            .withPropertyValues(
                    "forage.shiro.ini.resource.path=classpath:shiro.ini",
                    "forage.shiro.passphrase=MDEyMzQ1Njc4OWFiY2RlZg==",
                    "forage.shiro.roles=admin");

    @AfterEach
    void reset() {
        ConfigStore.getInstance().unregisterResolver(SpringConfigResolver.class);
        ConfigStore.getInstance().reload();
    }

    @Test
    void securesTheRouteWithTheSpringManagedPolicy() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            CamelContext camel = context.getBean(CamelContext.class);
            assertThat(camel.getRegistry().lookupByName("shiroPolicy"))
                    .as("the plain bean factory must not register a second policy")
                    .isSameAs(context.getBean("shiroPolicy"));

            ProducerTemplate template = camel.createProducerTemplate();
            assertThat(template.requestBodyAndHeaders("direct:secured", "", credentials("alice", "secret")))
                    .isEqualTo("granted");
            assertThatThrownBy(() -> template.requestBodyAndHeaders("direct:secured", "", credentials("bob", "secret")))
                    .as("bob does not have the admin role")
                    .isInstanceOf(CamelExecutionException.class);
            assertThatThrownBy(
                            () -> template.requestBodyAndHeaders("direct:secured", "", credentials("alice", "wrong")))
                    .isInstanceOf(CamelExecutionException.class);
        });
    }

    private static Map<String, Object> credentials(String username, String password) {
        return Map.of(
                ShiroSecurityConstants.SHIRO_SECURITY_USERNAME, username,
                ShiroSecurityConstants.SHIRO_SECURITY_PASSWORD, password);
    }
}
