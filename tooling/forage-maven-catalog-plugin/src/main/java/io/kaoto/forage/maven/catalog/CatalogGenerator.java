package io.kaoto.forage.maven.catalog;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import io.kaoto.forage.catalog.model.ConfigEntry;
import io.kaoto.forage.catalog.model.ConfigurationModule;
import io.kaoto.forage.catalog.model.FactoryVariant;
import io.kaoto.forage.catalog.model.FactoryVariants;
import io.kaoto.forage.catalog.model.FeatureBeans;
import io.kaoto.forage.catalog.model.ForageBean;
import io.kaoto.forage.catalog.model.ForageCatalog;
import io.kaoto.forage.catalog.model.ForageConfigurationCatalog;
import io.kaoto.forage.catalog.model.ForageFactory;
import io.kaoto.forage.core.annotations.FactoryType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * Core catalog generation logic for Forage components.
 *
 * This class generates a simplified catalog structure for Kaoto integration,
 * grouping factories with their platform variants, beans by feature, and config entries.
 */
public class CatalogGenerator {

    private final Log log;
    private final ObjectMapper objectMapper;
    private final ObjectMapper yamlObjectMapper;

    private String format = "json";

    public CatalogGenerator(Log log) {
        this.log = log;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        this.yamlObjectMapper = new ObjectMapper(new YAMLFactory());
    }

    /**
     * Generate a catalog from the given Maven project.
     *
     * @param project the Maven project to scan
     * @param outputDirectory the directory to write catalog files
     * @return the catalog generation result
     * @throws IOException if file operations fail
     */
    public CatalogResult generateCatalog(MavenProject project, File outputDirectory, File rootDir) throws IOException {
        log.info("Scanning project dependencies for Forage components...");

        List<ForageComponent> components = discoverComponents(project, rootDir);

        log.info("Found " + components.size() + " Forage components");

        List<ForageFactory> factories = transformToSimplifiedCatalog(components, project.getProperties());

        log.info("Generated " + factories.size() + " factories in simplified catalog");

        ForageCatalog catalog = new ForageCatalog();
        catalog.setVersion("2.0");
        catalog.setFactories(factories);
        catalog.setGeneratedBy("forage-maven-catalog-plugin");
        catalog.setTimestamp(System.currentTimeMillis());

        List<File> generatedFiles = new ArrayList<>();

        switch (format.toLowerCase()) {
            case "json" -> generatedFiles.add(generateJsonCatalog(catalog, outputDirectory));
            case "yaml" -> generatedFiles.add(generateYamlCatalog(catalog, outputDirectory));
            default -> {
                generatedFiles.add(generateJsonCatalog(catalog, outputDirectory));
                generatedFiles.add(generateYamlCatalog(catalog, outputDirectory));
            }
        }

        // Generate the configuration catalog with ALL configs across all modules
        generatedFiles.addAll(generateConfigurationCatalog(components, outputDirectory));

        return new CatalogResult(factories.size(), generatedFiles);
    }

    /**
     * Resolves a GAV string that may be missing a version by looking up "{artifactId}.version"
     * in the Maven project properties. GAVs already containing a version are returned as-is.
     *
     * @param gav the GAV string (e.g., "mvn:io.quarkiverse.artemis:quarkus-artemis-jms")
     * @param projectProperties the Maven project properties
     * @return the GAV with version resolved, or the original GAV if no version found
     */
    private String resolveGavVersion(String gav, Properties projectProperties) {
        if (gav == null || projectProperties == null) {
            return gav;
        }

        // Strip "mvn:" prefix for parsing
        String rawGav = gav.startsWith("mvn:") ? gav.substring(4) : gav;
        String[] parts = rawGav.split(":");

        // Already has a version (groupId:artifactId:version)
        if (parts.length >= 3) {
            return gav;
        }

        // Only groupId:artifactId — try to resolve version from properties
        if (parts.length == 2) {
            String artifactId = parts[1];
            String versionProp = artifactId + ".version";
            String version = projectProperties.getProperty(versionProp);
            if (version != null && !version.isEmpty()) {
                log.debug("Resolved version for " + artifactId + " -> " + version + " (from property " + versionProp
                        + ")");
                String prefix = gav.startsWith("mvn:") ? "mvn:" : "";
                return prefix + rawGav + ":" + version;
            } else {
                log.warn("No version found for dependency " + gav + " (looked up property: " + versionProp + ")");
            }
        }

        return gav;
    }

