package io.kaoto.forage.security.keycloak;

import java.io.IOException;
import java.net.ConnectException;
import org.apache.camel.component.keycloak.security.KeycloakTokenIntrospector;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ForageKeycloakSecurityPolicyTest {

    // Nothing listens there: an open HTTP client fails to connect, a closed one refuses to execute
    private static final String SERVER_URL = "http://localhost:1";

    @Test
    void closesTheClientsCreatedForTheRoutes() {
        ForageKeycloakSecurityPolicy policy = new ForageKeycloakSecurityPolicy(SERVER_URL, "forage", "camel", "secret");
        policy.setUseTokenIntrospection(true);
        policy.setIntrospectionCacheEnabled(true);
        // What Camel calls when a route uses the policy
        policy.beforeWrap(null, null);
        KeycloakTokenIntrospector introspector = policy.getTokenIntrospector();
        assertThatThrownBy(() -> introspector.introspect("token")).isInstanceOf(ConnectException.class);

        policy.close();

        assertThat(policy.isClosed()).isTrue();
        assertThat(policy.getKeycloakClient().isClosed()).isTrue();
        assertThatThrownBy(() -> introspector.introspect("token"))
                .isInstanceOf(IOException.class)
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("shut down");
    }

    @Test
    void closesAPolicyNoRouteUsed() {
        ForageKeycloakSecurityPolicy policy = new ForageKeycloakSecurityPolicy(SERVER_URL, "forage", "camel", "secret");

        policy.close();

        assertThat(policy.isClosed()).isTrue();
        assertThat(policy.getKeycloakClient()).isNull();
    }

    @Test
    void closesOnce() {
        ForageKeycloakSecurityPolicy policy = new ForageKeycloakSecurityPolicy(SERVER_URL, "forage", "camel", "secret");
        policy.beforeWrap(null, null);

        policy.close();

        assertThatNoException().isThrownBy(policy::close);
    }
}
