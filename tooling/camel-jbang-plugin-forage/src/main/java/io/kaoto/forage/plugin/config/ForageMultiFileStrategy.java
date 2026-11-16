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
 * <p>Files are discovered with
 * {@link ForagePropertyScanner#findForagePropertiesFiles(File, ForageCatalogReader)}: the catalog-known file
 * names plus any {@code forage-*.properties} file. Plain Camel and Quarkus only load the catalog-known names
 * at runtime; the {@code forage-*.properties} wildcard is only loaded on Spring Boot.
 *
 * <p>Some catalog factories (e.g. Security Policy, WebSearch) have no dedicated properties file; for those
 * {@link #getPropertiesFileName(String)} returns {@code null} and the caller reports an error.
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
