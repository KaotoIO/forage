package io.kaoto.forage.config.editor;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Strategy that stores all Forage configuration in a single {@code application.properties} file.
 *
 * <p>Every factory type reads from and writes to the same file in the top level of the directory. The
 * {@code application-<profile>.properties} files override it when their profile is active.
 *
 * @see PropertiesFileStrategy
 */
final class ApplicationPropertiesStrategy implements PropertiesFileStrategy {

    @Override
    public List<File> findPropertiesFiles(File directory) {
        File appProps = ForageConfigurationFiles.applicationProperties(directory);
        return appProps != null ? List.of(appProps) : List.of();
    }

    @Override
    public List<File> findOverrideFiles(File directory) throws IOException {
        return ForageConfigurationFiles.profileProperties(directory);
    }

    @Override
    public String getPropertiesFileName(String factoryTypeKey) {
        return ForageConfigurationFiles.APPLICATION_PROPERTIES;
    }
}
