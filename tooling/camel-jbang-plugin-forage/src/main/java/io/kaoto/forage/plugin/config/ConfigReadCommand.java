package io.kaoto.forage.plugin.config;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import io.kaoto.forage.catalog.model.ConditionalBeanGroup;
import io.kaoto.forage.catalog.model.ConditionalBeanInfo;
import io.kaoto.forage.catalog.model.ConfigEntry;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;
import io.kaoto.forage.config.editor.ForageConfiguration;
import io.kaoto.forage.config.editor.ForagePropertyParser;
import io.kaoto.forage.config.editor.ForagePropertyParser.ParsedProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import picocli.CommandLine;

/**
 * Command to read Forage configuration properties and generate a JSON list of beans
 * that would be created at runtime. This command supports the Kaoto VSCode extension
 * by providing a machine-readable view of configured beans.
 */
@CommandLine.Command(name = "read", description = "Read Forage configuration and list beans that would be created")
public class ConfigReadCommand extends AbstractConfigCommand {

    private static final String FORAGE_PROPERTIES_PREFIX = "forage.";

    @CommandLine.Option(
            names = {"--dir", "-d"},
            description = "Directory to scan for properties files. Defaults to current directory.")
    private File directory;

    @CommandLine.Option(
            names = {"--filter", "-f"},
            description = "Filter by factory type (e.g., 'jdbc', 'jms', 'agent'). If not specified, shows all.")
    private String filter;

    public ConfigReadCommand(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        try {
            Optional<String> strategyError = initCatalogAndStrategy();
            if (strategyError.isPresent()) {
                return outputError(strategyError.get());
            }

            if (directory == null) {
                directory = new File(System.getProperty("user.dir"));
            }

            if (!directory.exists() || !directory.isDirectory()) {
                return outputError("Directory does not exist: " + directory.getAbsolutePath());
            }

            // Find all properties files
            List<File> propertiesFiles = strategy.findPropertiesFiles(directory);
            if (propertiesFiles.isEmpty()) {
                return outputResult(List.of(), "No Forage properties files found");
            }

            // Resolve the keys of all the files together, then list the beans of each file
            ForageConfiguration configuration = ForageConfiguration.read(catalog, propertiesFiles, Set.of());
            List<BeanInfo> beans = new ArrayList<>();
            for (File file : propertiesFiles) {
                beans.addAll(beansOf(configuration, file));
            }

            // Apply filter if specified
            if (filter != null && !filter.isEmpty()) {
                beans = beans.stream()
                        .filter(b -> b.factoryType.equalsIgnoreCase(filter))
                        .toList();
            }

            return outputResult(beans, null);
        } catch (Exception e) {
            return outputError("Error reading configuration: " + e.getMessage());
        }
    }

    private List<BeanInfo> beansOf(ForageConfiguration configuration, File file) {
        // Group properties by factory type and instance name
        Map<String, InstanceProperties> instances = new LinkedHashMap<>();

        for (ForageConfiguration.Entry entry : configuration.entries()) {
            ParsedProperty parsed = entry.property();
            if (!entry.file().equals(file) || parsed == null) {
                continue;
            }

            // Create a unique key based on factory type, instance name, and bean type
            String instanceKey = buildInstanceKey(parsed);

            InstanceProperties instance = instances.computeIfAbsent(
                    instanceKey,
                    k -> new InstanceProperties(parsed.factoryType(), parsed.instanceName(), parsed.beanType()));
            // Update bean type if not set (in case first property didn't have it)
            if (instance.beanType == null && parsed.beanType() != null) {
                instance.beanType = parsed.beanType();
            }
            instance.properties.put(parsed.propertyName(), entry.value());
        }

        // Convert instances to BeanInfo
        List<BeanInfo> beans = new ArrayList<>();
        for (InstanceProperties instance : instances.values()) {
            BeanInfo beanInfo = createBeanInfo(instance, file);
            if (beanInfo != null) {
                beans.add(beanInfo);
            }
        }

        return beans;
    }

    /**
     * Builds a unique instance key for grouping properties.
     * Format: factoryType:instanceName:beanType (with defaults for null values)
     */
    private String buildInstanceKey(ParsedProperty parsed) {
        StringBuilder key = new StringBuilder(parsed.factoryType());
        key.append(":");
        key.append(parsed.instanceName() != null ? parsed.instanceName() : "default");
        if (parsed.beanType() != null) {
            key.append(":").append(parsed.beanType());
        }
        return key.toString();
    }

    private BeanInfo createBeanInfo(InstanceProperties instance, File sourceFile) {
        // Determine the bean name
        String beanName = determineBeanName(instance);

        // Determine the bean kind/type
        String beanKind = determineBeanKind(instance);

        // Get factory metadata
        Optional<ForageCatalogReader.FactoryMetadata> metadataOpt = catalog.getFactoryMetadata(instance.factoryType);

        // Determine the Java type
        String javaType = metadataOpt.isPresent() ? getFactoryJavaType(metadataOpt.get(), instance) : "Unknown";

        // Collect configuration values
        Map<String, String> configuration = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : instance.properties.entrySet()) {
            String value = entry.getValue();
            configuration.put(entry.getKey(), value);
        }

