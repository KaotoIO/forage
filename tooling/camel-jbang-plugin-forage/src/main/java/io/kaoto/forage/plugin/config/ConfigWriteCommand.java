package io.kaoto.forage.plugin.config;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import picocli.CommandLine;

/**
 * Command to write Forage configuration properties from JSON input.
 * This command creates, updates, or merges properties files based on JSON input from the Kaoto wizard.
 */
@CommandLine.Command(name = "write", description = "Write Forage configuration from JSON input to properties files")
public class ConfigWriteCommand extends CamelCommand {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @CommandLine.Option(
            names = {"--input", "-i"},
            description = "JSON input string containing configuration values. If not provided, reads from stdin.")
    private String input;

    @CommandLine.Option(
            names = {"--dir", "-d"},
            description = "Directory where properties files will be written. Defaults to current directory.")
    private File directory;

    @CommandLine.Option(
            names = {"--delete"},
            description = "Delete configuration for a specific instance name.")
    private boolean delete;

    @CommandLine.Option(
            names = {"--name", "-n"},
            description = "Instance name to delete (used with --delete).")
    private String instanceName;

    @CommandLine.Option(
            names = {"--strategy", "-s"},
            description = "Property file strategy: 'forage' writes to forage-*.properties files (default), "
                    + "'application' writes to application.properties.",
            defaultValue = "application")
    private String strategy;

    private ForageCatalogReader catalog;

    public ConfigWriteCommand(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        try {
            catalog = ForageCatalogReader.getInstance();

            if (directory == null) {
                directory = new File(System.getProperty("user.dir"));
            }

            if (!directory.exists() && !directory.mkdirs()) {
                return outputError("Failed to create directory: " + directory.getAbsolutePath());
            }

            if (delete) {
                return handleDelete();
            }

            String jsonInput = getJsonInput();
            if (jsonInput == null || jsonInput.trim().isEmpty()) {
                return outputError("No JSON input provided. Use --input or pipe JSON to stdin.");
            }

            Map<String, String> configMap = parseJsonInput(jsonInput);
            if (configMap.isEmpty()) {
                return outputError("Empty configuration provided.");
            }

            return writeConfiguration(configMap);
        } catch (Exception e) {
            return outputError("Error processing configuration: " + e.getMessage());
        }
    }

