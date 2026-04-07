package io.kaoto.forage.core.util.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized utility for locating and loading Forage properties files from the filesystem
 * and classpath.
 *
 * <p>This class consolidates the configuration source resolution logic that was previously
 * duplicated across {@link ConfigHelper}, {@link ConfigStore}, and the Spring Boot
 * {@code ForageEnvironmentPostProcessor}. All three classes now delegate to this utility
 * for consistent behavior.
 *
 * <p><strong>Filesystem resolution order:</strong>
 * <ol>
 *   <li>Current working directory</li>
 *   <li>{@code forage.config.dir} system property</li>
 *   <li>{@code FORAGE_CONFIG_DIR} environment variable</li>
 *   <li>Pluggable sources discovered via {@link PluggablePropertyFileSourceLoader}</li>
 * </ol>
 *
 * @since 1.2
 * @see ConfigStore
 * @see ConfigHelper
 * @see PropertyFileSource
 * @see PluggablePropertyFileSource
 */
public final class PropertyFileLocator {

    private static final Logger LOG = LoggerFactory.getLogger(PropertyFileLocator.class);

    static final String CONFIG_DIR_PROPERTY = "forage.config.dir";
    static final String CONFIG_DIR_ENV = "FORAGE_CONFIG_DIR";

    private static final List<PropertyFileSource> BUILT_IN_SOURCES;

    static {
        List<PropertyFileSource> sources = new ArrayList<>();
        sources.add(new WorkingDirectoryPropertyFileSource());
        sources.add(new ConfigDirPropertyFileSource());
        sources.sort(Comparator.comparingInt(PropertyFileSource::priority).reversed());
        BUILT_IN_SOURCES = List.copyOf(sources);
    }

    private PropertyFileLocator() {}

    /**
     * Resolves the Forage configuration directory from system property or environment variable.
     *
     * <p>Checks {@code forage.config.dir} system property first, then falls back to the
     * {@code FORAGE_CONFIG_DIR} environment variable.
     *
     * @return the configuration directory path, or {@code null} if neither is set
     */
    public static String resolveConfigDir() {
        String configDir = System.getProperty(CONFIG_DIR_PROPERTY);
        if (configDir == null) {
            configDir = System.getenv(CONFIG_DIR_ENV);
        }
        return configDir;
    }

    /**
     * Attempts to open a properties file by consulting all registered
     * {@link PropertyFileSource} instances (built-in and pluggable) in priority order.
     *
     * <p>Built-in sources (working directory, config directory) are tried first,
     * followed by any {@link PluggablePropertyFileSource} implementations discovered via
     * {@link java.util.ServiceLoader}.
     *
     * @param fileName the file name to locate (e.g., {@code "application.properties"})
     * @return an open {@link InputStream} for the file, or {@code null} if not found
     */
    public static InputStream locateFromFilesystem(String fileName) {
        // Try built-in sources first (sorted by descending priority)
        for (PropertyFileSource source : BUILT_IN_SOURCES) {
            InputStream is = source.locate(fileName);
            if (is != null) {
                LOG.debug("Located {} via {}", fileName, source.name());
                return is;
            }
        }

        // Then try pluggable sources discovered via ServiceLoader
        for (PluggablePropertyFileSource source : PluggablePropertyFileSourceLoader.getSources()) {
            InputStream is = source.locate(fileName);
            if (is != null) {
                LOG.debug("Located {} via pluggable source {}", fileName, source.name());
                return is;
            }
        }

        return null;
    }

    /**
     * Returns an unmodifiable list of the built-in property file sources.
     *
     * @return the built-in sources sorted by descending priority
     */
    public static List<PropertyFileSource> getBuiltInSources() {
        return BUILT_IN_SOURCES;
    }

    /**
     * Attempts to load a resource from the classpath using the provided classloaders in order.
     * Returns the first successful match.
     *
     * @param resourceName the classpath resource name
     * @param classLoaders classloaders to try, in order
     * @return an open {@link InputStream} for the resource, or {@code null} if not found
     */
    public static InputStream locateFromClasspath(String resourceName, ClassLoader... classLoaders) {
        for (ClassLoader cl : classLoaders) {
            if (cl != null) {
                InputStream is = cl.getResourceAsStream(resourceName);
                if (is != null) {
                    LOG.debug(
                            "Loading {} from classloader {}",
                            resourceName,
                            cl.getClass().getName());
                    return is;
                }
            }
        }
        return null;
    }

    /**
     * Reads a {@link Properties} object from an {@link InputStream}, closing it afterwards.
     * Returns an empty {@link Properties} if the input is {@code null}.
     *
     * @param is the input stream to read from (may be {@code null})
     * @return the loaded properties, never {@code null}
     */
    public static Properties readProperties(InputStream is) {
        Properties props = new Properties();
        if (is != null) {
            try (InputStream stream = is) {
                props.load(stream);
            } catch (IOException e) {
                LOG.error("Failed to load properties", e);
            }
        }
        return props;
    }

    /**
     * Extracts configuration prefixes matching the given regular expression from a
     * {@link Properties} object.
     *
     * <p>The regexp must contain exactly one capture group that extracts the prefix.
     * For example, with the pattern {@code "forage\\.(.+)\\.jdbc\\..+"} and properties
     * {@code forage.ds1.jdbc.url} and {@code forage.ds2.jdbc.url}, this method returns
     * the set {@code {"ds1", "ds2"}}.
     *
     * @param props  the properties to scan
     * @param regexp a regex with one capture group for the prefix
     * @return the set of matched prefixes, never {@code null}
     */
    public static Set<String> readPrefixes(Properties props, String regexp) {
        Pattern pattern = Pattern.compile(regexp);
        return Collections.list(props.keys()).stream()
                .map(key -> {
                    Matcher m = pattern.matcher((String) key);
                    if (m.find()) {
                        return m.group(1);
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
}
