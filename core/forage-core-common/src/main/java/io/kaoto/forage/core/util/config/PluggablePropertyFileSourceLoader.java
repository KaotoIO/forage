package io.kaoto.forage.core.util.config;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovers {@link PluggablePropertyFileSource} implementations using the Java
 * {@link ServiceLoader} mechanism.
 *
 * <p>Discovered sources are sorted by {@link PropertyFileSource#priority()} in descending
 * order (highest priority first) and cached for the lifetime of this loader.
 *
 * @since 1.2
 * @see PluggablePropertyFileSource
 */
public final class PluggablePropertyFileSourceLoader {

    private static final Logger LOG = LoggerFactory.getLogger(PluggablePropertyFileSourceLoader.class);

    private static volatile List<PluggablePropertyFileSource> cachedSources;

    private PluggablePropertyFileSourceLoader() {}

    /**
     * Returns the discovered pluggable property file sources, sorted by descending priority.
     *
     * <p>The result is cached after the first call. Use {@link #reload()} to re-discover sources.
     *
     * @return an unmodifiable list of pluggable sources
     */
    public static List<PluggablePropertyFileSource> getSources() {
        if (cachedSources == null) {
            synchronized (PluggablePropertyFileSourceLoader.class) {
                if (cachedSources == null) {
                    cachedSources = loadSources();
                }
            }
        }
        return cachedSources;
    }

    /**
     * Forces re-discovery of pluggable property file sources on the next call to {@link #getSources()}.
     */
    public static synchronized void reload() {
        cachedSources = null;
    }

    private static List<PluggablePropertyFileSource> loadSources() {
        List<PluggablePropertyFileSource> sources = new ArrayList<>();
        ServiceLoader<PluggablePropertyFileSource> loader = ServiceLoader.load(PluggablePropertyFileSource.class);
        for (PluggablePropertyFileSource source : loader) {
            LOG.info("Discovered pluggable property file source: {} (priority={})", source.name(), source.priority());
            sources.add(source);
        }
        sources.sort(Comparator.comparingInt(PropertyFileSource::priority).reversed());
        return List.copyOf(sources);
    }
}