        // Detect conditional beans that would be created
        List<OutputConditionalBean> conditionalBeans = detectConditionalBeans(instance);

        return new BeanInfo(
                beanName,
                beanKind,
                instance.factoryType,
                javaType,
                sourceFile.getAbsolutePath(),
                configuration,
                conditionalBeans);
    }

    private String determineBeanName(InstanceProperties instance) {
        // If there's an instance name, use it as the bean name
        if (instance.instanceName != null && !instance.instanceName.isEmpty()) {
            return instance.instanceName;
        }

        // Some beans register under their own name (e.g. one security policy per provider)
        Optional<String> beanDefaultName = catalog.getBeanDefaultBeanName(instance.beanType);
        if (beanDefaultName.isPresent()) {
            return beanDefaultName.get();
        }

        // Use the default bean name declared by the factory, or derive one from its factoryType
        Optional<ForageCatalogReader.FactoryMetadata> metadataOpt = catalog.getFactoryMetadata(instance.factoryType);
        if (metadataOpt.isPresent()) {
            String declaredName = metadataOpt.get().defaultBeanName();
            return declaredName != null ? declaredName : deriveDefaultBeanName(metadataOpt.get());
        }

        // Fallback to factory type key
        return instance.factoryType;
    }

    /**
     * Derives the default bean name from the factory metadata.
     * Uses the simple class name of the factoryType with the first letter in lowercase.
     * e.g., "javax.sql.DataSource" -> "dataSource"
     *       "jakarta.jms.ConnectionFactory" -> "connectionFactory"
     *       "org.apache.camel.component.langchain4j.agent.api.Agent" -> "agent"
     */
    private String deriveDefaultBeanName(ForageCatalogReader.FactoryMetadata metadata) {
        String factoryType = metadata.factoryType();
        if (factoryType == null || factoryType.isEmpty()) {
            return metadata.factoryTypeKey();
        }

        // Get the simple class name (last segment after the last dot)
        int lastDot = factoryType.lastIndexOf('.');
        String simpleName = lastDot >= 0 ? factoryType.substring(lastDot + 1) : factoryType;

        // Convert first letter to lowercase
        if (!simpleName.isEmpty()) {
            return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
        }
        return simpleName;
    }

    private String determineBeanKind(InstanceProperties instance) {
        // First, check if we have a bean type from the property key parsing
        // e.g., forage.test.ollama.model.name -> beanType = "ollama"
        if (instance.beanType != null && !instance.beanType.isEmpty()) {
            return instance.beanType;
        }

        // Look for kind/type properties in the config values
        for (String kindProp : ForagePropertyParser.KIND_PROPERTIES) {
            String value = instance.properties.get(kindProp);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private String getFactoryJavaType(ForageCatalogReader.FactoryMetadata metadata, InstanceProperties instance) {
        // For beans with a specific bean type, look up the feature to determine the Java type
        if (instance.beanType != null) {
            Optional<String> featureOpt = catalog.getBeanFeature(instance.beanType);
            if (featureOpt.isPresent()) {
                String feature = featureOpt.get();
                // The feature key in beansByFeature indicates the Java type
                // e.g., "Chat Model" -> ChatLanguageModel, "Memory" -> ChatMemory
                // For features that match a Java interface pattern, return that
                if (feature.contains(".")) {
                    // Feature is already a fully qualified type (e.g., "javax.sql.DataSource")
                    return feature;
                }
                // Map known feature names to their Java types
                return mapFeatureToJavaType(feature);
            }
        }

        // Use the factory's factoryType from the catalog
        String factoryType = metadata.factoryType();
        if (factoryType != null && !factoryType.isEmpty()) {
            return factoryType;
        }

        // Fallback to factory name
        return metadata.factoryName();
    }

    private String mapFeatureToJavaType(String feature) {
        // Map feature category names to their Java types
        return switch (feature) {
            case "Chat Model" -> "dev.langchain4j.model.chat.ChatModel";
            case "Memory" -> "dev.langchain4j.memory.ChatMemory";
            case "javax.sql.DataSource" -> "javax.sql.DataSource";
            case "jakarta.jms.ConnectionFactory" -> "jakarta.jms.ConnectionFactory";
            default -> feature;
        };
    }

    private List<OutputConditionalBean> detectConditionalBeans(InstanceProperties instance) {
        List<OutputConditionalBean> conditionalBeans = new ArrayList<>();

        // Get conditional beans from the catalog for this factory type
        List<ConditionalBeanGroup> catalogCondBeans = catalog.getConditionalBeans(instance.factoryType);

        for (ConditionalBeanGroup condBeanGroup : catalogCondBeans) {
            // Check if the config entry that enables this conditional bean is set to true
            String configEntry = condBeanGroup.getConfigEntry();
            if (configEntry != null) {
                String configValue = instance.properties.get(toInstancePropertyKey(configEntry, instance));
                if (!"true".equalsIgnoreCase(configValue)) {
                    continue; // This conditional bean group is not enabled
                }
            }

            // Add all beans from this conditional bean definition
            List<ConditionalBeanInfo> beanInfos = condBeanGroup.getBeans();
            if (beanInfos == null) {
                continue;
            }
            for (ConditionalBeanInfo catalogBean : beanInfos) {
                String beanName;
                if (catalogBean.getName() != null) {
                    // Fixed bean name
                    beanName = catalogBean.getName();
                } else if (catalogBean.getNameFromConfig() != null) {
                    // Bean name comes from a config property, falling back to the config entry default
                    beanName = resolveNameFromConfig(catalogBean.getNameFromConfig(), instance);
                    if (beanName == null) {
                        continue; // No configured name and no default: the runtime does not register the bean
                    }
                } else {
                    beanName = condBeanGroup.getId();
                }

                conditionalBeans.add(
                        new OutputConditionalBean(beanName, catalogBean.getJavaType(), catalogBean.getDescription()));
            }
        }

        return conditionalBeans;
    }

    /**
     * Converts a catalog config key (e.g., "forage.jdbc.transaction.enabled") into the key used in
     * instance.properties, which has both the forage prefix and the factory prefix stripped
     * (e.g., "transaction.enabled").
     */
    private String toInstancePropertyKey(String configKey, InstanceProperties instance) {
        String factoryPrefix = FORAGE_PROPERTIES_PREFIX + instance.factoryType + ".";
        if (configKey.startsWith(factoryPrefix)) {
            return configKey.substring(factoryPrefix.length());
        }
        return configKey;
    }

    /**
     * Resolves a conditional bean name from the config entry it references, using the configured
     * value or else the config entry default from the catalog. Returns null when neither is set.
     */
    private String resolveNameFromConfig(String nameConfigKey, InstanceProperties instance) {
        String configured = instance.properties.get(toInstancePropertyKey(nameConfigKey, instance));
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return catalog
                .getFactoryMetadata(instance.factoryType)
                .map(ForageCatalogReader.FactoryMetadata::configEntries)
                .stream()
                .flatMap(List::stream)
                .filter(entry -> nameConfigKey.equals(entry.getName()))
                .map(ConfigEntry::getDefaultValue)
                .filter(defaultValue -> defaultValue != null && !defaultValue.isBlank())
                .findFirst()
                .orElse(null);
    }

    private int outputResult(List<BeanInfo> beans, String message) throws JsonProcessingException {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        if (message != null) {
            result.put("message", message);
        }
        result.put("directory", directory.getAbsolutePath());
        result.put("beanCount", beans.size());

        // Output beans as a flat list - kind and javaType provide enough info
        List<Map<String, Object>> beansList = new ArrayList<>();
        for (BeanInfo bean : beans) {
            Map<String, Object> beanMap = new LinkedHashMap<>();
            beanMap.put("name", bean.name);
            if (bean.kind != null) {
                beanMap.put("kind", bean.kind);
            }
            beanMap.put("javaType", bean.javaType);
            beanMap.put("sourceFile", bean.sourceFile);
            beanMap.put("configuration", bean.configuration);

            if (!bean.conditionalBeans.isEmpty()) {
                List<Map<String, String>> conditionalList = new ArrayList<>();
                for (OutputConditionalBean cb : bean.conditionalBeans) {
                    Map<String, String> cbMap = new LinkedHashMap<>();
                    cbMap.put("name", cb.name);
                    cbMap.put("javaType", cb.javaType);
                    cbMap.put("description", cb.description);
                    conditionalList.add(cbMap);
                }
                beanMap.put("conditionalBeans", conditionalList);
            }

            beansList.add(beanMap);
        }

        result.put("beans", beansList);

        printer().println(OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        return 0;
    }

    // Helper classes

    private static class InstanceProperties {
        final String factoryType;
        final String instanceName;
        String beanType;
        final Map<String, String> properties = new LinkedHashMap<>();

        InstanceProperties(String factoryType, String instanceName, String beanType) {
            this.factoryType = factoryType;
            this.instanceName = instanceName;
            this.beanType = beanType;
        }
    }

    private record BeanInfo(
            String name,
            String kind,
            String factoryType,
            String javaType,
            String sourceFile,
            Map<String, String> configuration,
            List<OutputConditionalBean> conditionalBeans) {}

    private record OutputConditionalBean(String name, String javaType, String description) {}
}
