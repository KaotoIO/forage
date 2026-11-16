package io.kaoto.forage.plugin.config;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;
import io.kaoto.forage.config.editor.ForageConfigurationEditor;
import io.kaoto.forage.config.editor.ForageConfigurationEditor.ConfigurationEditException;
import io.kaoto.forage.config.editor.ForageConfigurationEditor.EditResult;
import io.kaoto.forage.config.editor.ForageConfigurationEditor.FileChange;
import io.kaoto.forage.config.editor.ForagePropertyParser;
import io.kaoto.forage.config.editor.ForagePropertyParser.ParsedProperty;
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

        // Fail before writing anything if a factory has no target file or a new instance an invalid name
        ForageConfigurationEditor editor = new ForageConfigurationEditor(catalog, directory, strategy);
        for (Map.Entry<String, FactoryConfig> entry : factoryConfigs.entrySet()) {
            FactoryConfig factoryConfig = entry.getValue();
            Optional<String> problem =
                    editor.validateWrite(entry.getKey(), factoryConfig.beanName(), factoryConfig.properties());
            if (problem.isPresent()) {
                return outputError(problem.get());
            }
        }

        if (!directory.exists() && !directory.mkdirs()) {
            return outputError("Failed to create directory: " + directory.getAbsolutePath());
        }

        // Process each factory type
        Map<String, Object> results = new LinkedHashMap<>();
        for (Map.Entry<String, FactoryConfig> entry : factoryConfigs.entrySet()) {
            FactoryConfig factoryConfig = entry.getValue();
            EditResult written = editor.write(entry.getKey(), factoryConfig.beanName(), factoryConfig.properties());
            written.write();
            results.put(entry.getKey(), toFactoryResult(entry.getKey(), factoryConfig, written));
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put(KEY_SUCCESS, true);
        output.put("factories", results);
        Map<String, Object> dependencyCleanup = removeStoredForageDependencies(editor);
        if (!dependencyCleanup.isEmpty()) {
            output.put("dependencyCleanup", dependencyCleanup);
        }
        printer().println(OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(output));

        return 0;
    }

    /**
     * The JSON result of writing the configuration of one factory.
     */
    private Map<String, Object> toFactoryResult(
            String factoryTypeKey, FactoryConfig factoryConfig, EditResult written) {
        FileChange first = written.changes().get(0);
        String operation = first.created() ? "create" : "update";
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(KEY_SUCCESS, true);
        result.put("propertiesFile", first.file().getAbsolutePath());
        if (written.changes().size() > 1) {
            result.put(
                    "propertiesFiles",
                    written.changes().stream()
                            .map(change -> change.file().getAbsolutePath())
                            .toList());
        }
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
        if (!written.warnings().isEmpty()) {
            result.put("warnings", written.warnings());
        }
        return result;
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
     * The properties are keyed as for the default instance, without the {@code forage.} prefix (e.g. "jdbc.url").
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

            // Keyed as for the default instance: the editor adds the instance name
            final String finalKind = kindForFactory;
            FactoryConfig config = factoryConfigs.computeIfAbsent(
                    factoryTypeKey, k -> new FactoryConfig(beanName, finalKind, new LinkedHashMap<>()));
            config.properties().put(normalizedKey, value);
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

    private String getFactoryDisplayName(String factoryTypeKey) {
        return catalog.getFactoryMetadata(factoryTypeKey)
                .map(ForageCatalogReader.FactoryMetadata::factoryName)
                .orElse(factoryTypeKey + " factory");
    }

    private int handleDelete() throws IOException {
        if (instanceName == null || instanceName.trim().isEmpty()) {
            return outputError("Instance name (--name) is required for delete operation.");
        }

        ForageConfigurationEditor editor = new ForageConfigurationEditor(catalog, directory, strategy);
        EditResult deleted;
        try {
            deleted = editor.delete(instanceName);
        } catch (ConfigurationEditException e) {
            return outputError(e.getMessage());
        }
        deleted.write();

        Map<String, Object> results = new LinkedHashMap<>();
        for (FileChange change : deleted.changes()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put(KEY_SUCCESS, true);
            result.put("propertiesFile", change.file().getAbsolutePath());
            result.put("deletedProperties", change.removedEntries());
            if (change.writtenEntries() > 0) {
                // Lists of instance names that referred to the instance (e.g. forage.multi.agent.names)
                result.put("updatedProperties", change.writtenEntries());
            }
            result.put("message", "Deleted configuration for instance '" + instanceName + "'");
            results.put(change.file().getName(), result);
        }

        Map<String, Object> dependencyCleanup = removeStoredForageDependencies(editor);
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
     * Removes the Forage dependencies older versions of this command stored in application.properties: the Forage
     * plugin resolves them whenever the project runs or is exported.
     *
     * @return the removed dependencies, empty if there were none
     */
    private static Map<String, Object> removeStoredForageDependencies(ForageConfigurationEditor editor)
            throws IOException {
        EditResult removed = editor.removeStoredForageDependencies();
        if (removed.removedDependencies().isEmpty()) {
            return Map.of();
        }
        removed.write();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("removedDependencies", removed.removedDependencies());
        result.put("count", removed.removedDependencies().size());
        return result;
    }

    /**
     * Record to hold factory configuration including bean name and properties.
     */
    private record FactoryConfig(String beanName, String kind, Map<String, String> properties) {}
}
