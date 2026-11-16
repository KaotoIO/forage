package io.kaoto.forage.plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;

/**
 * Scans a directory for forage properties files and extracts property values
 * grouped by factory type key.
 */
public final class ForagePropertyScanner {

    private static final String FORAGE_PREFIX = "forage.";
    private static final String UNKNOWN = "__unknown__";

    /** The shared application properties file name. */
    public static final String APPLICATION_PROPERTIES = "application.properties";

    private static final String PROFILE_PROPERTY = "camel.main.profile";
    private static final String PROFILE_ENV_VAR = "CAMEL_MAIN_PROFILE";

    private ForagePropertyScanner() {}

    /**
     * Tracks where a property was defined (for validation purposes).
     */
    public record PropertyOccurrence(File file, String fullPropertyName, String value) {}

    /**
     * Scans the given directory for forage properties and returns them grouped by factory type key.
     * Each factory key maps to a property name to a list of all values found for that property
     * (supporting multiple named instances like ds1.jdbc.db.kind=mysql, ds2.jdbc.db.kind=postgresql).
     *
     * @param directory the directory to scan
     * @param catalog the catalog reader
     * @return map of factoryTypeKey -> (propertyName -> [values])
     */
    public static Map<String, Map<String, List<String>>> scanProperties(File directory, ForageCatalogReader catalog)
            throws IOException {
        // Delegate to scanPropertiesWithFileTracking and convert PropertyOccurrence to String
        Map<String, Map<String, List<PropertyOccurrence>>> withTracking =
                scanPropertiesWithFileTracking(directory, catalog, false);

        Map<String, Map<String, List<String>>> result = new HashMap<>();
        for (Map.Entry<String, Map<String, List<PropertyOccurrence>>> factoryEntry : withTracking.entrySet()) {
            String factoryKey = factoryEntry.getKey();

            Map<String, List<String>> propertyMap = new HashMap<>();
            for (Map.Entry<String, List<PropertyOccurrence>> propEntry :
                    factoryEntry.getValue().entrySet()) {
                List<String> values = propEntry.getValue().stream()
                        .map(PropertyOccurrence::value)
                        .toList();
                propertyMap.put(propEntry.getKey(), values);
            }
            result.put(factoryKey, propertyMap);
        }

        return result;
    }

    /**
     * Scans properties files and tracks where each property is defined.
     * Returns a map of factory type key to properties, plus optionally a special "__unknown__" key for unparseable properties.
     *
     * @param directory the directory to scan
     * @param catalog the catalog reader
     * @param trackUnknown if true, unparseable forage properties are tracked in "__unknown__" bucket
     * @return map of factoryTypeKey -> (propertyName -> [PropertyOccurrence])
     * @throws IOException if properties files cannot be read
     */
    public static Map<String, Map<String, List<PropertyOccurrence>>> scanPropertiesWithFileTracking(
            File directory, ForageCatalogReader catalog, boolean trackUnknown) throws IOException {

        Map<String, Map<String, List<PropertyOccurrence>>> result = new HashMap<>();
        ForagePropertyParser parser = new ForagePropertyParser(catalog);

        // Load application properties with profile override semantics (like Camel does):
        // scan base first, then profile file — profile occurrences replace base ones per property key
        File applicationPropsFile = loadApplicationProperties(directory);
        Properties baseProps = null;
        if (applicationPropsFile != null) {
            baseProps = new Properties();
            try (FileInputStream fis = new FileInputStream(applicationPropsFile)) {
                baseProps.load(fis);
            }
            collectForageProperties(baseProps, applicationPropsFile, parser, trackUnknown, result);
        }

        File profileFile = findProfileProperties(directory, baseProps);
        if (profileFile != null) {
            Properties profileProps = new Properties();
            try (FileInputStream fis = new FileInputStream(profileFile)) {
                profileProps.load(fis);
            }
            // Profile properties override base: replace matching occurrences
            overrideForageProperties(profileProps, profileFile, parser, trackUnknown, result);
        }

        // Load forage-specific and catalog-named properties files independently
        List<File> nonAppFiles = findForagePropertiesFiles(directory, catalog);
        for (File file : nonAppFiles) {
            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(file)) {
                props.load(fis);
            }
            collectForageProperties(props, file, parser, trackUnknown, result);
        }

