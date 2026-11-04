package io.kaoto.forage.security.tls;

import org.apache.camel.support.jsse.SSLContextParameters;
import io.kaoto.forage.core.common.ForageModuleDescriptor;

/**
 * Describes the TLS module to the runtime adapters.
 *
 * <p>Each named profile ({@code forage.<name>.tls.*}) is registered under its name; without named profiles, the
 * default profile ({@code forage.tls.*}) is registered as {@code sslContextParameters}. Like {@link TlsBeanFactory},
 * the adapters only register profiles defining a keystore or a truststore.
 *
 * @since 1.6.3
 */
public class TlsModuleDescriptor implements ForageModuleDescriptor<TlsConfig, TlsProvider> {

    public static final String DEFAULT_BEAN_NAME = "sslContextParameters";

    @Override
    public String modulePrefix() {
        return "tls";
    }

    @Override
    public TlsConfig createConfig(String prefix) {
        return new TlsConfig(prefix);
    }

    @Override
    public Class<TlsProvider> providerClass() {
        return TlsProvider.class;
    }

    @Override
    public String resolveProviderClassName(TlsConfig config) {
        return TlsProvider.class.getName();
    }

    @Override
    public String defaultBeanName() {
        return DEFAULT_BEAN_NAME;
    }

    @Override
    public Class<?> primaryBeanClass() {
        return SSLContextParameters.class;
    }

    @Override
    public boolean transactionEnabled(TlsConfig config) {
        return false;
    }

    /**
     * Returns whether a profile creates a bean, that is defines a keystore or a truststore.
     *
     * @param config the profile configuration
     * @return {@code true} if the profile creates a bean
     */
    public boolean createsBean(TlsConfig config) {
        return config.keystorePath() != null || config.truststorePath() != null;
    }
}
