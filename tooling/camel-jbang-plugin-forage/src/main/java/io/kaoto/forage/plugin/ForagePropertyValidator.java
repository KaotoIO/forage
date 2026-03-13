package io.kaoto.forage.plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.camel.dsl.jbang.core.common.Printer;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.catalog.model.ConfigEntry;
import io.kaoto.forage.catalog.model.ForageBean;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;
import io.kaoto.forage.plugin.ForagePropertyScanner.PropertyOccurrence;

/**
 * Validates Forage properties against the catalog to detect typos,
 * unknown properties, and configuration issues.
 *
 * <p>This validator scans properties files and checks each Forage property against
 * the catalog metadata to ensure they are recognized. It provides helpful suggestions
 * for typos and lists valid alternatives for bean-name properties.
 *
 * @since 1.1
 */
public final class ForagePropertyValidator {

    private static final Logger LOG = LoggerFactory.getLogger(ForagePropertyValidator.class);

    private ForagePropertyValidator() {}

    /**
     * Helper method to avoid code redundancy.
     * Validates properties and prints warnings. Used by Forage commands.
     *
     * @param printer the printer to use for output
     * @param skipValidation if true, skip validation entirely
     * @param strict if true, return failure code when warnings exist
     * @return 0 if validation passed or was skipped, 1 if strict mode failed
     */
    public static int validateAndReport(Printer printer, boolean skipValidation, boolean strict) {
        if (skipValidation) {
            return 0;
        }

        try {
            File workingDir = new File(System.getProperty("user.dir"));
            ForageCatalogReader catalog = ForageCatalogReader.getInstance();

            ValidationResult validation = validate(workingDir, catalog);

            if (validation.hasWarnings()) {
                validation.printWarnings(printer);

                if (strict) {
                    printer.println("⛔ Validation failed in strict mode. Fix warnings and try again.");
                    return 1;
                }
            }

            return 0;
        } catch (Exception e) {
            LOG.warn("Property validation failed: {}", e.getMessage(), e);
            // Don't fail the command if validation itself fails
            return 0;
        }
    }

    /**
     * Validates all Forage properties in the given directory.
     *
     * @param directory the directory to scan for properties files
     * @param catalog the catalog reader containing valid property definitions
     * @return validation result with warnings and errors
     * @throws IOException if properties files cannot be read
     */
    public static ValidationResult validate(File directory, ForageCatalogReader catalog) throws IOException {
        ValidationResult result = new ValidationResult();

        // 1. Scan all properties with file tracking
        Map<String, Map<String, List<PropertyOccurrence>>> propertiesByFactory =
                scanPropertiesWithDetails(directory, catalog);

        if (propertiesByFactory.isEmpty()) {
            LOG.debug("No Forage properties found in directory: {}", directory);
            return result;
        }

        // 2. For each factory type, validate properties
        for (Map.Entry<String, Map<String, List<PropertyOccurrence>>> factoryEntry : propertiesByFactory.entrySet()) {

            String factoryTypeKey = factoryEntry.getKey();
            Map<String, List<PropertyOccurrence>> properties = factoryEntry.getValue();

            Optional<ForageCatalogReader.FactoryMetadata> metadataOpt = catalog.getFactoryMetadata(factoryTypeKey);

            if (metadataOpt.isEmpty()) {
                // Unknown factory type
                for (List<PropertyOccurrence> occurrences : properties.values()) {
                    for (PropertyOccurrence occ : occurrences) {
                        // Extract the attempted factory name from the property
                        String attemptedFactory = extractFactoryNameFromProperty(occ.fullPropertyName());
                        result.addWarning(new ValidationWarning(
                                occ.file(),
                                occ.fullPropertyName(),
                                ValidationWarning.Type.UNKNOWN_FACTORY,
                                String.format(
                                        "Unknown factory type '%s' in property '%s'",
                                        attemptedFactory != null ? attemptedFactory : factoryTypeKey,
                                        occ.fullPropertyName())));
                    }
                }
                continue;
            }

            ForageCatalogReader.FactoryMetadata metadata = metadataOpt.get();
            Set<String> validPropertyNames = extractValidPropertyNames(metadata, catalog, factoryTypeKey);

            // 3. Check each property against valid names
            for (Map.Entry<String, List<PropertyOccurrence>> propEntry : properties.entrySet()) {

                String propertyName = propEntry.getKey();
                List<PropertyOccurrence> occurrences = propEntry.getValue();

                if (!isValidProperty(propertyName, validPropertyNames, metadata, catalog, factoryTypeKey)) {
                    for (PropertyOccurrence occ : occurrences) {
                        // Generate suggestion
                        String suggestion = findClosestMatch(propertyName, validPropertyNames);

                        result.addWarning(new ValidationWarning(
                                occ.file(),
                                occ.fullPropertyName(),
                                ValidationWarning.Type.UNKNOWN_PROPERTY,
                                String.format(
                                        "Unknown property '%s' for factory '%s'%s",
                                        propertyName,
                                        factoryTypeKey,
                                        suggestion != null ? ". Did you mean '" + suggestion + "'?" : "")));
                    }
                }

                // 4. Validate property values for bean-name types
                ConfigEntry configEntry = findConfigEntry(propertyName, metadata, catalog, factoryTypeKey);
                if (configEntry != null && "bean-name".equals(configEntry.getType())) {
                    for (PropertyOccurrence occ : occurrences) {
                        validateBeanName(occ, configEntry, catalog, result);
                    }
                }
            }
        }

        return result;
    }

