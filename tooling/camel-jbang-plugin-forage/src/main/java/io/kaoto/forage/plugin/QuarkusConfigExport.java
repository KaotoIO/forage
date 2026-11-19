package io.kaoto.forage.plugin;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import io.kaoto.forage.core.common.ForageModuleDescriptor;
import io.kaoto.forage.core.common.QuarkusConfigHook;
import io.kaoto.forage.core.util.config.Config;
import io.kaoto.forage.core.util.config.ConfigHelper;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigResolver;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.core.util.config.MissingConfigException;
import io.kaoto.forage.core.util.config.PropertyFileLocator;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

/**
 * Translates, for {@code camel export --runtime=quarkus}, the Forage configurations that Quarkus extensions fully
 * cover into {@code quarkus.*} properties, so the exported application needs no Forage runtime for them.
 *
 * <p>A module is translated all or nothing: if any of its configurations needs Forage at runtime (transactions,
 * auxiliary repositories, {@code {{...}}} placeholders, profile-specific values, ...), the whole module stays
 * {@code forage.*}. Modules opt in with a {@link QuarkusConfigHook}. A value the export cannot keep fails it.
 */
final class QuarkusConfigExport {

    private static final String APPLICATION = "application.properties";

    /** The encoding of the {@code application.properties} Camel JBang exports. */
    static final Charset EXPORTED_APPLICATION = StandardCharsets.UTF_8;

    /**
     * @param properties the translated Quarkus properties
     * @param forageKeys the Forage keys they replace
     * @param modules the translated module prefixes (their Forage dependencies are not needed)
     * @param groups the catalog conditional groups the translated configurations enable
     * @param files the module property files the Forage keys were read from
     * @param messages what the export tells the user
     */
    record Result(
            Map<String, String> properties,
            Set<String> forageKeys,
            Set<String> modules,
            Set<String> groups,
            Set<String> files,
            List<String> messages) {

        Result() {
            this(
                    new LinkedHashMap<>(),
                    new LinkedHashSet<>(),
                    new LinkedHashSet<>(),
                    new LinkedHashSet<>(),
                    new LinkedHashSet<>(),
                    new ArrayList<>());
        }
    }

    /** {@code application.properties}, and the other {@code *.properties} files by name. */
    record Project(Properties application, Map<String, Properties> files) {}

    /** The translation of the project, and the {@code forage.*} values of application.properties and --property. */
    record Run(Result result, Map<String, String> application) {}

    private QuarkusConfigExport() {}

    /** Translates the project directory with the {@code --property} values, as the Forage runtime reads them. */
    static Run run(Path dir, Properties runProperties) throws IOException {
        Project project = load(dir, StandardCharsets.ISO_8859_1);
        project.application().putAll(runProperties);
        return new Run(translate(project), forageValues(project.application()));
    }

