package io.kaoto.forage.plugin;

import java.io.InputStream;
import java.util.stream.Stream;
import io.kaoto.forage.core.common.ExportCustomizer;

/**
 * Utility class for export configuration and version management in the Forage framework.
 */
public final class ExportHelper {

    public enum ResourceType {
        versions("versions.properties");

        private final String fileName;

        ResourceType(String fileName) {
            this.fileName = fileName;
        }

        public String getFileName() {
            return fileName;
        }
    }

    public static Stream<ExportCustomizer> getAllCustomizers() {
        return Stream.of(new CatalogDrivenExportCustomizer());
    }

    /**
     * Gets the quarkus version from the versions.properties file. (which is populated during buildtime)
     *
     * @return the quarkus version
     */
    public static String getQuarkusVersion() {
        return getString(
                "quarkus.version", ResourceType.versions, "Could not determine quarkus version from properties file.");
    }

    /**
     * Gets the camel version from the versions.properties file. (which is populated during buildtime)
     *
     * @return the camel version
     */
    public static String getCamelVersion() {
        return getString(
                "camel.version", ResourceType.versions, "Could not determine camel version from properties file.");
    }

    /**
     * Gets the project version from the versions.properties file. (which is populated during buildtime)
     *
     * @return the project version
     */
    public static String getProjectVersion() {
        return getString(
                "project.version", ResourceType.versions, "Could not determine project version from properties file.");
    }

    /**
     * Reads property from the file versions.properties (which contains build time resolved versions)
     */
    public static String getString(String key, ResourceType resourceType, String error) {
        try {
            java.util.Properties properties = new java.util.Properties();
            try (InputStream is = ExportHelper.class.getClassLoader().getResourceAsStream(resourceType.getFileName())) {
                if (is != null) {
                    properties.load(is);
                    String version = properties.getProperty(key);
                    if (version != null && !version.trim().isEmpty()) {
                        return version;
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(error, e);
        }

        // Ultimate fallback
        return null;
    }
}