        return result;
    }

    /**
     * Scans properties files and tracks where each property is defined.
     * Unparseable properties are tracked in "__unknown__" bucket.
     *
     * @param directory the directory to scan
     * @param catalog the catalog reader
     * @return map of factoryTypeKey -> (propertyName -> [PropertyOccurrence])
     * @throws IOException if properties files cannot be read
     */
    public static Map<String, Map<String, List<PropertyOccurrence>>> scanPropertiesWithFileTracking(
            File directory, ForageCatalogReader catalog) throws IOException {
        return scanPropertiesWithFileTracking(directory, catalog, true);
    }

    private static void collectForageProperties(
            Properties props,
            File sourceFile,
            ForagePropertyParser parser,
            boolean trackUnknown,
            Map<String, Map<String, List<PropertyOccurrence>>> result) {

        for (String key : props.stringPropertyNames()) {
            if (!key.startsWith(FORAGE_PREFIX)) {
                continue;
            }
            PropertyOccurrence occurrence = new PropertyOccurrence(sourceFile, key, props.getProperty(key));
            Optional<ForagePropertyParser.ParsedProperty> parsed = parser.parse(key);
            if (parsed.isPresent()) {
                result.computeIfAbsent(parsed.get().factoryType(), k -> new HashMap<>())
                        .computeIfAbsent(scannedPropertyName(parsed.get()), k -> new ArrayList<>())
                        .add(occurrence);
            } else if (trackUnknown) {
                result.computeIfAbsent(UNKNOWN, k -> new HashMap<>())
                        .computeIfAbsent(key.substring(FORAGE_PREFIX.length()), k -> new ArrayList<>())
                        .add(occurrence);
            }
        }
    }

    private static void overrideForageProperties(
            Properties profileProps,
            File profileFile,
            ForagePropertyParser parser,
            boolean trackUnknown,
            Map<String, Map<String, List<PropertyOccurrence>>> result) {

        for (String key : profileProps.stringPropertyNames()) {
            if (!key.startsWith(FORAGE_PREFIX)) {
                continue;
            }
            PropertyOccurrence occurrence = new PropertyOccurrence(profileFile, key, profileProps.getProperty(key));
            Optional<ForagePropertyParser.ParsedProperty> parsed = parser.parse(key);
            if (parsed.isPresent()) {
                replaceBaseOccurrence(
                        parsed.get().factoryType(), scannedPropertyName(parsed.get()), key, occurrence, result);
            } else if (trackUnknown) {
                replaceBaseOccurrence(UNKNOWN, key.substring(FORAGE_PREFIX.length()), key, occurrence, result);
            }
        }
    }

    /**
     * The property name the scan groups values under: relative to the factory for factory properties
     * (e.g. {@code db.kind}), and including the bean prefix for bean properties (e.g. {@code ollama.model.name}).
     */
    static String scannedPropertyName(ForagePropertyParser.ParsedProperty parsed) {
        return parsed.beanType() != null ? parsed.entryKey() : parsed.propertyName();
    }

    // Override is only called after collectForageProperties has processed application.properties,
    // and before non-application files (forage-*.properties) are scanned. This ordering guarantees
    // that removeIf only removes base application.properties occurrences, never forage-*.properties ones.
    private static void replaceBaseOccurrence(
            String groupKey,
            String propertyName,
            String fullPropertyName,
            PropertyOccurrence occurrence,
            Map<String, Map<String, List<PropertyOccurrence>>> result) {

        Map<String, List<PropertyOccurrence>> groupMap = result.computeIfAbsent(groupKey, k -> new HashMap<>());
        List<PropertyOccurrence> occurrences = groupMap.get(propertyName);
        if (occurrences != null) {
            occurrences.removeIf(o -> o.fullPropertyName().equals(fullPropertyName));
        }
        groupMap.computeIfAbsent(propertyName, k -> new ArrayList<>()).add(occurrence);
    }

    /**
     * Returns the {@code application.properties} file of the given directory.
     *
     * @param dir the directory to look in
     * @return the file, or {@code null} if it does not exist
     */
    public static File loadApplicationProperties(File dir) {
        File file = new File(dir, APPLICATION_PROPERTIES);
        return file.isFile() ? file : null;
    }

    /**
     * Returns the {@code application-<profile>.properties} file for the active Camel profile, resolved from
     * {@code camel.main.profile} / {@code CAMEL_MAIN_PROFILE} or the given base properties.
     *
     * @return the profile file, or {@code null} if no profile is active or the file does not exist
     */
    private static File findProfileProperties(File dir, Properties applicationProps) {
        String profile = resolveProfile(applicationProps);
        if (profile == null) {
            return null;
        }
        File file = new File(dir, "application-" + profile + ".properties");
        return file.isFile() ? file : null;
    }

    /**
     * Returns every properties file that can hold Forage configuration for the directory, whatever the active
     * profile: {@code application.properties}, every {@code application-<profile>.properties}, and the files
     * returned by {@link #findForagePropertiesFiles(File, ForageCatalogReader)}. Only the top level of the
     * directory is scanned.
     *
     * <p>Use this when every profile matters, e.g. for the {@code camel.jbang.dependencies*} lists in
     * {@code application.properties}, which are shared by all profiles.
     *
     * @param dir the directory to scan
     * @param catalog the catalog reader
     * @return the existing files, never {@code null}
     * @throws IOException if the directory cannot be scanned
     */
    public static List<File> findAllConfigurationFiles(File dir, ForageCatalogReader catalog) throws IOException {
        List<File> files = new ArrayList<>();
        File applicationPropsFile = loadApplicationProperties(dir);
        if (applicationPropsFile != null) {
            files.add(applicationPropsFile);
        }
        files.addAll(findProfilePropertiesFiles(dir));
        files.addAll(findForagePropertiesFiles(dir, catalog));
        return files;
    }

    /**
     * Returns every {@code application-<profile>.properties} file of the directory, whatever the active profile,
     * sorted by name. Only the top level of the directory is scanned.
     *
     * @param dir the directory to scan
     * @return the existing files, never {@code null}; empty if the directory does not exist
     * @throws IOException if the directory cannot be scanned
     */
    public static List<File> findProfilePropertiesFiles(File dir) throws IOException {
        return listFiles(dir, fileName -> fileName.startsWith("application-") && fileName.endsWith(".properties"));
    }

    static String resolveProfile(Properties applicationProps) {
        String profile = resolveProfileFromEnvironment();
        if (profile != null) {
            return profile;
        }

        if (applicationProps != null) {
            profile = applicationProps.getProperty(PROFILE_PROPERTY);
        }
        return profile;
    }

    private static String resolveProfileFromEnvironment() {
        String profile = System.getProperty(PROFILE_PROPERTY);
        if (profile != null) {
            return profile;
        }
        return System.getenv(PROFILE_ENV_VAR);
    }

    /**
     * Finds the non-application Forage properties files in the given directory: the catalog-known file names
     * (one per module, named after its {@code Config.name()}) plus any {@code forage-*.properties} file. Only the
     * top level of the directory is scanned.
     *
     * <p>Note that the {@code forage-*.properties} wildcard is only loaded at runtime on Spring Boot; plain Camel
     * and Quarkus only load the catalog-known file names (and {@code application.properties}).
     *
     * @param dir the directory to scan
     * @param catalog the catalog reader
     * @return the matching files, never {@code null}; empty if the directory does not exist
     * @throws IOException if the directory cannot be scanned
     */
    public static List<File> findForagePropertiesFiles(File dir, ForageCatalogReader catalog) throws IOException {
        Set<String> catalogFileNames = new java.util.HashSet<>();

        for (ForageCatalogReader.FactoryMetadata metadata : catalog.getAllFactories()) {
            String propsFile = metadata.propertiesFileName();
            if (propsFile != null && !propsFile.isEmpty()) {
                catalogFileNames.add(propsFile);
            }
        }

        return listFiles(
                dir,
                fileName -> catalogFileNames.contains(fileName)
                        || (fileName.startsWith("forage-") && fileName.endsWith(".properties")));
    }

    private static List<File> listFiles(File dir, Predicate<String> fileNameFilter) throws IOException {
        if (!dir.isDirectory()) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(dir.toPath())) {
            return paths.filter(Files::isRegularFile)
                    .filter(p -> fileNameFilter.test(p.getFileName().toString()))
                    .sorted()
                    .map(Path::toFile)
                    .toList();
        }
    }
}
