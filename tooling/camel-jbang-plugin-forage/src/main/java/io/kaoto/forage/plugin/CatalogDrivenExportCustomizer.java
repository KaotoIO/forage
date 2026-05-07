package io.kaoto.forage.plugin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.catalog.model.ConditionalBeanGroup;
import io.kaoto.forage.catalog.model.ConfigEntry;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;
import io.kaoto.forage.core.common.ExportCustomizer;
import io.kaoto.forage.core.common.RuntimeType;

/**
 * Generic, catalog-driven export customizer that replaces all per-module customizers.
 * Uses the Forage catalog metadata to resolve runtime dependencies for any factory type.
 */
public class CatalogDrivenExportCustomizer implements ExportCustomizer {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogDrivenExportCustomizer.class);

    private Map<String, Map<String, List<String>>> scannedProperties;
    private Boolean enabled;

    @Override
    public boolean isEnabled() {
        if (enabled == null) {
            enabled = !getScannedProperties().isEmpty();
        }
        return enabled;
    }

    @Override
    public Set<String> resolveRuntimeDependencies(RuntimeType runtime) {
        Set<String> dependencies = new LinkedHashSet<>();
        ForageCatalogReader catalog = ForageCatalogReader.getInstance();
        Map<String, Map<String, List<String>>> properties = getScannedProperties();

        String variantName = mapRuntimeToVariant(runtime);

        for (String factoryTypeKey : properties.keySet()) {
            Map<String, List<String>> factoryProperties = properties.get(factoryTypeKey);

            // 1. Add the factory variant GAV (or fall back to base if variant doesn't exist)
            var variantGav = catalog.getFactoryVariantGav(factoryTypeKey, variantName);
            if (variantGav.isPresent()) {
                dependencies.add(toMvnGav(variantGav.get()));
            } else {
                // No variant-specific module; use base
                catalog.getFactoryVariantGav(factoryTypeKey, "base")
                        .map(CatalogDrivenExportCustomizer::toMvnGav)
                        .ifPresent(dependencies::add);
            }

            // 2. Add variant additional dependencies (e.g., camel-quarkus-sql)
            List<String> additionalDeps = catalog.getVariantAdditionalDependencies(factoryTypeKey, variantName);
            additionalDeps.stream().map(CatalogDrivenExportCustomizer::toMvnGav).forEach(dependencies::add);

            // 3. Find bean-name config entries and resolve bean GAVs
            catalog.getFactoryMetadata(factoryTypeKey).ifPresent(metadata -> {
                if (metadata.configEntries() != null) {
                    for (ConfigEntry entry : metadata.configEntries()) {
                        if ("bean-name".equals(entry.getType())) {
                            // Extract the property suffix (e.g., "db.kind" from "forage.jdbc.db.kind")
                            String propSuffix = extractPropertySuffix(entry.getName(), factoryTypeKey);
                            if (propSuffix != null) {
                                // Collect ALL bean kind values across all instances
                                Set<String> beanKinds = findAllValues(factoryProperties, propSuffix);
                                if (beanKinds.isEmpty()
                                        && entry.getDefaultValue() != null
                                        && !entry.getDefaultValue().isEmpty()) {
                                    beanKinds = Set.of(entry.getDefaultValue());
                                }
                                for (String kind : beanKinds) {
                                    // Add bean GAVs
                                    Collection<String> beanGavs = catalog.getBeanGavs(kind);
                                    beanGavs.stream()
                                            .map(CatalogDrivenExportCustomizer::toMvnGav)
                                            .forEach(dependencies::add);

                                    // Add bean runtime dependencies for this variant
                                    List<String> beanDeps = catalog.getBeanRuntimeDependencies(kind, variantName);
                                    beanDeps.stream()
                                            .map(CatalogDrivenExportCustomizer::toMvnGav)
                                            .forEach(dependencies::add);
                                }
                            }
                        }
                    }
                }
            });

            // 4. Check conditional beans for runtime dependencies
            List<ConditionalBeanGroup> conditionalGroups = catalog.getConditionalBeans(factoryTypeKey);
            for (ConditionalBeanGroup group : conditionalGroups) {
                if (isConditionMet(group.getConfigEntry(), factoryProperties)) {
                    List<String> conditionalDeps =
                            catalog.getConditionalRuntimeDependencies(factoryTypeKey, group.getId(), variantName);
                    conditionalDeps.stream()
                            .map(CatalogDrivenExportCustomizer::toMvnGav)
                            .forEach(dependencies::add);
                }
            }
        }

        LOG.debug("Resolved {} dependencies for runtime {}", dependencies.size(), runtime);
        dependencies.forEach(dep -> LOG.debug("  Dependency: {}", dep));

        return dependencies;
    }

    private Map<String, Map<String, List<String>>> getScannedProperties() {
        if (scannedProperties == null) {
            try {
                String configDir = System.getProperty("forage.config.dir");
                File workingDir = configDir != null ? new File(configDir) : new File(System.getProperty("user.dir"));
                ForageCatalogReader catalog = ForageCatalogReader.getInstance();
                scannedProperties = ForagePropertyScanner.scanProperties(workingDir, catalog);
            } catch (IOException e) {
                LOG.warn("Failed to scan for forage properties: {}", e.getMessage());
                scannedProperties = Map.of();
            }
        }
        return scannedProperties;
    }

    private static String mapRuntimeToVariant(RuntimeType runtime) {
        return switch (runtime) {
            case main -> "base";
            case springBoot -> "springboot";
            case quarkus -> "quarkus";
        };
    }

    /**
     * Ensures a GAV string has the "mvn:" prefix required by Camel JBang.
     * Catalog GAVs are stored as "groupId:artifactId:version", but Camel JBang
     * expects "mvn:groupId:artifactId:version".
     */
    private static String toMvnGav(String gav) {
        if (gav == null) {
            return null;
        }
        return gav.startsWith("mvn:") ? gav : "mvn:" + gav;
    }

    /**
     * Extracts the property suffix from a full config entry name.
     * E.g., "forage.jdbc.db.kind" with factoryTypeKey "jdbc" -> "db.kind"
     */
    private static String extractPropertySuffix(String entryName, String factoryTypeKey) {
        if (entryName == null) {
            return null;
        }
        // Try with the factoryTypeKey first (e.g., "forage.jdbc." for jdbc factory)
        String prefix = "forage." + factoryTypeKey + ".";
        if (entryName.startsWith(prefix)) {
            return entryName.substring(prefix.length());
        }
        // Fallback: extract the suffix using the entry's own prefix segment
        // This handles cases where the factoryTypeKey differs from the config entry prefix
        // (e.g., agent factory has key "multi" but bean-name entries use "forage.agent.*")
        if (entryName.startsWith("forage.")) {
            String remaining = entryName.substring("forage.".length());
            int dotIndex = remaining.indexOf('.');
            if (dotIndex > 0) {
                return remaining.substring(dotIndex + 1);
            }
        }
        return null;
    }

    /**
     * Finds all unique values for properties matching the given suffix.
     * Collects values from all named instances (e.g., both "mysql" and "postgresql"
     * from ds1.jdbc.db.kind=mysql and ds2.jdbc.db.kind=postgresql).
     */
    private static Set<String> findAllValues(Map<String, List<String>> factoryProperties, String propSuffix) {
        Set<String> values = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> entry : factoryProperties.entrySet()) {
            String key = entry.getKey();
            // Match direct property or property within a named instance
            if (key.equals(propSuffix) || key.endsWith("." + propSuffix)) {
                for (String value : entry.getValue()) {
                    if (value != null && !value.isEmpty()) {
                        values.add(value);
                    }
                }
            }
        }
        return values;
    }

    /**
     * Checks if a conditional config entry is set to a truthy value in any instance.
     * The configEntry is the full property name (e.g., "forage.jdbc.transaction.enabled").
     */
    private static boolean isConditionMet(String configEntry, Map<String, List<String>> factoryProperties) {
        if (configEntry == null) {
            return false;
        }

        for (Map.Entry<String, List<String>> entry : factoryProperties.entrySet()) {
            String key = entry.getKey();
            if (configEntry.endsWith("." + key) || configEntry.equals("forage." + key)) {
                for (String value : entry.getValue()) {
                    if ("true".equalsIgnoreCase(value)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
