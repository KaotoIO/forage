package io.kaoto.forage.quarkus.security;

import java.util.ServiceLoader;
import org.apache.camel.spi.AuthorizationPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.core.security.SecurityPolicyProvider;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;

/**
 * Creates security policies at runtime initialization, so that they read the runtime configuration and, in a native
 * image, are not built into the image heap together with their secrets.
 */
@Recorder
public class ForageSecurityPolicyRecorder {
    private static final Logger LOG = LoggerFactory.getLogger(ForageSecurityPolicyRecorder.class);

    /**
     * Creates the default policy of a provider and closes it on shutdown when it holds resources.
     *
     * @param providerClassName the {@link SecurityPolicyProvider} implementation
     * @param shutdown the shutdown context
     * @return the policy
     */
    public RuntimeValue<AuthorizationPolicy> createPolicy(String providerClassName, ShutdownContext shutdown) {
        SecurityPolicyProvider provider =
                ServiceLoader.load(
                                SecurityPolicyProvider.class,
                                Thread.currentThread().getContextClassLoader())
                        .stream()
                        .filter(candidate -> candidate.type().getName().equals(providerClassName))
                        .findFirst()
                        .map(ServiceLoader.Provider::get)
                        .orElseThrow(() ->
                                new IllegalStateException("Security policy provider not found: " + providerClassName));

        AuthorizationPolicy policy = provider.create(null);
        if (policy instanceof AutoCloseable closeable) {
            shutdown.addShutdownTask(() -> {
                try {
                    closeable.close();
                } catch (Exception e) {
                    LOG.warn("Failed to close the {} security policy", provider.name(), e);
                }
            });
        }
        return new RuntimeValue<>(policy);
    }
}
