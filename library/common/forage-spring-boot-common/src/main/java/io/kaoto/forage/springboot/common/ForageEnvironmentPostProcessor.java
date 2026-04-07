package io.kaoto.forage.springboot.common;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.core.util.config.PropertyFileLocator;

/**
 * Spring Boot {@link EnvironmentPostProcessor} that bridges Forage's configuration system
 * with Spring's {@link org.springframework.core.env.Environment}.
 *
 * <p>This post-processor runs early in the Spring Boot lifecycle and performs the following actions:
 * <ol>
 *   <li>Loads {@code forage-*.properties} files from the classpath and filesystem into
 *       Spring's Environment, so that {@link ForageSpringBootModuleAdapter} can discover
 *       prefixed configurations (e.g., {@code forage.ds1.jdbc.*})</li>
 *   <li>Registers a {@link SpringConfigResolver} into {@link ConfigStore}, so that Forage's
 *       config system can resolve values from Spring's Environment (profiles, YAML, placeholders,
 *       Cloud Config, etc.)</li>
 *   <li>Adds a {@link ForagePropertySource} to Spring's Environment, so that standard Spring
 *       features ({@code @Value}, {@code @ConditionalOnProperty}) can access Forage properties</li>
 * </ol>
 *
 * <p>Registered via {@code META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports}.
 *
 * @since 1.1
 */
public class ForageEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(ForageEnvironmentPostProcessor.class);
    private static final String FORAGE_PROPERTIES_PATTERN = "forage-*.properties";
    private static final String CLASSPATH_SOURCE_PREFIX = "forage:";
    private static final String FILE_SOURCE_PREFIX = "forage-file:";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        LOG.info("Registering Forage Spring Boot integration");

        // 1. Load forage-*.properties from filesystem and classpath into Spring Environment
        loadForagePropertiesFromFilesystem(environment);
        loadForagePropertiesFromClasspath(environment);

        // 2. Register SpringConfigResolver so ConfigStore can read from Spring Environment
        ConfigStore.getInstance().registerResolver(new SpringConfigResolver(environment));

        // 3. Add ForagePropertySource so Spring can see forage.* properties
        if (!environment.getPropertySources().contains(ForagePropertySource.FORAGE_PROPERTY_SOURCE_NAME)) {
            environment.getPropertySources().addLast(new ForagePropertySource());
        }
    }

    /**
     * Scans the classpath for {@code forage-*.properties} files and adds each as a
     * {@link PropertiesPropertySource} with low priority ({@code addLast}).
     *
     * <p>This allows properties from Forage module files (e.g., {@code forage-datasource-factory.properties})
     * to be visible in Spring's Environment while being overridable by {@code application.properties},
     * environment variables, and system properties.
     */
    private void loadForagePropertiesFromClasspath(ConfigurableEnvironment environment) {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources("classpath*:" + FORAGE_PROPERTIES_PATTERN);
            for (Resource resource : resources) {
                if (resource.isReadable()) {
                    loadResourceIntoEnvironment(environment, resource, CLASSPATH_SOURCE_PREFIX);
                }
            }
        } catch (IOException e) {
            LOG.warn("Failed to scan for {} on classpath", FORAGE_PROPERTIES_PATTERN, e);
        }
    }

    /**
     * Scans the working directory and optional config directory for {@code forage-*.properties} files.
     *
     * <p>Checks in order:
     * <ol>
     *   <li>{@code forage.config.dir} system property</li>
     *   <li>{@code FORAGE_CONFIG_DIR} environment variable</li>
     *   <li>Current working directory</li>
     * </ol>
     *
     * <p>File-based properties are added before classpath properties (called first with {@code addLast}),
     * giving them higher precedence than classpath-bundled defaults.
     */
    private void loadForagePropertiesFromFilesystem(ConfigurableEnvironment environment) {
        String configDir = PropertyFileLocator.resolveConfigDir();
        if (configDir != null) {
            loadDirectoryIntoEnvironment(environment, Path.of(configDir));
        }
        loadDirectoryIntoEnvironment(environment, Path.of("").toAbsolutePath());
    }

    private void loadDirectoryIntoEnvironment(ConfigurableEnvironment environment, Path dir) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, FORAGE_PROPERTIES_PATTERN)) {
            for (Path path : stream) {
                Properties props = new Properties();
                try (InputStream is = Files.newInputStream(path)) {
                    props.load(is);
                }
                if (!props.isEmpty()) {
                    String sourceName =
                            FILE_SOURCE_PREFIX + path.toAbsolutePath().normalize();
                    LOG.info("Loaded {} Forage properties from file: {}", props.size(), path);
                    environment.getPropertySources().addLast(new PropertiesPropertySource(sourceName, props));
                }
            }
        } catch (IOException e) {
            LOG.warn("Failed to scan for {} in directory: {}", FORAGE_PROPERTIES_PATTERN, dir, e);
        }
    }

    private void loadResourceIntoEnvironment(
            ConfigurableEnvironment environment, Resource resource, String sourcePrefix) {
        try {
            Properties props = new Properties();
            try (InputStream is = resource.getInputStream()) {
                props.load(is);
            }
            if (!props.isEmpty()) {
                String sourceName = sourcePrefix + resource.getDescription();
                if (!environment.getPropertySources().contains(sourceName)) {
                    LOG.info("Loaded {} Forage properties from classpath: {}", props.size(), resource.getFilename());
                    environment.getPropertySources().addLast(new PropertiesPropertySource(sourceName, props));
                }
            }
        } catch (IOException e) {
            LOG.warn("Failed to load Forage properties from: {}", resource, e);
        }
    }
}
