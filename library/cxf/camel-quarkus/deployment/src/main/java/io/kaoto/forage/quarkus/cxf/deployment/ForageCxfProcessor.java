package io.kaoto.forage.quarkus.cxf.deployment;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.camel.quarkus.core.deployment.spi.CamelRuntimeBeanBuildItem;
import org.jboss.logging.Logger;
import io.kaoto.forage.core.annotations.FactoryType;
import io.kaoto.forage.core.annotations.FactoryVariant;
import io.kaoto.forage.core.annotations.ForageFactory;
import io.kaoto.forage.core.util.config.ConfigEntries;
import io.kaoto.forage.core.util.config.ConfigEntry;
import io.kaoto.forage.core.util.config.ConfigHelper;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.cxf.common.CxfConfig;
import io.kaoto.forage.cxf.common.CxfConfigEntries;
import io.kaoto.forage.cxf.common.CxfModuleDescriptor;
import io.kaoto.forage.quarkus.cxf.ForageCxfRecorder;
import io.quarkiverse.cxf.deployment.CxfRouteRegistrationRequestorBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.SystemPropertyBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.runtime.RuntimeValue;

@ForageFactory(
        value = "CXF (Quarkus)",
        variant = FactoryVariant.QUARKUS,
        components = {"camel-cxf"},
        description = "Native CXF SOAP endpoint for Quarkus with compile-time optimization",
        type = FactoryType.CXF_ENDPOINT,
        autowired = true,
        configClass = CxfConfig.class,
        runtimeDependencies = {"mvn:org.apache.camel.quarkus:camel-quarkus-cxf-soap"})
public class ForageCxfProcessor {

    private static final Logger LOG = Logger.getLogger(ForageCxfProcessor.class);
    private static final String FEATURE = "forage-cxf";
    private static final CxfModuleDescriptor DESCRIPTOR = new CxfModuleDescriptor();

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    CxfRouteRegistrationRequestorBuildItem requestCxfRouteRegistration() {
        return new CxfRouteRegistrationRequestorBuildItem(FEATURE);
    }

    @BuildStep
    void discoverCxfEndpoints(
            BuildProducer<ForageCxfBuildItem> cxfEndpoints, BuildProducer<SystemPropertyBuildItem> systemProperties) {

        CxfConfig defaultConfig = DESCRIPTOR.createConfig(null);
        Set<String> named = ConfigStore.getInstance()
                .readPrefixes(defaultConfig, ConfigHelper.getNamedPropertyRegexp(DESCRIPTOR.modulePrefix()));

        if (!named.isEmpty()) {
            for (String name : named) {
                CxfConfig config = DESCRIPTOR.createConfig(name);
                cxfEndpoints.produce(new ForageCxfBuildItem(name, name, config));
                propagateConfigAsSystemProperties(name, systemProperties);
            }
        } else {
            Set<String> defaultPrefixes = ConfigStore.getInstance()
                    .readPrefixes(defaultConfig, ConfigHelper.getDefaultPropertyRegexp(DESCRIPTOR.modulePrefix()));
            if (!defaultPrefixes.isEmpty()) {
                cxfEndpoints.produce(new ForageCxfBuildItem(DESCRIPTOR.defaultBeanName(), null, defaultConfig));
                propagateConfigAsSystemProperties(null, systemProperties);
            } else {
                LOG.debug("No Forage CXF configuration found, skipping CXF endpoint discovery");
            }
        }
    }

    private void propagateConfigAsSystemProperties(
            String prefix, BuildProducer<SystemPropertyBuildItem> systemProperties) {

        Map<ConfigModule, ConfigEntry> modules = ConfigEntries.getModules(CxfConfigEntries.class);
        for (ConfigModule module : modules.keySet()) {
            ConfigModule resolved = prefix != null ? module.asNamed(prefix) : module;
            Optional<String> value = ConfigStore.getInstance().get(resolved);
            if (value.isPresent()) {
                String propertyName = resolved.propertyName();
                LOG.debugf("Propagating %s=%s as system property", propertyName, value.get());
                systemProperties.produce(new SystemPropertyBuildItem(propertyName, value.get()));
            }
        }
    }

    @BuildStep
    @Record(value = ExecutionTime.RUNTIME_INIT)
    void registerCxfEndpoints(
            ForageCxfRecorder recorder,
            List<ForageCxfBuildItem> cxfEndpoints,
            BuildProducer<CamelRuntimeBeanBuildItem> beans) {

        for (ForageCxfBuildItem item : cxfEndpoints) {
            LOG.infof("Registering CXF endpoint bean '%s'", item.getName());
            RuntimeValue<Object> endpoint = recorder.createCxfEndpoint(item.getPrefix());
            if (endpoint != null) {
                beans.produce(new CamelRuntimeBeanBuildItem(item.getName(), Object.class.getName(), endpoint));
            }
        }
    }

    @BuildStep
    void registerReflectiveClasses(
            List<ForageCxfBuildItem> cxfEndpoints, BuildProducer<ReflectiveClassBuildItem> reflectiveClasses) {

        for (ForageCxfBuildItem item : cxfEndpoints) {
            String serviceClassName = item.getConfig().serviceClassName();
            if (serviceClassName != null) {
                LOG.infof("Registering CXF service class for reflection: %s", serviceClassName);
                reflectiveClasses.produce(ReflectiveClassBuildItem.builder(serviceClassName)
                        .methods()
                        .fields()
                        .build());
            }
        }
    }
}
