package io.kaoto.forage.config.editor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import io.kaoto.forage.catalog.model.ForageBean;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;

/**
 * Finds the properties files of a directory that can hold Forage configuration. Only the top level of the directory
 * is scanned, and a directory that does not exist has no files.
 */
public final class ForageConfigurationFiles {

    /** The shared application properties file name. */
    public static final String APPLICATION_PROPERTIES = "application.properties";

    private ForageConfigurationFiles() {}

    /**
     * Returns the {@code application.properties} file of the given directory.
     *
     * @param dir the directory to look in
     * @return the file, or {@code null} if it does not exist
     */
    public static File applicationProperties(File dir) {
        File file = new File(dir, APPLICATION_PROPERTIES);
        return file.isFile() ? file : null;
    }

    /**
     * Returns every {@code application-<profile>.properties} file of the directory, whatever the active profile,
     * sorted by name.
     *
     * @param dir the directory to scan
     * @return the existing files, never {@code null}
     * @throws IOException if the directory cannot be scanned
     */
    public static List<File> profileProperties(File dir) throws IOException {
        return listFiles(dir, fileName -> fileName.startsWith("application-") && fileName.endsWith(".properties"));
    }

    /**
     * Finds the non-application Forage properties files in the given directory: the file names the catalog knows
     * (one per module, named after its {@code Config.name()}) plus any {@code forage-*.properties} file, sorted by
     * name.
     *
     * <p>Note that the {@code forage-*.properties} wildcard is only loaded at runtime on Spring Boot; plain Camel
     * and Quarkus only load the catalog-known file names (and {@code application.properties}).
     *
     * @param dir the directory to scan
     * @param catalog the catalog reader
     * @return the matching files, never {@code null}
     * @throws IOException if the directory cannot be scanned
     */
    public static List<File> forageProperties(File dir, ForageCatalogReader catalog) throws IOException {
        Set<String> catalogFileNames = catalogFileNames(catalog);
        return listFiles(
                dir,
                fileName -> catalogFileNames.contains(fileName)
                        || (fileName.startsWith("forage-") && fileName.endsWith(".properties")));
    }

    /**
     * Returns every properties file that can hold Forage configuration for the directory, whatever the active
     * profile: {@code application.properties}, every {@code application-<profile>.properties}, and the files returned
     * by {@link #forageProperties(File, ForageCatalogReader)}.
     *
     * @param dir the directory to scan
     * @param catalog the catalog reader
     * @return the existing files, never {@code null}
     * @throws IOException if the directory cannot be scanned
     */
    public static List<File> all(File dir, ForageCatalogReader catalog) throws IOException {
        List<File> files = new ArrayList<>();
        File applicationProperties = applicationProperties(dir);
        if (applicationProperties != null) {
            files.add(applicationProperties);
        }
        files.addAll(profileProperties(dir));
        files.addAll(forageProperties(dir, catalog));
        return files;
    }

    /**
     * The properties file names the catalog records for factories and beans.
     */
    static Set<String> catalogFileNames(ForageCatalogReader catalog) {
        Set<String> fileNames = new HashSet<>();
        for (ForageCatalogReader.FactoryMetadata metadata : catalog.getAllFactories()) {
            addIfPresent(fileNames, metadata.propertiesFileName());
            for (ForageBean bean : catalog.getAllBeansForFactory(metadata.factoryTypeKey())) {
                addIfPresent(fileNames, bean.getPropertiesFile());
            }
        }
        return fileNames;
    }

    private static void addIfPresent(Set<String> fileNames, String fileName) {
        if (fileName != null && !fileName.isEmpty()) {
            fileNames.add(fileName);
        }
    }

    private static List<File> listFiles(File dir, Predicate<String> fileNameFilter) throws IOException {
        if (!dir.isDirectory()) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(dir.toPath())) {
            return paths.filter(Files::isRegularFile)
                    .filter(p -> fileNameFilter.test(p.getFileName().toString()))
                    .sorted()
                    .map(Path::toFile)
                    .toList();
        }
    }
}