    /**
     * Resolves versions in a list of GAV strings.
     */
    private List<String> resolveGavVersions(List<String> gavs, Properties projectProperties) {
        if (gavs == null || gavs.isEmpty()) {
            return gavs;
        }
        return gavs.stream().map(g -> resolveGavVersion(g, projectProperties)).toList();
    }

    /**
     * Resolves versions in a variant-keyed dependency map.
     */
    private Map<String, List<String>> resolveVariantDependencyVersions(
            Map<String, List<String>> deps, Properties projectProperties) {
        if (deps == null || deps.isEmpty()) {
            return deps;
        }
        Map<String, List<String>> resolved = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : deps.entrySet()) {
            resolved.put(entry.getKey(), resolveGavVersions(entry.getValue(), projectProperties));
        }
        return resolved;
    }

    /**
     * Transform component-based structure to simplified factory-centric catalog.
     */
    private List<ForageFactory> transformToSimplifiedCatalog(
            List<ForageComponent> components, Properties projectProperties) {
        ConfigMappings configMappings = collectConfigMappings(components);
        log.info("Found " + configMappings.classToConfigName.size() + " Config classes total");

        CollectedData data = collectDataFromComponents(components, configMappings, projectProperties);

        associateBeansWithFactories(data, configMappings.classToConfigName);
        associateConfigEntriesWithFactories(data, configMappings.classToArtifactId);

        return new ArrayList<>(data.factoryMap.values());
    }

    /**
     * Builds both config class mappings in a single pass over components.
     */
    private ConfigMappings collectConfigMappings(List<ForageComponent> components) {
        Map<String, String> classToConfigName = new HashMap<>();
        Map<String, String> classToArtifactId = new HashMap<>();

        for (ForageComponent component : components) {
            if (component.getConfigClasses() != null) {
                classToConfigName.putAll(component.getConfigClasses());
                for (String className : component.getConfigClasses().keySet()) {
                    classToArtifactId.put(className, component.getArtifactId());
                }
            }
        }

        return new ConfigMappings(classToConfigName, classToArtifactId);
    }

    /**
     * Collects factories, config entries, and beans from components.
     */
    private CollectedData collectDataFromComponents(
            List<ForageComponent> components, ConfigMappings configMappings, Properties projectProperties) {
        CollectedData data = new CollectedData();

        for (ForageComponent component : components) {
            String artifactId = component.getArtifactId();
            String gav = createGav(component.getGroupId(), artifactId, component.getVersion());

            collectConfigEntries(component, artifactId, data.configsByPrefix);
            collectFactories(component, data, gav, configMappings, projectProperties);
            collectBeans(component, data.beansByComponent, gav, projectProperties);
        }

        return data;
    }

    private static String createGav(String groupId, String artifactId, String version) {
        return groupId + ":" + artifactId + ":" + version;
    }

    /**
     * Collects configuration entries from a component.
     */
    private void collectConfigEntries(
            ForageComponent component, String artifactId, Map<String, List<ConfigEntry>> configsByPrefix) {
        if (component.getConfigurationProperties() != null
                && !component.getConfigurationProperties().isEmpty()) {
            configsByPrefix.put(artifactId, new ArrayList<>(component.getConfigurationProperties()));
        }
    }

    /**
     * Collects factories from a component.
     */
    private void collectFactories(
            ForageComponent component,
            CollectedData data,
            String gav,
            ConfigMappings configMappings,
            Properties projectProperties) {

        if (component.getFactories() == null) {
            return;
        }

        for (ScannedFactory scannedFactory : component.getFactories()) {
            String platform = mapVariantToPlatformKey(scannedFactory.getVariant());
            String factoryType = scannedFactory.getFactoryType();

            ForageFactory catalogFactory = data.factoryMap.computeIfAbsent(
                    factoryType, k -> createCatalogFactory(scannedFactory, configMappings.classToConfigName));

            // Track config class for this factory type (used later for config entry association)
            String configClassName = scannedFactory.getConfigClassName();
            if (configClassName != null && !configClassName.equals(Constants.DEFAULT_CONFIG_FQN)) {
                data.factoryTypeToConfigClassName.put(factoryType, configClassName);
            }

            // Add this platform variant
            FactoryVariant variant = new FactoryVariant(scannedFactory.getClassName(), gav);
            if (scannedFactory.getRuntimeDependencies() != null
                    && !scannedFactory.getRuntimeDependencies().isEmpty()) {
                variant.setAdditionalDependencies(
                        resolveGavVersions(scannedFactory.getRuntimeDependencies(), projectProperties));
            }
            setVariantByPlatform(catalogFactory.getVariants(), platform, variant);

            // Copy conditional beans if present, resolving versions in runtimeDependencies
            if (scannedFactory.getConditionalBeans() != null
                    && !scannedFactory.getConditionalBeans().isEmpty()) {
                List<io.kaoto.forage.catalog.model.ConditionalBeanGroup> resolved =
                        scannedFactory.getConditionalBeans().stream()
                                .map(group -> {
                                    if (group.getRuntimeDependencies() != null) {
                                        group.setRuntimeDependencies(resolveVariantDependencyVersions(
                                                group.getRuntimeDependencies(), projectProperties));
                                    }
                                    return group;
                                })
                                .toList();
                catalogFactory.setConditionalBeans(resolved);
            }
        }
    }

    /**
     * Creates a new ForageFactory for the catalog from a scanned factory.
     */
    private ForageFactory createCatalogFactory(ScannedFactory scannedFactory, Map<String, String> classToConfigName) {
        ForageFactory catalogFactory = new ForageFactory();
        catalogFactory.setName(scannedFactory.getName());
        catalogFactory.setFactoryType(scannedFactory.getFactoryType());
        catalogFactory.setDescription(scannedFactory.getDescription());
        catalogFactory.setComponents(scannedFactory.getComponents());
        catalogFactory.setAutowired(scannedFactory.isAutowired());
        catalogFactory.setVariants(new FactoryVariants());
        catalogFactory.setBeansByFeature(new ArrayList<>());
        catalogFactory.setPropertiesFile(resolvePropertiesFile(scannedFactory.getConfigClassName(), classToConfigName));
        return catalogFactory;
    }

    /**
     * Collects beans from a component.
     */
    private void collectBeans(
            ForageComponent component,
            Map<String, List<BeanWithGav>> beansByComponent,
            String gav,
            Properties projectProperties) {
        if (component.getBeans() == null) {
            return;
        }

        for (ScannedBean bean : component.getBeans()) {
            // Resolve versions in bean runtime dependencies
            if (bean.getRuntimeDependencies() != null) {
                bean.setRuntimeDependencies(
                        resolveVariantDependencyVersions(bean.getRuntimeDependencies(), projectProperties));
            }
            BeanWithGav beanWithGav = new BeanWithGav(bean, gav, component.getConfigurationProperties());
            if (bean.getComponents() != null) {
                for (String comp : bean.getComponents()) {
                    beansByComponent
                            .computeIfAbsent(comp, k -> new ArrayList<>())
                            .add(beanWithGav);
                }
            }
        }
    }

    /**
     * Associates beans with their corresponding factories.
     */
    private void associateBeansWithFactories(CollectedData data, Map<String, String> classToConfigName) {
        for (ForageFactory factory : data.factoryMap.values()) {
            if (factory.getComponents() == null) {
                continue;
            }

            // Track already-added beans by name to avoid duplicates
            Map<String, Set<String>> addedBeansByFeature = new HashMap<>();

            for (String component : factory.getComponents()) {
                List<BeanWithGav> matchingBeans = data.beansByComponent.get(component);
                if (matchingBeans != null) {
                    addBeansToFactory(factory, matchingBeans, addedBeansByFeature, classToConfigName);
                }
            }
        }
    }

    /**
     * Adds beans to a factory, avoiding duplicates.
     */
    private void addBeansToFactory(
            ForageFactory factory,
            List<BeanWithGav> matchingBeans,
            Map<String, Set<String>> addedBeansByFeature,
            Map<String, String> classToConfigName) {

        for (BeanWithGav beanWithGav : matchingBeans) {
            String feature = determineFeature(beanWithGav.bean, factory.getFactoryType());
            String beanName = beanWithGav.bean.getName();

            // Skip if already added
            Set<String> addedBeans = addedBeansByFeature.computeIfAbsent(feature, k -> new HashSet<>());
            if (addedBeans.contains(beanName)) {
                continue;
            }
            addedBeans.add(beanName);

            ForageBean catalogBean = createCatalogBean(beanWithGav, classToConfigName);
            getOrCreateFeatureBeans(factory.getBeansByFeature(), feature)
                    .getBeans()
                    .add(catalogBean);
        }
    }

    /**
     * Finds or creates a FeatureBeans entry for the given feature.
     */
    private FeatureBeans getOrCreateFeatureBeans(List<FeatureBeans> beansByFeature, String feature) {
        return beansByFeature.stream()
                .filter(fb -> fb.getFeature().equals(feature))
                .findFirst()
                .orElseGet(() -> {
                    FeatureBeans newFeatureBeans = new FeatureBeans(feature, new ArrayList<>());
                    beansByFeature.add(newFeatureBeans);
                    return newFeatureBeans;
                });
    }

    /**
     * Associates config entries with factories.
     */
    private void associateConfigEntriesWithFactories(CollectedData data, Map<String, String> classToArtifactId) {
        for (ForageFactory factory : data.factoryMap.values()) {
            List<ConfigEntry> factoryConfigs = findConfigsForFactory(
                    factory, data.configsByPrefix, classToArtifactId, data.factoryTypeToConfigClassName);
            if (!factoryConfigs.isEmpty()) {
                factory.setConfigEntries(factoryConfigs);
            }
        }
    }

    /**
     * Map variant enum value to platform key used in catalog.
     */
    private String mapVariantToPlatformKey(String variant) {
        if (variant == null || variant.isEmpty()) {
            return io.kaoto.forage.core.annotations.FactoryVariant.BASE.getKey();
        }

        try {
            return io.kaoto.forage.core.annotations.FactoryVariant.valueOf(variant)
                    .getKey();
        } catch (IllegalArgumentException e) {
            log.warn("Unknown variant: " + variant + ", using BASE as fallback");
            return io.kaoto.forage.core.annotations.FactoryVariant.BASE.getKey();
        }
    }

    /**
     * Sets the variant on the FactoryVariants object based on platform key.
     */
    private void setVariantByPlatform(FactoryVariants variants, String platform, FactoryVariant variant) {
        switch (platform) {
            case "base" -> variants.setBase(variant);
            case "springboot" -> variants.setSpringboot(variant);
            case "quarkus" -> variants.setQuarkus(variant);
            default -> {
                log.warn("Unknown platform: " + platform + ", setting as base");
                variants.setBase(variant);
            }
        }
    }

    /**
     * Resolves the properties file name from a config class name.
     * Returns null for absent or default config classes.
     */
    private String resolvePropertiesFile(String configClassName, Map<String, String> classToConfigName) {
        if (configClassName == null || configClassName.equals(Constants.DEFAULT_CONFIG_FQN)) {
            return null;
        }

        String configName = classToConfigName.get(configClassName);
        if (configName != null) {
            return configName + ".properties";
        }

        log.debug("Config class not found in mappings: " + configClassName);
        return null;
    }

    /**
     * Determine the feature category for a bean based on its info and factory type.
     */
    private String determineFeature(ScannedBean bean, String factoryType) {
        // Use the feature from annotation if available
        if (bean.getFeature() != null && !bean.getFeature().isEmpty()) {
            return bean.getFeature();
        }

        return FactoryType.fromDisplayName(factoryType)
                .map(FactoryType::toString)
                .orElse(factoryType);
    }

    /**
     * Create a ForageBean for the catalog output from the scanned bean with GAV and configs.
     */
    private ForageBean createCatalogBean(BeanWithGav beanWithGav, Map<String, String> classToConfigName) {
        ForageBean catalogBean = beanWithGav.bean.toCatalogBean(beanWithGav.gav);

        // Add config entries if available
        if (beanWithGav.configs != null && !beanWithGav.configs.isEmpty()) {
            catalogBean.setConfigEntries(new ArrayList<>(beanWithGav.configs));
        }

        // Set properties file based on bean's Config class
        catalogBean.setPropertiesFile(resolvePropertiesFile(beanWithGav.bean.getConfigClassName(), classToConfigName));

        return catalogBean;
    }

    /**
     * Finds config entries for a factory by merging entries from the FactoryType enum's
     * configArtifactId and from the factory's own configClass module.
     */
    private List<ConfigEntry> findConfigsForFactory(
            ForageFactory factory,
            Map<String, List<ConfigEntry>> configsByPrefix,
            Map<String, String> classToArtifactId,
            Map<String, String> factoryTypeToConfigClassName) {
        List<ConfigEntry> result = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();

        String factoryType = factory.getFactoryType();
        if (factoryType == null || factoryType.isEmpty()) {
            return result;
        }

        // 1. Configs from the FactoryType enum's configArtifactId
        String configPrefix = FactoryType.fromDisplayName(factoryType)
                .map(FactoryType::getConfigArtifactId)
                .orElse(null);

        if (configPrefix != null && configsByPrefix.containsKey(configPrefix)) {
            for (ConfigEntry entry : configsByPrefix.get(configPrefix)) {
                if (seenNames.add(entry.getName())) {
                    result.add(entry);
                }
            }
        }

        // 2. Configs from the factory's configClass module (may be in a different artifact)
        String configClassName = factoryTypeToConfigClassName.get(factoryType);
        if (configClassName != null) {
            String configArtifactId = classToArtifactId.get(configClassName);
            if (configArtifactId != null && configsByPrefix.containsKey(configArtifactId)) {
                for (ConfigEntry configEntry : configsByPrefix.get(configArtifactId)) {
                    if (seenNames.add(configEntry.getName())) {
                        result.add(configEntry);
                    }
                }
            }
        }

        return result;
    }

    /**
     * Generates a configuration catalog containing ALL configuration entries across all modules,
     * regardless of whether they are associated with a factory.
     */
    List<File> generateConfigurationCatalog(List<ForageComponent> components, File outputDirectory) throws IOException {
        List<ConfigurationModule> modules = new ArrayList<>();

        for (ForageComponent component : components) {
            if (component.getConfigurationProperties() == null
                    || component.getConfigurationProperties().isEmpty()) {
                continue;
            }

            ConfigurationModule module = new ConfigurationModule();
            module.setArtifactId(component.getArtifactId());
            module.setGroupId(component.getGroupId());
            module.setPropertiesFile(resolvePropertiesFileForComponent(component));
            module.setConfigEntries(new ArrayList<>(component.getConfigurationProperties()));
            modules.add(module);
        }

        log.info("Generated configuration catalog with " + modules.size() + " modules");

        ForageConfigurationCatalog configCatalog = new ForageConfigurationCatalog();
        configCatalog.setVersion("1.0");
        configCatalog.setGeneratedBy("forage-maven-catalog-plugin");
        configCatalog.setTimestamp(System.currentTimeMillis());
        configCatalog.setModules(modules);

        List<File> generatedFiles = new ArrayList<>();

        switch (format.toLowerCase()) {
            case "json" -> generatedFiles.add(generateJsonConfigurationCatalog(configCatalog, outputDirectory));
            case "yaml" -> generatedFiles.add(generateYamlConfigurationCatalog(configCatalog, outputDirectory));
            default -> {
                generatedFiles.add(generateJsonConfigurationCatalog(configCatalog, outputDirectory));
                generatedFiles.add(generateYamlConfigurationCatalog(configCatalog, outputDirectory));
            }
        }

        return generatedFiles;
    }

    /**
     * Resolves the properties file name for a component by looking at its config classes.
     */
    private String resolvePropertiesFileForComponent(ForageComponent component) {
        if (component.getConfigClasses() == null || component.getConfigClasses().isEmpty()) {
            return null;
        }

        // Use the first config class that resolves to a properties file
        for (Map.Entry<String, String> entry : component.getConfigClasses().entrySet()) {
            String configName = entry.getValue();
            if (configName != null && !configName.isEmpty()) {
                return configName + ".properties";
            }
        }

        return null;
    }

    private File generateJsonConfigurationCatalog(ForageConfigurationCatalog catalog, File outputDirectory)
            throws IOException {
        File outputFile = new File(outputDirectory, "forage-configuration-catalog.json");
        objectMapper.writeValue(outputFile, catalog);
        return outputFile;
    }

    private File generateYamlConfigurationCatalog(ForageConfigurationCatalog catalog, File outputDirectory)
            throws IOException {
        File outputFile = new File(outputDirectory, "forage-configuration-catalog.yaml");
        yamlObjectMapper.writeValue(outputFile, catalog);
        return outputFile;
    }

    private List<ForageComponent> discoverComponents(MavenProject project, File rootDir) {
        List<ForageComponent> components = new ArrayList<>();

        Set<Artifact> artifacts = project.getArtifacts();

        if (artifacts == null) {
            log.warn("No artifacts found in project");
            return components;
        }

        CodeScanner codeScanner = new CodeScanner(log);
        Path rootPath = rootDir.toPath();

        for (Artifact artifact : artifacts) {
            if (isForageComponent(artifact)) {
                log.debug("Processing Forage component: " + artifact.getArtifactId());
                ForageComponent component = createComponentFromArtifact(artifact, rootPath, codeScanner);
                components.add(component);
            }
        }

        return components;
    }

    private boolean isForageComponent(Artifact artifact) {
        String groupId = artifact.getGroupId();
        return groupId.equals(Constants.FORAGE_GROUP_ID) || groupId.startsWith(Constants.FORAGE_GROUP_ID + ".");
    }

    private ForageComponent createComponentFromArtifact(Artifact artifact, Path rootDir, CodeScanner codeScanner) {
        ForageComponent component = new ForageComponent();
        component.setArtifactId(artifact.getArtifactId());
        component.setGroupId(artifact.getGroupId());
        component.setVersion(artifact.getVersion());

        ScanResult scanResult = codeScanner.scanAllInOnePass(artifact, rootDir);

        component.setBeans(scanResult.getBeans());
        component.setFactories(scanResult.getFactories());
        component.setConfigurationProperties(scanResult.getConfigProperties());
        component.setConfigClasses(scanResult.getConfigClasses());

        return component;
    }

    private File generateJsonCatalog(ForageCatalog catalog, File outputDirectory) throws IOException {
        File outputFile = new File(outputDirectory, "forage-catalog.json");
        objectMapper.writeValue(outputFile, catalog);
        return outputFile;
    }

    private File generateYamlCatalog(ForageCatalog catalog, File outputDirectory) throws IOException {
        File outputFile = new File(outputDirectory, "forage-catalog.yaml");
        yamlObjectMapper.writeValue(outputFile, catalog);
        return outputFile;
    }

    // Setters for configuration
    public void setFormat(String format) {
        this.format = format;
    }

    /**
     * Holds both config class mappings built from a single pass over components.
     */
    private static class ConfigMappings {
        /** Maps config class FQN to Config.name() return value. */
        final Map<String, String> classToConfigName;

        /** Maps config class FQN to the artifact ID of the containing module. */
        final Map<String, String> classToArtifactId;

        ConfigMappings(Map<String, String> classToConfigName, Map<String, String> classToArtifactId) {
            this.classToConfigName = classToConfigName;
            this.classToArtifactId = classToArtifactId;
        }
    }

    /**
     * Holds collected data from component scanning.
     */
    private static class CollectedData {
        final Map<String, ForageFactory> factoryMap = new LinkedHashMap<>();
        final Map<String, List<ConfigEntry>> configsByPrefix = new HashMap<>();
        final Map<String, List<BeanWithGav>> beansByComponent = new HashMap<>();

        /** Maps factory type to the config class name from the factory's @ForageFactory annotation. */
        final Map<String, String> factoryTypeToConfigClassName = new HashMap<>();
    }

    /**
     * Helper class to track bean with its GAV and configs.
     */
    private static class BeanWithGav {
        final ScannedBean bean;
        final String gav;
        final List<ConfigEntry> configs;

        BeanWithGav(ScannedBean bean, String gav, List<ConfigEntry> configs) {
            this.bean = bean;
            this.gav = gav;
            this.configs = configs;
        }
    }
}
