package io.kaoto.forage.plugin.config;

import java.io.File;
import java.io.IOException;
import java.util.List;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;
import io.kaoto.forage.plugin.ForagePropertyScanner;

/**
 * Strategy that stores Forage configuration in per-factory properties files, named after the module's
 * {@code Config.name()} as recorded in the catalog (e.g. {@code forage-datasource-factory.properties}).
 *
 * <p>Files are discovered exactly like the runtime does (see
 * {@link ForagePropertyScanner#findForagePropertiesFiles(File, ForageCatalogReader)}): catalog-known file
 * names plus any {@code forage-*.properties} file, so modules not yet present in the catalog are still
 * found. The write side returns {@code null} for factory types unknown to the catalog, signalling the
 * caller to skip them.
 *
 * @see PropertiesFileStrategy
 */
final class ForageMultiFileStrategy implements PropertiesFileStrategy {

    private final ForageCatalogReader catalog;

    ForageMultiFileStrategy(ForageCatalogReader catalog) {
        this.catalog = catalog;
    }

    @Override
    public List<File> findPropertiesFiles(File directory) throws IOException {
        return ForagePropertyScanner.findForagePropertiesFiles(directory, catalog);
    }

    @Override
    public String getPropertiesFileName(String factoryTypeKey) {
        return catalog.getPropertiesFileName(factoryTypeKey)
                .filter(name -> !name.isEmpty())
                .orElse(null);
    }
}
