package io.kaoto.forage.maven.catalog;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * Maven plugin goal to generate a catalog of Forage components.
 *
 * This Mojo scans the project dependencies and generates a comprehensive catalog
 * of all available Forage components, including their configuration options,
 * capabilities, and usage examples.
 */
@Mojo(
        name = "generate-catalog",
        defaultPhase = LifecyclePhase.PROCESS_CLASSES,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
        threadSafe = true)
public class GenerateCatalogMojo extends AbstractMojo {

    /**
     * The Maven project instance.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * The output directory where the catalog will be generated.
     */
    @Parameter(property = "forage.catalog.outputDirectory", defaultValue = "${project.build.directory}/forage-catalog")
    private File outputDirectory;

    /**
     * The format of the generated catalog.
     * Supported formats: json, yaml, markdown
     */
    @Parameter(property = "forage.catalog.format", defaultValue = "json")
    private String format;

    /**
     * The root directory of the multi-module project.
     * Used to locate source directories for annotation scanning.
     * This is more reliable than project.getParent().getBasedir() which can
     * return null or a repository path when the parent is resolved from the
     * Maven cache rather than the reactor.
     */
    @Parameter(defaultValue = "${maven.multiModuleProjectDirectory}", readonly = true)
    protected File rootDir;

    @Override
    public void execute() throws MojoExecutionException {
        getLog().info("Generating Forage component catalog...");

        try {
            // Ensure output directory exists
            ensureOutputDirectory();

            // Initialize catalog generator
            CatalogGenerator generator = new CatalogGenerator(getLog());

            // Configure generator
            generator.setFormat(format);

            // Generate catalog
            getLog().info("Using root directory for source scanning: " + rootDir.getAbsolutePath());
            CatalogResult result = generator.generateCatalog(project, outputDirectory, rootDir);

            if (result.getComponentCount() == 0) {
                throw new MojoExecutionException(
                        "Catalog generation produced 0 factories — this likely indicates a source scanning failure. "
                                + "Root directory used: " + rootDir.getAbsolutePath());
            }

            getLog().info("Catalog generation completed. Found %d components, generated %d files"
                    .formatted(result.getComponentCount(), result.getGeneratedFileCount()));

            // Log generated files
            result.getGeneratedFiles().forEach(file -> getLog().info("Generated: " + file.getAbsolutePath()));

        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to generate Forage catalog", e);
        }
    }

    private void ensureOutputDirectory() throws IOException {
        Path outputPath = Path.of(outputDirectory.getAbsolutePath());
        if (!Files.exists(outputPath)) {
            Files.createDirectories(outputPath);
            getLog().debug("Created output directory: " + outputPath);
        }
    }

    // Getters for testing
    public File getOutputDirectory() {
        return outputDirectory;
    }

    public String getFormat() {
        return format;
    }
}
