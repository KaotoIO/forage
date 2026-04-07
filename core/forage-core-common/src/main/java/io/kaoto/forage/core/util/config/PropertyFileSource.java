package io.kaoto.forage.core.util.config;

import java.io.InputStream;

/**
 * Abstraction for locating property files from different sources.
 *
 * <p>Implementations represent different strategies for resolving property files
 * (e.g., from the working directory, a configuration directory, the classpath,
 * or a custom source such as a database or remote configuration service).
 *
 * <p>Sources are consulted in order of descending {@link #priority()} until one
 * returns a non-{@code null} {@link InputStream}.
 *
 * @since 1.2
 * @see PluggablePropertyFileSource
 * @see PropertyFileLocator
 */
public interface PropertyFileSource {

    /**
     * Attempts to locate the given property file and return an open stream to it.
     *
     * @param fileName the file name to locate (e.g., {@code "forage-model-open-ai.properties"})
     * @return an open {@link InputStream} for the file, or {@code null} if this source
     *         cannot provide it
     */
    InputStream locate(String fileName);

    /**
     * Returns the priority of this source. Higher values are consulted first.
     *
     * <p>Built-in sources use priorities in the 100–300 range. Custom sources
     * should use lower values (e.g., 0–50) to avoid overriding built-in behaviour,
     * or higher values if the custom source should take precedence.
     *
     * @return the priority (default {@code 0})
     */
    default int priority() {
        return 0;
    }

    /**
     * Returns a human-readable name for this source, used in log messages.
     *
     * @return the source name
     */
    default String name() {
        return getClass().getSimpleName();
    }
}
