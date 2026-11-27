package io.kaoto.forage.catalog.reader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import io.kaoto.forage.catalog.model.ConfigEntry;
import io.kaoto.forage.catalog.model.ConfigurationModule;
import io.kaoto.forage.catalog.model.ForageConfigurationCatalog;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Helper class to load and query the Forage configuration catalog.
 * The configuration catalog contains metadata about all configuration entries
 * across all modules, unlike the factory catalog which only includes configs
 * associated with factories.
 */
public final class ForageConfigurationCatalogReader {

    private static final String CATALOG_RESOURCE = "catalog/forage-configuration-catalog.json";
    // a catalog of a later Forage version may add fields
    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static volatile ForageConfigurationCatalogReader instance;
    private final ForageConfigurationCatalog catalog;
    // Maps config name (e.g., "forage.openai.api.key") to its ConfigEntry
    private final Map<String, ConfigEntry> configNameToEntry;
    // Maps artifactId (e.g., "forage-model-openai") to its ConfigurationModule
    private final Map<String, ConfigurationModule> artifactIdToModule;

    private ForageConfigurationCatalogReader(ForageConfigurationCatalog catalog) {
        this.catalog = catalog;
        this.configNameToEntry = new HashMap<>();
        this.artifactIdToModule = new HashMap<>();
        buildIndexes();
    }

    public static ForageConfigurationCatalogReader getInstance() {
        if (instance == null) {
            synchronized (ForageConfigurationCatalogReader.class) {
                if (instance == null) {
                    instance = loadCatalog();
                }
            }
        }
        return instance;
    }

    /**
     * Creates a ForageConfigurationCatalogReader from the given input stream.
     * This is useful for testing or for loading catalogs from non-classpath sources.
     *
     * @param inputStream the input stream containing the configuration catalog JSON
     * @return a new ForageConfigurationCatalogReader instance
     * @throws IOException if the catalog cannot be read
     */
    public static ForageConfigurationCatalogReader fromInputStream(InputStream inputStream) throws IOException {
        ForageConfigurationCatalog catalogModel =
                OBJECT_MAPPER.readValue(inputStream, ForageConfigurationCatalog.class);
        return new ForageConfigurationCatalogReader(catalogModel);
    }

    private static ForageConfigurationCatalogReader loadCatalog() {
        try (InputStream is =
                ForageConfigurationCatalogReader.class.getClassLoader().getResourceAsStream(CATALOG_RESOURCE)) {
            if (is == null) {
                throw new IllegalStateException("Forage configuration catalog not found: " + CATALOG_RESOURCE);
            }
            return fromInputStream(is);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load Forage configuration catalog", e);
        }
    }

    private void buildIndexes() {
        List<ConfigurationModule> modules = catalog.getModules();
        if (modules == null) {
            return;
        }

        for (ConfigurationModule module : modules) {
            String artifactId = module.getArtifactId();
            if (artifactId != null) {
                artifactIdToModule.put(artifactId, module);
            }

            List<ConfigEntry> entries = module.getConfigEntries();
            if (entries != null) {
                for (ConfigEntry entry : entries) {
                    String name = entry.getName();
                    if (name != null) {
                        configNameToEntry.put(name, entry);
                    }
                }
            }
        }
    }

    /**
     * Checks if the given configuration name is a valid (known) configuration entry.
     *
     * @param name the configuration name (e.g., "forage.openai.api.key")
     * @return true if the config name exists in the catalog
     */
    public boolean isValidConfig(String name) {
        if (name == null) {
            return false;
        }
        return configNameToEntry.containsKey(name);
    }

    /**
     * Gets the configuration entry for the given name.
     *
     * @param name the configuration name (e.g., "forage.openai.api.key")
     * @return Optional containing the ConfigEntry if found
     */
    public Optional<ConfigEntry> getConfigEntry(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(configNameToEntry.get(name));
    }

    /**
     * Gets the configuration module for the given artifact ID.
     *
     * @param artifactId the Maven artifact ID (e.g., "forage-model-openai")
     * @return Optional containing the ConfigurationModule if found
     */
    public Optional<ConfigurationModule> getModule(String artifactId) {
        if (artifactId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(artifactIdToModule.get(artifactId));
    }

    /**
     * Gets all configuration modules in the catalog.
     *
     * @return unmodifiable list of all configuration modules
     */
    public List<ConfigurationModule> getAllModules() {
        List<ConfigurationModule> modules = catalog.getModules();
        if (modules == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(modules);
    }

    /**
     * Gets the raw catalog model.
     *
     * @return the ForageConfigurationCatalog model
     */
    public ForageConfigurationCatalog getCatalog() {
        return catalog;
    }
}
