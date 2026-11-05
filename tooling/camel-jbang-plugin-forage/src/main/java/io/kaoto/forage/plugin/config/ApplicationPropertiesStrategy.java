package io.kaoto.forage.plugin.config;

import java.io.File;
import java.util.List;

/**
 * Strategy that stores all Forage configuration in a single {@code application.properties} file.
 *
 * <p>Every factory type reads from and writes to the same file in the top level of the directory.
 *
 * @see PropertiesFileStrategy
 */
final class ApplicationPropertiesStrategy implements PropertiesFileStrategy {

    static final String APPLICATION_PROPERTIES = "application.properties";

    @Override
    public List<File> findPropertiesFiles(File directory) {
        File appProps = new File(directory, APPLICATION_PROPERTIES);
        return appProps.isFile() ? List.of(appProps) : List.of();
    }

    @Override
    public String getPropertiesFileName(String factoryTypeKey) {
        return APPLICATION_PROPERTIES;
    }
}
