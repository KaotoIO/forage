package io.kaoto.forage.plugin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import io.kaoto.forage.catalog.model.ConfigEntry;
import io.kaoto.forage.catalog.model.QuarkusSelector;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;
import io.kaoto.forage.core.util.config.ConfigHelper;
import io.kaoto.forage.core.util.config.PropertyFileLocator;
import io.kaoto.forage.core.util.config.PropertyFileSource;

/**
 * Adds, on {@code camel export --runtime=quarkus}, native {@code quarkus.*} keys for the Forage modules the catalog maps
 * (today the JDBC datasources and the default Artemis JMS connection factory) to the exported
 * {@code application.properties}, so the exported app needs no Forage runtime for them. The {@code forage.*} keys
 * stay; each Quarkus key reads its Forage key as {@code ${forage.<key>:<value>}}, so {@code FORAGE_*} and
 * {@code -Dforage.*} still win. A module with anything the catalog does not map stays {@code forage.*} as before; a
 * configured module the catalog lists in {@code quarkusUnsupported} gets a note with the reason.
 */
final class QuarkusExport {

    static final String LOCATIONS = "quarkus.config.locations";

    /**
     * A Quarkus key ({@code {name}}: the quoted configuration name; ending with {@code .}: a map), and a unit; or none,
     * {@code ignored}: the Forage key has no effect on Quarkus; {@code selector}: the key selects the bean.
     */
    private record To(String key, String unit, boolean ignored, QuarkusSelector selector) {}

    private static final To IGNORED = new To(null, "", true, null);

    /**
     * Per module: the Forage keys (after {@code forage.[<name>.]<module>.}; ending with {@code .}: a map) with a
     * Quarkus equivalent, from the catalog ({@code quarkusProperty} / {@code quarkusUnit}, {@code quarkusIgnored},
     * {@code quarkusHandled}, {@code quarkusSelector}, written from the module descriptor).
     */
    static Map<String, Map<String, To>> table(ForageCatalogReader catalog) {
        Map<String, Map<String, To>> table = new TreeMap<>();
        for (var factory : catalog.getAllFactories()) {
            String module = factory.factoryTypeKey();
            Map<String, To> keys = new TreeMap<>();
            for (ConfigEntry entry : factory.configEntries()) {
                String to = entry.getQuarkusProperty();
                String key = entry.getName().substring(("forage." + module + ".").length());
                QuarkusSelector selector = entry.getQuarkusSelector();
                if (to != null) {
                    keys.put(
                            to.endsWith(".") ? key + "." : key,
                            new To(to, entry.getQuarkusUnit() == null ? "" : entry.getQuarkusUnit(), false, selector));
                } else if (entry.isQuarkusIgnored()) {
                    keys.put(key, IGNORED);
                } else if (selector != null || entry.isQuarkusHandled()) {
                    keys.put(key, new To(null, "", false, selector));
                }
            }
            if (keys.values().stream().anyMatch(to -> to.key() != null)) {
                table.put(module, keys);
            }
        }
        return table;
    }

    /** The added Quarkus properties, and the translated modules (their Forage artifacts are not needed). */
    record Result(Map<String, String> properties, Set<String> modules, List<String> notes) {}

    private QuarkusExport() {}

    /** Translates the exported resources and appends the Quarkus properties to their application.properties. */
    static Result export(Path resources) throws IOException {
        return export(resources, null);
    }

