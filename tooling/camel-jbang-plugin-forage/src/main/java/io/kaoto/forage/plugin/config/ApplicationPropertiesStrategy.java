package io.kaoto.forage.plugin.config;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import io.kaoto.forage.plugin.ForagePropertyScanner;

/**
 * Strategy that stores all Forage configuration in a single {@code application.properties} file.
 *
 * <p>Every factory type reads from and writes to the same file in the top level of the directory. Deleting an
 * instance also removes it from every {@code application-<profile>.properties} file, which override
 * {@code application.properties} when their profile is active.
 *
 * @see PropertiesFileStrategy
 */
final class ApplicationPropertiesStrategy implements PropertiesFileStrategy {

    @Override
    public List<File> findPropertiesFiles(File directory) {
        File appProps = ForagePropertyScanner.loadApplicationProperties(directory);
        return appProps != null ? List.of(appProps) : List.of();
    }

    @Override
    public List<File> findDeletionFiles(File directory) throws IOException {
        List<File> files = new ArrayList<>(findPropertiesFiles(directory));
        files.addAll(ForagePropertyScanner.findProfilePropertiesFiles(directory));
        return files;
    }

    @Override
    public String getPropertiesFileName(String factoryTypeKey) {
        return ForagePropertyScanner.APPLICATION_PROPERTIES;
    }
}
