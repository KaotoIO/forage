package io.kaoto.forage.plugin.config;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;

/**
 * Strategy that stores Forage configuration in per-factory {@code forage-{factoryType}.properties}
 * files resolved from the catalog.
 *
 * <p>The read side also accepts any {@code forage-*.properties} file via the wildcard matcher
 * so that modules not yet present in the catalog are still discovered. The write side returns
 * {@code null} for factory types unknown to the catalog, signalling the caller to skip them.
 *
 * @see PropertiesFileStrategy
 */
final class ForageMultiFileStrategy implements PropertiesFileStrategy {

    private static final String FORAGE_PREFIX = "forage-";
    private static final String PROPERTIES_SUFFIX = ".properties";

    private final ForageCatalogReader catalog;

    ForageMultiFileStrategy(ForageCatalogReader catalog) {
        this.catalog = catalog;
    }

    @Override
    public Set<String> getTargetFileNames() {
        Set<String> targetFileNames = new HashSet<>();
        for (ForageCatalogReader.FactoryMetadata metadata : catalog.getAllFactories()) {
            String propsFile = metadata.propertiesFileName();
            if (propsFile != null && !propsFile.isEmpty()) {
                targetFileNames.add(propsFile);
            }
        }
        return targetFileNames;
    }

    @Override
    public boolean matchesWildcard(String fileName) {
        return fileName.startsWith(FORAGE_PREFIX) && fileName.endsWith(PROPERTIES_SUFFIX);
    }

    @Override
    public String getPropertiesFileName(String factoryTypeKey) {
        return catalog.getPropertiesFileName(factoryTypeKey).orElse(null);
    }

    @Override
    public Set<File> getScanableProperties(File directory) {
        Set<File> propertiesFiles = new HashSet<>();
        for (ForageCatalogReader.FactoryMetadata metadata : catalog.getAllFactories()) {
            String propertiesFileName = getPropertiesFileName(metadata.factoryTypeKey());
            if (propertiesFileName != null) {
                File propertiesFile = new File(directory, propertiesFileName);
                if (propertiesFile.exists()) {
                    propertiesFiles.add(propertiesFile);
                }
            }
        }
        return propertiesFiles;
    }
}