    /** As {@link #export(Path)}, translating only the {@code allowed} modules ({@code null}: all). */
    static Result export(Path resources, Set<String> allowed) throws IOException {
        Path application = resources.resolve("application.properties");
        Properties existing = read(application, StandardCharsets.UTF_8);
        Result result = translate(resources, existing, allowed);
        Properties added = new Properties();
        result.properties().forEach((key, value) -> {
            if (key.equals(LOCATIONS) && existing.getProperty(LOCATIONS) != null) {
                added.setProperty(key, existing.getProperty(LOCATIONS) + "," + value);
            } else if (existing.stringPropertyNames().stream()
                    .noneMatch(k -> unquoted(k).equals(unquoted(key)))) {
                added.setProperty(key, value); // a user quarkus.* key wins, as before
            }
        });
        if (!added.isEmpty()) {
            // escaped as ASCII; drop the date comment
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            added.store(out, null);
            String lines = out.toString(StandardCharsets.ISO_8859_1);
            Files.writeString(
                    application,
                    "\n# Forage configuration as Quarkus configuration\n" + lines.substring(lines.indexOf('\n') + 1),
                    StandardCharsets.US_ASCII,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        }
        return result;
    }

    static Result translate(Path resources, Properties application) throws IOException {
        return translate(resources, application, null);
    }

    /** Reads {@code application.properties} of {@code dir} and returns the modules it translates. */
    static Set<String> modules(Path dir) throws IOException {
        return translate(dir, read(dir.resolve("application.properties"), StandardCharsets.ISO_8859_1))
                .modules();
    }

    private static Result translate(Path resources, Properties application, Set<String> allowed) throws IOException {
        ForageCatalogReader catalog = ForageCatalogReader.getInstance();
        Result result = new Result(new TreeMap<>(), new TreeSet<>(), new ArrayList<>());
        List<String> locations = new ArrayList<>();
        Map<String, Map<String, To>> table = table(catalog);
        for (String module : table.keySet()) {
            String file = catalog.getPropertiesFileName(module).orElseThrow();
            Properties moduleFile = read(resources.resolve(file), StandardCharsets.ISO_8859_1);
            // per configuration name ("" for the default one): the Forage keys, application.properties wins
            Map<String, Map<String, String>> configs = new TreeMap<>();
            String reason = collect(module, moduleFile, configs);
            if (reason == null) {
                reason = collect(module, application, configs);
            }
            if (configs.isEmpty()) {
                continue;
            }
            if (reason == null && configs.containsKey("") && configs.size() > 1) {
                reason = "default and named configurations together";
            }
            Map<String, String> translated = new TreeMap<>();
            List<String> ignored = new ArrayList<>();
            for (var config : configs.entrySet()) {
                if (reason == null) {
                    reason = translate(
                            catalog,
                            module,
                            table.get(module),
                            config.getKey(),
                            config.getValue(),
                            translated,
                            ignored);
                }
            }
            if (reason == null) {
                reason = otherFiles(resources, module, file, moduleFile, application);
            }
            if (reason == null && allowed != null && !allowed.contains(module)) {
                // the run data of the export holds its Forage artifacts
                reason = "the run of the export kept it";
            }
            if (reason != null) {
                result.notes().add("Forage " + module + " stays as forage.* configuration: " + reason + ".");
                continue;
            }
            result.properties().putAll(translated);
            result.modules().add(module);
            locations.add(file);
            result.notes()
                    .add("Forage " + module + " exported as quarkus.* configuration; FORAGE_* and -Dforage.* still"
                            + " override it.");
            if (!ignored.isEmpty()) {
                result.notes()
                        .add("Forage " + module + ": " + String.join(", ", ignored)
                                + " not translated, no effect on Quarkus.");
            }
        }
        for (var unsupported : catalog.getQuarkusUnsupported().entrySet()) {
            if (configured(resources, application, unsupported.getKey())) {
                result.notes()
                        .add("Forage " + unsupported.getKey() + " stays as forage.* configuration: "
                                + unsupported.getValue() + ".");
            }
        }
        if (!locations.isEmpty()) {
            // module files of the working directory, the classpath and FORAGE_CONFIG_DIR are still read
            locations.addAll(locations.stream()
                    .map(file -> "${forage.config.dir:none}/" + file)
                    .toList());
            result.properties().put(LOCATIONS, String.join(",", locations));
        }
        return result;
    }

    /** Adds the module's keys to {@code configs}, or returns why the module stays forage.*. */
    private static String collect(String module, Properties source, Map<String, Map<String, String>> configs) {
        Pattern named = Pattern.compile(ConfigHelper.getNamedPropertyRegexp(module));
        Pattern unnamed = Pattern.compile(ConfigHelper.getDefaultPropertyRegexp(module));
        for (String key : source.stringPropertyNames()) {
            String plain = key.startsWith("%") ? key.substring(key.indexOf('.') + 1) : key;
            Matcher m = named.matcher(plain);
            String name = m.matches() ? m.group(1) : unnamed.matcher(plain).matches() ? "" : null;
            if (name == null) {
                continue;
            }
            if (!plain.equals(key)) {
                return "profile-specific values";
            }
            configs.computeIfAbsent(name, n -> new TreeMap<>()).put(key, source.getProperty(key));
        }
        return null;
    }

    /** Whether application.properties or another properties file of the resources sets a key of the module. */
    private static boolean configured(Path resources, Properties application, String module) throws IOException {
        List<Properties> sources = new ArrayList<>(List.of(application));
        if (Files.isDirectory(resources)) {
            try (Stream<Path> files = Files.list(resources)) {
                for (Path file : files.filter(f -> f.getFileName().toString().endsWith(".properties"))
                        .toList()) {
                    sources.add(read(file, StandardCharsets.ISO_8859_1));
                }
            }
        }
        Pattern named = Pattern.compile(ConfigHelper.getNamedPropertyRegexp(module));
        Pattern unnamed = Pattern.compile(ConfigHelper.getDefaultPropertyRegexp(module));
        return sources.stream()
                .flatMap(source -> source.stringPropertyNames().stream())
                .map(key -> key.startsWith("%") ? key.substring(key.indexOf('.') + 1) : key)
                .anyMatch(key ->
                        named.matcher(key).matches() || unnamed.matcher(key).matches());
    }

    /**
     * Adds the Quarkus properties of one configuration and its keys without effect on Quarkus to {@code ignored}, or
     * returns why the module stays forage.*.
     */
    private static String translate(
            ForageCatalogReader catalog,
            String module,
            Map<String, To> table,
            String name,
            Map<String, String> values,
            Map<String, String> translated,
            List<String> ignored) {
        String prefix = "forage." + (name.isEmpty() ? "" : name + ".") + module + ".";
        for (var entry : values.entrySet()) {
            String suffix = entry.getKey().substring(prefix.length());
            if (!table.containsKey(suffix) && !(table.containsKey("properties.") && suffix.startsWith("properties."))) {
                return entry.getKey() + " has no Quarkus equivalent";
            }
            if (table.containsKey(suffix) && table.get(suffix).ignored()) {
                ignored.add(entry.getKey());
            }
            // Quarkus cannot escape { or } inside ${...:<value>}; Forage expands ${...} and {{...}} itself
            if ((entry.getKey() + entry.getValue()).matches("(?s).*[{}].*")
                    || entry.getKey().contains(":")) {
                return entry.getKey() + " cannot be written as a Quarkus expression";
            }
        }
        var metadata = catalog.getFactoryMetadata(module).orElseThrow();
        Map<String, String> defaults = new TreeMap<>();
        for (ConfigEntry entry : metadata.configEntries()) {
            String key = prefix + entry.getName().substring(("forage." + module + ".").length());
            if (entry.getDefaultValue() != null) {
                defaults.put(key, entry.getDefaultValue());
            } else if (entry.isRequired() && !values.containsKey(key)) {
                return key + " is not set";
            }
        }
        for (var entry : table.entrySet()) {
            QuarkusSelector selector = entry.getValue().selector();
            if (selector == null) {
                continue;
            }
            if (selector.isDefaultOnly() && !name.isEmpty()) {
                return "named configurations have no Quarkus equivalent";
            }
            String kind = values.get(prefix + entry.getKey());
            if (catalog.getBeanRuntimeDependencies(kind, "quarkus").isEmpty()
                    || (selector.getKinds() != null && !selector.getKinds().containsKey(kind))) {
                return "the kind of " + prefix + "* has no Quarkus equivalent in the Forage catalog";
            }
            if (selector.getKinds() != null) {
                translated.putAll(selector.getKinds().get(kind));
            }
        }
        String quarkusName = "\"" + (name.isEmpty() ? metadata.defaultBeanName() : name) + "\"";
        for (var entry : table.entrySet()) {
            To to = entry.getValue();
            if (to.key() == null) {
                continue;
            }
            String key = to.key().replace("{name}", quarkusName);
            String forageKey = prefix + entry.getKey();
            if (key.endsWith(".")) {
                values.forEach((k, v) -> {
                    if (k.startsWith(forageKey)) {
                        translated.put(
                                key + "\"" + k.substring(forageKey.length()) + "\"", "${" + k + ":" + escaped(v) + "}");
                    }
                });
            } else {
                String value = values.getOrDefault(forageKey, defaults.getOrDefault(forageKey, ""));
                translated.put(key, "${" + forageKey + ":" + escaped(value) + "}" + to.unit());
            }
        }
        return null;
    }

    /** Why other files the exported app reads keep the module forage.*, or {@code null}. */
    private static String otherFiles(
            Path resources, String module, String file, Properties moduleFile, Properties application)
            throws IOException {
        // Forage reads it as ISO-8859-1, Quarkus (quarkus.config.locations) as UTF-8
        if (Files.isRegularFile(resources.resolve(file))
                && new String(Files.readAllBytes(resources.resolve(file)), StandardCharsets.ISO_8859_1)
                        .chars()
                        .anyMatch(c -> c > 0x7f)) {
            return file + " is not ASCII";
        }
        for (String key : moduleFile.stringPropertyNames()) {
            if (application.containsKey(key) && !application.getProperty(key).equals(moduleFile.getProperty(key))) {
                return key + " differs between " + file + " and application.properties";
            }
        }
        try (Stream<Path> files = Files.list(resources)) {
            for (Path profileFile : files.filter(f -> f.getFileName().toString().startsWith("application-"))
                    .toList()) {
                Map<String, Map<String, String>> configs = new TreeMap<>();
                collect(module, read(profileFile, StandardCharsets.ISO_8859_1), configs);
                if (!configs.isEmpty()) {
                    return "profile-specific values in " + profileFile.getFileName();
                }
            }
        }
        // read at runtime from outside the project
        String configDir = System.getenv("FORAGE_CONFIG_DIR");
        if (configDir != null && Files.isRegularFile(Path.of(configDir, file))) {
            return file + " is read from FORAGE_CONFIG_DIR";
        }
        for (PropertyFileSource source : PropertyFileLocator.getBuiltInSources()) {
            if (source.name().equals("HomeDirectoryPropertyFileSource")) {
                try (InputStream in = source.locate(file)) {
                    if (in != null) {
                        return file + " is read from the Forage home directory";
                    }
                }
            }
        }
        return null;
    }

    /** A backslash is an escape inside a Quarkus expression. */
    private static String escaped(String value) {
        return value.replace("\\", "\\\\");
    }

    private static String unquoted(String key) {
        return key.replace("\"", "");
    }

    private static Properties read(Path file, Charset charset) throws IOException {
        Properties properties = new Properties();
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, charset)) {
                properties.load(reader);
            }
        }
        return properties;
    }
}
