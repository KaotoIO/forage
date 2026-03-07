package io.kaoto.forage.catalog.reader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import io.kaoto.forage.catalog.model.ConditionalBeanGroup;
import io.kaoto.forage.catalog.model.ConfigEntry;
import io.kaoto.forage.catalog.model.FactoryVariant;
import io.kaoto.forage.catalog.model.FactoryVariants;
import io.kaoto.forage.catalog.model.FeatureBeans;
import io.kaoto.forage.catalog.model.ForageBean;
import io.kaoto.forage.catalog.model.ForageCatalog;
import io.kaoto.forage.catalog.model.ForageFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Helper class to load and query the Forage catalog.
 * The catalog contains metadata about factory configurations including the prefix property
 * that identifies instance names.
 */
public final class ForageCatalogReader {

    private static final String CATALOG_RESOURCE = "catalog/forage-catalog.json";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static volatile ForageCatalogReader instance;
    private final ForageCatalog catalog;
    private final Map<String, FactoryMetadata> factoryMetadataByKey;
    // Maps bean names (e.g., "ollama", "openai", "postgresql") to their parent factory type key
    private final Map<String, String> beanNameToFactoryKey;
    // Maps property prefixes (e.g., "google", "azure.openai") to their bean names
    private final Map<String, String> propertyPrefixToBeanName;
    // Maps bean names to their feature category (e.g., "ollama" -> "Chat Model", "infinispan" -> "Memory")
    private final Map<String, String> beanNameToFeature;
    // Maps factory type key to list of conditional bean groups
    private final Map<String, List<ConditionalBeanGroup>> conditionalBeansByFactory;
    // Maps factory type key to its variants (base, springboot, quarkus)
    private final Map<String, FactoryVariants> factoryVariantsByKey;
    // Maps bean name to its GAV coordinates (multiple beans can share a name across features)
    private final Map<String, Set<String>> beanNameToGav;

    private ForageCatalogReader(ForageCatalog catalog) {
        this.catalog = catalog;
        this.factoryMetadataByKey = new HashMap<>();
        this.beanNameToFactoryKey = new HashMap<>();
        this.propertyPrefixToBeanName = new HashMap<>();
        this.beanNameToFeature = new HashMap<>();
        this.conditionalBeansByFactory = new HashMap<>();
        this.factoryVariantsByKey = new HashMap<>();
        this.beanNameToGav = new HashMap<>();
        parseCatalog();
    }

    public static ForageCatalogReader getInstance() {
        if (instance == null) {
            synchronized (ForageCatalogReader.class) {
                if (instance == null) {
                    instance = loadCatalog();
                }
            }
        }
        return instance;
    }

    /**
     * Creates a ForageCatalogReader from the given input stream.
     * This is useful for testing or for loading catalogs from non-classpath sources.
     *
     * @param inputStream the input stream containing the catalog JSON
     * @return a new ForageCatalogReader instance
     * @throws IOException if the catalog cannot be read
     */
    public static ForageCatalogReader fromInputStream(InputStream inputStream) throws IOException {
        ForageCatalog catalogModel = OBJECT_MAPPER.readValue(inputStream, ForageCatalog.class);
        return new ForageCatalogReader(catalogModel);
    }

    private static ForageCatalogReader loadCatalog() {
        try (InputStream is = ForageCatalogReader.class.getClassLoader().getResourceAsStream(CATALOG_RESOURCE)) {
            if (is == null) {
                throw new IllegalStateException("Forage catalog not found: " + CATALOG_RESOURCE);
            }
            return fromInputStream(is);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load Forage catalog", e);
        }
    }

