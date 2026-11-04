package io.kaoto.forage.security.keycloak;

import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.camel.component.keycloak.security.KeycloakSecurityPolicy;
import org.apache.camel.component.keycloak.security.KeycloakTokenIntrospector;
import org.keycloak.admin.client.Keycloak;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link KeycloakSecurityPolicy} that releases the clients it creates.
 *
 * <p>The policy creates a Keycloak admin client and, with token introspection enabled, a
 * {@link KeycloakTokenIntrospector} holding a pooled {@code CloseableHttpClient}, the first time a route uses it.
 * Camel never closes them: whoever owns the policy (the Forage bean factory on plain Camel, the Spring context on
 * Spring Boot, the Quarkus shutdown context on Quarkus) calls {@link #close()}.
 *
 * @since 1.6.3
 */
public class ForageKeycloakSecurityPolicy extends KeycloakSecurityPolicy implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(ForageKeycloakSecurityPolicy.class);

    private final AtomicBoolean closed = new AtomicBoolean();

    public ForageKeycloakSecurityPolicy(String serverUrl, String realm, String clientId, String clientSecret) {
        super(serverUrl, realm, clientId, clientSecret);
    }

    /**
     * Closes the token introspector (its HTTP client and cache) and the Keycloak admin client, if they were created.
     * Calling it again has no effect.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        KeycloakTokenIntrospector introspector = getTokenIntrospector();
        if (introspector != null) {
            introspector.close();
        }
        Keycloak client = getKeycloakClient();
        if (client != null) {
            try {
                client.close();
            } catch (RuntimeException e) {
                LOG.warn("Failed to close the Keycloak admin client for realm '{}'", getRealm(), e);
            }
        }
        LOG.debug("Closed Keycloak security policy for realm '{}'", getRealm());
    }

    /**
     * Returns whether {@link #close()} has been called.
     *
     * @return {@code true} once closed
     */
    public boolean isClosed() {
        return closed.get();
    }
}
