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
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;
import io.kaoto.forage.plugin.ForageDependencyResolver;
import io.kaoto.forage.plugin.ForagePropertyParser;
import io.kaoto.forage.plugin.ForagePropertyScanner;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import picocli.CommandLine;

/**
 * Command to write Forage configuration properties from JSON input.
 * This command creates, updates, or merges properties files based on JSON input from the Kaoto wizard.
 */
@CommandLine.Command(name = "write", description = "Write Forage configuration from JSON input to properties files")
public class ConfigWriteCommand extends AbstractConfigCommand {

    private static final String KEY_SUCCESS = "success";
    private static final String FORAGE_PREFIX = "forage.";

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

    public ConfigWriteCommand(CamelJBangMain main) {
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

        // Fail before writing anything if a factory has no target file under the selected strategy
        for (String factoryTypeKey : factoryConfigs.keySet()) {
            if (strategy.getPropertiesFileName(factoryTypeKey) == null) {
                return outputError(getFactoryDisplayName(factoryTypeKey)
                        + " has no dedicated properties file; use --strategy application to configure it.");
            }
        }

        if (!directory.exists() && !directory.mkdirs()) {
            return outputError("Failed to create directory: " + directory.getAbsolutePath());
        }

        // Process each factory type
        Map<String, Object> results = new LinkedHashMap<>();
        for (Map.Entry<String, FactoryConfig> entry : factoryConfigs.entrySet()) {
            String factoryTypeKey = entry.getKey();
            FactoryConfig factoryConfig = entry.getValue();

            String propertiesFileName = strategy.getPropertiesFileName(factoryTypeKey);
            File propertiesFile = new File(directory, propertiesFileName);
            String operation = propertiesFile.exists() ? "update" : "create";

            writePropertiesFile(propertiesFile, factoryConfig.properties(), factoryTypeKey);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put(KEY_SUCCESS, true);
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
            File appPropertiesFile = new File(directory, ForagePropertyScanner.APPLICATION_PROPERTIES);
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
            output.put(KEY_SUCCESS, true);
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
        StringBuilder sb = new StringBuilder(FORAGE_PREFIX);
        if (beanName != null && !beanName.isEmpty()) {
            sb.append(beanName).append(".");
        }
        sb.append(inputKey);
        return sb.toString();
    }

    /**
     * Collects dependencies from the catalog for all factory configs.
     * For each factory, collects:
     * - Bean GAV (e.g., forage-jdbc-postgresql) -> base dependencies
     * - Factory variant GAVs for each runtime (base, springboot, quarkus)
     */
    private DependencyInfo collectDependencies(Map<String, FactoryConfig> factoryConfigs) {
        ForageDependencyResolver resolver = new ForageDependencyResolver(catalog);
        ForageDependencyResolver.Dependencies dependencies = new ForageDependencyResolver.Dependencies();

        for (Map.Entry<String, FactoryConfig> entry : factoryConfigs.entrySet()) {
            FactoryConfig factoryConfig = entry.getValue();
            // The factory itself, even when only its name property was given
            resolver.addFactory(dependencies, entry.getKey());
            // The kind given as metadata (e.g. "kind": "postgresql")
            resolver.addBean(dependencies, factoryConfig.kind());
            // Everything the written properties need, using the same rule as the delete cleanup
            ForageDependencyResolver.Dependencies written = resolver.resolve(factoryConfig.properties());
            dependencies.base().addAll(written.base());
            dependencies.main().addAll(written.main());
            dependencies.springBoot().addAll(written.springBoot());
            dependencies.quarkus().addAll(written.quarkus());
        }

        return new DependencyInfo(
                dependencies.base(), dependencies.main(), dependencies.springBoot(), dependencies.quarkus());
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

        ForagePropertyParser parser = new ForagePropertyParser(catalog);
        ForageDependencyResolver resolver = new ForageDependencyResolver(catalog, parser);

        // Read every file before modifying any: the files to delete from (the ones `config read` lists for this
        // strategy) and every file whose instances may still need dependencies, whatever the active profile
        List<File> targetFiles = strategy.findPropertiesFiles(directory);
        Map<File, LoadedFile> allFiles = new LinkedHashMap<>();
        for (File file : ForagePropertyScanner.findAllConfigurationFiles(directory, catalog)) {
            allFiles.put(file, LoadedFile.load(file));
        }
        for (File file : targetFiles) {
            if (!allFiles.containsKey(file)) {
                allFiles.put(file, LoadedFile.load(file));
            }
        }

        Map<File, Map<String, String>> deletedByFile = new LinkedHashMap<>();
        for (File file : targetFiles) {
            LoadedFile loaded = allFiles.get(file);
            Map<String, String> deleted = new LinkedHashMap<>();
            loaded.properties().forEach((key, value) -> {
                if (belongsToInstance(parser, key, instanceName)) {
                    deleted.put(key, value);
                }
            });
            if (!deleted.isEmpty()) {
                deletedByFile.put(file, deleted);
            }
        }

        if (deletedByFile.isEmpty()) {
            return outputError("No configuration found for instance '" + instanceName + "'");
        }

        // Dependencies the deleted instance needed, minus those still needed by what remains in any file
        Map<String, String> deletedProperties = new LinkedHashMap<>();
        deletedByFile.values().forEach(deletedProperties::putAll);
        Map<String, String> remainingProperties = new LinkedHashMap<>();
        allFiles.forEach((file, loaded) -> {
            Set<String> deletedKeys = deletedByFile.getOrDefault(file, Map.of()).keySet();
            loaded.properties().forEach((key, value) -> {
                if (!deletedKeys.contains(key)) {
                    remainingProperties.put(key, value);
                }
            });
        });
        Set<String> gavsToRemove = resolver.resolve(deletedProperties).all();
        gavsToRemove.removeAll(resolver.resolve(remainingProperties).all());

        Map<String, Object> results = new LinkedHashMap<>();
        for (Map.Entry<File, Map<String, String>> entry : deletedByFile.entrySet()) {
            File propertiesFile = entry.getKey();
            writeLines(
                    propertiesFile,
                    computeRemovableLines(
                            allFiles.get(propertiesFile).lines(),
                            entry.getValue().keySet()));
            results.put(
                    propertiesFile.getName(),
                    Map.of(
                            KEY_SUCCESS,
                            true,
                            "propertiesFile",
                            propertiesFile.getAbsolutePath(),
                            "deletedProperties",
                            entry.getValue().size(),
                            "message",
                            "Deleted configuration for instance '" + instanceName + "'"));
        }

        File appPropertiesFile = new File(directory, ForagePropertyScanner.APPLICATION_PROPERTIES);
        if (!gavsToRemove.isEmpty() && appPropertiesFile.exists()) {
            Map<String, Object> dependencyCleanupResult = removeDependencies(appPropertiesFile, gavsToRemove);
            if (!dependencyCleanupResult.isEmpty()) {
                results.put("dependencyCleanup", dependencyCleanupResult);
            }
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put(KEY_SUCCESS, true);
        output.put("operation", "delete");
        output.put("instanceName", instanceName);
        output.put("results", results);
        printer().println(OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(output));

        return 0;
    }

    /**
     * Whether a property belongs to the given instance, using the same parsing as {@code config read}. Keys the
     * parser does not recognise (e.g. typos) fall back to the {@code forage.<instanceName>.} prefix.
     */
    private static boolean belongsToInstance(ForagePropertyParser parser, String key, String instanceName) {
        return parser.parse(key)
                .map(parsed -> instanceName.equals(parsed.instanceName()))
                .orElseGet(() -> key.startsWith(FORAGE_PREFIX + instanceName + "."));
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

    private static void writeLines(File file, List<String> lines) throws IOException {
        StringBuilder content = new StringBuilder();
        for (String line : lines) {
            content.append(line).append("\n");
        }
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * A properties file read once: its raw lines (to rewrite it preserving comments and order) and its
     * {@code key=value} entries, parsed with the same line rules used to remove them.
     */
    private record LoadedFile(List<String> lines, Map<String, String> properties) {

        static LoadedFile load(File file) throws IOException {
            List<String> lines = java.nio.file.Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            Map<String, String> properties = new LinkedHashMap<>();
            for (String line : lines) {
                String trimmed = line.trim();
                int equalsIndex = line.indexOf('=');
                if (!trimmed.isEmpty() && !trimmed.startsWith("#") && equalsIndex > 0) {
                    properties.put(
                            line.substring(0, equalsIndex).trim(),
                            line.substring(equalsIndex + 1).trim());
                }
            }
            return new LoadedFile(lines, properties);
        }
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
}
