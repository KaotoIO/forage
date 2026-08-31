package io.kaoto.forage.plugin.config;

import java.io.File;
import java.util.Set;

/**
 * Strategy that stores all Forage configuration in a single {@code application.properties} file.
 *
 * <p>Every factory type reads from and writes to the same file; wildcard matching is disabled
 * so only the explicit {@code application.properties} is picked up.
 *
 * @see PropertiesFileStrategy
 */
final class ApplicationPropertiesStrategy implements PropertiesFileStrategy {

    static final String APPLICATION_PROPERTIES = "application.properties";

    @Override
    public Set<String> getTargetFileNames() {
        return Set.of(APPLICATION_PROPERTIES);
    }

    @Override
    public boolean matchesWildcard(String fileName) {
        return false;
    }

    @Override
    public String getPropertiesFileName(String factoryTypeKey) {
        return APPLICATION_PROPERTIES;
    }

    @Override
    public Set<File> getScanableProperties(File directory) {
        File appProps = new File(directory, APPLICATION_PROPERTIES);
        return appProps.exists() ? Set.of(appProps) : Set.of();
    }
}
