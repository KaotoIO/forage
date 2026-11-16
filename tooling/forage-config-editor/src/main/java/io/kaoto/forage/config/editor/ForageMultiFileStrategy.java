package io.kaoto.forage.config.editor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import io.kaoto.forage.catalog.model.ForageBean;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;

/**
 * Strategy that stores Forage configuration in per-factory properties files, named after the module's
 * {@code Config.name()} as recorded in the catalog (e.g. {@code forage-datasource-factory.properties}).
 *
 * <p>Files are discovered with
 * {@link ForageConfigurationFiles#forageProperties(File, ForageCatalogReader)}: the catalog-known file
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
        return ForageConfigurationFiles.forageProperties(directory, catalog);
    }

    /**
     * {@code application.properties} and the {@code application-<profile>.properties} files, which every runtime
     * loads next to the per-module files.
     */
    @Override
    public List<File> findOverrideFiles(File directory) throws IOException {
        List<File> files = new ArrayList<>();
        File applicationProperties = ForageConfigurationFiles.applicationProperties(directory);
        if (applicationProperties != null) {
            files.add(applicationProperties);
        }
        files.addAll(ForageConfigurationFiles.profileProperties(directory));
        return files;
    }

    /**
     * The bean's own properties file when the catalog records one (e.g. {@code forage-jms-artemis.properties}), since
     * on plain Camel and Quarkus a bean only reads its own file and {@code application.properties}; the factory's
     * file otherwise.
     */
    @Override
    public String getPropertiesFileName(String factoryTypeKey, String beanName) {
        if (beanName != null) {
            Optional<String> beanFile = findBeanPropertiesFile(factoryTypeKey, beanName);
            if (beanFile.isPresent()) {
                return beanFile.get();
            }
        }
        return getPropertiesFileName(factoryTypeKey);
    }

    /**
     * The properties file the catalog records for a bean of a factory, if any.
     */
    Optional<String> findBeanPropertiesFile(String factoryTypeKey, String beanName) {
        return catalog.getAllBeansForFactory(factoryTypeKey).stream()
                .filter(bean -> beanName.equals(bean.getName()))
                .map(ForageBean::getPropertiesFile)
                .filter(file -> file != null && !file.isEmpty())
                .findFirst();
    }

    @Override
    public String getPropertiesFileName(String factoryTypeKey) {
        return catalog.getPropertiesFileName(factoryTypeKey)
                .filter(name -> !name.isEmpty())
                .orElse(null);
    }
}
