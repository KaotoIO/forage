package io.kaoto.forage.config.editor;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Strategy for resolving which properties files a Forage configuration command reads from, writes to
 * and deletes from.
 *
 * <p>This is the Gang-of-Four Strategy pattern: each implementation encapsulates a complete file
 * resolution policy so that the command classes no longer branch on a string discriminator. Use
 * {@link PropertiesFileStrategyType#create(io.kaoto.forage.catalog.reader.ForageCatalogReader)} to
 * obtain an instance.
 *
 * <p>Two built-in strategies are provided:
 * <ul>
 *   <li>{@link ApplicationPropertiesStrategy} &mdash; all factories share a single
 *       {@code application.properties} file.</li>
 *   <li>{@link ForageMultiFileStrategy} &mdash; each factory has its own properties file, named after
 *       the module's {@code Config.name()} as recorded in the catalog
 *       (e.g. {@code forage-datasource-factory.properties}).</li>
 * </ul>
 *
 * @since 1.6.3
 */
public interface PropertiesFileStrategy {

    /**
     * Finds the existing properties files this strategy manages in the given directory.
     *
     * <p>The same set is used by {@code config read} to list beans and by {@code config write --delete}
     * to remove an instance, so that anything read can also be deleted.
     *
     * @param directory the directory to scan (never {@code null})
     * @return the existing properties files, never {@code null}
     * @throws IOException if the directory cannot be scanned
     */
    List<File> findPropertiesFiles(File directory) throws IOException;

    /**
     * Finds the existing properties files that can override the configuration of the files returned by
     * {@link #findPropertiesFiles(File)}, such as the {@code application-<profile>.properties} files. Deleting an
     * instance found in this strategy's files also removes it from these files, so that it does not come back,
     * partly configured, through an override.
     *
     * @param directory the directory to scan (never {@code null})
     * @return the existing properties files, never {@code null}
     * @throws IOException if the directory cannot be scanned
     */
    default List<File> findOverrideFiles(File directory) throws IOException {
        return List.of();
    }

    /**
     * Resolves the properties file name a given factory type should be written to.
     *
     * @param factoryTypeKey the factory type key (e.g., {@code "jdbc"}, {@code "agent"})
     * @return the file name, or {@code null} if this strategy has no file for the factory type (for example
     *         a catalog factory without a dedicated properties file under the {@code forage} strategy); the
     *         caller must then report an error instead of writing the configuration
     */
    String getPropertiesFileName(String factoryTypeKey);

    /**
     * Resolves the properties file name a property of a given factory type and bean should be written to.
     *
     * @param factoryTypeKey the factory type key (e.g., {@code "jdbc"}, {@code "agent"})
     * @param beanName the bean the property configures (e.g., {@code "ollama"}), or {@code null} for a factory
     *     property
     * @return the file name, or {@code null} if this strategy has no file for the factory type
     */
    default String getPropertiesFileName(String factoryTypeKey, String beanName) {
        return getPropertiesFileName(factoryTypeKey);
    }
}
