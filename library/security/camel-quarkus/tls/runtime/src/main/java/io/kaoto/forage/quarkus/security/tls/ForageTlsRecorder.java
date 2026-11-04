package io.kaoto.forage.quarkus.security.tls;

import org.apache.camel.support.jsse.SSLContextParameters;
import io.kaoto.forage.security.tls.TlsProvider;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

/**
 * Creates {@link SSLContextParameters} at runtime initialization, so that they read the runtime configuration and,
 * in a native image, keystore passwords are not built into the image heap.
 */
@Recorder
public class ForageTlsRecorder {

    /**
     * Creates the parameters of a TLS profile.
     *
     * @param prefix the profile name, or {@code null} for the default profile
     * @return the parameters
     * @throws IllegalStateException if the profile no longer defines a keystore or a truststore
     */
    public RuntimeValue<SSLContextParameters> createSslContextParameters(String prefix) {
        SSLContextParameters parameters = new TlsProvider().create(prefix);
        if (parameters == null) {
            String properties = prefix == null ? "forage.tls" : "forage." + prefix + ".tls";
            throw new IllegalStateException("TLS profile '" + (prefix == null ? "default" : prefix)
                    + "' was configured at build time, but at runtime neither " + properties
                    + ".keystore.path nor " + properties + ".truststore.path is set");
        }
        return new RuntimeValue<>(parameters);
    }
}
