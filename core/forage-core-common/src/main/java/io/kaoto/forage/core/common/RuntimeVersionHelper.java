package io.kaoto.forage.core.common;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * A utility class that provides runtime version information for Apache Camel,
 * Camel Spring Boot, and Camel Extension for Quarkus.
 */
public final class RuntimeVersionHelper {

    public static final Properties VERSIONS;

    static {
        VERSIONS = initVersions();
    }

    private RuntimeVersionHelper() {}

    private static Properties initVersions() {
        Properties props = new Properties();
        try (InputStream stream = RuntimeVersionHelper.class.getResourceAsStream("/runtime-versions.properties")) {
            if (stream == null) {
                throw new IllegalStateException("Resource /runtime-versions.properties not found on classpath");
            }
            props.load(stream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return props;
    }
}
