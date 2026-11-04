package io.kaoto.forage.quarkus.security.deployment;

import jakarta.inject.Inject;

import java.util.Map;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.shiro.security.ShiroSecurityConstants;
import org.apache.camel.component.shiro.security.ShiroSecurityPolicy;
import org.apache.camel.spi.AuthorizationPolicy;
import io.kaoto.forage.core.security.SecurityPolicyBeanFactory;
import io.kaoto.forage.security.keycloak.ForageKeycloakSecurityPolicy;
import io.quarkus.test.QuarkusUnitTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Boots a Camel Quarkus application (JVM mode) with Shiro and Keycloak configured through application properties.
 */
class ForageSecurityPolicyQuarkusTest {

    @RegisterExtension
    static final QuarkusUnitTest TEST = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClass(SecuredRoutes.class).addAsResource("forage-test-shiro.ini"))
            .overrideConfigKey("forage.shiro.ini.resource.path", "classpath:forage-test-shiro.ini")
            .overrideConfigKey("forage.shiro.passphrase", "MDEyMzQ1Njc4OWFiY2RlZg==")
            .overrideConfigKey("forage.shiro.roles", "admin")
            .overrideConfigKey("forage.keycloak.server.url", "http://localhost:8180")
            .overrideConfigKey("forage.keycloak.realm", "forage")
            .overrideConfigKey("forage.keycloak.client.id", "camel")
            .overrideConfigKey("forage.keycloak.client.secret", "secret")
            .overrideConfigKey("forage.keycloak.required.roles", "admin")
            // The plain factory would log the policies it registers during static initialization
            .setLogRecordPredicate(
                    record -> SecurityPolicyBeanFactory.class.getName().equals(record.getLoggerName()))
            .assertLogRecords(records -> assertThat(records)
                    .as("the plain SecurityPolicyBeanFactory must not run on Quarkus")
                    .isEmpty());

    @Inject
    CamelContext context;

    @Inject
    ProducerTemplate template;

    @Test
    void registersTheConfiguredPolicies() {
        assertThat(context.getRegistry().lookupByNameAndType("shiroPolicy", AuthorizationPolicy.class))
                .isInstanceOf(ShiroSecurityPolicy.class);
        ForageKeycloakSecurityPolicy keycloak =
                context.getRegistry().lookupByNameAndType("keycloakPolicy", ForageKeycloakSecurityPolicy.class);
        assertThat(keycloak).isNotNull();
        assertThat(keycloak.getRequiredRolesAsList()).containsExactly("admin");
        assertThat(keycloak.isClosed()).isFalse();
    }

    @Test
    void securesTheRoute() {
        assertThat(template.requestBodyAndHeaders("direct:secured", "", credentials("alice", "secret")))
                .isEqualTo("granted");
        assertThatThrownBy(() -> template.requestBodyAndHeaders("direct:secured", "", credentials("bob", "secret")))
                .isInstanceOf(CamelExecutionException.class);
        assertThatThrownBy(() -> template.requestBodyAndHeaders("direct:secured", "", credentials("alice", "wrong")))
                .isInstanceOf(CamelExecutionException.class);
    }

    private static Map<String, Object> credentials(String username, String password) {
        return Map.of(
                ShiroSecurityConstants.SHIRO_SECURITY_USERNAME, username,
                ShiroSecurityConstants.SHIRO_SECURITY_PASSWORD, password);
    }

    public static class SecuredRoutes extends RouteBuilder {
        @Override
        public void configure() {
            from("direct:secured").policy("shiroPolicy").setBody(constant("granted"));
        }
    }
}
