package io.kaoto.forage.plugin.config;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;
import io.kaoto.forage.plugin.ForageDependencyResolver;
import io.kaoto.forage.plugin.ForageDependencyResolver.Dependencies;
import io.kaoto.forage.plugin.ForagePropertyParser;
import io.kaoto.forage.plugin.ForagePropertyParser.ParsedProperty;
import io.kaoto.forage.plugin.ForagePropertyScanner;
import io.kaoto.forage.plugin.PropertiesDocument;
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
        Dependencies dependencies = collectDependencies(factoryConfigs);
        if (!dependencies.isEmpty()) {
            File appPropertiesFile = new File(directory, ForagePropertyScanner.APPLICATION_PROPERTIES);
            updateDependencies(appPropertiesFile, dependencies);

            // Add dependency info to results
            Map<String, Object> depResult = new LinkedHashMap<>();
            depResult.put("baseDependencies", dependencies.base());
            depResult.put("mainDependencies", dependencies.main());
            depResult.put("springBootDependencies", dependencies.springBoot());
            depResult.put("quarkusDependencies", dependencies.quarkus());
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
        ForagePropertyParser parser = new ForagePropertyParser(catalog);
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
            Optional<String> factoryTypeOpt = findFactoryTypeKey(parser, normalizedKey, null);
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

            Optional<String> factoryTypeOpt = findFactoryTypeKey(parser, normalizedKey, kindFactoryTypeKey);
            if (factoryTypeOpt.isEmpty()) {
                // Cannot determine factory type for this property
                continue;
            }
            String factoryTypeKey = factoryTypeOpt.get();

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
     * Finds the factory an input key (written for the default instance, without {@code forage.}) belongs to: the
     * factory that declares it in the catalog, parsed like the properties files are (so
     * {@code google.custom.search.api.key} is WebSearch, not the Gemini agent bean that also uses {@code google}),
     * else the factory named by its first segment (e.g. {@code jdbc} for a {@code jdbc.*} key the catalog does not
     * declare), else the factory of the given kind, else the parser's segment heuristics.
     */
    private Optional<String> findFactoryTypeKey(
            ForagePropertyParser parser, String normalizedKey, String kindFactoryTypeKey) {
        Optional<ParsedProperty> parsed = parser.parse(FORAGE_PREFIX + normalizedKey);
        if (parsed.isPresent()
                && parser.isConfigEntry(FORAGE_PREFIX + parsed.get().entryKey())) {
            return Optional.of(parsed.get().factoryType());
        }
        Optional<String> byFirstSegment = catalog.findFactoryTypeKeyForInputProperty(normalizedKey);
        if (byFirstSegment.isPresent()) {
            return byFirstSegment;
        }
        if (kindFactoryTypeKey != null) {
            return Optional.of(kindFactoryTypeKey);
        }
        return parsed.map(ParsedProperty::factoryType);
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
    private Dependencies collectDependencies(Map<String, FactoryConfig> factoryConfigs) {
        ForageDependencyResolver resolver = new ForageDependencyResolver(catalog);
        Dependencies dependencies = new Dependencies();

        for (Map.Entry<String, FactoryConfig> entry : factoryConfigs.entrySet()) {
            FactoryConfig factoryConfig = entry.getValue();
            // The factory itself, even when only its name property was given
            resolver.addFactory(dependencies, entry.getKey());
            // The kind given as metadata (e.g. "kind": "postgresql")
            resolver.addBean(dependencies, factoryConfig.kind());
            // Everything the written properties need, using the same rule as the delete cleanup
            dependencies.addAll(resolver.resolve(factoryConfig.properties()));
        }

        return dependencies;
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
    private void updateDependencies(File propertiesFile, Dependencies newDeps) throws IOException {
        PropertiesDocument document = PropertiesDocument.read(propertiesFile);
        Map<String, Set<String>> dependencies = readDependencies(document);

        dependencies.get(CAMEL_JBANG_DEPENDENCIES).addAll(newDeps.base());
        dependencies.get(CAMEL_JBANG_DEPENDENCIES_MAIN).addAll(newDeps.main());
        dependencies.get(CAMEL_JBANG_DEPENDENCIES_SPRING_BOOT).addAll(newDeps.springBoot());
        dependencies.get(CAMEL_JBANG_DEPENDENCIES_QUARKUS).addAll(newDeps.quarkus());

        PropertiesDocument.write(propertiesFile, document.withValues(toPropertyValues(dependencies)));
    }

    /**
     * Reads the GAVs of every {@code camel.jbang.dependencies*} property, whichever separator and line layout they
     * were written with.
     */
    private static Map<String, Set<String>> readDependencies(PropertiesDocument document) {
        Map<String, Set<String>> dependencies = newDependencyMap();
        for (PropertiesDocument.Entry entry : document.entries()) {
            Set<String> gavs = dependencies.get(entry.key());
            if (gavs != null) {
                for (String gav : entry.value().split(",")) {
                    if (!gav.isBlank()) {
                        gavs.add(gav.trim());
                    }
                }
            }
        }
        return dependencies;
    }

    /**
     * The property values to write for the dependency lists; an empty list removes its property.
     */
    private static Map<String, String> toPropertyValues(Map<String, Set<String>> dependencies) {
        Map<String, String> values = new LinkedHashMap<>();
        dependencies.forEach((key, gavs) -> values.put(key, gavs.isEmpty() ? null : String.join(",", gavs)));
        return values;
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
        // A new file starts with a header; an existing one is updated in place, preserving order and comments
        PropertiesDocument document = file.exists()
                ? PropertiesDocument.read(file)
                : PropertiesDocument.of(List.of(
                        "# Forage " + getFactoryDisplayName(factoryTypeKey) + " Configuration",
                        "# Generated by Forage JBang Plugin",
                        ""));
        PropertiesDocument.write(file, document.withValues(newProperties));
    }

    private int handleDelete() throws IOException {
        if (instanceName == null || instanceName.trim().isEmpty()) {
            return outputError("Instance name (--name) is required for delete operation.");
        }

        ForagePropertyParser parser = new ForagePropertyParser(catalog);
        ForageDependencyResolver resolver = new ForageDependencyResolver(catalog, parser);

        // Read every file before modifying any: the files to delete from (the ones `config read` lists for this
        // strategy, plus the files overriding them) and every file whose instances may still need dependencies,
        // whatever the active profile
        Set<File> deletionFiles = new LinkedHashSet<>(strategy.findDeletionFiles(directory));
        Set<File> files = new LinkedHashSet<>(ForagePropertyScanner.findAllConfigurationFiles(directory, catalog));
        files.addAll(deletionFiles);
        Map<File, PropertiesDocument> documents = new LinkedHashMap<>();
        for (File file : files) {
            documents.put(file, PropertiesDocument.read(file));
        }

        // Each entry is parsed once, to tell whether it belongs to the instance and which dependencies it needs.
        // Entries are kept per file, since the same key can have another value in another file.
        Map<File, List<PropertiesDocument.Entry>> deletedByFile = new LinkedHashMap<>();
        Dependencies deletedDependencies = new Dependencies();
        Dependencies remainingDependencies = new Dependencies();
        for (Map.Entry<File, PropertiesDocument> document : documents.entrySet()) {
            for (PropertiesDocument.Entry entry : document.getValue().entries()) {
                Optional<ParsedProperty> parsed = parser.parse(entry.key());
                boolean deleted = deletionFiles.contains(document.getKey())
                        && belongsToInstance(parser, entry.key(), parsed, instanceName);
                if (deleted) {
                    deletedByFile
                            .computeIfAbsent(document.getKey(), file -> new ArrayList<>())
                            .add(entry);
                }
                Dependencies dependencies = deleted ? deletedDependencies : remainingDependencies;
                parsed.ifPresent(property -> resolver.add(dependencies, property, entry.value()));
            }
        }

        if (deletedByFile.isEmpty()) {
            return outputError("No configuration found for instance '" + instanceName + "'");
        }

        // Dependencies the deleted instance needed, minus those still needed by what remains in any file
        Set<String> gavsToRemove = deletedDependencies.all();
        gavsToRemove.removeAll(remainingDependencies.all());

        Map<String, Object> results = new LinkedHashMap<>();
        for (Map.Entry<File, List<PropertiesDocument.Entry>> entry : deletedByFile.entrySet()) {
            File propertiesFile = entry.getKey();
            PropertiesDocument.write(
                    propertiesFile, documents.get(propertiesFile).without(entry.getValue()));
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
     * Whether a property belongs to the instance. Keys are parsed like {@code config read} does, which reads a key
     * that is itself a catalog entry, or that starts with a bean prefix, as the default instance's. A key under
     * {@code forage.<instance>.} also belongs to the instance when the rest of it reads as a default-instance key:
     * a catalog entry (e.g. {@code forage.azure.openai.api.key} for an OpenAI instance named {@code azure},
     * although it is also the default Azure OpenAI entry) or a key of a known factory or bean (e.g. the typo
     * {@code forage.ollama.agent.modle.nmae} for an agent named {@code ollama}). Genuine default-instance keys
     * ({@code forage.ollama.model.name}) and dotted instance names ({@code forage.my.ds.*} for {@code my}) do not
     * qualify, since their rest does not read as a default-instance key. A key under the prefix that nothing
     * recognises (e.g. {@code forage.myPG.jdbcc.url}) belongs to the instance too.
     */
    private static boolean belongsToInstance(
            ForagePropertyParser parser, String key, Optional<ParsedProperty> parsed, String instanceName) {
        if (parsed.isPresent() && instanceName.equals(parsed.get().instanceName())) {
            return true;
        }
        String instancePrefix = FORAGE_PREFIX + instanceName + ".";
        if (!key.startsWith(instancePrefix)) {
            return false;
        }
        Optional<ParsedProperty> rest = parser.parse(FORAGE_PREFIX + key.substring(instancePrefix.length()));
        if (rest.isPresent()) {
            return rest.get().instanceName() == null;
        }
        return parsed.isEmpty();
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
        PropertiesDocument document = PropertiesDocument.read(appPropertiesFile);
        Map<String, Set<String>> dependencies = readDependencies(document);

        Set<String> removedGavs = new LinkedHashSet<>();
        for (Set<String> gavs : dependencies.values()) {
            for (String gav : gavs) {
                if (gavsToRemove.contains(gav)) {
                    removedGavs.add(gav);
                }
            }
            gavs.removeAll(gavsToRemove);
        }

        if (removedGavs.isEmpty()) {
            return Map.of();
        }

        // An emptied dependency property is removed
        PropertiesDocument.write(appPropertiesFile, document.withValues(toPropertyValues(dependencies)));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("removedDependencies", new ArrayList<>(removedGavs));
        result.put("count", removedGavs.size());
        return result;
    }

    /**
     * Record to hold factory configuration including bean name and properties.
     */
    private record FactoryConfig(String beanName, String kind, Map<String, String> properties) {}
}
