package io.kaoto.forage.quarkus.cxf;

import java.util.List;
import java.util.ServiceLoader;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;
import io.kaoto.forage.core.common.RuntimeType;
import io.kaoto.forage.core.common.ServiceLoaderHelper;
import io.kaoto.forage.core.cxf.CxfEndpointProvider;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.cxf.common.CxfCommonExportHelper;
import io.kaoto.forage.cxf.common.CxfConfig;
import io.kaoto.forage.cxf.soap.ForageCxfEndpoint;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class ForageCxfRecorder {

    private static final Logger LOG = Logger.getLogger(ForageCxfRecorder.class);
    private static final String DEFAULT_CXF_SERVLET_PATH = "/services";

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
            throw new IllegalStateException(
                    ("CXF endpoint '%s' could not be created: no CxfEndpointProvider of type %s was found via "
                                    + "ServiceLoader. Ensure the Forage CXF provider artifact for kind '%s' "
                                    + "(e.g. forage-cxf-soap-client or forage-cxf-soap-service) is on the classpath.")
                            .formatted(id, providerClass, config.cxfKind()));
        }

        Object endpoint = provider.get().create(id);
        if (endpoint == null) {
            throw new IllegalStateException(
                    ("CXF endpoint '%s' could not be created: provider %s returned no endpoint. Verify the "
                                    + "'forage.cxf.*' configuration for '%s' (service class, address, etc.).")
                            .formatted(id, providerClass, id));
        }
        if (endpoint instanceof ForageCxfEndpoint forageCxfEndpoint) {
            String cxfServletPath = ConfigProvider.getConfig()
                    .getOptionalValue("quarkus.cxf.path", String.class)
                    .orElse(DEFAULT_CXF_SERVLET_PATH);
            forageCxfEndpoint.setServletContainerCxfPath(cxfServletPath, RuntimeType.QUARKUS);
        }
        return new RuntimeValue<>(endpoint);
    }
}
