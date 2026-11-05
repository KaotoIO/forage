package io.kaoto.forage.plugin.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import io.kaoto.forage.catalog.model.ConditionalBeanGroup;
import io.kaoto.forage.catalog.model.ConditionalBeanInfo;
import io.kaoto.forage.catalog.model.ConfigEntry;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import picocli.CommandLine;

/**
 * Command to read Forage configuration properties and generate a JSON list of beans
 * that would be created at runtime. This command supports the Kaoto VSCode extension
 * by providing a machine-readable view of configured beans.
 */
@CommandLine.Command(name = "read", description = "Read Forage configuration and list beans that would be created")
public class ConfigReadCommand extends CamelCommand {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // Pattern to match forage properties: forage.{factoryTypeKey}.{property} or
    // forage.{instanceName}.{factoryTypeKey}.{property}
    private static final Pattern FORAGE_PROPERTY_PATTERN = Pattern.compile("^forage\\.(.+)$");

    // Properties that indicate the bean kind/type
    private static final Set<String> KIND_PROPERTIES = Set.of("db.kind", "kind");

    private static final String FORAGE_PROPERTIES_PREFIX = "forage.";

    @CommandLine.Option(
            names = {"--dir", "-d"},
            description = "Directory to scan for properties files. Defaults to current directory.")
    private File directory;

    @CommandLine.Option(
            names = {"--filter", "-f"},
            description = "Filter by factory type (e.g., 'jdbc', 'jms', 'agent'). If not specified, shows all.")
    private String filter;

    @CommandLine.Mixin
    private PropertiesFileStrategyOption strategyOption = new PropertiesFileStrategyOption();

    private ForageCatalogReader catalog;

    private PropertiesFileStrategy strategy;

    public ConfigReadCommand(CamelJBangMain main) {
        super(main);
    }

    /**
     * Checks if the given key is a known factory type key by querying the catalog.
     *
     * @param key the key to check
     * @return true if the key is a known factory type, false otherwise
     */
    private boolean isKnownFactoryKey(String key) {
        return catalog.getFactoryMetadata(key).isPresent();
    }

