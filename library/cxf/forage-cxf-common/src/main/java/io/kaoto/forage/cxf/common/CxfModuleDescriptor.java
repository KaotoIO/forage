package io.kaoto.forage.cxf.common;

import io.kaoto.forage.core.common.ForageModuleDescriptor;
import io.kaoto.forage.core.cxf.CxfEndpointProvider;

public class CxfModuleDescriptor implements ForageModuleDescriptor<CxfConfig, CxfEndpointProvider> {

    @Override
    public String modulePrefix() {
        return "cxf";
    }

    @Override
    public CxfConfig createConfig(String prefix) {
        return prefix == null ? new CxfConfig() : new CxfConfig(prefix);
    }

    @Override
    public Class<CxfEndpointProvider> providerClass() {
        return CxfEndpointProvider.class;
    }

    @Override
    public String resolveProviderClassName(CxfConfig config) {
        return CxfCommonExportHelper.transformCxfKindIntoProviderClass(config.cxfKind());
    }

    @Override
    public String defaultBeanName() {
        return "cxfEndpoint";
    }

    @Override
    public Class<?> primaryBeanClass() {
        return Object.class;
    }

    @Override
    public boolean transactionEnabled(CxfConfig config) {
        return false;
    }
}