    /**
     * Scans properties files and tracks where each property is defined.
     * Returns a map of factory type key to properties, plus a special "__unknown__" key for unparseable properties.
     */
    private static Map<String, Map<String, List<PropertyOccurrence>>> scanPropertiesWithDetails(
            File directory, ForageCatalogReader catalog) throws IOException {
        return ForagePropertyScanner.scanPropertiesWithFileTracking(directory, catalog);
    }

    /**
     * Extracts all valid property names from factory metadata and bean configs.
     */
    private static Set<String> extractValidPropertyNames(
            ForageCatalogReader.FactoryMetadata metadata, ForageCatalogReader catalog, String factoryTypeKey) {

        Set<String> validNames = new HashSet<>();
        String factoryPrefix = "forage." + factoryTypeKey + ".";

        // Add factory-level config entries
        if (metadata.configEntries() != null) {
            for (ConfigEntry entry : metadata.configEntries()) {
                String name = entry.getName();
                if (name != null && name.startsWith(factoryPrefix)) {
                    validNames.add(name.substring(factoryPrefix.length()));
                }
            }
        }

        // Add bean-specific properties from beansByFeature
        List<ForageBean> beans = getAllBeansForFactory(catalog, factoryTypeKey);
        for (ForageBean bean : beans) {
            if (bean.getConfigEntries() != null) {
                for (ConfigEntry entry : bean.getConfigEntries()) {
                    String name = entry.getName();
                    if (name != null && name.startsWith("forage.")) {
                        // Extract property suffix
                        String beanPrefix = extractBeanPrefix(name);
                        if (beanPrefix != null) {
                            String suffix = name.substring(("forage." + beanPrefix + ".").length());
                            // Use fully qualified keys (prefix + suffix) to prevent collisions
                            validNames.add(beanPrefix + "." + suffix);
                        }
                    }
                }
            }
        }

        return validNames;
    }