    @Override
    public Integer doCall() throws Exception {
        try {
            catalog = ForageCatalogReader.getInstance();
            strategy = strategyOption.resolve(catalog);

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

            // Parse all properties files and detect beans
            List<BeanInfo> beans = new ArrayList<>();
            for (File file : propertiesFiles) {
                List<BeanInfo> fileBeans = parsePropertiesFile(file);
                beans.addAll(fileBeans);
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

    private List<BeanInfo> parsePropertiesFile(File file) throws IOException {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(file)) {
            props.load(fis);
        }

        // Group properties by factory type and instance name
        Map<String, InstanceProperties> instances = new LinkedHashMap<>();

        for (String key : props.stringPropertyNames()) {
            Matcher matcher = FORAGE_PROPERTY_PATTERN.matcher(key);
            if (!matcher.matches()) {
                continue;
            }

            String remainder = matcher.group(1);
            ParsedProperty parsed = parsePropertyKey(remainder);
            if (parsed == null) {
                continue;
            }

            // Create a unique key based on factory type, instance name, and bean type
            String instanceKey = buildInstanceKey(parsed);

            InstanceProperties instance = instances.computeIfAbsent(
                    instanceKey, k -> new InstanceProperties(parsed.factoryType, parsed.instanceName, parsed.beanType));
            // Update bean type if not set (in case first property didn't have it)
            if (instance.beanType == null && parsed.beanType != null) {
                instance.beanType = parsed.beanType;
            }
            instance.properties.put(parsed.propertyName, props.getProperty(key));
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
     * Parses a property key (after removing the "forage." prefix) to extract
     * instance name, factory type, bean type, and property name.
     *
     * Examples:
     * - "jdbc.url" -> factoryType=jdbc, instanceName=null, propertyName=url
     * - "ds1.jdbc.url" -> factoryType=jdbc, instanceName=ds1, propertyName=url
     * - "jms.broker.url" -> factoryType=jms, instanceName=null, propertyName=broker.url
     * - "cf1.jms.broker.url" -> factoryType=jms, instanceName=cf1, propertyName=broker.url
     * - "ollama.model.name" -> factoryType=multi, beanType=ollama, instanceName=null, propertyName=model.name
     * - "test.ollama.model.name" -> factoryType=multi, beanType=ollama, instanceName=test, propertyName=model.name
     * - "myMemory.infinispan.server-list" -> factoryType=multi, beanType=infinispan, instanceName=myMemory
     * - "postgresql.jdbc.url" -> factoryType=jdbc, instanceName=postgresql, propertyName=url
     * - "my.ds.jdbc.url" -> factoryType=jdbc, instanceName=my.ds, propertyName=url
     */
    private ParsedProperty parsePropertyKey(String key) {
        ParsedProperty fromConfigEntries = parseUsingConfigEntries(key);
        if (fromConfigEntries != null) {
            return fromConfigEntries;
        }

        // Fall back to segment heuristics for keys that match no catalog config entry (e.g., typos)
        String[] parts = key.split("\\.", 2);
        if (parts.length < 2) {
            return null;
        }

        String firstPart = parts[0];
        String restOfKey = parts[1];

        // Check if the first part is a known factory type key
        if (isKnownFactoryKey(firstPart)) {
            return new ParsedProperty(firstPart, null, null, restOfKey);
        }

        // Check if the first part is a known bean type (e.g., ollama, infinispan)
        Optional<String> beanFactoryOpt = catalog.findFactoryTypeKeyForBeanName(firstPart);
        if (beanFactoryOpt.isPresent()) {
            // e.g., "ollama.model.name" -> factoryType=multi, beanType=ollama, propertyName=model.name
            return new ParsedProperty(beanFactoryOpt.get(), null, firstPart, restOfKey);
        }

        // Otherwise, the first part might be an instance name
        // Check the second segment for a factory type or bean type
        String[] restParts = restOfKey.split("\\.", 2);
        if (restParts.length >= 1) {
            String secondPart = restParts[0];
            String propertyName = restParts.length > 1 ? restParts[1] : "";

            // Check if second part is a known factory type key
            if (isKnownFactoryKey(secondPart)) {
                return new ParsedProperty(secondPart, firstPart, null, propertyName);
            }

            // Check if second part is a known bean type (e.g., "test.ollama.model.name")
            Optional<String> beanFactoryOpt2 = catalog.findFactoryTypeKeyForBeanName(secondPart);
            if (beanFactoryOpt2.isPresent()) {
                return new ParsedProperty(beanFactoryOpt2.get(), firstPart, secondPart, propertyName);
            }
        }

        // Could be a bean-specific property like "forage.google.api.key" (for agent factory)
        // Try to match using the catalog's property prefix mapping
        Optional<String> factoryTypeOpt = catalog.findFactoryTypeKeyForPropertyPrefix(firstPart);
        return factoryTypeOpt.map(s -> new ParsedProperty(s, null, null, key)).orElse(null);
    }

    /**
     * Parses a property key the way the runtime resolves it: "forage.&lt;instance&gt;.&lt;config entry&gt;",
     * where the instance name is optional and may contain dots. Tries the shortest instance name first,
     * so a key that is itself a config entry belongs to the default instance.
     *
     * @return the parsed property, or null when no suffix of the key is a catalog config entry
     */
    private ParsedProperty parseUsingConfigEntries(String key) {
        String instanceName = null;
        String entryKey = key;
        while (true) {
            Optional<ForageCatalogReader.ConfigEntryOwner> owner =
                    catalog.findConfigEntryOwner(FORAGE_PROPERTIES_PREFIX + entryKey);
            if (owner.isPresent()) {
                return toParsedProperty(instanceName, entryKey, owner.get());
            }
            int dot = entryKey.indexOf('.');
            if (dot < 0) {
                return null;
            }
            String segment = entryKey.substring(0, dot);
            instanceName = instanceName == null ? segment : instanceName + "." + segment;
            entryKey = entryKey.substring(dot + 1);
        }
    }

    private ParsedProperty toParsedProperty(
            String instanceName, String entryKey, ForageCatalogReader.ConfigEntryOwner owner) {
        String factoryTypeKey = owner.factoryTypeKey();
        String factoryPrefix = factoryTypeKey + ".";
        // Factory-level property (e.g., "jdbc.url", "jms.artemis.reconnect.attempts"), unless the
        // factory key is really a bean prefix (e.g., the security policy factory keyed as "keycloak")
        if (entryKey.startsWith(factoryPrefix)
                && catalog.findBeanNameForPropertyPrefix(factoryTypeKey).isEmpty()) {
            return new ParsedProperty(factoryTypeKey, instanceName, null, entryKey.substring(factoryPrefix.length()));
        }
        // Bean property (e.g., "ollama.model.name", "azure.openai.api.key")
        if (owner.beanName() != null) {
            Optional<String> beanPrefix = catalog.findBeanPropertyPrefix(owner.beanName(), entryKey);
            if (beanPrefix.isPresent()) {
                return new ParsedProperty(
                        factoryTypeKey,
                        instanceName,
                        owner.beanName(),
                        entryKey.substring(beanPrefix.get().length() + 1));
            }
        }
        // Factory property outside the factory namespace (e.g., "multi.agent.names", "guardrail.*")
        return new ParsedProperty(factoryTypeKey, instanceName, null, entryKey);
    }

    /**
     * Builds a unique instance key for grouping properties.
     * Format: factoryType:instanceName:beanType (with defaults for null values)
     */
    private String buildInstanceKey(ParsedProperty parsed) {
        StringBuilder key = new StringBuilder(parsed.factoryType);
        key.append(":");
        key.append(parsed.instanceName != null ? parsed.instanceName : "default");
        if (parsed.beanType != null) {
            key.append(":").append(parsed.beanType);
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
        for (String kindProp : KIND_PROPERTIES) {
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

    private int outputError(String message) throws JsonProcessingException {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        printer().println(OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(error));
        return 1;
    }

    // Helper classes

    private record ParsedProperty(String factoryType, String instanceName, String beanType, String propertyName) {}

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
