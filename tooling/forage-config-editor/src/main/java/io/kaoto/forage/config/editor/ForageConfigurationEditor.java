package io.kaoto.forage.config.editor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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
     * @param charset the encoding of the file, which the edited content is written in
     */
    public record FileChange(
            File file,
            boolean created,
            List<TextEdit> edits,
            List<String> lines,
            int removedEntries,
            int writtenEntries,
            Charset charset) {

        /**
         * Writes the edited content to the file, in its encoding.
         */
        public void write() throws IOException {
            PropertiesDocument.write(file, lines, charset);
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

    private static final EditResult NO_CHANGE = new EditResult(List.of(), List.of(), List.of());

    private final ForageCatalogReader catalog;
    private final File directory;
    private final PropertiesFileStrategy strategy;
    private final DocumentSource source;
    private final ForagePropertyParser parser;

    /**
     * An editor that reads the files from disk.
     *
     * @param catalog the catalog of the Forage version the configuration is for
     * @param directory the directory holding the properties files
     * @param strategy which files the configuration is read from and written to
     */
    public ForageConfigurationEditor(ForageCatalogReader catalog, File directory, PropertiesFileStrategy strategy) {
        this(catalog, directory, strategy, DocumentSource.DISK);
    }

    /**
     * An editor that reads the files from a document source, such as the open documents of an IDE with their unsaved
     * changes: the edits it computes then apply to that content.
     *
     * @param catalog the catalog of the Forage version the configuration is for
     * @param directory the directory holding the properties files
     * @param strategy which files the configuration is read from and written to
     * @param source the current content of the files
     */
    public ForageConfigurationEditor(
            ForageCatalogReader catalog, File directory, PropertiesFileStrategy strategy, DocumentSource source) {
        this.catalog = catalog;
        this.directory = directory;
        this.strategy = strategy;
        this.source = source;
        this.parser = new ForagePropertyParser(catalog);
    }

    /**
     * Reads the configuration of the files the strategy manages.
     */
    public ForageConfiguration read() throws IOException {
        return ForageConfiguration.read(catalog, strategy.findPropertiesFiles(directory), Set.of(), source);
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
     * Deletes an instance found in the files the strategy manages: its entries there and in the files that override
     * them (see {@link PropertiesFileStrategy#findOverrideFiles(File)}), and its name in the lists of instance names
     * that refer to it (e.g. {@code forage.multi.agent.names}), whose entry is removed once empty.
     *
     * <p>The name to delete is not taken as evidence that an instance exists: a key that reads in several ways goes to
     * the instance the files show, so deleting a name no instance has never removes the default instance's keys.
     *
     * @param instanceName the instance name
     * @return the changes
     * @throws ConfigurationEditException if the files of the strategy have no configuration for the instance
     */
    public EditResult delete(String instanceName) throws IOException {
        List<File> instanceFiles = strategy.findPropertiesFiles(directory);
        Set<File> editableFiles = new LinkedHashSet<>(instanceFiles);
        editableFiles.addAll(strategy.findOverrideFiles(directory));
        Set<File> files = new LinkedHashSet<>(ForageConfigurationFiles.all(directory, catalog));
        files.addAll(editableFiles);
        ForageConfiguration configuration = ForageConfiguration.read(catalog, files, Set.of(), source);

        List<ForageConfiguration.Entry> instanceEntries = configuration.entries().stream()
                .filter(entry -> editableFiles.contains(entry.file()) && belongsTo(entry, instanceName))
                .toList();
        if (instanceEntries.stream().noneMatch(entry -> instanceFiles.contains(entry.file()))) {
            throw new ConfigurationEditException("No configuration found for instance '" + instanceName + "'");
        }

        Map<File, Map<PropertiesDocument.Entry, String>> newValues = new LinkedHashMap<>();
        Map<File, int[]> counts = new HashMap<>();
        for (ForageConfiguration.Entry entry : instanceEntries) {
            newValues.computeIfAbsent(entry.file(), f -> new HashMap<>()).put(entry.line(), null);
            counts.computeIfAbsent(entry.file(), f -> new int[2])[0]++;
        }
        for (ForageConfiguration.Entry entry : configuration.entries()) {
            if (editableFiles.contains(entry.file()) && refersTo(entry, instanceName)) {
                String remaining = withoutName(entry.value(), instanceName);
                newValues.computeIfAbsent(entry.file(), f -> new HashMap<>()).put(entry.line(), remaining);
                counts.computeIfAbsent(entry.file(), f -> new int[2])[remaining == null ? 0 : 1]++;
            }
        }

        List<FileChange> changes = new ArrayList<>();
        newValues.forEach((file, values) -> {
            PropertiesDocument document = configuration.documents().get(file);
            List<TextEdit> edits = document.edits(values, Map.of());
            int[] count = counts.get(file);
            changes.add(
                    new FileChange(file, false, edits, document.apply(edits), count[0], count[1], document.charset()));
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
        File file = new File(directory, ForageConfigurationFiles.APPLICATION_PROPERTIES);
        Optional<PropertiesDocument> loaded = source.load(file);
        if (loaded.isEmpty()) {
            return NO_CHANGE;
        }
        PropertiesDocument document = loaded.get();
        Set<String> forageArtifacts = forageArtifacts();
        Map<PropertiesDocument.Entry, String> newValues = new HashMap<>();
        List<String> removed = new ArrayList<>();
        for (PropertiesDocument.Entry entry : document.entries()) {
            if (DEPENDENCY_PROPERTIES.contains(entry.key())) {
                withoutForageDependencies(entry.value(), forageArtifacts, removed)
                        .ifPresent(kept -> newValues.put(entry, kept.isEmpty() ? null : kept));
            }
        }
        if (newValues.isEmpty()) {
            return NO_CHANGE;
        }
        List<TextEdit> edits = document.edits(newValues, Map.of());
        FileChange change =
                new FileChange(file, false, edits, document.apply(edits), removed.size(), 0, document.charset());
        return new EditResult(List.of(change), List.copyOf(removed), List.of());
    }

    /**
     * The value of a dependency property without its Forage dependencies, empty if it has none. The removed
     * dependencies are added to {@code removed}.
     */
    private static Optional<String> withoutForageDependencies(
            String value, Set<String> forageArtifacts, List<String> removed) {
        List<String> gavs = PropertyKeys.splitValues(value);
        List<String> kept = gavs.stream()
                .filter(gav -> !forageArtifacts.contains(groupAndArtifact(gav)))
                .toList();
        if (kept.size() == gavs.size()) {
            return Optional.empty();
        }
        gavs.stream().filter(gav -> !kept.contains(gav)).forEach(removed::add);
        return Optional.of(String.join(",", kept));
    }

    private FileChange setValues(File file, Map<String, String> values, List<String> header) throws IOException {
        Optional<PropertiesDocument> existing = source.load(file);
        if (existing.isPresent()) {
            PropertiesDocument document = existing.get();
            List<TextEdit> edits = document.editsSettingValues(values);
            return new FileChange(file, false, edits, document.apply(edits), 0, values.size(), document.charset());
        }
        List<String> lines = PropertiesDocument.of(header).withValues(values);
        return new FileChange(
                file, true, List.of(new TextEdit(0, 0, lines)), lines, 0, values.size(), StandardCharsets.UTF_8);
    }

    private ForageConfiguration allFiles() throws IOException {
        Set<File> files = new LinkedHashSet<>(ForageConfigurationFiles.all(directory, catalog));
        files.addAll(strategy.findPropertiesFiles(directory));
        return ForageConfiguration.read(catalog, files, Set.of(), source);
    }

    /**
     * Whether an entry belongs to the instance: assigned to it, or recognised by nothing and under its prefix (e.g. the
     * typo {@code forage.myPG.jdbcc.url}).
     */
    private static boolean belongsTo(ForageConfiguration.Entry entry, String instanceName) {
        return instanceName.equals(entry.instanceName())
                || (entry.property() == null
                        && entry.instanceName() == null
                        && entry.key().startsWith(FORAGE_PREFIX + instanceName + "."));
    }

    /**
     * A list of instance names without the given one, {@code null} once empty.
     */
    private static String withoutName(String names, String instanceName) {
        List<String> remaining = new ArrayList<>(PropertyKeys.splitValues(names));
        remaining.remove(instanceName);
        return remaining.isEmpty() ? null : String.join(",", remaining);
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
