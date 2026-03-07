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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;

/**
 * Scans a directory for forage properties files and extracts property values
 * grouped by factory type key.
 */
public final class ForagePropertyScanner {

    private static final Pattern FORAGE_PROPERTY_PATTERN = Pattern.compile("^forage\\.(.+)$");

    private ForagePropertyScanner() {}

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
        Map<String, Map<String, List<String>>> result = new HashMap<>();

        List<File> propertiesFiles = findPropertiesFiles(directory, catalog);
        for (File file : propertiesFiles) {
            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(file)) {
                props.load(fis);
            }

            for (String key : props.stringPropertyNames()) {
                Matcher matcher = FORAGE_PROPERTY_PATTERN.matcher(key);
                if (!matcher.matches()) {
                    continue;
                }

                String remainder = matcher.group(1);
                ParseResult parsed = parsePropertyKey(remainder, catalog);
                if (parsed == null) {
                    continue;
                }

                result.computeIfAbsent(parsed.factoryTypeKey, k -> new HashMap<>())
                        .computeIfAbsent(parsed.propertyName, k -> new ArrayList<>())
                        .add(props.getProperty(key));
            }
        }

        return result;
    }

    private static List<File> findPropertiesFiles(File dir, ForageCatalogReader catalog) throws IOException {
        Set<String> targetFileNames = new java.util.HashSet<>();
        targetFileNames.add("application.properties");

        for (ForageCatalogReader.FactoryMetadata metadata : catalog.getAllFactories()) {
            String propsFile = metadata.propertiesFileName();
            if (propsFile != null && !propsFile.isEmpty()) {
                targetFileNames.add(propsFile);
            }
        }

        try (Stream<Path> paths = Files.walk(dir.toPath(), 1)) {
            return paths.filter(Files::isRegularFile)
                    .filter(p -> {
                        String fileName = p.getFileName().toString();
                        return targetFileNames.contains(fileName)
                                || (fileName.startsWith("forage-") && fileName.endsWith(".properties"));
                    })
                    .map(Path::toFile)
                    .toList();
        }
    }

    private static ParseResult parsePropertyKey(String key, ForageCatalogReader catalog) {
        String[] parts = key.split("\\.", 2);
        if (parts.length < 2) {
            return null;
        }

        String firstPart = parts[0];
        String restOfKey = parts[1];

        // Check if the first part is a known factory type key
        if (catalog.getFactoryMetadata(firstPart).isPresent()) {
            return new ParseResult(firstPart, restOfKey);
        }

        // Check if the first part is a known bean type
        Optional<String> beanFactory = catalog.findFactoryTypeKeyForBeanName(firstPart);
        if (beanFactory.isPresent()) {
            return new ParseResult(beanFactory.get(), key);
        }

        // The first part might be an instance name, check second segment
        String[] restParts = restOfKey.split("\\.", 2);
        if (restParts.length >= 1) {
            String secondPart = restParts[0];
            String propertyName = restParts.length > 1 ? restParts[1] : "";

            if (catalog.getFactoryMetadata(secondPart).isPresent()) {
                return new ParseResult(secondPart, propertyName);
            }

            Optional<String> beanFactory2 = catalog.findFactoryTypeKeyForBeanName(secondPart);
            if (beanFactory2.isPresent()) {
                return new ParseResult(beanFactory2.get(), restOfKey);
            }
        }

        // Try property prefix mapping
        Optional<String> factoryType = catalog.findFactoryTypeKeyForPropertyPrefix(firstPart);
        return factoryType.map(s -> new ParseResult(s, key)).orElse(null);
    }

    private record ParseResult(String factoryTypeKey, String propertyName) {}
}
