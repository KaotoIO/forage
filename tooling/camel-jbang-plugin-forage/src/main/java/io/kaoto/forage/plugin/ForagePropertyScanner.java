package io.kaoto.forage.plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;
import io.kaoto.forage.config.editor.ForageConfigurationFiles;
import io.kaoto.forage.config.editor.ForagePropertyParser;

/**
 * Scans a directory for forage properties files and extracts property values
 * grouped by factory type key.
 */
public final class ForagePropertyScanner {

    private static final String FORAGE_PREFIX = "forage.";
    private static final String UNKNOWN = "__unknown__";

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
        File applicationPropsFile = ForageConfigurationFiles.applicationProperties(directory);
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
        List<File> nonAppFiles = ForageConfigurationFiles.forageProperties(directory, catalog);
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
    private static String scannedPropertyName(ForagePropertyParser.ParsedProperty parsed) {
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
}
