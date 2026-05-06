package io.kaoto.forage.quarkus.cxf;

import io.kaoto.forage.core.common.ForageModuleDescriptor;
import io.kaoto.forage.core.common.ForageQuarkusConfigSourceAdapter;
import io.kaoto.forage.core.cxf.CxfEndpointProvider;
import io.kaoto.forage.cxf.common.CxfConfig;
import io.kaoto.forage.cxf.common.CxfModuleDescriptor;

public class ForageCxfConfigSourceFactory extends ForageQuarkusConfigSourceAdapter<CxfConfig> {

    @Override
    protected ForageModuleDescriptor<CxfConfig, CxfEndpointProvider> descriptor() {
        return new CxfModuleDescriptor();
    }
}
