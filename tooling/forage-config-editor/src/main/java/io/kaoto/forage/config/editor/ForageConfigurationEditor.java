package io.kaoto.forage.config.editor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import io.kaoto.forage.catalog.model.ConfigEntry;
import io.kaoto.forage.catalog.model.ForageBean;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;
import io.kaoto.forage.config.editor.ForagePropertyParser.ParsedProperty;
import io.kaoto.forage.config.editor.PropertiesDocument.TextEdit;

/**
 * Reads and edits the Forage configuration of a directory, with the files a {@link PropertiesFileStrategy} selects.
 *
 * <p>Edit operations compute the changes without writing anything: callers write them with {@link EditResult#write()},
 * or turn the {@link TextEdit}s of each {@link FileChange} into editor edits (e.g. a VS Code {@code WorkspaceEdit}), so
 * that unsaved changes of an open file are respected.
 *
 * <p>This class has no CLI dependencies, so it can be reused outside the {@code config} commands.
 */
public final class ForageConfigurationEditor {

    private static final String FORAGE_PREFIX = "forage.";
    private static final Pattern VALID_INSTANCE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]*");
    private static final List<String> DEPENDENCY_PROPERTIES = List.of(
            "camel.jbang.dependencies",
            "camel.jbang.dependencies.main",
            "camel.jbang.dependencies.spring-boot",
            "camel.jbang.dependencies.quarkus");

    /**
     * The change of one file.
     *
     * @param file the file
     * @param created whether the file does not exist yet
     * @param edits the edits to apply to the current content of the file
     * @param lines the content of the file once edited
     * @param removedEntries how many entries the change removes
     * @param writtenEntries how many entries the change adds or updates
     */
    public record FileChange(
            File file,
            boolean created,
            List<TextEdit> edits,
            List<String> lines,
            int removedEntries,
            int writtenEntries) {

        /**
         * Writes the edited content to the file.
         */
        public void write() throws IOException {
            PropertiesDocument.write(file, lines);
        }
    }

    /**
     * The result of an edit operation.
     *
     * @param changes the file changes, empty if nothing changes
     * @param removedDependencies the dependencies removed from {@code camel.jbang.dependencies*}
     * @param warnings things the caller should tell the user about the change
     */
    public record EditResult(List<FileChange> changes, List<String> removedDependencies, List<String> warnings) {

        /**
         * Writes every file change.
         */
        public void write() throws IOException {
            for (FileChange change : changes) {
                change.write();
            }
        }
    }

    /**
     * An edit that cannot be made, with the message to report.
     */
    public static final class ConfigurationEditException extends IllegalArgumentException {

        public ConfigurationEditException(String message) {
            super(message);
        }
    }

    private final ForageCatalogReader catalog;
    private final File directory;
    private final PropertiesFileStrategy strategy;
    private final ForagePropertyParser parser;

    /**
     * @param catalog the catalog of the Forage version the configuration is for
     * @param directory the directory holding the properties files
     * @param strategy which files the configuration is read from and written to
     */
    public ForageConfigurationEditor(ForageCatalogReader catalog, File directory, PropertiesFileStrategy strategy) {
        this.catalog = catalog;
        this.directory = directory;
        this.strategy = strategy;
        this.parser = new ForagePropertyParser(catalog);
    }

    /**
     * Reads the configuration of the files the strategy manages.
     */
    public ForageConfiguration read() throws IOException {
        return ForageConfiguration.read(catalog, strategy.findPropertiesFiles(directory), Set.of());
    }

    /**
     * Checks the name of an instance to create. An instance name must start with a letter or a digit and contain
     * only letters, digits, {@code -} and {@code _}, and must not be the start of Forage property keys, such as a
     * factory key ({@code jdbc}) or a bean prefix ({@code ollama}, {@code azure}): the keys of such an instance would
     * also read as keys of the default instance.
     *
     * @param name the instance name
     * @return the problem with the name, empty if it can be used
     */
    public Optional<String> validateNewInstanceName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.of("The instance name is required.");
        }
        if (!VALID_INSTANCE_NAME.matcher(name).matches()) {
            return Optional.of("Invalid instance name '" + name
                    + "': use only letters, digits, '-' and '_', starting with a letter or a digit.");
        }
        if (reservedInstanceNames().contains(name.toLowerCase(Locale.ROOT))) {
            return Optional.of("Invalid instance name '" + name + "': Forage property keys already start with 'forage."
                    + name + ".', so the properties of the instance would be ambiguous. Choose another name.");
        }
        return Optional.empty();
    }

    /**
     * Checks a {@link #write(String, String, Map)} without computing it.
     *
     * @return the problem, empty if the write can be made
     */
    public Optional<String> validateWrite(String factoryTypeKey, String instanceName, Map<String, String> properties)
            throws IOException {
        String noFile = displayName(factoryTypeKey)
                + " has no dedicated properties file; use --strategy application to configure it.";
        if (strategy.getPropertiesFileName(factoryTypeKey) == null) {
            return Optional.of(noFile);
        }
        for (String key : properties.keySet()) {
            if (strategy.getPropertiesFileName(factoryTypeKey, ownerBean(entryKey(key))) == null) {
                return Optional.of(noFile);
            }
        }
        if (instanceName != null && !allFiles().hasInstance(instanceName)) {
            return validateNewInstanceName(instanceName);
        }
        return Optional.empty();
    }

    /**
     * Sets properties of an instance. A new instance needs a valid name (see {@link #validateNewInstanceName(String)}).
     *
     * <p>Each property goes to the file the strategy selects for its factory and bean. Under the {@code forage}
     * strategy, a bean property goes to the bean's own file when the catalog records one, and to the factory's file
     * otherwise, with a warning: plain Camel and Quarkus only read a bean's properties from its own file and from
     * {@code application.properties}.
     *
     * @param factoryTypeKey the factory of the instance (e.g. {@code jdbc})
     * @param instanceName the instance name, or {@code null} for the default instance
     * @param properties the properties, keyed as for the default instance, with or without {@code forage.} (e.g.
     *     {@code jdbc.url}, {@code forage.ollama.model.name})
     * @return the changes
     * @throws ConfigurationEditException if the write cannot be made
     */
    public EditResult write(String factoryTypeKey, String instanceName, Map<String, String> properties)
            throws IOException {
        Optional<String> problem = validateWrite(factoryTypeKey, instanceName, properties);
        if (problem.isPresent()) {
            throw new ConfigurationEditException(problem.get());
        }

        String factoryFile = strategy.getPropertiesFileName(factoryTypeKey);
        Map<String, Map<String, String>> valuesByFile = new LinkedHashMap<>();
        Set<String> warnings = new LinkedHashSet<>();
        for (Map.Entry<String, String> property : properties.entrySet()) {
            String entryKey = entryKey(property.getKey());
            String fileName = strategy.getPropertiesFileName(factoryTypeKey, ownerBean(entryKey));
            // A bean property outside the factory's namespace (e.g. ollama.model.name for the agent factory) is read
            // by the bean's own configuration, which plain Camel and Quarkus load from the bean's own file only
            String bean = beanPropertyOf(entryKey);
            if (bean != null
                    && !ForageConfigurationFiles.APPLICATION_PROPERTIES.equals(fileName)
                    && fileName.equals(factoryFile)) {
                warnings.add("The " + bean + " properties are written to " + fileName
                        + ": Spring Boot reads them there, but plain Camel and Quarkus only read them from "
                        + ForageConfigurationFiles.APPLICATION_PROPERTIES
                        + " (--strategy application) or from the bean's own properties file.");
            }
            String fullKey = FORAGE_PREFIX + (instanceName != null ? instanceName + "." : "") + entryKey;
            valuesByFile.computeIfAbsent(fileName, f -> new LinkedHashMap<>()).put(fullKey, property.getValue());
        }
        // An instance given by name only still gets its factory's file
        valuesByFile.putIfAbsent(factoryFile, new LinkedHashMap<>());

        List<FileChange> changes = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> fileValues : valuesByFile.entrySet()) {
            File file = new File(directory, fileValues.getKey());
            changes.add(setValues(file, fileValues.getValue(), header(factoryTypeKey)));
        }
        return new EditResult(List.copyOf(changes), List.of(), List.copyOf(warnings));
    }

    /**
     * Deletes an instance: its entries in the files the strategy deletes from (see
     * {@link PropertiesFileStrategy#findDeletionFiles(File)}), and its name in the lists of instance names that refer
     * to it (e.g. {@code forage.multi.agent.names}), whose entry is removed once empty.
     *
     * @param instanceName the instance name
     * @return the changes
     * @throws ConfigurationEditException if the files have no configuration for the instance
     */
    public EditResult delete(String instanceName) throws IOException {
        List<File> deletionFiles = strategy.findDeletionFiles(directory);
        Set<File> files = new LinkedHashSet<>(ForageConfigurationFiles.all(directory, catalog));
        files.addAll(deletionFiles);
        ForageConfiguration configuration = ForageConfiguration.read(catalog, files, Set.of(instanceName));

        Map<File, Map<PropertiesDocument.Entry, String>> newValues = new LinkedHashMap<>();
        Map<File, int[]> counts = new HashMap<>();
        for (ForageConfiguration.Entry entry : configuration.entriesOf(instanceName)) {
            if (deletionFiles.contains(entry.file())) {
                newValues.computeIfAbsent(entry.file(), f -> new HashMap<>()).put(entry.line(), null);
                counts.computeIfAbsent(entry.file(), f -> new int[2])[0]++;
            }
        }
        for (ForageConfiguration.Entry entry : configuration.entries()) {
            if (deletionFiles.contains(entry.file()) && refersTo(entry, instanceName)) {
                List<String> names = new ArrayList<>(PropertyKeys.splitValues(entry.value()));
                names.remove(instanceName);
                newValues
                        .computeIfAbsent(entry.file(), f -> new HashMap<>())
                        .put(entry.line(), names.isEmpty() ? null : String.join(",", names));
                counts.computeIfAbsent(entry.file(), f -> new int[2])[names.isEmpty() ? 0 : 1]++;
            }
        }
        if (newValues.isEmpty()) {
            throw new ConfigurationEditException("No configuration found for instance '" + instanceName + "'");
        }

        List<FileChange> changes = new ArrayList<>();
        newValues.forEach((file, values) -> {
            PropertiesDocument document = configuration.documents().get(file);
            List<TextEdit> edits = document.edits(values, Map.of());
            int[] count = counts.get(file);
            changes.add(new FileChange(file, false, edits, document.apply(edits), count[0], count[1]));
        });
        return new EditResult(List.copyOf(changes), List.of(), List.of());
    }

    /**
     * Removes, from the {@code camel.jbang.dependencies*} properties of {@code application.properties}, the
     * dependencies of the factories and beans the catalog knows, whatever their version. Older versions of
     * {@code camel forage config write} stored them there; the Forage plugin resolves them from the configuration
     * whenever the project runs or is exported, so a stored copy is redundant and can pin an outdated Forage version.
     * Other dependencies are kept, and a property is removed only once empty.
     *
     * @return the changes, with the removed dependencies
     */
    public EditResult removeStoredForageDependencies() throws IOException {
        File file = ForageConfigurationFiles.applicationProperties(directory);
        if (file == null) {
            return new EditResult(List.of(), List.of(), List.of());
        }
        PropertiesDocument document = PropertiesDocument.read(file);
        Set<String> forageArtifacts = forageArtifacts();
        Map<PropertiesDocument.Entry, String> newValues = new HashMap<>();
        List<String> removed = new ArrayList<>();
        for (PropertiesDocument.Entry entry : document.entries()) {
            if (DEPENDENCY_PROPERTIES.contains(entry.key())) {
                List<String> kept = new ArrayList<>();
                for (String gav : PropertyKeys.splitValues(entry.value())) {
                    if (forageArtifacts.contains(groupAndArtifact(gav))) {
                        removed.add(gav);
                    } else {
                        kept.add(gav);
                    }
                }
                if (kept.size() != PropertyKeys.splitValues(entry.value()).size()) {
                    newValues.put(entry, kept.isEmpty() ? null : String.join(",", kept));
                }
            }
        }
        if (newValues.isEmpty()) {
            return new EditResult(List.of(), List.of(), List.of());
        }
        List<TextEdit> edits = document.edits(newValues, Map.of());
        FileChange change = new FileChange(file, false, edits, document.apply(edits), removed.size(), 0);
        return new EditResult(List.of(change), List.copyOf(removed), List.of());
    }

    private FileChange setValues(File file, Map<String, String> values, List<String> header) throws IOException {
        if (file.isFile()) {
            PropertiesDocument document = PropertiesDocument.read(file);
            List<TextEdit> edits = document.editsSettingValues(values);
            return new FileChange(file, false, edits, document.apply(edits), 0, values.size());
        }
        List<String> lines = PropertiesDocument.of(header).withValues(values);
        return new FileChange(file, true, List.of(new TextEdit(0, 0, lines)), lines, 0, values.size());
    }

    private ForageConfiguration allFiles() throws IOException {
        Set<File> files = new LinkedHashSet<>(ForageConfigurationFiles.all(directory, catalog));
        files.addAll(strategy.findPropertiesFiles(directory));
        return ForageConfiguration.read(catalog, files, Set.of());
    }

    private static boolean refersTo(ForageConfiguration.Entry entry, String instanceName) {
        ParsedProperty property = entry.property();
        return property != null
                && ForageConfiguration.INSTANCE_LIST_ENTRIES.contains(property.entryKey())
                && PropertyKeys.splitValues(entry.value()).contains(instanceName);
    }

    private static String entryKey(String key) {
        return key.startsWith(FORAGE_PREFIX) ? key.substring(FORAGE_PREFIX.length()) : key;
    }

    /**
     * The bean that declares a default-instance entry key in the catalog, if a single one does.
     */
    private String ownerBean(String entryKey) {
        return catalog.findConfigEntryOwner(FORAGE_PREFIX + entryKey)
                .map(ForageCatalogReader.ConfigEntryOwner::beanName)
                .orElse(null);
    }

    /**
     * The bean a default-instance entry key configures outside its factory's namespace (e.g. {@code ollama} for
     * {@code ollama.model.name}), if any.
     */
    private String beanPropertyOf(String entryKey) {
        return parser.parse(FORAGE_PREFIX + entryKey)
                .map(ParsedProperty::beanType)
                .orElse(null);
    }

    /**
     * The first segment of every config entry the catalog declares (e.g. {@code jdbc}, {@code ollama},
     * {@code azure}, {@code multi}), in lower case.
     */
    private Set<String> reservedInstanceNames() {
        Set<String> names = new HashSet<>();
        for (ForageCatalogReader.FactoryMetadata factory : catalog.getAllFactories()) {
            addFirstSegments(names, factory.configEntries());
            for (ForageBean bean : catalog.getAllBeansForFactory(factory.factoryTypeKey())) {
                addFirstSegments(names, bean.getConfigEntries());
            }
        }
        return names;
    }

    private static void addFirstSegments(Set<String> names, List<ConfigEntry> entries) {
        if (entries == null) {
            return;
        }
        for (ConfigEntry entry : entries) {
            String name = entry.getName();
            if (name != null && name.startsWith(FORAGE_PREFIX)) {
                String rest = name.substring(FORAGE_PREFIX.length());
                int dot = rest.indexOf('.');
                names.add((dot > 0 ? rest.substring(0, dot) : rest).toLowerCase(Locale.ROOT));
            }
        }
    }

    /**
     * The {@code groupId:artifactId} of every factory variant and bean the catalog knows.
     */
    private Set<String> forageArtifacts() {
        Set<String> artifacts = new HashSet<>();
        for (ForageCatalogReader.FactoryMetadata factory : catalog.getAllFactories()) {
            for (String variant : List.of("base", "springboot", "quarkus")) {
                catalog.getFactoryVariantGav(factory.factoryTypeKey(), variant)
                        .map(ForageConfigurationEditor::groupAndArtifact)
                        .ifPresent(artifacts::add);
            }
            for (ForageBean bean : catalog.getAllBeansForFactory(factory.factoryTypeKey())) {
                if (bean.getGav() != null && !bean.getGav().isBlank()) {
                    artifacts.add(groupAndArtifact(bean.getGav()));
                }
            }
        }
        return artifacts;
    }

    private static String groupAndArtifact(String gav) {
        String coordinates = gav.startsWith("mvn:") ? gav.substring("mvn:".length()) : gav;
        String[] parts = coordinates.split(":");
        return parts.length >= 2 ? parts[0] + ":" + parts[1] : coordinates;
    }

    private List<String> header(String factoryTypeKey) {
        return List.of(
                "# Forage " + displayName(factoryTypeKey) + " Configuration", "# Generated by Forage JBang Plugin", "");
    }

    private String displayName(String factoryTypeKey) {
        return catalog.getFactoryMetadata(factoryTypeKey)
                .map(ForageCatalogReader.FactoryMetadata::factoryName)
                .orElse(factoryTypeKey + " factory");
    }
}
