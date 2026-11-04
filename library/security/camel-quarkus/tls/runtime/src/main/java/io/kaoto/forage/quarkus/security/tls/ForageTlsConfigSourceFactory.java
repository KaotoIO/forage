package io.kaoto.forage.quarkus.security.tls;

import io.kaoto.forage.core.common.ForageModuleDescriptor;
import io.kaoto.forage.core.common.ForageQuarkusConfigSourceAdapter;
import io.kaoto.forage.security.tls.TlsConfig;
import io.kaoto.forage.security.tls.TlsModuleDescriptor;

/**
 * Makes the {@code forage.tls.*} and {@code forage.<name>.tls.*} values of all Quarkus configuration sources visible
 * to Forage. TLS needs no Quarkus properties, so no configuration source is contributed.
 */
public class ForageTlsConfigSourceFactory extends ForageQuarkusConfigSourceAdapter<TlsConfig> {

    @Override
    protected ForageModuleDescriptor<TlsConfig, ?> descriptor() {
        return new TlsModuleDescriptor();
    }
}
