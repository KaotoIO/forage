package io.kaoto.forage.config.editor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;
import io.kaoto.forage.config.editor.ForagePropertyParser.ParsedProperty;

/**
 * The Forage configuration of a set of properties files: every {@code forage.*} entry, the instance it belongs to and
 * the instances it adds up to.
 *
 * <p>Keys are resolved against all the files together, in two passes, so that a key that reads in several ways is
 * assigned like the rest of the configuration suggests:
 * <ol>
 *   <li>Keys with a single catalog reading (e.g. {@code forage.ds1.jdbc.url}), keys the segment heuristics recognise
 *       (e.g. the typo {@code forage.ds1.jdbc.urll}) and the names listed by instance references
 *       ({@code forage.multi.agent.names}) make the known instance names.</li>
 *   <li>A key with several catalog readings goes to the known instance it can belong to (the longest name wins), or
 *       to the default instance; for example {@code forage.azure.openai.api.key} belongs to an OpenAI instance named
 *       {@code azure} when one exists, and to the default Azure OpenAI bean otherwise. A key no catalog entry matches
 *       that starts with a known instance name (e.g. {@code forage.ollama.agent.modle.nmae} for an agent named
 *       {@code ollama}) belongs to that instance.</li>
 * </ol>
 *
 * <p>This class has no CLI dependencies, so it can be reused outside the {@code config} commands.
 */
public final class ForageConfiguration {

    /**
     * Factory entries whose value lists instance names, which an instance refers to by name.
     */
    static final Set<String> INSTANCE_LIST_ENTRIES = Set.of("multi.agent.names");

    private static final String FORAGE_PREFIX = "forage.";

    /**
     * A {@code forage.*} entry of a properties file.
     *
     * @param file the file the entry is in
     * @param line the entry in the file
     * @param instanceName the instance the entry belongs to, or {@code null} for the default instance (or no instance,
     *     when {@code property} is {@code null})
     * @param property the parsed property, or {@code null} if the key is not recognised
     */
    public record Entry(File file, PropertiesDocument.Entry line, String instanceName, ParsedProperty property) {

        /**
         * @return the property key
         */
        public String key() {
            return line.key();
        }

        /**
         * @return the property value
         */
        public String value() {
            return line.value();
        }
    }

    /**
     * An instance of a factory: the named or default bean the runtime creates from its entries.
     *
     * @param factoryType the factory type key (e.g. {@code jdbc})
     * @param name the instance name, or {@code null} for the default instance
     * @param beans the beans the instance configures or selects (e.g. {@code ollama}, {@code postgresql})
     * @param entries the recognised entries of the instance, in file order
     */
    public record Instance(String factoryType, String name, Set<String> beans, List<Entry> entries) {}

    private final ForagePropertyParser parser;
    private final Map<File, PropertiesDocument> documents;
    private final List<Entry> entries;

    private ForageConfiguration(
            ForagePropertyParser parser, Map<File, PropertiesDocument> documents, List<Entry> entries) {
        this.parser = parser;
        this.documents = documents;
        this.entries = entries;
    }

    /**
     * Reads the given properties files. Missing files are read as empty.
     *
     * @param catalog the catalog the keys are resolved against
     * @param files the files to read
     * @param knownInstanceNames instance names known to exist besides those the files show (e.g. the name of an
     *     instance about to be deleted)
     * @return the configuration
     * @throws IOException if a file cannot be read
     */
    public static ForageConfiguration read(
            ForageCatalogReader catalog, Collection<File> files, Set<String> knownInstanceNames) throws IOException {
        Map<File, PropertiesDocument> documents = new LinkedHashMap<>();
        for (File file : files) {
            documents.put(file, PropertiesDocument.read(file));
        }
        return new Resolver(new ForagePropertyParser(catalog), knownInstanceNames).resolve(documents);
    }

    /**
     * @return the files read, with their content
     */
    public Map<File, PropertiesDocument> documents() {
        return documents;
    }

    /**
     * @return every {@code forage.*} entry, in file order
     */
    public List<Entry> entries() {
        return entries;
    }

    /**
     * @return the entries of the instances with the given name, recognised or not, in file order
     */
    public List<Entry> entriesOf(String instanceName) {
        return entries.stream()
                .filter(entry -> instanceName.equals(entry.instanceName()))
                .toList();
    }

