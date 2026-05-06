package io.kaoto.forage.quarkus.cxf;

import java.util.List;
import java.util.ServiceLoader;
import org.jboss.logging.Logger;
import io.kaoto.forage.core.common.ServiceLoaderHelper;
import io.kaoto.forage.core.cxf.CxfEndpointProvider;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.cxf.common.CxfCommonExportHelper;
import io.kaoto.forage.cxf.common.CxfConfig;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class ForageCxfRecorder {

    private static final Logger LOG = Logger.getLogger(ForageCxfRecorder.class);

    public RuntimeValue<Object> createCxfEndpoint(String id) {
        ConfigStore.getInstance().setClassLoader(Thread.currentThread().getContextClassLoader());
        CxfConfig config = id == null ? new CxfConfig() : new CxfConfig(id);
        String providerClass = CxfCommonExportHelper.transformCxfKindIntoProviderClass(config.cxfKind());
        LOG.infof("Creating CXF endpoint of type %s for id '%s'", providerClass, id);

        List<ServiceLoader.Provider<CxfEndpointProvider>> providers =
                ServiceLoader.load(CxfEndpointProvider.class).stream().toList();

        ServiceLoader.Provider<CxfEndpointProvider> provider =
                ServiceLoaderHelper.findProviderByClassName(providers, providerClass);

        if (provider == null) {
            LOG.warnf("No CXF endpoint provider found for class %s", providerClass);
            return null;
        }

        Object endpoint = provider.get().create(id);
        if (endpoint != null) {
            return new RuntimeValue<>(endpoint);
        }
        return null;
    }
}
