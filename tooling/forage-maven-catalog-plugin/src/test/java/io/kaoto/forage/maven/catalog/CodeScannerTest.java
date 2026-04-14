package io.kaoto.forage.maven.catalog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for CodeScanner, covering POM parsing, annotation scanning, and parallel execution.
 */
public class CodeScannerTest {

    @TempDir
    Path tempDir;

    private CodeScanner scanner;
    private Log log;

    @BeforeEach
    void setUp() {
        log = new SystemStreamLog();
        scanner = new CodeScanner(log);
    }

    @Test
    void testScanFindsForageBean() throws IOException {
        // Set up a module with a pom.xml and a Java file with @ForageBean
        Path moduleDir = tempDir.resolve("forage-test-module");
        Files.createDirectories(moduleDir.resolve("src/main/java/com/example"));

        writePom(moduleDir, "forage-test-module");

        Files.writeString(
                moduleDir.resolve("src/main/java/com/example/TestProvider.java"),
                """
                package com.example;

                import io.kaoto.forage.core.annotations.ForageBean;

                @ForageBean(value = "test-provider",
                    components = {"camel-langchain4j-chat"},
                    description = "A test provider")
                public class TestProvider {
                }
                """);

        Artifact artifact = createArtifact("forage-test-module");
        ScanResult result = scanner.scanAllInOnePass(artifact, tempDir);

        assertThat(result.getBeans()).hasSize(1);
        assertThat(result.getBeans().get(0).getName()).isEqualTo("test-provider");
        assertThat(result.getBeans().get(0).getDescription()).isEqualTo("A test provider");
        assertThat(result.getBeans().get(0).getComponents()).containsExactly("camel-langchain4j-chat");
    }

    @Test
    void testScanFindsForageFactory() throws IOException {
        Path moduleDir = tempDir.resolve("forage-test-factory");
        Files.createDirectories(moduleDir.resolve("src/main/java/com/example"));

        writePom(moduleDir, "forage-test-factory");

        Files.writeString(
                moduleDir.resolve("src/main/java/com/example/TestFactory.java"),
                """
                package com.example;

                import io.kaoto.forage.core.annotations.ForageFactory;
                import io.kaoto.forage.core.annotations.FactoryType;

                @ForageFactory(value = "test-factory",
                    components = {"camel-langchain4j-chat"},
                    description = "A test factory",
                    type = FactoryType.DATA_SOURCE)
                public class TestFactory {
                }
                """);

        Artifact artifact = createArtifact("forage-test-factory");
        ScanResult result = scanner.scanAllInOnePass(artifact, tempDir);

        assertThat(result.getFactories()).hasSize(1);
        assertThat(result.getFactories().get(0).getName()).isEqualTo("test-factory");
        assertThat(result.getFactories().get(0).getDescription()).isEqualTo("A test factory");
    }

    @Test
    void testScanSkipsTestDirectory() throws IOException {
        Path moduleDir = tempDir.resolve("forage-test-skip");
        Files.createDirectories(moduleDir.resolve("src/main/java/com/example"));
        Files.createDirectories(moduleDir.resolve("src/test/java/com/example"));

        writePom(moduleDir, "forage-test-skip");

        // Production source
        Files.writeString(
                moduleDir.resolve("src/main/java/com/example/MainProvider.java"),
                """
                package com.example;

                import io.kaoto.forage.core.annotations.ForageBean;

                @ForageBean(value = "main-provider", components = {"comp"}, description = "Main")
                public class MainProvider {
                }
                """);

        // Test source — should be ignored
        Files.writeString(
                moduleDir.resolve("src/test/java/com/example/TestOnlyProvider.java"),
                """
                package com.example;

                import io.kaoto.forage.core.annotations.ForageBean;

                @ForageBean(value = "test-only-provider", components = {"comp"}, description = "Test only")
                public class TestOnlyProvider {
                }
                """);

        Artifact artifact = createArtifact("forage-test-skip");
        ScanResult result = scanner.scanAllInOnePass(artifact, tempDir);

        assertThat(result.getBeans()).hasSize(1);
        assertThat(result.getBeans().get(0).getName()).isEqualTo("main-provider");
    }

    @Test
    void testScanMultipleFilesInParallel() throws IOException {
        Path moduleDir = tempDir.resolve("forage-test-parallel");
        Files.createDirectories(moduleDir.resolve("src/main/java/com/example"));

        writePom(moduleDir, "forage-test-parallel");

        // Create multiple Java files to exercise parallel scanning
        for (int i = 0; i < 10; i++) {
            Files.writeString(
                    moduleDir.resolve("src/main/java/com/example/Provider" + i + ".java"),
                    """
                    package com.example;

                    import io.kaoto.forage.core.annotations.ForageBean;

                    @ForageBean(value = "provider-%d", components = {"comp"}, description = "Provider %d")
                    public class Provider%d {
                    }
                    """
                            .formatted(i, i, i));
        }

        Artifact artifact = createArtifact("forage-test-parallel");
        ScanResult result = scanner.scanAllInOnePass(artifact, tempDir);

        // All 10 beans should be found regardless of parallel execution order
        assertThat(result.getBeans()).hasSize(10);
    }

    @Test
    void testScanNoSourceDirectory() {
        // Artifact with no matching module directory
        Artifact artifact = createArtifact("nonexistent-module");
        ScanResult result = scanner.scanAllInOnePass(artifact, tempDir);

        assertThat(result.getBeans()).isEmpty();
        assertThat(result.getFactories()).isEmpty();
        assertThat(result.getConfigProperties()).isEmpty();
        assertThat(result.getConfigClasses()).isEmpty();
    }

    @Test
    void testScanConfigEntries() throws IOException {
        Path moduleDir = tempDir.resolve("forage-test-config");
        Files.createDirectories(moduleDir.resolve("src/main/java/com/example"));

        writePom(moduleDir, "forage-test-config");

        Files.writeString(
                moduleDir.resolve("src/main/java/com/example/TestConfigEntries.java"),
                """
                package com.example;

                import io.kaoto.forage.core.util.config.ConfigEntries;
                import io.kaoto.forage.core.util.config.ConfigModule;
                import io.kaoto.forage.core.util.config.ConfigTag;

                public final class TestConfigEntries extends ConfigEntries {
                    public static final ConfigModule API_KEY = ConfigModule.of(
                        TestConfig.class, "forage.test.api.key",
                        "API key for testing", "API Key", "", "string", true, ConfigTag.COMMON);

                    static {
                        initModules(TestConfigEntries.class, API_KEY);
                    }
                }
                """);

        Artifact artifact = createArtifact("forage-test-config");
        ScanResult result = scanner.scanAllInOnePass(artifact, tempDir);

        assertThat(result.getConfigProperties()).hasSize(1);
        assertThat(result.getConfigProperties().get(0).getName()).isEqualTo("forage.test.api.key");
        assertThat(result.getConfigProperties().get(0).getDescription()).isEqualTo("API key for testing");
        assertThat(result.getConfigProperties().get(0).isRequired()).isTrue();
    }

    private void writePom(Path moduleDir, String artifactId) throws IOException {
        Files.writeString(
                moduleDir.resolve("pom.xml"),
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>io.kaoto.forage</groupId>
                    <artifactId>%s</artifactId>
                    <version>1.0-SNAPSHOT</version>
                </project>
                """
                        .formatted(artifactId));
    }

    private Artifact createArtifact(String artifactId) {
        return new DefaultArtifact(
                "io.kaoto.forage", artifactId, "1.0-SNAPSHOT", "compile", "jar", null, new DefaultArtifactHandler());
    }
}
