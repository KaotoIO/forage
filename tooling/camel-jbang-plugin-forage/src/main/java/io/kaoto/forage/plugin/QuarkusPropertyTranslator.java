package io.kaoto.forage.plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;
import io.kaoto.forage.core.common.ForageModuleDescriptor;
import io.kaoto.forage.core.util.config.Config;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.plugin.ForagePropertyScanner.PropertyOccurrence;

/**
 * Translates forage properties to Quarkus-native properties at export time.
 * Uses existing {@link ForageModuleDescriptor#translatePropertiesForExport} logic
 * by populating {@link ConfigStore} with scanned property values.
 */
public final class QuarkusPropertyTranslator {

    private static final Logger LOG = LoggerFactory.getLogger(QuarkusPropertyTranslator.class);

    private QuarkusPropertyTranslator() {}

    public record TranslationGroup(
            String moduleType, String prefix, Map<String, String> sourceProperties, Map<String, String> properties) {}

    public record TranslationResult(List<TranslationGroup> groups, Set<String> translatedForageKeys) {

        public boolean isEmpty() {
            return groups.isEmpty();
        }

        public int propertyCount() {
            return groups.stream().mapToInt(g -> g.properties().size()).sum();
        }
    }

    /**
     * Translates forage properties found in the working directory to Quarkus-native properties.
     *
     * @param workingDir the directory containing route and properties files
     * @return translation result with quarkus properties and the forage keys that were translated
     */
    @SuppressWarnings("rawtypes")
    public static TranslationResult translate(File workingDir) throws IOException {
        // 1. Scan working directory for forage.* properties across all files
        ForageCatalogReader catalog = ForageCatalogReader.getInstance();
        Map<String, Map<String, List<PropertyOccurrence>>> scanned =
                ForagePropertyScanner.scanPropertiesWithFileTracking(workingDir, catalog, false);

        // 2. Discover available module descriptors (jdbc, agent, jms, ...) via ServiceLoader
        Map<String, ForageModuleDescriptor> descriptors = discoverDescriptors();

        // 3. For each module type, group its properties by named prefix and translate
        List<TranslationGroup> groups = new ArrayList<>();
        Set<String> translatedForageKeys = new LinkedHashSet<>();

        for (Map.Entry<String, Map<String, List<PropertyOccurrence>>> factoryEntry : scanned.entrySet()) {
            String factoryTypeKey = factoryEntry.getKey();

            ForageModuleDescriptor descriptor = descriptors.get(factoryTypeKey);
            if (descriptor == null) {
                LOG.warn(
                        "No ForageModuleDescriptor for factory type '{}' — properties will not be translated to Quarkus format",
                        factoryTypeKey);
                continue;
            }

            // Group by named prefix, e.g. "ds1" for forage.ds1.jdbc.*, null for forage.jdbc.*
            Map<String, Map<String, String>> byPrefix =
                    groupByPrefix(factoryEntry.getValue(), descriptor.modulePrefix());

            for (Map.Entry<String, Map<String, String>> prefixEntry : byPrefix.entrySet()) {
                String prefix = prefixEntry.getKey();
                Map<String, String> props = prefixEntry.getValue();

                TranslationGroup group = translatePrefixGroup(descriptor, factoryTypeKey, prefix, props);
                if (group != null) {
                    groups.add(group);
                    translatedForageKeys.addAll(props.keySet());
                }
            }
        }

        return new TranslationResult(groups, translatedForageKeys);
    }

    /**
     * Translates a single prefix group's forage properties to Quarkus-native format.
     * Returns {@code null} if translation produces no output or fails.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static TranslationGroup translatePrefixGroup(
            ForageModuleDescriptor descriptor, String factoryTypeKey, String prefix, Map<String, String> props) {
        try {
            Config config = descriptor.createConfig(prefix);
            props.forEach(config::register);

            Map<String, String> translated = descriptor.translatePropertiesForExport(prefix, config);
            if (translated.isEmpty()) {
                return null;
            }
            return new TranslationGroup(descriptor.modulePrefix(), prefix, props, translated);
        } catch (Exception e) {
            LOG.warn(
                    "Failed to translate properties for module '{}' prefix '{}': {}",
                    factoryTypeKey,
                    prefix,
                    e.getMessage());
            LOG.debug("Translation error details:", e);
            return null;
        }
    }

    @SuppressWarnings("rawtypes")
    private static Map<String, ForageModuleDescriptor> discoverDescriptors() {
        Map<String, ForageModuleDescriptor> result = new HashMap<>();
        try {
            ServiceLoader<ForageModuleDescriptor> loader =
                    ServiceLoader.load(ForageModuleDescriptor.class, QuarkusPropertyTranslator.class.getClassLoader());
            for (ForageModuleDescriptor descriptor : loader) {
                String prefix = descriptor.modulePrefix();
                result.put(prefix, descriptor);
                LOG.debug(
                        "Discovered ForageModuleDescriptor: {} → {}",
                        prefix,
                        descriptor.getClass().getName());
            }
        } catch (Exception e) {
            LOG.warn("Error discovering ForageModuleDescriptors: {}", e.getMessage());
            LOG.debug("ServiceLoader error details:", e);
        }
        return result;
    }

    /**
     * Groups scanned properties by their named prefix.
     *
     * <p>For {@code "forage.ds1.jdbc.url=..."} with modulePrefix {@code "jdbc"},
     * the prefix is {@code "ds1"} and the full key {@code "forage.ds1.jdbc.url"} is preserved.
     *
     * <p>For {@code "forage.jdbc.url=..."} the prefix is {@code null}.
     *
     * @return map of prefix (nullable) → (fullPropertyName → value)
     */
    private static Map<String, Map<String, String>> groupByPrefix(
            Map<String, List<PropertyOccurrence>> factoryProperties, String modulePrefix) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();

        for (List<PropertyOccurrence> occurrences : factoryProperties.values()) {
            for (PropertyOccurrence occ : occurrences) {
                String prefix = extractNamedPrefix(occ.fullPropertyName(), modulePrefix);
                result.computeIfAbsent(prefix, k -> new LinkedHashMap<>()).put(occ.fullPropertyName(), occ.value());
            }
        }

        return result;
    }

    /**
     * Extracts the named prefix from a full forage property name.
     *
     * <p>Examples with modulePrefix {@code "jdbc"}:
     * <ul>
     *   <li>{@code "forage.jdbc.url"} → {@code null} (default instance)</li>
     *   <li>{@code "forage.ds1.jdbc.url"} → {@code "ds1"}</li>
     * </ul>
     */
    // Property format: forage.[<namedPrefix>.]<modulePrefix>.<property>
    // e.g., forage.jdbc.url → null (default), forage.ds1.jdbc.url → "ds1"
    static String extractNamedPrefix(String fullPropertyName, String modulePrefix) {
        if (!fullPropertyName.startsWith("forage.")) {
            return null;
        }
        String afterForage = fullPropertyName.substring("forage.".length());

        // Default instance: starts directly with modulePrefix (e.g. "jdbc.url")
        if (afterForage.startsWith(modulePrefix + ".") || afterForage.equals(modulePrefix)) {
            return null;
        }

        // Named instance: first segment is the prefix (e.g. "ds1" in "ds1.jdbc.url")
        int firstDot = afterForage.indexOf('.');
        if (firstDot > 0) {
            String remaining = afterForage.substring(firstDot + 1);
            if (remaining.startsWith(modulePrefix + ".") || remaining.equals(modulePrefix)) {
                return afterForage.substring(0, firstDot);
            }
        }

        return null;
    }
}