    /**
     * @return the instances, grouped by factory and name, in the order their first entry appears
     */
    public List<Instance> instances() {
        Map<List<String>, Instance> instances = new LinkedHashMap<>();
        for (Entry entry : entries) {
            ParsedProperty property = entry.property();
            if (property == null) {
                continue;
            }
            Instance instance = instances.computeIfAbsent(
                    Arrays.asList(property.factoryType(), entry.instanceName()),
                    k -> new Instance(
                            property.factoryType(), entry.instanceName(), new LinkedHashSet<>(), new ArrayList<>()));
            instance.entries().add(entry);
            if (property.beanType() != null) {
                instance.beans().add(property.beanType());
            }
            instance.beans().addAll(parser.selectedBeans(property, entry.value()));
        }
        return List.copyOf(instances.values());
    }

    /**
     * @return whether any entry belongs to an instance with the given name
     */
    public boolean hasInstance(String instanceName) {
        return entries.stream().anyMatch(entry -> instanceName.equals(entry.instanceName()));
    }

    /**
     * Resolves the entries of a set of documents in two passes.
     */
    private static final class Resolver {

        private final ForagePropertyParser parser;
        private final Set<String> known;

        Resolver(ForagePropertyParser parser, Set<String> knownInstanceNames) {
            this.parser = parser;
            this.known = new HashSet<>(knownInstanceNames);
        }

        ForageConfiguration resolve(Map<File, PropertiesDocument> documents) {
            // First pass: the candidate readings of every key, and the instance names they make known
            Map<PendingKey, List<ParsedProperty>> candidates = new LinkedHashMap<>();
            for (Map.Entry<File, PropertiesDocument> document : documents.entrySet()) {
                for (PropertiesDocument.Entry line : document.getValue().entries()) {
                    if (line.key().startsWith(FORAGE_PREFIX)) {
                        List<ParsedProperty> keyCandidates = parser.candidates(line.key());
                        candidates.put(new PendingKey(document.getKey(), line), keyCandidates);
                        learn(line, keyCandidates);
                    }
                }
            }

            // Second pass: assign every entry, preferring the known instances
            List<Entry> entries = new ArrayList<>();
            candidates.forEach((pending, keyCandidates) -> entries.add(assign(pending, keyCandidates)));
            return new ForageConfiguration(parser, documents, List.copyOf(entries));
        }

        private void learn(PropertiesDocument.Entry line, List<ParsedProperty> keyCandidates) {
            if (keyCandidates.size() == 1) {
                ParsedProperty only = keyCandidates.get(0);
                if (only.instanceName() != null) {
                    known.add(only.instanceName());
                }
                if (INSTANCE_LIST_ENTRIES.contains(only.entryKey())) {
                    known.addAll(PropertyKeys.splitValues(line.value()));
                }
            } else if (keyCandidates.isEmpty()) {
                parser.parseHeuristically(line.key())
                        .map(ParsedProperty::instanceName)
                        .ifPresent(known::add);
            }
        }

        private Entry assign(PendingKey pending, List<ParsedProperty> keyCandidates) {
            if (keyCandidates.size() == 1) {
                return entry(pending, keyCandidates.get(0));
            }
            if (keyCandidates.size() > 1) {
                ParsedProperty chosen = keyCandidates.stream()
                        .filter(candidate ->
                                candidate.instanceName() != null && known.contains(candidate.instanceName()))
                        .max(Comparator.comparingInt(
                                candidate -> candidate.instanceName().length()))
                        .orElse(keyCandidates.get(0));
                return entry(pending, chosen);
            }
            return assignUnmatched(pending);
        }

        /**
         * A key no catalog entry matches: it belongs to the longest known instance it starts with, read as that
         * instance's key, or else to what the segment heuristics find.
         */
        private Entry assignUnmatched(PendingKey pending) {
            String key = pending.line().key();
            Optional<String> instanceName = known.stream()
                    .filter(name -> key.startsWith(FORAGE_PREFIX + name + "."))
                    .max(Comparator.comparingInt(String::length));
            if (instanceName.isPresent()) {
                String rest = key.substring(
                        FORAGE_PREFIX.length() + instanceName.get().length() + 1);
                Optional<ParsedProperty> restProperty = parser.parse(FORAGE_PREFIX + rest);
                if (restProperty.isEmpty()) {
                    return new Entry(pending.file(), pending.line(), instanceName.get(), null);
                }
                // The rest may itself start with an instance name, making a dotted name (e.g. my + ds = my.ds)
                String restInstance = restProperty.get().instanceName();
                String name = restInstance == null ? instanceName.get() : instanceName.get() + "." + restInstance;
                return entry(pending, restProperty.get().withInstanceName(name));
            }
            return parser.parseHeuristically(key)
                    .map(property -> entry(pending, property))
                    .orElseGet(() -> new Entry(pending.file(), pending.line(), null, null));
        }

        private static Entry entry(PendingKey pending, ParsedProperty property) {
            return new Entry(pending.file(), pending.line(), property.instanceName(), property);
        }
    }

    private record PendingKey(File file, PropertiesDocument.Entry line) {}
}