    /** The {@code forage.*} and {@code %<profile>.forage.*} entries. */
    private static Map<String, String> forageValues(Properties properties) {
        Map<String, String> values = new TreeMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (unprofiled(key).startsWith("forage.")) {
                values.put(key, properties.getProperty(key));
            }
        }
        return values;
    }

    private static String unprofiled(String key) {
        return key.startsWith("%") && key.indexOf('.') > 0 ? key.substring(key.indexOf('.') + 1) : key;
    }

    /**
     * Fails if the exported {@code application.properties} does not hold the {@code forage.*} values the run read:
     * Camel JBang writes it without escaping and drops blank values.
     */
    static void checkExported(Run run, Path resources) throws IOException {
        Path file = resources.resolve(APPLICATION);
        Map<String, String> exported =
                forageValues(Files.exists(file) ? read(file, EXPORTED_APPLICATION) : new Properties());
        Set<String> keys = new TreeSet<>(run.application().keySet());
        keys.addAll(exported.keySet());
        for (String key : keys) {
            String expected = run.application().get(key);
            String found = exported.get(key);
            if (!Objects.equals(expected, found)) {
                throw new IllegalStateException("The exported " + APPLICATION + " changes " + key + ": expected "
                        + quote(expected) + ", found " + quote(found) + ". Camel JBang writes it without escaping"
                        + " and drops blank values: change the value, or set it in the module's forage-*.properties"
                        + " file (copied as it is, without %profile keys) and pass that file to camel export.");
            }
        }
    }

    private static String quote(String value) {
        return value == null ? "no value" : "'" + value + "'";
    }

    /**
     * Loads the {@code *.properties} files of a directory as ISO-8859-1, as the Forage runtime reads them, except
     * {@code application.properties} (Camel JBang exports it as UTF-8). Fails on a {@code forage.*} key in
     * {@code application-<profile>.properties}: the export does not support them there.
     */
    static Project load(Path dir, Charset application) throws IOException {
        Project project = new Project(new Properties(), new TreeMap<>());
        if (!Files.isDirectory(dir)) {
            return project;
        }
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file :
                    files.filter(f -> f.toString().endsWith(".properties")).toList()) {
                String name = file.getFileName().toString();
                Properties props = read(file, name.equals(APPLICATION) ? application : StandardCharsets.ISO_8859_1);
                if (name.equals(APPLICATION)) {
                    project.application().putAll(props);
                } else if (!name.startsWith("application-")) {
                    project.files().put(name, props);
                } else if (!forageValues(props).isEmpty()) {
                    String profile = name.substring("application-".length(), name.length() - ".properties".length());
                    throw new IllegalStateException("The Quarkus export does not support forage.* keys in " + name
                            + ": move " + forageValues(props).keySet() + " to " + APPLICATION + " with a %" + profile
                            + ". prefix.");
                }
            }
        }
        return project;
    }

    /** Translates the project; Forage values of the export environment ({@code FORAGE_*}, ...) are ignored. */
    static Result translate(Project project) {
        return translate(
                project,
                ServiceLoader.load(QuarkusConfigHook.class, QuarkusConfigExport.class.getClassLoader()).stream()
                        .<QuarkusConfigHook<?>>map(ServiceLoader.Provider::get)
                        .toList());
    }

    static Result translate(Project project, List<QuarkusConfigHook<?>> hooks) {
        Result result = new Result();
        try {
            for (QuarkusConfigHook<?> hook : hooks) {
                // as at runtime: the module file (its %profile keys are ignored), then application.properties
                String file = hook.descriptor().createConfig(null).name() + ".properties";
                Properties values = new Properties();
                project.files().getOrDefault(file, new Properties()).forEach((key, value) -> {
                    if (!key.toString().startsWith("%")) {
                        values.put(key, value);
                    }
                });
                values.putAll(project.application());
                if (translate(hook, project.application(), values, result)
                        && project.files().containsKey(file)) {
                    result.files().add(file);
                }
            }
        } finally {
            // drop the export-only values from the shared store
            ConfigStore.getInstance().reload();
        }
        return result;
    }

    /** Adds the module's translation to the result, or the reason it stays {@code forage.*}. */
    private static boolean translate(
            QuarkusConfigHook<?> hook, Properties application, Properties values, Result result) {
        String module = hook.descriptor().modulePrefix();
        String namedRegexp = ConfigHelper.getNamedPropertyRegexp(module);
        String defaultRegexp = ConfigHelper.getDefaultPropertyRegexp(module);
        boolean profiled = false;
        for (String key : values.stringPropertyNames()) {
            String unprofiled = unprofiled(key);
            if (unprofiled.matches(namedRegexp) || unprofiled.matches(defaultRegexp)) {
                checkValue(key, values.getProperty(key));
                profiled |= !key.equals(unprofiled);
            }
        }
        Set<String> named = PropertyFileLocator.readPrefixes(values, namedRegexp);
        boolean hasDefault =
                !PropertyFileLocator.readPrefixes(values, defaultRegexp).isEmpty();
        if (named.isEmpty() && !hasDefault) {
            return false;
        }

        String reason = profiled
                ? "profile-specific values"
                : !named.isEmpty() && hasDefault ? "default and named configurations together" : null;
        Result translation = new Result();
        for (String name : named.isEmpty() ? Collections.<String>singletonList(null) : named) {
            if (reason == null) {
                reason = translate(hook, name, application, values, translation);
            }
        }
        if (reason != null) {
            result.messages().add("Forage " + module + " stays as forage.* configuration: " + reason + ".");
            return false;
        }
        result.properties().putAll(translation.properties());
        result.forageKeys().addAll(translation.forageKeys());
        result.modules().add(module);
        result.groups().addAll(translation.groups());
        result.messages().addAll(translation.messages());
        return true;
    }

    /** Quarkus reads an empty value as not set and expands {@code ${...}}, {@code $$} and {@code \$}: Forage not. */
    private static void checkValue(String key, String value) {
        if (value.isEmpty()) {
            throw new IllegalStateException("Quarkus reads the empty value of " + key + " as not set, the Forage"
                    + " runtime uses it: set a value, or remove " + key + ".");
        }
        if (value.contains("${") || value.contains("$$") || value.contains("\\$")) {
            throw new IllegalStateException("Quarkus would read " + key + " differently: it expands ${...}, $$ and"
                    + " \\$ in values, the Forage runtime does not. Change the value.");
        }
    }

    /** Translates one configuration ({@code name} is {@code null} for the default one), or returns why it cannot. */
    private static <C extends Config> String translate(
            QuarkusConfigHook<C> hook, String name, Properties application, Properties values, Result result) {
        ForageModuleDescriptor<C, ?> descriptor = hook.descriptor();
        String module = descriptor.modulePrefix();
        String label = "'" + (name == null ? module : name) + "'";
        String keyPrefix = name == null ? "forage." + module + "." : "forage." + name + "." + module + ".";
        Map<String, String> config = new TreeMap<>();
        values.stringPropertyNames().stream()
                .filter(key -> key.startsWith(keyPrefix))
                .forEach(key -> config.put(key, values.getProperty(key)));

        // never drop configuration: every key must be one the hook covers
        List<String> uncovered = config.keySet().stream()
                .filter(key -> hook.covered().stream()
                        .noneMatch(c -> c.endsWith(".") ? key.startsWith(keyPrefix + c) : key.equals(keyPrefix + c)))
                .toList();
        if (config.values().stream().anyMatch(value -> value.contains("{{"))) {
            return label + " uses {{...}} placeholders";
        }
        if (!uncovered.isEmpty()) {
            return label + " uses " + String.join(", ", uncovered) + ", not covered by the Quarkus export";
        }
        C forage = descriptor.createConfig(name);
        useOnly(forage, keyPrefix, config);
        String unsupported = hook.unsupported(name, forage);
        if (unsupported != null) {
            return label + " " + unsupported;
        }
        // some configs (e.g. JDBC driver properties) read system properties and resolvers directly
        Map<String, String> hidden = new HashMap<>();
        System.getProperties().stringPropertyNames().stream()
                .filter(key -> key.startsWith(keyPrefix))
                .forEach(key -> hidden.put(key, (String) System.getProperties().remove(key)));
        ConfigStore store = ConfigStore.getInstance();
        List<ConfigResolver> resolvers = List.copyOf(store.getResolvers());
        resolvers.forEach(resolver -> store.unregisterResolver(resolver.getClass()));
        Map<String, String> translated;
        try {
            translated = new TreeMap<>(descriptor.translateProperties(name, forage));
        } catch (MissingConfigException | IllegalArgumentException e) {
            return label + ": " + e.getMessage();
        } finally {
            hidden.forEach(System::setProperty);
            resolvers.forEach(store::registerResolver);
        }
        if (translated.isEmpty()) {
            return label + " has no Quarkus equivalent";
        }
        String conflict = dependencyConflict(hook, translated, application);
        if (conflict != null) {
            return label + " has " + conflict;
        }
        result.groups().addAll(hook.adjust(forage, translated));
        result.properties().putAll(translated);
        result.forageKeys().addAll(config.keySet());
        result.messages().add(overrideMessage(module, label, keyPrefix, translated));
        return null;
    }

    /** A user {@code quarkus.*} value (also {@code %profile.}) differing on a dependency key, or {@code null}. */
    private static String dependencyConflict(
            QuarkusConfigHook<?> hook, Map<String, String> translated, Properties application) {
        for (Map.Entry<String, String> entry : translated.entrySet()) {
            if (hook.dependencyKeys().stream()
                    .noneMatch(suffix -> entry.getKey().endsWith("." + suffix))) {
                continue;
            }
            for (String user : application.stringPropertyNames()) {
                String value = application.getProperty(user);
                if (sameQuarkusKey(unprofiled(user), entry.getKey()) && !value.equals(entry.getValue())) {
                    return user + "=" + value + " in application.properties, but its dependencies follow "
                            + entry.getValue();
                }
            }
        }
        return null;
    }

    /** {@code "ds1"} and {@code ds1} are the same Quarkus key. */
    private static boolean sameQuarkusKey(String a, String b) {
        return a.replace("\"", "").equals(b.replace("\"", ""));
    }

    /** Makes the configuration use only the given values, not the environment, system properties or other files. */
    private static void useOnly(Config config, String keyPrefix, Map<String, String> values) {
        ConfigStore store = ConfigStore.getInstance();
        for (Map.Entry<Object, Object> entry : store.entries()) {
            if (entry.getKey() instanceof ConfigModule configModule
                    && configModule.propertyName().startsWith(keyPrefix)) {
                store.set(configModule, null);
            } else if (entry.getKey() instanceof String key && key.startsWith(keyPrefix)) {
                // e.g. JDBC driver properties, loaded from the home directory
                store.setDirect(key, null);
            }
        }
        values.forEach(config::register);
    }

    private static String overrideMessage(
            String module, String label, String keyPrefix, Map<String, String> translated) {
        String overrides = translated.keySet().stream()
                .sorted()
                .map(QuarkusConfigExport::envName)
                .collect(Collectors.joining(", "));
        String message = "Forage " + module + " " + label + " exported as Quarkus configuration: override it with "
                + overrides + " (" + envName(keyPrefix) + "* is not read).";
        boolean exportTimeOverrides = System.getenv().keySet().stream().anyMatch(n -> n.startsWith(envName(keyPrefix)))
                || System.getProperties().stringPropertyNames().stream().anyMatch(n -> n.startsWith(keyPrefix));
        return exportTimeOverrides
                ? message + " Ignored the " + envName(keyPrefix) + "* / " + keyPrefix
                        + "* values set in the export environment."
                : message;
    }

    /** The environment variable name of a property, as Quarkus and Forage map it. */
    static String envName(String property) {
        return property.replaceAll("[^A-Za-z0-9]", "_").toUpperCase(Locale.ROOT);
    }

    /**
     * Rewrites the exported resources: adds the Quarkus properties to {@code application.properties}, keeping the keys
     * it already defines, and removes the translated Forage keys from it and from the module files (deleting the module
     * files left empty). Fails if the files do not end up as intended.
     */
    static void write(Result result, Path resourcesDir) throws IOException {
        if (result.properties().isEmpty()) {
            return;
        }
        Path application = resourcesDir.resolve(APPLICATION);
        Properties existing = Files.exists(application) ? read(application, EXPORTED_APPLICATION) : new Properties();
        Map<String, String> written = new TreeMap<>();
        result.properties().forEach((key, value) -> {
            if (existing.stringPropertyNames().stream().noneMatch(user -> sameQuarkusKey(user, key))) {
                written.put(key, value);
            }
        });
        StringBuilder block = new StringBuilder("\n# Translated from the Forage configuration\n");
        written.forEach((key, value) -> block.append(escape(key, true))
                .append('=')
                .append(escape(value, false))
                .append('\n'));
        // add first: a failure leaves the Forage keys in place; the block is ASCII, any reader sees the same
        Files.writeString(
                application, block, StandardCharsets.US_ASCII, StandardOpenOption.CREATE, StandardOpenOption.APPEND);

        List<Path> files = new ArrayList<>(List.of(application));
        result.files().forEach(file -> files.add(resourcesDir.resolve(file)));
        for (Path file : files) {
            if (!Files.exists(file)) {
                continue;
            }
            // the lines left keep their bytes; a module file can hold any byte, as ISO-8859-1 maps them all
            Charset charset = file.equals(application) ? EXPORTED_APPLICATION : StandardCharsets.ISO_8859_1;
            List<String> original = Files.readAllLines(file, charset);
            List<String> lines = removeKeys(original, result.forageKeys());
            if (lines.size() != original.size()
                    && lines.stream().allMatch(String::isBlank)
                    && !file.equals(application)) {
                Files.delete(file);
                continue;
            } else if (lines.size() != original.size()) {
                // one line separator, on every platform
                Files.writeString(file, String.join("\n", lines) + "\n", charset);
            }
            read(file, charset).stringPropertyNames().stream()
                    .filter(result.forageKeys()::contains)
                    .findFirst()
                    .ifPresent(key -> {
                        throw new IllegalStateException("Could not remove " + key + " from the exported " + file);
                    });
        }
        // as Quarkus reads application.properties (UTF-8), expanding expressions
        SmallRyeConfig quarkus = new SmallRyeConfigBuilder()
                .addDefaultInterceptors()
                .withSources(new PropertiesConfigSource(read(application, StandardCharsets.UTF_8), APPLICATION, 250))
                .build();
        written.forEach((key, value) -> {
            if (!value.equals(quarkus.getConfigValue(key).getValue())) {
                throw new IllegalStateException("The exported " + APPLICATION + " does not keep " + key);
            }
        });
    }

    private static Properties read(Path file, Charset charset) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = new InputStreamReader(Files.newInputStream(file), charset)) {
            properties.load(reader);
        }
        return properties;
    }

    /** Escapes for a properties file, as ASCII; {@code =:#!} and spaces only where {@link Properties#load} needs it. */
    private static String escape(String text, boolean key) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' || ((key || i == 0) && " =:#!".indexOf(c) >= 0)) {
                escaped.append('\\').append(c);
            } else if (c < 0x20 || c > 0x7e) {
                escaped.append(String.format("\\u%04x", (int) c));
            } else {
                escaped.append(c);
            }
        }
        return escaped.toString();
    }

    /** Removes the lines of the given keys, including their continuation lines. */
    static List<String> removeKeys(List<String> lines, Set<String> keys) {
        List<String> kept = new ArrayList<>();
        boolean skipping = false;
        boolean continuation = false;
        for (String line : lines) {
            String trimmed = line.stripLeading();
            boolean comment = !continuation && (trimmed.startsWith("#") || trimmed.startsWith("!"));
            if (!continuation) {
                skipping = !comment && keys.contains(keyOf(trimmed));
            }
            if (!skipping) {
                kept.add(line);
            }
            continuation = !comment && endsWithContinuation(line);
        }
        return kept;
    }

    /** The key of a property line, unescaped as {@link Properties#load} reads it. */
    private static String keyOf(String line) {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(line));
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
        return properties.stringPropertyNames().stream().findFirst().orElse(null);
    }

    private static boolean endsWithContinuation(String line) {
        int backslashes = 0;
        for (int i = line.length() - 1; i >= 0 && line.charAt(i) == '\\'; i--) {
            backslashes++;
        }
        return backslashes % 2 == 1;
    }
}
