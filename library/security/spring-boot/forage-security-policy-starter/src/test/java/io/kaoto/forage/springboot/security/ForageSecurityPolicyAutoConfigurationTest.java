package io.kaoto.forage.springboot.security;

import javax.security.auth.Subject;

import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.shiro.security.ShiroSecurityPolicy;
import org.apache.camel.component.spring.security.SpringSecurityAuthorizationPolicy;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.AuthorizationPolicy;
import org.apache.camel.support.DefaultExchange;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.security.keycloak.ForageKeycloakSecurityPolicy;
import io.kaoto.forage.springboot.common.ForageEnvironmentPostProcessor;
import io.kaoto.forage.springboot.common.SpringConfigResolver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The test classpath holds the Shiro, Keycloak and Spring Security providers. The Spring Security policy has no
 * required property, so it is registered in every context and needs an authentication source.
 */
class ForageSecurityPolicyAutoConfigurationTest {

    private final ApplicationContextRunner runnerWithoutUsers = new ApplicationContextRunner()
            .withInitializer(context -> new ForageEnvironmentPostProcessor()
                    .postProcessEnvironment(context.getEnvironment(), new SpringApplication()))
            .withConfiguration(AutoConfigurations.of(ForageSecurityPolicyAutoConfiguration.class));

    private final ApplicationContextRunner runner = runnerWithoutUsers.withUserConfiguration(Users.class);

    @AfterEach
    void reset() {
        ConfigStore.getInstance().unregisterResolver(SpringConfigResolver.class);
        ConfigStore.getInstance().reload();
    }

    @Test
    void registersNoShiroOrKeycloakPolicyWithoutProperties() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean("shiroPolicy");
            assertThat(context).doesNotHaveBean("keycloakPolicy");
        });
    }

    @Test
    void registersShiroPolicyFromApplicationProperties() {
        runner.withPropertyValues(
                        "forage.shiro.ini.resource.path=classpath:shiro.ini",
                        "forage.shiro.roles=admin",
                        "forage.shiro.always.reauthenticate=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ShiroSecurityPolicy policy = context.getBean("shiroPolicy", ShiroSecurityPolicy.class);
                    assertThat(policy.getRolesList()).containsExactly("admin");
                    assertThat(policy.isAlwaysReauthenticate()).isTrue();
                });
    }

    @Test
    void keepsTheApplicationPolicyBean() {
        AuthorizationPolicy own = new ShiroSecurityPolicy();
        runner.withBean("shiroPolicy", AuthorizationPolicy.class, () -> own)
                .withPropertyValues("forage.shiro.ini.resource.path=classpath:shiro.ini")
                .run(context -> assertThat(context.getBean("shiroPolicy")).isSameAs(own));
    }

    @Test
    void closesTheKeycloakClientsWithTheContext() throws Exception {
        ForageKeycloakSecurityPolicy[] holder = new ForageKeycloakSecurityPolicy[1];
        runner.withPropertyValues(
                        "forage.keycloak.server.url=http://localhost:8180",
                        "forage.keycloak.realm=forage",
                        "forage.keycloak.client.id=camel",
                        "forage.keycloak.client.secret=secret",
                        "forage.keycloak.required.roles=admin",
                        "forage.keycloak.use.token.introspection=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ForageKeycloakSecurityPolicy policy =
                            context.getBean("keycloakPolicy", ForageKeycloakSecurityPolicy.class);
                    assertThat(policy.getRequiredRolesAsList()).containsExactly("admin");
                    // The clients are created when a route first uses the policy
                    policy.beforeWrap(null, null);
                    assertThat(policy.getTokenIntrospector()).isNotNull();
                    assertThat(policy.getKeycloakClient().isClosed()).isFalse();
                    holder[0] = policy;
                });
        assertThat(holder[0].isClosed()).isTrue();
        assertThat(holder[0].getKeycloakClient().isClosed()).isTrue();
    }

    @Test
    void wiresTheSpringSecurityPolicyWithTheApplicationManagers() {
        runnerWithoutUsers.withUserConfiguration(Managers.class).run(context -> {
            assertThat(context).hasNotFailed();
            SpringSecurityAuthorizationPolicy policy =
                    context.getBean("springSecurityPolicy", SpringSecurityAuthorizationPolicy.class);
            assertThat(policy.getAuthenticationManager()).isSameAs(context.getBean(AuthenticationManager.class));
            assertThat(policy.getAuthorizationManager()).isSameAs(context.getBean("adminOnly"));
        });
    }

    @Test
    void springSecurityPolicyAuthenticatesAgainstTheUserDetailsService() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            SpringSecurityAuthorizationPolicy policy =
                    context.getBean("springSecurityPolicy", SpringSecurityAuthorizationPolicy.class);

            try (CamelContext camel = new DefaultCamelContext()) {
                Processor secured =
                        policy.wrap(null, exchange -> exchange.getMessage().setBody("granted"));

                Exchange granted = exchangeFor(camel, "alice", "secret");
                secured.process(granted);
                assertThat(granted.getMessage().getBody()).isEqualTo("granted");

                assertThatThrownBy(() -> secured.process(exchangeFor(camel, "alice", "wrong")))
                        .isInstanceOf(CamelAuthorizationException.class);
            }
        });
    }

    @Test
    void failsClearlyWithoutAnAuthenticationSourceForTheSpringSecurityPolicy() {
        runnerWithoutUsers.run(context -> assertThat(context)
                .hasFailed()
                .getFailure()
                .hasMessageContaining("The Spring Security policy needs an AuthenticationManager"));
    }

    private static Exchange exchangeFor(CamelContext camel, String username, String password) {
        Subject subject = new Subject();
        subject.getPrincipals().add(UsernamePasswordAuthenticationToken.unauthenticated(username, password));
        Exchange exchange = new DefaultExchange(camel);
        exchange.getIn().setHeader(Exchange.AUTHENTICATION, subject);
        return exchange;
    }

    @Configuration(proxyBeanMethods = false)
    static class Users {
        @Bean
        UserDetailsService users() {
            return new InMemoryUserDetailsManager(User.withUsername("alice")
                    .password("{noop}secret")
                    .roles("ADMIN")
                    .build());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Managers {
        @Bean
        AuthenticationManager authenticationManager() {
            return authentication -> authentication;
        }

        @Bean
        AuthorizationManager<Exchange> adminOnly() {
            return AuthorityAuthorizationManager.hasRole("ADMIN");
        }
    }
}
