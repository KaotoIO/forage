package io.kaoto.forage.integration.tests;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import org.citrusframework.spi.Resource;
import org.citrusframework.spi.Resources;

/**
 * Helper class for creating temporary properties files from templates for integration tests.
 * <p>
 * Integration tests should use properties files with placeholder values replaced by testcontainer
 * connection details, rather than using environment variables. This ensures tests validate
 * the same configuration loading mechanism (ConfigSourceFactory) that end users experience.
 */
public final class PropertiesTemplateHelper {

    private PropertiesTemplateHelper() {
        // Utility class
    }

    private static FileAttribute<?>[] ownerOnlyPermissions() {
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            return new FileAttribute<?>[] {
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))
            };
        }
        return new FileAttribute<?>[0];
    }

    /**
     * Load a properties template, apply replacements, and write to a temporary file.
     *
     * @param templateResource the .properties.template file from test resources
     * @param replacements map of regex patterns to replacement values (use Matcher.quoteReplacement for values)
     * @param afterAll cleanup callback to register temp file deletion
     * @return FileSystemResource pointing to the temp properties file
     */
    public static Resource createFromTemplate(
            Resource templateResource, Map<String, String> replacements, Consumer<AutoCloseable> afterAll) {
        try {
            // Load template content
            String template;
            try (var inputStream = templateResource.getInputStream()) {
                template = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }

            // Apply all replacements
            String propertiesContent = template;
            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                propertiesContent = propertiesContent.replaceAll(entry.getKey(), entry.getValue());
            }

            // Extract filename from template (remove .template suffix)
            String templateFileName = templateResource.getFile().getName();
            String propertiesFileName = templateFileName.replace(".template", "");

            // Write to temp directory with proper name. Restrict permissions to the owner
            // only, since the generated properties files can carry testcontainer credentials
            // and the default temp directory is otherwise world-readable.
            Path tempDir = Files.createTempDirectory("forage-test-", ownerOnlyPermissions());
            Path tempPropertiesFile = tempDir.resolve(propertiesFileName);
            Files.writeString(tempPropertiesFile, propertiesContent, StandardCharsets.UTF_8);

            // Register cleanup to delete temp file and directory
            afterAll.accept(() -> {
                try {
                    Files.deleteIfExists(tempPropertiesFile);
                    Files.deleteIfExists(tempDir);
                } catch (java.nio.file.DirectoryNotEmptyException e) {
                    // Ignore - temp directory will be cleaned up by OS
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });

            // Return FileSystemResource
            return Resources.create(tempPropertiesFile.toFile());

        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to prepare properties from template: " + templateResource.getLocation(), e);
        }
    }

    /**
     * Convenience method for single property replacement.
     *
     * @param templateResource the .properties.template file from test resources
     * @param propertyPattern regex pattern matching the property to replace
     * @param replacementValue the replacement value (will be quoted for regex safety)
     * @param afterAll cleanup callback
     * @return FileSystemResource pointing to the temp properties file
     */
    public static Resource createFromTemplate(
            Resource templateResource,
            String propertyPattern,
            String replacementValue,
            Consumer<AutoCloseable> afterAll) {
        return createFromTemplate(
                templateResource, Map.of(propertyPattern, Matcher.quoteReplacement(replacementValue)), afterAll);
    }

    /**
     * Copy a resource into the directory of an already-templated properties file, i.e. into the
     * integration's working directory. Files passed to the run action as resources become Camel
     * properties-component locations; files that must be picked up as camel-jbang run
     * configuration (e.g. {@code application.properties} carrying runtime settings such as
     * {@code quarkus.http.port}) have to sit in the working directory instead.
     *
     * @param anchor a temp-file resource previously returned by {@link #createFromTemplate}
     * @param resource the resource to copy next to it (keeps its file name)
     * @param afterAll cleanup callback to register temp file deletion
     */
    public static void copyIntoSameDirectory(Resource anchor, Resource resource, Consumer<AutoCloseable> afterAll) {
        try {
            Path targetFile = anchor.getFile()
                    .toPath()
                    .getParent()
                    .resolve(resource.getFile().getName());
            try (var inputStream = resource.getInputStream()) {
                Files.copy(inputStream, targetFile);
            }
            afterAll.accept(() -> Files.deleteIfExists(targetFile));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to copy resource next to " + anchor.getLocation(), e);
        }
    }
}