    private String getJsonInput() throws IOException {
        if (input != null && !input.trim().isEmpty()) {
            return input;
        }

        // Read from stdin
        if (System.in.available() > 0) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                return sb.toString();
            }
        }

        return null;
    }

    private Map<String, String> parseJsonInput(String json) throws JsonProcessingException {
        return OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
    }

    private int writeConfiguration(Map<String, String> configMap) throws IOException {
        // Group configurations by factory type and detect bean names
        Map<String, FactoryConfig> factoryConfigs = groupByFactory(configMap);

        if (factoryConfigs.isEmpty()) {
            return outputError("Could not detect factory type from configuration keys. "
                    + "Keys should follow the pattern 'forage.{factoryType}.{property}' or '{factoryType}.{property}' "
                    + "(e.g., 'forage.jdbc.url', 'jdbc.url', 'forage.jms.broker.url', 'jms.broker.url').");
        }

        // Process each factory type
        Map<String, Object> results = new LinkedHashMap<>();
        for (Map.Entry<String, FactoryConfig> entry : factoryConfigs.entrySet()) {
            String factoryTypeKey = entry.getKey();
            FactoryConfig factoryConfig = entry.getValue();

            String propertiesFileName = getPropertiesFileName(factoryTypeKey);
            if (propertiesFileName == null) {
                continue;
            }

            File propertiesFile = new File(directory, propertiesFileName);
            String operation = propertiesFile.exists() ? "update" : "create";

            writePropertiesFile(propertiesFile, factoryConfig.properties(), factoryTypeKey);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("propertiesFile", propertiesFile.getAbsolutePath());
            result.put("operation", operation);
            result.put(
                    "message",
                    "Successfully " + operation + "d configuration for " + getFactoryDisplayName(factoryTypeKey));
            if (factoryConfig.beanName() != null) {
                result.put("beanName", factoryConfig.beanName());
            }
            if (factoryConfig.kind() != null) {
                result.put("kind", factoryConfig.kind());
            }
            results.put(factoryTypeKey, result);
        }

        // Collect and write dependencies to application.properties
        DependencyInfo dependencies = collectDependencies(factoryConfigs);
        if (hasDependencies(dependencies)) {
            File appPropertiesFile = new File(directory, "application.properties");
            updateDependencies(appPropertiesFile, dependencies);

            // Add dependency info to results
            Map<String, Object> depResult = new LinkedHashMap<>();
            depResult.put("baseDependencies", dependencies.baseDependencies());
            depResult.put("mainDependencies", dependencies.mainDependencies());
            depResult.put("springBootDependencies", dependencies.springBootDependencies());
            depResult.put("quarkusDependencies", dependencies.quarkusDependencies());
            results.put("dependencies", depResult);
        }

        // Output result
        if (results.size() == 1) {
            printer()
                    .println(OBJECT_MAPPER
                            .writerWithDefaultPrettyPrinter()
                            .writeValueAsString(results.values().iterator().next()));
        } else {
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("success", true);
            output.put("factories", results);
            printer().println(OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(output));
        }

        return 0;
    }

    // Property keys that contain the bean name (not written to output, used for prefixing)
    private static final Set<String> BEAN_NAME_KEYS = Set.of("forage.bean.name", "bean.name", "forage.name", "name");

    // Property keys that contain the kind/type of bean (e.g., "ollama", "postgresql", "artemis")
    private static final Set<String> KIND_KEYS = Set.of("kind", "forage.kind", "type", "forage.type");

    /**
     * Groups the input configuration by factory type and extracts bean names.
     * Input keys can follow either pattern:
     * - With forage prefix: "forage.jdbc.url", "forage.jms.broker.url", "forage.ollama.model.name"
     * - Without forage prefix: "jdbc.url", "jms.broker.url", "ollama.model.name"
     * The bean name is extracted from "forage.bean.name" or "bean.name" property.
     * The kind is extracted from "kind" or "forage.kind" property to identify the bean type.
     */
    private Map<String, FactoryConfig> groupByFactory(Map<String, String> configMap) {
        Map<String, FactoryConfig> factoryConfigs = new LinkedHashMap<>();
        // Track kind per factory type key
        Map<String, String> factoryKinds = new HashMap<>();

        // First: extract the global bean name from forage.bean.name or bean.name
        String globalBeanName = null;
        for (String beanNameKey : BEAN_NAME_KEYS) {
            String value = configMap.get(beanNameKey);
            if (value != null && !value.trim().isEmpty()) {
                globalBeanName = value.trim();
                break;
            }
        }

        // Extract the kind (bean type) from kind property
        String globalKind = null;
        for (String kindKey : KIND_KEYS) {
            String value = configMap.get(kindKey);
            if (value != null && !value.trim().isEmpty()) {
                globalKind = value.trim();
                break;
            }
        }

        // If kind is provided, find the factory type key for this bean type
        String kindFactoryTypeKey = null;
        if (globalKind != null) {
            Optional<String> factoryTypeOpt = catalog.findFactoryTypeKeyForBeanName(globalKind);
            if (factoryTypeOpt.isPresent()) {
                kindFactoryTypeKey = factoryTypeOpt.get();
                factoryKinds.put(kindFactoryTypeKey, globalKind);
            }
        }

        // Also check for factory-specific name properties (e.g., forage.jdbc.name, jdbc.name)
        Map<String, String> factoryBeanNames = new HashMap<>();
        for (Map.Entry<String, String> entry : configMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            // Normalize the key (strip forage. prefix if present)
            String normalizedKey = catalog.normalizePropertyKey(key);

            // Find which factory type this key belongs to
            Optional<String> factoryTypeOpt = catalog.findFactoryTypeKeyForInputProperty(key);
            if (factoryTypeOpt.isEmpty()) {
                continue;
            }

            String factoryTypeKey = factoryTypeOpt.get();

            // Check if this key is the prefix/name property for this factory
            Optional<ForageCatalogReader.FactoryMetadata> metadataOpt = catalog.getFactoryMetadata(factoryTypeKey);
            if (metadataOpt.isPresent()) {
                ForageCatalogReader.FactoryMetadata metadata = metadataOpt.get();
                Optional<String> shortPrefixKeyOpt = metadata.getShortPrefixPropertyKey();
                if (shortPrefixKeyOpt.isPresent()
                        && normalizedKey.equals(shortPrefixKeyOpt.get())
                        && value != null
                        && !value.trim().isEmpty()) {
                    factoryBeanNames.put(factoryTypeKey, value.trim());
                }
            }
        }

        // Second pass: build property maps with proper forage prefixes
        for (Map.Entry<String, String> entry : configMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            // Skip bean name properties - they are metadata, not config values
            if (BEAN_NAME_KEYS.contains(key)) {
                continue;
            }

            // Skip kind properties - they are metadata, not config values
            if (KIND_KEYS.contains(key)) {
                continue;
            }

            // Normalize the key (strip forage. prefix if present)
            String normalizedKey = catalog.normalizePropertyKey(key);

            // Try to find factory type from the property key first
            Optional<String> factoryTypeOpt = catalog.findFactoryTypeKeyForInputProperty(key);
            String factoryTypeKey;

            if (factoryTypeOpt.isPresent()) {
                factoryTypeKey = factoryTypeOpt.get();
            } else if (kindFactoryTypeKey != null) {
                // Use the factory type determined from the kind
                factoryTypeKey = kindFactoryTypeKey;
            } else {
                // Try to find factory type from the property prefix
                // (e.g., "google.api.key" -> prefix "google" -> bean "google-gemini" -> factory "multi")
                int dotIndex = normalizedKey.indexOf('.');
                if (dotIndex > 0) {
                    String propertyPrefix = normalizedKey.substring(0, dotIndex);
                    Optional<String> prefixFactoryTypeOpt = catalog.findFactoryTypeKeyForPropertyPrefix(propertyPrefix);
                    if (prefixFactoryTypeOpt.isPresent()) {
                        factoryTypeKey = prefixFactoryTypeOpt.get();
                    } else {
                        // Cannot determine factory type for this property
                        continue;
                    }
                } else {
                    // Cannot determine factory type for this property
                    continue;
                }
            }

            // Use factory-specific bean name if available, otherwise use global bean name
            String beanName = factoryBeanNames.getOrDefault(factoryTypeKey, globalBeanName);
            // Use factory-specific kind if available, otherwise use global kind
            String kindForFactory = factoryKinds.getOrDefault(factoryTypeKey, globalKind);

            // Skip the factory-specific name property itself - it's used for prefixing, not stored directly
            Optional<ForageCatalogReader.FactoryMetadata> metadataOpt = catalog.getFactoryMetadata(factoryTypeKey);
            if (metadataOpt.isPresent()) {
                ForageCatalogReader.FactoryMetadata metadata = metadataOpt.get();
                Optional<String> shortPrefixKeyOpt = metadata.getShortPrefixPropertyKey();
                if (shortPrefixKeyOpt.isPresent() && normalizedKey.equals(shortPrefixKeyOpt.get())) {
                    // Skip the name property, but ensure we have an entry for this factory
                    final String finalKind = kindForFactory;
                    factoryConfigs.computeIfAbsent(
                            factoryTypeKey, k -> new FactoryConfig(beanName, finalKind, new LinkedHashMap<>()));
                    continue;
                }
            }

            // Build the full property key with forage prefix and optional bean name
            // Use the normalized key (without forage. prefix) to build the final key
            String fullPropertyKey = buildPropertyKey(beanName, normalizedKey);

            final String finalKind = kindForFactory;
            FactoryConfig config = factoryConfigs.computeIfAbsent(
                    factoryTypeKey, k -> new FactoryConfig(beanName, finalKind, new LinkedHashMap<>()));
            config.properties().put(fullPropertyKey, value);
        }

        return factoryConfigs;
    }

    /**
     * Builds the full property key with forage prefix and optional bean name.
     * Input: "jdbc.url", beanName: "ds1" -> Output: "forage.ds1.jdbc.url"
     * Input: "jdbc.url", beanName: null -> Output: "forage.jdbc.url"
     */
    private String buildPropertyKey(String beanName, String inputKey) {
        StringBuilder sb = new StringBuilder("forage.");
        if (beanName != null && !beanName.isEmpty()) {
            sb.append(beanName).append(".");
        }
        sb.append(inputKey);
        return sb.toString();
    }

    private String getPropertiesFileName(String factoryTypeKey) {
        if ("application".equalsIgnoreCase(strategy)) {
            return "application.properties";
        }
        return catalog.getPropertiesFileName(factoryTypeKey).orElse(null);
    }

    /**
     * Collects dependencies from the catalog for all factory configs.
     * For each factory, collects:
     * - Bean GAV (e.g., forage-jdbc-postgresql) -> base dependencies
     * - Factory variant GAVs for each runtime (base, springboot, quarkus)
     */
    private DependencyInfo collectDependencies(Map<String, FactoryConfig> factoryConfigs) {
        Set<String> baseDeps = new HashSet<>();
        Set<String> mainDeps = new HashSet<>();
        Set<String> springBootDeps = new HashSet<>();
        Set<String> quarkusDeps = new HashSet<>();

        for (Map.Entry<String, FactoryConfig> entry : factoryConfigs.entrySet()) {
            String factoryTypeKey = entry.getKey();
            FactoryConfig factoryConfig = entry.getValue();
            String kind = factoryConfig.kind();

            // Add bean GAV if kind is specified
            if (kind != null && !kind.isEmpty()) {
                baseDeps.addAll(catalog.getBeanGavs(kind));
            }

            // Add factory variant GAVs
            catalog.getFactoryVariantGav(factoryTypeKey, "base").ifPresent(mainDeps::add);
            catalog.getFactoryVariantGav(factoryTypeKey, "springboot").ifPresent(springBootDeps::add);
            catalog.getFactoryVariantGav(factoryTypeKey, "quarkus").ifPresent(quarkusDeps::add);
        }

        return new DependencyInfo(baseDeps, mainDeps, springBootDeps, quarkusDeps);
    }

    /**
     * Checks if there are any dependencies to write.
     */
    private boolean hasDependencies(DependencyInfo deps) {
        return !deps.baseDependencies().isEmpty()
                || !deps.mainDependencies().isEmpty()
                || !deps.springBootDependencies().isEmpty()
                || !deps.quarkusDependencies().isEmpty();
    }

    // Dependency property keys
    private static final String CAMEL_JBANG_DEPENDENCIES = "camel.jbang.dependencies";
    private static final String CAMEL_JBANG_DEPENDENCIES_MAIN = "camel.jbang.dependencies.main";
    private static final String CAMEL_JBANG_DEPENDENCIES_SPRING_BOOT = "camel.jbang.dependencies.spring-boot";
    private static final String CAMEL_JBANG_DEPENDENCIES_QUARKUS = "camel.jbang.dependencies.quarkus";

    /**
     * Updates the application.properties file with dependencies.
     * Merges new forage dependencies with existing ones without removing existing dependencies.
     */
    private void updateDependencies(File propertiesFile, DependencyInfo newDeps) throws IOException {
        final Map<String, Set<String>> existingDeps = newDependencyMap();

        List<String> lines = new ArrayList<>();
        Set<String> dependencyKeysFound = new HashSet<>();

        // Read existing file and parse dependencies
        if (propertiesFile.exists()) {
            lines = new ArrayList<>(java.nio.file.Files.readAllLines(propertiesFile.toPath(), StandardCharsets.UTF_8));

            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                int equalsIndex = line.indexOf('=');
                if (equalsIndex > 0) {
                    String key = line.substring(0, equalsIndex).trim();
                    String value = line.substring(equalsIndex + 1).trim();

                    if (existingDeps.containsKey(key)) {
                        dependencyKeysFound.add(key);
                        // Parse existing dependencies
                        if (!value.isEmpty()) {
                            for (String dep : value.split(",")) {
                                String trimmedDep = dep.trim();
                                if (!trimmedDep.isEmpty()) {
                                    existingDeps.get(key).add(trimmedDep);
                                }
                            }
                        }
                    }
                }
            }
        }

        // Merge new dependencies with existing ones
        existingDeps.get(CAMEL_JBANG_DEPENDENCIES).addAll(newDeps.baseDependencies());
        existingDeps.get(CAMEL_JBANG_DEPENDENCIES_MAIN).addAll(newDeps.mainDependencies());
        existingDeps.get(CAMEL_JBANG_DEPENDENCIES_SPRING_BOOT).addAll(newDeps.springBootDependencies());
        existingDeps.get(CAMEL_JBANG_DEPENDENCIES_QUARKUS).addAll(newDeps.quarkusDependencies());

        // Update or append dependency lines
        List<String> updatedLines = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                updatedLines.add(line);
                continue;
            }

            int equalsIndex = line.indexOf('=');
            if (equalsIndex > 0) {
                String key = line.substring(0, equalsIndex).trim();

                if (existingDeps.containsKey(key)) {
                    Set<String> deps = existingDeps.get(key);
                    if (!deps.isEmpty()) {
                        updatedLines.add(key + "=" + String.join(",", deps));
                    }
                    // Mark as processed so we don't append it again
                    dependencyKeysFound.add(key);
                } else {
                    updatedLines.add(line);
                }
            } else {
                updatedLines.add(line);
            }
        }

        // Append any new dependency keys that weren't in the original file
        for (String key : existingDeps.keySet()) {
            if (!dependencyKeysFound.contains(key)) {
                Set<String> deps = existingDeps.get(key);
                if (!deps.isEmpty()) {
                    updatedLines.add(key + "=" + String.join(",", deps));
                }
            }
        }

        // Write the updated content
        try (FileOutputStream fos = new FileOutputStream(propertiesFile)) {
            StringBuilder content = new StringBuilder();
            for (String line : updatedLines) {
                content.append(line).append("\n");
            }
            fos.write(content.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static Map<String, Set<String>> newDependencyMap() {
        Map<String, Set<String>> existingDeps = new LinkedHashMap<>();
        existingDeps.put(CAMEL_JBANG_DEPENDENCIES, new LinkedHashSet<>());
        existingDeps.put(CAMEL_JBANG_DEPENDENCIES_MAIN, new LinkedHashSet<>());
        existingDeps.put(CAMEL_JBANG_DEPENDENCIES_SPRING_BOOT, new LinkedHashSet<>());
        existingDeps.put(CAMEL_JBANG_DEPENDENCIES_QUARKUS, new LinkedHashSet<>());
        return existingDeps;
    }

    private String getFactoryDisplayName(String factoryTypeKey) {
        return catalog.getFactoryMetadata(factoryTypeKey)
                .map(ForageCatalogReader.FactoryMetadata::factoryName)
                .orElse(factoryTypeKey + " factory");
    }

    private void writePropertiesFile(File file, Map<String, String> newProperties, String factoryTypeKey)
            throws IOException {
        if (!file.exists()) {
            // Create new file with header
            try (FileOutputStream fos = new FileOutputStream(file)) {
                StringBuilder content = new StringBuilder();
                content.append("# Forage ")
                        .append(getFactoryDisplayName(factoryTypeKey))
                        .append(" Configuration\n");
                content.append("# Generated by Forage JBang Plugin\n\n");

                for (Map.Entry<String, String> entry : newProperties.entrySet()) {
                    content.append(entry.getKey())
                            .append("=")
                            .append(entry.getValue())
                            .append("\n");
                }

                fos.write(content.toString().getBytes(StandardCharsets.UTF_8));
            }
            return;
        }

        // Read existing file preserving order and comments
        List<String> lines = java.nio.file.Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);

        // Track which properties need to be updated vs appended
        final List<String> updatedLines = computeUpdatableProperties(newProperties, lines);

        // Write the updated content
        try (FileOutputStream fos = new FileOutputStream(file)) {
            StringBuilder content = new StringBuilder();
            for (String line : updatedLines) {
                content.append(line).append("\n");
            }
            fos.write(content.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static List<String> computeUpdatableProperties(Map<String, String> newProperties, List<String> lines) {
        Map<String, String> propsToUpdate = new LinkedHashMap<>(newProperties);
        Set<String> updatedKeys = new HashSet<>();

        // Update existing properties in place
        List<String> updatedLines = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();

            // Preserve comments and blank lines
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                updatedLines.add(line);
                continue;
            }

            // Parse property line
            int equalsIndex = line.indexOf('=');
            if (equalsIndex > 0) {
                String key = line.substring(0, equalsIndex).trim();

                if (propsToUpdate.containsKey(key)) {
                    // Update the property value
                    updatedLines.add(key + "=" + propsToUpdate.get(key));
                    updatedKeys.add(key);
                } else {
                    // Keep existing property unchanged
                    updatedLines.add(line);
                }
            } else {
                // Not a valid property line, keep as-is
                updatedLines.add(line);
            }
        }

        // Append new properties that weren't in the original file
        for (Map.Entry<String, String> entry : propsToUpdate.entrySet()) {
            if (!updatedKeys.contains(entry.getKey())) {
                updatedLines.add(entry.getKey() + "=" + entry.getValue());
            }
        }
        return updatedLines;
    }

    private int handleDelete() throws IOException {
        if (instanceName == null || instanceName.trim().isEmpty()) {
            return outputError("Instance name (--name) is required for delete operation.");
        }

        Map<String, Object> results = new LinkedHashMap<>();
        Set<String> allDeletedTypes = new HashSet<>();
        Set<String> allDeletedFactoryTypeKeys = new HashSet<>();

        // Determine which properties files to scan based on strategy
        final Set<File> propertiesFilesToScan = determineScanableProperties();

        // Delete instance configuration from all relevant files
        for (File propertiesFile : propertiesFilesToScan) {
            DeletedConfigInfo deletedInfo = deleteInstanceConfiguration(propertiesFile, instanceName);
            if (!deletedInfo.deletedPropertyKeys().isEmpty()) {
                allDeletedTypes.addAll(deletedInfo.deletedTypes());
                allDeletedFactoryTypeKeys.addAll(deletedInfo.deletedFactoryTypeKeys());
                results.put(
                        propertiesFile.getName(),
                        Map.of(
                                "success",
                                true,
                                "propertiesFile",
                                propertiesFile.getAbsolutePath(),
                                "deletedProperties",
                                deletedInfo.deletedPropertyKeys().size(),
                                "message",
                                "Deleted configuration for instance '" + instanceName + "'"));
            }
        }

        if (results.isEmpty()) {
            return outputError("No configuration found for instance '" + instanceName + "'");
        }

        // After deletion, clean up dependencies that are no longer needed
        File appPropertiesFile = new File(directory, "application.properties");
        Map<String, Object> dependencyCleanupResult =
                cleanupUnusedDependencies(appPropertiesFile, allDeletedTypes, allDeletedFactoryTypeKeys);
        if (!dependencyCleanupResult.isEmpty()) {
            results.put("dependencyCleanup", dependencyCleanupResult);
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("success", true);
        output.put("operation", "delete");
        output.put("instanceName", instanceName);
        output.put("results", results);
        printer().println(OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(output));

        return 0;
    }

    private Set<File> determineScanableProperties() {
        Set<File> propertiesFilesToScan = new HashSet<>();
        if ("application".equalsIgnoreCase(strategy)) {
            File appProps = new File(directory, "application.properties");
            if (appProps.exists()) {
                propertiesFilesToScan.add(appProps);
            }
        } else {
            for (ForageCatalogReader.FactoryMetadata metadata : catalog.getAllFactories()) {
                String propertiesFileName = getPropertiesFileName(metadata.factoryTypeKey());
                if (propertiesFileName != null) {
                    File propertiesFile = new File(directory, propertiesFileName);
                    if (propertiesFile.exists()) {
                        propertiesFilesToScan.add(propertiesFile);
                    }
                }
            }
        }
        return propertiesFilesToScan;
    }

    // Property key suffixes that contain the bean kind (e.g., *.db.kind, *.kind)
    private static final Set<String> KIND_PROPERTY_SUFFIXES = Set.of(".db.kind", ".kind");

    /**
     * Deletes all configuration properties for a given instance name.
     * Properties matching the pattern forage.{instanceName}.* are deleted.
     *
     * @param file the properties file to modify
     * @param instanceName the instance name to delete
     * @return information about what was deleted
     */
    private DeletedConfigInfo deleteInstanceConfiguration(File file, String instanceName) throws IOException {
        Set<String> deletedPropertyKeys = new HashSet<>();
        Set<String> deletedTypes = new HashSet<>();
        Set<String> deletedFactoryTypeKeys = new HashSet<>();

        // Read existing file preserving order and comments
        List<String> lines = java.nio.file.Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);

        // Find keys to remove matching the instance prefix
        // Pattern: forage.{instanceName}.*
        String prefixToMatch = "forage." + instanceName + ".";

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int equalsIndex = line.indexOf('=');
            if (equalsIndex > 0) {
                String key = line.substring(0, equalsIndex).trim();
                String value = line.substring(equalsIndex + 1).trim();

                if (key.startsWith(prefixToMatch)) {
                    deletedPropertyKeys.add(key);

                    // Extract the type segment (third part after forage.{instanceName}.)
                    String afterPrefix = key.substring(prefixToMatch.length());
                    int dotIndex = afterPrefix.indexOf('.');
                    if (dotIndex > 0) {
                        String typeSegment = afterPrefix.substring(0, dotIndex);
                        deletedTypes.add(typeSegment);

                        // Check if this is a known factory type key
                        if (catalog.getFactoryMetadata(typeSegment).isPresent()) {
                            deletedFactoryTypeKeys.add(typeSegment);
                        }
                    }

                    // Also extract kind from property VALUES (e.g., *.db.kind=postgresql, *.kind=artemis)
                    for (String kindSuffix : KIND_PROPERTY_SUFFIXES) {
                        if (key.endsWith(kindSuffix) && !value.isEmpty()) {
                            // Add the bean kind (e.g., "postgresql", "artemis", "ollama") to deleted types
                            deletedTypes.add(value.toLowerCase());
                            break;
                        }
                    }
                }
            }
        }

        if (deletedPropertyKeys.isEmpty()) {
            return new DeletedConfigInfo(Set.of(), Set.of(), Set.of());
        }

        // Filter out lines with keys to remove, preserving order
        final List<String> updatedLines = computeRemovableLines(lines, deletedPropertyKeys);

        // Write the updated content
        try (FileOutputStream fos = new FileOutputStream(file)) {
            StringBuilder content = new StringBuilder();
            for (String line : updatedLines) {
                content.append(line).append("\n");
            }
            fos.write(content.toString().getBytes(StandardCharsets.UTF_8));
        }

        return new DeletedConfigInfo(deletedPropertyKeys, deletedTypes, deletedFactoryTypeKeys);
    }

    private static List<String> computeRemovableLines(List<String> lines, Set<String> deletedPropertyKeys) {
        List<String> updatedLines = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();

            // Preserve comments and blank lines
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                updatedLines.add(line);
                continue;
            }

            // Check if this line should be removed
            int equalsIndex = line.indexOf('=');
            if (equalsIndex > 0) {
                String key = line.substring(0, equalsIndex).trim();
                if (!deletedPropertyKeys.contains(key)) {
                    updatedLines.add(line);
                }
            } else {
                updatedLines.add(line);
            }
        }
        return updatedLines;
    }

    /**
     * Scans all properties files to find what types are still configured.
     * This includes both type segments from property keys (e.g., "jdbc", "jms")
     * and kind values from property values (e.g., "postgresql", "artemis").
     *
     * @return set of type segments still in use (e.g., "jdbc", "ollama", "postgresql")
     */
    private Set<String> findConfiguredTypes() throws IOException {
        Set<String> configuredTypes = new HashSet<>();

        final Set<File> propertiesFilesToScan = determineScanableProperties();

        for (File propertiesFile : propertiesFilesToScan) {
            List<String> lines = java.nio.file.Files.readAllLines(propertiesFile.toPath(), StandardCharsets.UTF_8);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int equalsIndex = line.indexOf('=');
                if (equalsIndex > 0) {
                    String key = line.substring(0, equalsIndex).trim();
                    String value = line.substring(equalsIndex + 1).trim();

                    // Parse forage.{instanceName}.{type}.* pattern
                    if (key.startsWith("forage.")) {
                        String afterForage = key.substring("forage.".length());
                        // Skip to second segment (after instanceName)
                        int firstDot = afterForage.indexOf('.');
                        if (firstDot > 0) {
                            String afterInstanceName = afterForage.substring(firstDot + 1);
                            int secondDot = afterInstanceName.indexOf('.');
                            if (secondDot > 0) {
                                String typeSegment = afterInstanceName.substring(0, secondDot);
                                configuredTypes.add(typeSegment);
                            }
                        }

                        // Also extract kind values from properties like *.db.kind, *.kind
                        for (String kindSuffix : KIND_PROPERTY_SUFFIXES) {
                            if (key.endsWith(kindSuffix) && !value.isEmpty()) {
                                configuredTypes.add(value.toLowerCase());
                                break;
                            }
                        }
                    }
                }
            }
        }

        return configuredTypes;
    }

    /**
     * Cleans up dependencies that are no longer needed after configuration deletion.
     *
     * @param appPropertiesFile the application.properties file
     * @param deletedTypes the types that were deleted (e.g., "ollama", "postgresql")
     * @param deletedFactoryTypeKeys the factory type keys that were deleted (e.g., "jdbc", "agent")
     * @return result map with information about removed dependencies
     */
    private Map<String, Object> cleanupUnusedDependencies(
            File appPropertiesFile, Set<String> deletedTypes, Set<String> deletedFactoryTypeKeys) throws IOException {

        if (!appPropertiesFile.exists()) {
            return Map.of();
        }

        // Find what types are still configured after deletion
        Set<String> remainingTypes = findConfiguredTypes();

        // Determine which GAVs to remove
        Set<String> gavsToRemove = new HashSet<>();

        for (String deletedType : deletedTypes) {
            if (!remainingTypes.contains(deletedType)) {
                // This type is no longer configured, remove its dependency
                gavsToRemove.addAll(catalog.getBeanGavs(deletedType));
            }
        }

        // Check factory type keys - if no instances remain for a factory, remove its variant GAVs
        for (String factoryTypeKey : deletedFactoryTypeKeys) {
            boolean factoryStillInUse = remainingTypes.stream()
                    .anyMatch(type -> type.equals(factoryTypeKey)
                            || catalog.findFactoryTypeKeyForBeanName(type)
                                    .map(ftk -> ftk.equals(factoryTypeKey))
                                    .orElse(false));

            if (!factoryStillInUse) {
                catalog.getFactoryVariantGav(factoryTypeKey, "base").ifPresent(gavsToRemove::add);
                catalog.getFactoryVariantGav(factoryTypeKey, "springboot").ifPresent(gavsToRemove::add);
                catalog.getFactoryVariantGav(factoryTypeKey, "quarkus").ifPresent(gavsToRemove::add);
            }
        }

        if (gavsToRemove.isEmpty()) {
            return Map.of();
        }

        // Remove the GAVs from dependency properties
        return removeDependencies(appPropertiesFile, gavsToRemove);
    }

    /**
     * Removes specified GAVs from all dependency properties in the application.properties file.
     *
     * @param appPropertiesFile the application.properties file
     * @param gavsToRemove the GAVs to remove
     * @return result map with information about removed dependencies
     */
    private Map<String, Object> removeDependencies(File appPropertiesFile, Set<String> gavsToRemove)
            throws IOException {
        List<String> lines = java.nio.file.Files.readAllLines(appPropertiesFile.toPath(), StandardCharsets.UTF_8);

        final Map<String, Set<String>> dependencyProperties = newDependencyMap();

        Set<String> foundDependencyKeys = new HashSet<>();
        Set<String> removedGavs = new HashSet<>();

        // Parse existing dependencies
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int equalsIndex = line.indexOf('=');
            if (equalsIndex > 0) {
                String key = line.substring(0, equalsIndex).trim();
                String value = line.substring(equalsIndex + 1).trim();

                if (dependencyProperties.containsKey(key)) {
                    foundDependencyKeys.add(key);
                    for (String dep : value.split(",")) {
                        String trimmedDep = dep.trim();
                        if (!trimmedDep.isEmpty()) {
                            if (gavsToRemove.contains(trimmedDep)) {
                                removedGavs.add(trimmedDep);
                            } else {
                                dependencyProperties.get(key).add(trimmedDep);
                            }
                        }
                    }
                }
            }
        }

        if (removedGavs.isEmpty()) {
            return Map.of();
        }

        // Rebuild the file with updated dependencies
        List<String> updatedLines = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                updatedLines.add(line);
                continue;
            }

            int equalsIndex = line.indexOf('=');
            if (equalsIndex > 0) {
                String key = line.substring(0, equalsIndex).trim();

                if (dependencyProperties.containsKey(key)) {
                    Set<String> deps = dependencyProperties.get(key);
                    if (!deps.isEmpty()) {
                        updatedLines.add(key + "=" + String.join(",", deps));
                    }
                    // If deps is empty, we skip the line entirely (remove the property)
                } else {
                    updatedLines.add(line);
                }
            } else {
                updatedLines.add(line);
            }
        }

        // Write the updated content
        try (FileOutputStream fos = new FileOutputStream(appPropertiesFile)) {
            StringBuilder content = new StringBuilder();
            for (String line : updatedLines) {
                content.append(line).append("\n");
            }
            fos.write(content.toString().getBytes(StandardCharsets.UTF_8));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("removedDependencies", new ArrayList<>(removedGavs));
        result.put("count", removedGavs.size());
        return result;
    }

    private int outputError(String message) throws JsonProcessingException {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        printer().println(OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(error));
        return 1;
    }

    /**
     * Record to hold factory configuration including bean name and properties.
     */
    private record FactoryConfig(String beanName, String kind, Map<String, String> properties) {}

    /**
     * Record to hold dependencies collected for each runtime variant.
     */
    private record DependencyInfo(
            Set<String> baseDependencies,
            Set<String> mainDependencies,
            Set<String> springBootDependencies,
            Set<String> quarkusDependencies) {}

    /**
     * Record to hold information about deleted configuration.
     */
    private record DeletedConfigInfo(
            Set<String> deletedPropertyKeys, Set<String> deletedTypes, Set<String> deletedFactoryTypeKeys) {}
}
