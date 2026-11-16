package io.kaoto.forage.plugin.config;

import java.io.BufferedReader;
import java.io.File;
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
import java.util.function.Predicate;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import io.kaoto.forage.catalog.model.ForageBean;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;
import io.kaoto.forage.config.editor.ForageConfigurationFiles;
import io.kaoto.forage.config.editor.ForagePropertyParser;
import io.kaoto.forage.config.editor.ForagePropertyParser.ParsedProperty;
import io.kaoto.forage.config.editor.PropertiesDocument;
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

        Map<String, Object> output = new LinkedHashMap<>();
        output.put(KEY_SUCCESS, true);
        output.put("factories", results);
        Map<String, Object> dependencyCleanup = removeForageDependencies();
        if (!dependencyCleanup.isEmpty()) {
            output.put("dependencyCleanup", dependencyCleanup);
        }
        printer().println(OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(output));

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

    // Dependency property keys
    private static final String CAMEL_JBANG_DEPENDENCIES = "camel.jbang.dependencies";
    private static final String CAMEL_JBANG_DEPENDENCIES_MAIN = "camel.jbang.dependencies.main";
    private static final String CAMEL_JBANG_DEPENDENCIES_SPRING_BOOT = "camel.jbang.dependencies.spring-boot";
    private static final String CAMEL_JBANG_DEPENDENCIES_QUARKUS = "camel.jbang.dependencies.quarkus";

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

        // Read every file to delete from before modifying any: the ones `config read` lists for this strategy,
        // plus the files overriding them
        Map<File, PropertiesDocument> documents = new LinkedHashMap<>();
        for (File file : strategy.findDeletionFiles(directory)) {
            documents.put(file, PropertiesDocument.read(file));
        }

        Map<File, List<PropertiesDocument.Entry>> deletedByFile = new LinkedHashMap<>();
        for (Map.Entry<File, PropertiesDocument> document : documents.entrySet()) {
            for (PropertiesDocument.Entry entry : document.getValue().entries()) {
                if (belongsToInstance(parser, entry.key(), parser.parse(entry.key()), instanceName)) {
                    deletedByFile
                            .computeIfAbsent(document.getKey(), file -> new ArrayList<>())
                            .add(entry);
                }
            }
        }

        if (deletedByFile.isEmpty()) {
            return outputError("No configuration found for instance '" + instanceName + "'");
        }

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

        Map<String, Object> dependencyCleanup = removeForageDependencies();
        if (!dependencyCleanup.isEmpty()) {
            results.put("dependencyCleanup", dependencyCleanup);
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
     * Removes the Forage dependencies older versions of this command wrote to the {@code camel.jbang.dependencies*}
     * properties of application.properties. The Forage plugin resolves them from the configuration whenever the
     * project runs or is exported, so a stored copy is redundant and can pin an outdated Forage version. Other
     * dependencies are kept.
     *
     * @return the removed dependencies, empty if there were none
     */
    private Map<String, Object> removeForageDependencies() throws IOException {
        File appPropertiesFile = new File(directory, ForageConfigurationFiles.APPLICATION_PROPERTIES);
        if (!appPropertiesFile.isFile()) {
            return Map.of();
        }
        Set<String> forageArtifacts = findForageArtifacts();
        return removeDependencies(appPropertiesFile, gav -> forageArtifacts.contains(groupAndArtifact(gav)));
    }

    /**
     * The {@code groupId:artifactId} of every factory variant and bean the catalog knows, whatever their version.
     */
    private Set<String> findForageArtifacts() {
        Set<String> artifacts = new HashSet<>();
        for (ForageCatalogReader.FactoryMetadata factory : catalog.getAllFactories()) {
            for (String variant : List.of("base", "springboot", "quarkus")) {
                catalog.getFactoryVariantGav(factory.factoryTypeKey(), variant)
                        .map(ConfigWriteCommand::groupAndArtifact)
                        .ifPresent(artifacts::add);
            }
            catalog.getAllBeansForFactory(factory.factoryTypeKey()).stream()
                    .map(ForageBean::getGav)
                    .filter(gav -> gav != null && !gav.isBlank())
                    .map(ConfigWriteCommand::groupAndArtifact)
                    .forEach(artifacts::add);
        }
        return artifacts;
    }

    private static String groupAndArtifact(String gav) {
        String coordinates = gav.startsWith("mvn:") ? gav.substring("mvn:".length()) : gav;
        String[] parts = coordinates.split(":");
        return parts.length >= 2 ? parts[0] + ":" + parts[1] : coordinates;
    }

    /**
     * Removes the matching GAVs from all dependency properties in the application.properties file.
     *
     * @param appPropertiesFile the application.properties file
     * @param toRemove which GAVs to remove
     * @return result map with information about removed dependencies
     */
    private Map<String, Object> removeDependencies(File appPropertiesFile, Predicate<String> toRemove)
            throws IOException {
        PropertiesDocument document = PropertiesDocument.read(appPropertiesFile);
        Map<String, Set<String>> dependencies = readDependencies(document);

        Set<String> removedGavs = new LinkedHashSet<>();
        for (Set<String> gavs : dependencies.values()) {
            gavs.stream().filter(toRemove).forEach(removedGavs::add);
            gavs.removeIf(toRemove);
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
