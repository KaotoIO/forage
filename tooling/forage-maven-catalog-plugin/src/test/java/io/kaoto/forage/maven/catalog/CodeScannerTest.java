package io.kaoto.forage.maven.catalog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.plugin.logging.SystemStreamLog;
import io.kaoto.forage.catalog.model.BeanReference;
import io.kaoto.forage.catalog.model.ConfigEntry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for CodeScanner path filtering and annotation parsing.
 */
public class CodeScannerTest {

    @TempDir
    Path projectDir;

    @Test
    public void testInternalConfigMarksEntryHidden() throws IOException {
        writeSource(
                "ExampleConfigEntries.java",
                """
                package io.kaoto.forage.example;

                import io.kaoto.forage.core.annotations.InternalConfig;
                import io.kaoto.forage.core.util.config.ConfigEntries;
                import io.kaoto.forage.core.util.config.ConfigModule;
                import io.kaoto.forage.core.util.config.ConfigTag;

                public final class ExampleConfigEntries extends ConfigEntries {
                    public static final ConfigModule URL = ConfigModule.of(
                            ExampleConfig.class, "forage.example.url", "URL", "URL", null, "string", true, ConfigTag.COMMON);

                    @InternalConfig
                    public static final ConfigModule IMPL_CLASS = ConfigModule.of(
                            ExampleConfig.class, "forage.example.impl.class", "Class", "Class", null, "string", false,
                            ConfigTag.ADVANCED);
                }
                """);

        ScanResult result = scan();

        assertThat(result.getConfigProperties())
                .extracting(ConfigEntry::getName, ConfigEntry::isHidden)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("forage.example.url", false),
                        org.assertj.core.groups.Tuple.tuple("forage.example.impl.class", true));
    }

    @Test
    public void testFactoryNamedInstancesAndBeanReferencesAreParsed() throws IOException {
        writeSource(
                "ExampleFactory.java",
                """
                package io.kaoto.forage.example;

                import io.kaoto.forage.core.annotations.BeanReference;
                import io.kaoto.forage.core.annotations.FactoryType;
                import io.kaoto.forage.core.annotations.ForageFactory;

                @ForageFactory(
                        value = "Example",
                        components = {"camel-jdbc"},
                        type = FactoryType.DATA_SOURCE,
                        namedInstances = false,
                        beanReferences = {
                            @BeanReference(name = "jdbc", option = "dataSourceName", optionKind = "path"),
                            @BeanReference(catalogKind = "processor", name = "policy", option = "ref",
                                    optionKind = "attribute", valueTemplate = "{beanRef}")
                        })
                public class ExampleFactory {}
                """);
        writeSource(
                "SingleReferenceFactory.java",
                """
                package io.kaoto.forage.example;

                import io.kaoto.forage.core.annotations.BeanReference;
                import io.kaoto.forage.core.annotations.FactoryType;
                import io.kaoto.forage.core.annotations.ForageFactory;

                @ForageFactory(
                        value = "Single",
                        type = FactoryType.CXF_ENDPOINT,
                        beanReferences = @BeanReference(name = "cxf", option = "beanId", optionKind = "path",
                                valueTemplate = "bean:{beanRef}"))
                public class SingleReferenceFactory {}
                """);

        ScanResult result = scan();

        ScannedFactory example = factory(result, "Example");
        assertThat(example.isNamedInstances()).isFalse();
        assertThat(example.getBeanReferences())
                .containsExactly(
                        new BeanReference("component", "jdbc", "dataSourceName", "path", "{beanRef}"),
                        new BeanReference("processor", "policy", "ref", "attribute", "{beanRef}"));

        ScannedFactory single = factory(result, "Single");
        assertThat(single.isNamedInstances()).isTrue();
        assertThat(single.getBeanReferences())
                .containsExactly(new BeanReference("component", "cxf", "beanId", "path", "bean:{beanRef}"));
    }

    private void writeSource(String fileName, String source) throws IOException {
        Files.writeString(projectDir.resolve("pom.xml"), "<project><artifactId>forage-example</artifactId></project>");
        Path packageDir = projectDir.resolve("src/main/java/io/kaoto/forage/example");
        Files.createDirectories(packageDir);
        Files.writeString(packageDir.resolve(fileName), source);
    }

    private ScanResult scan() {
        Artifact artifact = new DefaultArtifact(
                "io.kaoto.forage", "forage-example", "1.0", "compile", "jar", null, new DefaultArtifactHandler("jar"));
        return new CodeScanner(new SystemStreamLog()).scanAllInOnePass(artifact, projectDir);
    }

    private static ScannedFactory factory(ScanResult result, String name) {
        return result.getFactories().stream()
                .filter(f -> name.equals(f.getName()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    public void testScannablePathAcceptsRegularSources() {
        Path scanRoot = Path.of("/home/user/forage/library/module/src/main/java");
        Path file = scanRoot.resolve("io/kaoto/forage/Example.java");

        assertThat(CodeScanner.isScannablePath(scanRoot, file)).isTrue();
    }

    @Test
    public void testScannablePathSkipsTestDirectoriesBelowScanRoot() {
        Path scanRoot = Path.of("/home/user/forage/library/module");
        Path testFile = scanRoot.resolve("src/test/java/io/kaoto/forage/ExampleTest.java");

        assertThat(CodeScanner.isScannablePath(scanRoot, testFile)).isFalse();
    }

    @Test
    public void testScannablePathSkipsTargetDirectoriesBelowScanRoot() {
        Path scanRoot = Path.of("/home/user/forage/library/module");
        Path generatedFile = scanRoot.resolve("target/generated-sources/io/kaoto/forage/Generated.java");

        assertThat(CodeScanner.isScannablePath(scanRoot, generatedFile)).isFalse();
    }

    @Test
    public void testScannablePathIgnoresTestSegmentAboveScanRoot() {
        // A checkout under a directory literally named "test" must not cause files to be skipped
        Path scanRoot = Path.of("/home/ci/test/forage/library/module/src/main/java");
        Path file = scanRoot.resolve("io/kaoto/forage/Example.java");

        assertThat(CodeScanner.isScannablePath(scanRoot, file)).isTrue();
    }

    @Test
    public void testScannablePathIgnoresTargetSegmentAboveScanRoot() {
        Path scanRoot = Path.of("/home/ci/target/forage/library/module/src/main/java");
        Path file = scanRoot.resolve("io/kaoto/forage/Example.java");

        assertThat(CodeScanner.isScannablePath(scanRoot, file)).isTrue();
    }

    @Test
    public void testScannablePathSkipsTestSegmentBelowScanRootEvenWhenRootContainsTest() {
        // Segments below the scan root are still filtered when the root itself lives under "test"
        Path scanRoot = Path.of("/home/ci/test/forage/library/module");
        Path testFile = scanRoot.resolve("src/test/java/io/kaoto/forage/ExampleTest.java");

        assertThat(CodeScanner.isScannablePath(scanRoot, testFile)).isFalse();
    }
}