    /**
     * Checks if a property is valid for the factory.
     */
    private static boolean isValidProperty(
            String propertyName,
            Set<String> validPropertyNames,
            ForageCatalogReader.FactoryMetadata metadata,
            ForageCatalogReader catalog,
            String factoryTypeKey) {

        // Direct match
        if (validPropertyNames.contains(propertyName)) {
            return true;
        }

        // Check with wildcards for nested properties (e.g., ds1.jdbc.url matches jdbc.url)
        String[] parts = propertyName.split("\\.");
        if (parts.length >= 2) {
            // Try matching from second segment onwards
            for (int i = 1; i < parts.length; i++) {
                String suffix = String.join(".", java.util.Arrays.copyOfRange(parts, i, parts.length));
                if (validPropertyNames.contains(suffix)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Finds the ConfigEntry for a property name.
     */
    private static ConfigEntry findConfigEntry(
            String propertyName,
            ForageCatalogReader.FactoryMetadata metadata,
            ForageCatalogReader catalog,
            String factoryTypeKey) {

        String factoryPrefix = "forage." + factoryTypeKey + ".";

        // Check factory-level entries
        if (metadata.configEntries() != null) {
            for (ConfigEntry entry : metadata.configEntries()) {
                String name = entry.getName();
                if (name != null && name.startsWith(factoryPrefix)) {
                    String suffix = name.substring(factoryPrefix.length());
                    if (propertyName.equals(suffix) || propertyName.endsWith("." + suffix)) {
                        return entry;
                    }
                }
            }
        }

        // Check bean-level entries
        List<ForageBean> beans = getAllBeansForFactory(catalog, factoryTypeKey);
        for (ForageBean bean : beans) {
            if (bean.getConfigEntries() != null) {
                for (ConfigEntry entry : bean.getConfigEntries()) {
                    String name = entry.getName();
                    if (name != null && name.startsWith("forage.")) {
                        String beanPrefix = extractBeanPrefix(name);
                        if (beanPrefix != null) {
                            String suffix = name.substring(("forage." + beanPrefix + ".").length());
                            if (propertyName.endsWith(suffix)) {
                                return entry;
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * Validates a bean-name property value (e.g., db.kind=postgresql).
     */
    private static void validateBeanName(
            PropertyOccurrence occurrence,
            ConfigEntry configEntry,
            ForageCatalogReader catalog,
            ValidationResult result) {

        String beanName = occurrence.value();
        if (beanName == null || beanName.trim().isEmpty()) {
            return;
        }

        String feature = configEntry.getSelectsFrom();

        if (feature != null) {
            // Get feature-scoped beans and check if the bean exists within this feature
            List<String> validBeans = getAllBeansForFeature(catalog, feature);
            if (!validBeans.contains(beanName.trim())) {
                // Bean not found in this feature - suggest alternatives
                String suggestion = findClosestMatch(beanName.trim(), new HashSet<>(validBeans));

                result.addWarning(new ValidationWarning(
                        occurrence.file(),
                        occurrence.fullPropertyName(),
                        ValidationWarning.Type.INVALID_BEAN_VALUE,
                        String.format(
                                "Unknown %s '%s'%s. Valid options: %s",
                                feature.toLowerCase(),
                                beanName,
                                suggestion != null ? ". Did you mean '" + suggestion + "'?" : "",
                                validBeans.isEmpty() ? "none" : String.join(", ", validBeans))));
            }
        }
    }

    /**
     * Gets all beans for a specific factory type.
     */
    private static List<ForageBean> getAllBeansForFactory(ForageCatalogReader catalog, String factoryTypeKey) {
        return catalog.getAllBeansForFactory(factoryTypeKey);
    }

    /**
     * Gets all bean names for a specific feature.
     */
    private static List<String> getAllBeansForFeature(ForageCatalogReader catalog, String feature) {
        return catalog.getBeanNamesByFeature(feature);
    }

    /**
     * Extracts the bean prefix from a config entry name.
     * E.g., "forage.openai.api.key" -> "openai"
     */
    private static String extractBeanPrefix(String configName) {
        if (configName == null || !configName.startsWith("forage.")) {
            return null;
        }

        String remaining = configName.substring("forage.".length());
        int dotIndex = remaining.indexOf('.');
        if (dotIndex > 0) {
            return remaining.substring(0, dotIndex);
        }

        return null;
    }

    /**
     * Extracts the factory name from a property for error messages.
     * E.g., "forage.unknown.property" -> "unknown"
     */
    private static String extractFactoryNameFromProperty(String propertyName) {
        if (propertyName == null || !propertyName.startsWith("forage.")) {
            return null;
        }

        String remaining = propertyName.substring("forage.".length());
        int dotIndex = remaining.indexOf('.');
        if (dotIndex > 0) {
            return remaining.substring(0, dotIndex);
        }
        return remaining; // If no dot, the whole thing is the factory name
    }

    /**
     * Finds the closest matching property name using Levenshtein distance.
     */
    private static String findClosestMatch(String target, Set<String> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }

        String closest = null;
        int minDistance = Integer.MAX_VALUE;

        for (String candidate : candidates) {
            int distance = levenshteinDistance(target.toLowerCase(), candidate.toLowerCase());
            if (distance < minDistance && distance <= 3) { // Max 3 edits for suggestions
                minDistance = distance;
                closest = candidate;
            }
        }

        return closest;
    }

    /**
     * Calculates the Levenshtein distance between two strings using Apache Commons Text.
     */
    private static int levenshteinDistance(String s1, String s2) {
        return LevenshteinDistance.getDefaultInstance().apply(s1, s2);
    }

    /**
     * Result object containing validation warnings.
     */
    public static class ValidationResult {
        private final List<ValidationWarning> warnings = new ArrayList<>();

        public void addWarning(ValidationWarning warning) {
            warnings.add(warning);
        }

        public List<ValidationWarning> getWarnings() {
            return Collections.unmodifiableList(warnings);
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        public void printWarnings(java.io.PrintStream out) {
            if (!hasWarnings()) {
                return;
            }

            out.println("\n⚠️  Forage Property Validation Warnings:");
            out.println("═".repeat(70));

            for (ValidationWarning warning : warnings) {
                out.println("\n" + warning.format());
            }

            out.println("\n" + "═".repeat(70));
            out.println(String.format("Total warnings: %d\n", warnings.size()));
        }

        /**
         * Prints validation warnings using a Printer (for Camel JBang commands).
         *
         * @param printer the printer to use for output
         */
        public void printWarnings(Printer printer) {
            if (!hasWarnings()) {
                return;
            }

            printer.println("\n⚠️  Forage Property Validation Warnings:");
            printer.println("═".repeat(70));

            for (ValidationWarning warning : warnings) {
                printer.println("\n" + warning.format());
            }

            printer.println("\n" + "═".repeat(70));
            printer.println(String.format("Total warnings: %d\n", warnings.size()));
        }
    }

    /**
     * Represents a validation warning with file location and helpful message.
     */
    public static class ValidationWarning {
        public enum Type {
            UNKNOWN_FACTORY,
            UNKNOWN_PROPERTY,
            INVALID_BEAN_VALUE,
            MISSING_REQUIRED // TODO: Implement required property validation
        }

        private final File file;
        private final String propertyName;
        private final Type type;
        private final String message;

        public ValidationWarning(File file, String propertyName, Type type, String message) {
            this.file = file;
            this.propertyName = propertyName;
            this.type = type;
            this.message = message;
        }

        public File getFile() {
            return file;
        }

        public String getPropertyName() {
            return propertyName;
        }

        public Type getType() {
            return type;
        }

        public String getMessage() {
            return message;
        }

        public String format() {
            return String.format("  [%s] in %s\n    Property: %s\n    %s", type, file.getName(), propertyName, message);
        }
    }
}