    private void parseCatalog() {
        List<ForageFactory> factories = catalog.getFactories();
        if (factories == null) {
            return;
        }

        for (ForageFactory factory : factories) {
            String factoryName = factory.getName();
            String factoryType = factory.getFactoryType();
            String propertiesFile = factory.getPropertiesFile();

            // Get config entries directly from the model
            List<ConfigEntry> configEntries = factory.getConfigEntries();
            String prefixPropertyName = null;
            String factoryTypeKey = null;

            if (configEntries != null) {
                for (ConfigEntry entry : configEntries) {
                    String name = entry.getName();
                    String type = entry.getType() != null ? entry.getType() : "string";

                    // The prefix property identifies the instance name
                    if ("prefix".equals(type)) {
                        prefixPropertyName = name;
                        // Extract factory type key from prefix property name
                        // e.g., "forage.jdbc.name" -> "jdbc"
                        factoryTypeKey = extractFactoryTypeKeyFromPropertyName(name);
                    }
                }
            }

            // If no prefix property found, try to derive the factory type key from any config entry
            if (factoryTypeKey == null && configEntries != null && !configEntries.isEmpty()) {
                factoryTypeKey = extractFactoryTypeKeyFromPropertyName(
                        configEntries.get(0).getName());
            }

            if (factoryTypeKey != null) {

                String description = factory.getDescription();
                List<String> components = factory.getComponents();
                boolean autowired = factory.isAutowired();

                FactoryMetadata metadata = new FactoryMetadata(
                        factoryName,
                        factoryType,
                        description,
                        components,
                        autowired,
                        propertiesFile,
                        prefixPropertyName,
                        factoryTypeKey,
                        configEntries);
                factoryMetadataByKey.put(factoryTypeKey, metadata);

                // Store factory variants
                FactoryVariants variants = factory.getVariants();
                if (variants != null) {
                    factoryVariantsByKey.put(factoryTypeKey, variants);
                }

                // Parse beansByFeature to map bean names to this factory type key
                List<FeatureBeans> beansByFeature = factory.getBeansByFeature();
                if (beansByFeature != null) {
                    for (FeatureBeans featureEntry : beansByFeature) {
                        String featureName = featureEntry.getFeature();
                        List<ForageBean> beans = featureEntry.getBeans();
                        if (beans != null) {
                            for (ForageBean bean : beans) {
                                String beanName = bean.getName();
                                if (beanName != null && !beanName.isEmpty()) {
                                    beanNameToFactoryKey.put(beanName.toLowerCase(), factoryTypeKey);
                                    beanNameToFeature.put(beanName.toLowerCase(), featureName);

                                    // Store bean GAV (multiple beans may share a name across features)
                                    String beanGav = bean.getGav();
                                    if (beanGav != null && !beanGav.isEmpty()) {
                                        beanNameToGav
                                                .computeIfAbsent(beanName.toLowerCase(), k -> new HashSet<>())
                                                .add(beanGav);
                                    }

                                    // Extract property prefix from bean's configEntries
                                    List<ConfigEntry> beanConfigEntries = bean.getConfigEntries();
                                    if (beanConfigEntries != null) {
                                        for (ConfigEntry configEntry : beanConfigEntries) {
                                            String configName = configEntry.getName();
                                            if (configName != null) {
                                                // Extract prefix from property name
                                                String prefix = extractPropertyPrefixFromConfigName(configName);
                                                if (prefix != null && !prefix.isEmpty()) {
                                                    propertyPrefixToBeanName.put(prefix.toLowerCase(), beanName);
                                                    break; // One prefix per bean is enough
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Store conditionalBeans directly from the model
                List<ConditionalBeanGroup> conditionalBeans = factory.getConditionalBeans();
                if (conditionalBeans != null && !conditionalBeans.isEmpty()) {
                    conditionalBeansByFactory.put(factoryTypeKey, conditionalBeans);
                }
            }
        }
    }

    /**
     * Extracts the factory type key from a property name.
     * Property names follow the pattern: forage.{factoryTypeKey}.{propertyName}
     * For example: "forage.jdbc.name" -> "jdbc", "forage.jms.kind" -> "jms"
     */
    private String extractFactoryTypeKeyFromPropertyName(String propertyName) {
        if (propertyName == null || !propertyName.startsWith("forage.")) {
            return null;
        }

        // Remove "forage." prefix
        String remaining = propertyName.substring("forage.".length());

        // The factory type key is the first segment
        int dotIndex = remaining.indexOf('.');
        if (dotIndex > 0) {
            return remaining.substring(0, dotIndex);
        }

        return null;
    }

    /**
     * Extracts the property prefix from a config entry name.
     * Property names follow the pattern: forage.{prefix}.{propertyName}
     * For example: "forage.google.api.key" -> "google", "forage.azure.openai.api.key" -> "azure.openai"
     */
    private String extractPropertyPrefixFromConfigName(String configName) {
        if (configName == null || !configName.startsWith("forage.")) {
            return null;
        }

        // Remove "forage." prefix
        String remaining = configName.substring("forage.".length());

        // The prefix is everything before the last two segments (assuming name.property pattern)
        // E.g., "google.api.key" -> "google", "azure.openai.api.key" -> "azure.openai"
        int lastDotIndex = remaining.lastIndexOf('.');
        if (lastDotIndex > 0) {
            String beforeLast = remaining.substring(0, lastDotIndex);
            // Check if there's another dot (meaning we have more than 2 segments)
            int secondLastDotIndex = beforeLast.lastIndexOf('.');
            if (secondLastDotIndex > 0) {
                // Return everything before the last segment of beforeLast
                // E.g., "google.api" -> "google", "azure.openai.api" -> "azure.openai"
                return beforeLast.substring(0, secondLastDotIndex);
            } else {
                // Only 2 segments after forage., the first segment is the prefix
                int firstDotIndex = remaining.indexOf('.');
                if (firstDotIndex > 0) {
                    return remaining.substring(0, firstDotIndex);
                }
            }
        }

        return null;
    }

    /**
     * Gets the factory metadata for a given factory type key.
     *
     * @param factoryTypeKey the factory type key (e.g., jdbc, jms, agent)
     * @return Optional containing the factory metadata if found
     */
    public Optional<FactoryMetadata> getFactoryMetadata(String factoryTypeKey) {
        return Optional.ofNullable(factoryMetadataByKey.get(factoryTypeKey.toLowerCase()));
    }

    /**
     * Gets all factory metadata.
     *
     * @return Collection of all factory metadata
     */
    public Collection<FactoryMetadata> getAllFactories() {
        return factoryMetadataByKey.values();
    }

    /**
     * Gets the name of the prefix property for a given factory type.
     * This is the property that contains the instance/bean name.
     *
     * @param factoryTypeKey the factory type key (e.g., jdbc, jms)
     * @return Optional containing the prefix property name if found
     */
    public Optional<String> getPrefixPropertyName(String factoryTypeKey) {
        return getFactoryMetadata(factoryTypeKey).map(FactoryMetadata::prefixPropertyName);
    }

    /**
     * Gets the properties file name for a given factory type.
     *
     * @param factoryTypeKey the factory type key (e.g., jdbc, jms)
     * @return Optional containing the properties file name if found
     */
    public Optional<String> getPropertiesFileName(String factoryTypeKey) {
        return getFactoryMetadata(factoryTypeKey).map(FactoryMetadata::propertiesFileName);
    }

    /**
     * Finds the factory type key that matches a given input property key pattern.
     * Handles both formats:
     * - With forage prefix: "forage.jdbc.url" -> "jdbc"
     * - Without forage prefix: "jdbc.url" -> "jdbc"
     *
     * @param inputPropertyKey the input property key (with or without forage prefix)
     * @return Optional containing the matching factory type key
     */
    public Optional<String> findFactoryTypeKeyForInputProperty(String inputPropertyKey) {
        if (inputPropertyKey == null) {
            return Optional.empty();
        }

        String keyToProcess = inputPropertyKey;

        // Strip "forage." prefix if present
        if (keyToProcess.startsWith("forage.")) {
            keyToProcess = keyToProcess.substring("forage.".length());
        }

        int dotIndex = keyToProcess.indexOf('.');
        if (dotIndex <= 0) {
            return Optional.empty();
        }

        String potentialKey = keyToProcess.substring(0, dotIndex);
        if (factoryMetadataByKey.containsKey(potentialKey)) {
            return Optional.of(potentialKey);
        }

        return Optional.empty();
    }

    /**
     * Normalizes an input property key by stripping the "forage." prefix if present.
     *
     * @param inputPropertyKey the input property key
     * @return the normalized key without "forage." prefix
     */
    public String normalizePropertyKey(String inputPropertyKey) {
        if (inputPropertyKey != null && inputPropertyKey.startsWith("forage.")) {
            return inputPropertyKey.substring("forage.".length());
        }
        return inputPropertyKey;
    }

    /**
     * Finds the factory type key for a given bean name (kind).
     * Bean names are defined in beansByFeature sections of the catalog.
     * For example, "ollama", "openai", "postgresql", "artemis" are bean names.
     *
     * @param beanName the bean name (e.g., "ollama", "postgresql", "redis")
     * @return Optional containing the factory type key if found
     */
    public Optional<String> findFactoryTypeKeyForBeanName(String beanName) {
        if (beanName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(beanNameToFactoryKey.get(beanName.toLowerCase()));
    }

    /**
     * Finds the bean name (kind) for a given property prefix.
     * Property prefixes are extracted from bean configEntries (e.g., "google" from "forage.google.api.key").
     *
     * @param propertyPrefix the property prefix (e.g., "google", "azure.openai")
     * @return Optional containing the bean name if found
     */
    public Optional<String> findBeanNameForPropertyPrefix(String propertyPrefix) {
        if (propertyPrefix == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(propertyPrefixToBeanName.get(propertyPrefix.toLowerCase()));
    }

    /**
     * Finds the factory type key for a given property prefix.
     * First looks up the bean name from the prefix, then finds the factory type key.
     *
     * @param propertyPrefix the property prefix (e.g., "google", "ollama")
     * @return Optional containing the factory type key if found
     */
    public Optional<String> findFactoryTypeKeyForPropertyPrefix(String propertyPrefix) {
        Optional<String> beanNameOpt = findBeanNameForPropertyPrefix(propertyPrefix);
        return beanNameOpt.flatMap(this::findFactoryTypeKeyForBeanName);
    }

    /**
     * Gets the feature category for a given bean name.
     * Feature categories are defined in beansByFeature (e.g., "Chat Model", "Memory").
     *
     * @param beanName the bean name (e.g., "ollama", "infinispan")
     * @return Optional containing the feature category if found
     */
    public Optional<String> getBeanFeature(String beanName) {
        if (beanName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(beanNameToFeature.get(beanName.toLowerCase()));
    }

    /**
     * Gets the conditional bean groups for a given factory type key.
     *
     * @param factoryTypeKey the factory type key (e.g., "jdbc", "jms")
     * @return List of conditional bean groups (empty if none)
     */
    public List<ConditionalBeanGroup> getConditionalBeans(String factoryTypeKey) {
        if (factoryTypeKey == null) {
            return List.of();
        }
        return conditionalBeansByFactory.getOrDefault(factoryTypeKey.toLowerCase(), List.of());
    }

    /**
     * Gets the factory variants for a given factory type key.
     *
     * @param factoryTypeKey the factory type key (e.g., "jdbc", "jms")
     * @return FactoryVariants containing the platform variants (null if none)
     */
    public FactoryVariants getFactoryVariants(String factoryTypeKey) {
        if (factoryTypeKey == null) {
            return null;
        }
        return factoryVariantsByKey.get(factoryTypeKey.toLowerCase());
    }

    /**
     * Gets the GAV coordinate for a specific variant of a factory.
     *
     * @param factoryTypeKey the factory type key (e.g., "jdbc", "jms")
     * @param variantName the variant name (e.g., "base", "springboot", "quarkus")
     * @return Optional containing the GAV if found
     */
    public Optional<String> getFactoryVariantGav(String factoryTypeKey, String variantName) {
        FactoryVariants variants = getFactoryVariants(factoryTypeKey);
        if (variants == null) {
            return Optional.empty();
        }
        FactoryVariant variant = getVariantByName(variants, variantName);
        if (variant != null) {
            return Optional.ofNullable(variant.getGav());
        }
        return Optional.empty();
    }

    /**
     * Gets a specific variant from FactoryVariants by name.
     */
    private FactoryVariant getVariantByName(FactoryVariants variants, String variantName) {
        if (variantName == null) {
            return null;
        }
        return switch (variantName.toLowerCase()) {
            case "base" -> variants.getBase();
            case "springboot" -> variants.getSpringboot();
            case "quarkus" -> variants.getQuarkus();
            default -> null;
        };
    }

    /**
     * Gets all GAV coordinates for a bean by its name.
     * Multiple beans can share a name across different features (e.g., "ollama" exists
     * as both a Chat Model and an Embeddings Model).
     *
     * @param beanName the bean name (e.g., "postgresql", "artemis", "ollama")
     * @return a collection of matching GAVs, or an empty collection if none found
     */
    public Collection<String> getBeanGavs(String beanName) {
        if (beanName == null) {
            return Collections.emptySet();
        }
        return beanNameToGav.getOrDefault(beanName.toLowerCase(), Collections.emptySet());
    }

    /**
     * Gets the additional dependencies for a specific variant of a factory.
     *
     * @param factoryTypeKey the factory type key (e.g., "jdbc", "jms")
     * @param variantName the variant name (e.g., "base", "springboot", "quarkus")
     * @return list of additional GAVs, or empty list if none
     */
    public List<String> getVariantAdditionalDependencies(String factoryTypeKey, String variantName) {
        FactoryVariants variants = getFactoryVariants(factoryTypeKey);
        if (variants == null) {
            return List.of();
        }
        FactoryVariant variant = getVariantByName(variants, variantName);
        if (variant == null || variant.getAdditionalDependencies() == null) {
            return List.of();
        }
        return variant.getAdditionalDependencies();
    }

    /**
     * Gets runtime dependencies for a bean in a specific variant.
     *
     * @param beanName the bean name (e.g., "postgresql", "artemis")
     * @param variantName the variant name (e.g., "quarkus", "springboot")
     * @return list of GAVs for the given variant, or empty list if none
     */
    public List<String> getBeanRuntimeDependencies(String beanName, String variantName) {
        if (beanName == null || variantName == null) {
            return List.of();
        }

        // Look through all factories' beansByFeature to find the bean
        for (ForageFactory factory : catalog.getFactories()) {
            List<FeatureBeans> beansByFeature = factory.getBeansByFeature();
            if (beansByFeature == null) {
                continue;
            }
            for (FeatureBeans featureBeans : beansByFeature) {
                if (featureBeans.getBeans() == null) {
                    continue;
                }
                for (ForageBean bean : featureBeans.getBeans()) {
                    if (beanName.equalsIgnoreCase(bean.getName()) && bean.getRuntimeDependencies() != null) {
                        List<String> deps = bean.getRuntimeDependencies().get(variantName.toLowerCase());
                        if (deps != null) {
                            return deps;
                        }
                    }
                }
            }
        }
        return List.of();
    }

    /**
     * Gets runtime dependencies for a conditional bean group in a specific variant.
     *
     * @param factoryTypeKey the factory type key (e.g., "jdbc", "jms")
     * @param groupId the conditional bean group ID (e.g., "jta-transaction-policies")
     * @param variantName the variant name (e.g., "quarkus", "springboot")
     * @return list of GAVs for the given variant, or empty list if none
     */
    public List<String> getConditionalRuntimeDependencies(String factoryTypeKey, String groupId, String variantName) {
        List<ConditionalBeanGroup> groups = getConditionalBeans(factoryTypeKey);
        for (ConditionalBeanGroup group : groups) {
            if (groupId.equals(group.getId()) && group.getRuntimeDependencies() != null) {
                List<String> deps = group.getRuntimeDependencies().get(variantName.toLowerCase());
                if (deps != null) {
                    return deps;
                }
            }
        }
        return List.of();
    }

    /**
     * Metadata about a factory configuration.
     */
    public record FactoryMetadata(
            String factoryName,
            String factoryType,
            String description,
            List<String> components,
            boolean autowired,
            String propertiesFileName,
            String prefixPropertyName,
            String factoryTypeKey,
            List<ConfigEntry> configEntries) {

        /**
         * Gets the short name property key (without forage prefix) for the instance name.
         * For example, if prefixPropertyName is "forage.jdbc.name", returns "jdbc.name".
         */
        public Optional<String> getShortPrefixPropertyKey() {
            if (prefixPropertyName == null || !prefixPropertyName.startsWith("forage.")) {
                return Optional.empty();
            }
            return Optional.of(prefixPropertyName.substring("forage.".length()));
        }
    }
}
