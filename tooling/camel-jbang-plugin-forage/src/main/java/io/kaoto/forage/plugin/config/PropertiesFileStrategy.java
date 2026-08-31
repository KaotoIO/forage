package io.kaoto.forage.plugin.config;

import java.io.File;
import java.util.Set;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;

/**
 * Strategy for resolving which properties files a Forage configuration command reads from and writes to.
 *
 * <p>This is the Gang-of-Four Strategy pattern: each implementation encapsulates a complete
 * resolution policy (file naming, scanning, and wildcard matching) so that command classes
 * no longer branch on a string discriminator.
 *
 * <p>Two built-in strategies are provided:
 * <ul>
 *   <li>{@link ApplicationPropertiesStrategy} &mdash; all factories share a single
 *       {@code application.properties} file.</li>
 *   <li>{@link ForageMultiFileStrategy} &mdash; each factory writes to its own
 *       {@code forage-{factoryType}.properties} file resolved from the catalog.</li>
 * </ul>
 *
 * @since 1.4
 */
public interface PropertiesFileStrategy {

    /**
     * Returns the concrete set of property file names a read command should look for.
     *
     * <p>The returned set is used for exact-name matching when walking a directory.
     *
     * @return the set of target file names (never {@code null})
     */
    Set<String> getTargetFileNames();

    /**
     * Indicates whether the given file name should be picked up by the read command's
     * wildcard matcher in addition to {@link #getTargetFileNames()}.
     *
     * <p>The {@code application} strategy returns {@code false} for all names, while
     * the {@code forage} strategy returns {@code true} for any {@code forage-*.properties}
     * file so uncatalogued modules are still discovered.
     *
     * @param fileName the file name to test (never {@code null})
     * @return {@code true} if the wildcard matcher should accept this file name
     */
    boolean matchesWildcard(String fileName);

    /**
     * Resolves the properties file name a given factory type should be written to.
     *
     * @param factoryTypeKey the factory type key (e.g., {@code "jdbc"}, {@code "agent"})
     * @return the file name, or {@code null} if the strategy cannot determine a target
     *         for this factory type (in which case the caller should skip it)
     */
    String getPropertiesFileName(String factoryTypeKey);

    /**
     * Collects the existing on-disk properties files that should be scanned when
     * deleting configuration or computing which types are still configured.
     *
     * @param directory the working directory to resolve files relative to (never {@code null})
     * @return the set of existing files to scan (never {@code null})
     */
    Set<File> getScanableProperties(File directory);

    /**
     * Resolves the strategy instance for the given string identifier.
     *
     * <p>The {@code "application"} identifier (case-insensitive) yields the
     * {@link ApplicationPropertiesStrategy}; any other value (including the
     * canonical {@code "forage"}) yields the {@link ForageMultiFileStrategy}.
     *
     * @param strategy the raw strategy identifier from the command line (may be {@code null})
     * @param catalog the catalog reader used by the {@code forage} strategy; ignored by the
     *                {@code application} strategy (may be {@code null} when strategy is
     *                {@code "application"})
     * @return the resolved strategy, never {@code null}
     */
    static PropertiesFileStrategy from(String strategy, ForageCatalogReader catalog) {
        if (strategy == null || "application".equalsIgnoreCase(strategy)) {
            return new ApplicationPropertiesStrategy();
        }
        return new ForageMultiFileStrategy(catalog);
    }
}
