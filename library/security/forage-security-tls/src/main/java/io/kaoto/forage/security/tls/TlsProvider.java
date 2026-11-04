package io.kaoto.forage.security.tls;

import org.apache.camel.support.jsse.CipherSuitesParameters;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.SSLContextServerParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.core.common.BeanProvider;

/**
 * Builds {@link SSLContextParameters} from a TLS profile ({@code forage.tls.*} or {@code forage.<name>.tls.*}).
 *
 * <p>Shared by {@link TlsBeanFactory} and the runtime adapters. It is not a pluggable provider: TLS has a single
 * implementation, so it is neither discovered through {@link java.util.ServiceLoader} nor listed in the catalog,
 * where the TLS Context factory describes the configuration.
 *
 * @since 1.6.3
 */
public class TlsProvider implements BeanProvider<SSLContextParameters> {
    private static final Logger LOG = LoggerFactory.getLogger(TlsProvider.class);

    /**
     * Creates the parameters of a TLS profile.
     *
     * @param id the profile name, or {@code null} for the default profile
     * @return the parameters, or {@code null} when the profile defines neither a keystore nor a truststore
     */
    @Override
    public SSLContextParameters create(String id) {
        return create(new TlsConfig(id));
    }

    /**
     * Creates the parameters of a TLS profile.
     *
     * @param config the profile configuration
     * @return the parameters, or {@code null} when the profile defines neither a keystore nor a truststore
     */
    public SSLContextParameters create(TlsConfig config) {
        if (config.keystorePath() == null && config.truststorePath() == null) {
            LOG.debug("No keystore or truststore configured, skipping SSLContextParameters creation");
            return null;
        }

        SSLContextParameters sslCtxParams = new SSLContextParameters();

        if (config.keystorePath() != null) {
            KeyStoreParameters ksp = new KeyStoreParameters();
            ksp.setResource(config.keystorePath());
            ksp.setPassword(config.keystorePassword());
            ksp.setType(config.keystoreType());

            KeyManagersParameters kmp = new KeyManagersParameters();
            kmp.setKeyStore(ksp);
            kmp.setKeyPassword(config.keystorePassword());

            sslCtxParams.setKeyManagers(kmp);
        }

        if (config.truststorePath() != null) {
            KeyStoreParameters tsp = new KeyStoreParameters();
            tsp.setResource(config.truststorePath());
            tsp.setPassword(config.truststorePassword());
            tsp.setType(config.truststoreType());

            TrustManagersParameters tmp = new TrustManagersParameters();
            tmp.setKeyStore(tsp);

            sslCtxParams.setTrustManagers(tmp);
        }

        String clientAuth = config.clientAuthentication();
        if (clientAuth != null && !"NONE".equalsIgnoreCase(clientAuth)) {
            SSLContextServerParameters serverParams = new SSLContextServerParameters();
            serverParams.setClientAuthentication(clientAuth);
            sslCtxParams.setServerParameters(serverParams);
        }

        if (config.cipherSuites().isPresent()) {
            CipherSuitesParameters csp = new CipherSuitesParameters();
            csp.setCipherSuite(config.cipherSuitesList());
            sslCtxParams.setCipherSuites(csp);
        }

        sslCtxParams.setSecureSocketProtocol(config.secureSocketProtocol());

        return sslCtxParams;
    }
}
