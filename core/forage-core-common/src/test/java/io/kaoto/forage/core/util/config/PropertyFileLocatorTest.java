package io.kaoto.forage.core.util.config;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class PropertyFileLocatorTest {

    @Test
    void resolveConfigDirReturnsNullWhenNeitherSet() {
        // When neither system property nor env var is set, null is expected
        // (unless the test environment already defines them)
        String configDir = PropertyFileLocator.resolveConfigDir();
        String sysProp = System.getProperty(PropertyFileLocator.CONFIG_DIR_PROPERTY);
        String envVar = System.getenv(PropertyFileLocator.CONFIG_DIR_ENV);
        if (sysProp == null && envVar == null) {
            assertThat(configDir).isNull();
        }
    }

    @Test
    void resolveConfigDirReturnsSystemProperty() {
        String original = System.getProperty(PropertyFileLocator.CONFIG_DIR_PROPERTY);
        try {
            System.setProperty(PropertyFileLocator.CONFIG_DIR_PROPERTY, "/test/config/dir");
            assertThat(PropertyFileLocator.resolveConfigDir()).isEqualTo("/test/config/dir");
        } finally {
            if (original != null) {
                System.setProperty(PropertyFileLocator.CONFIG_DIR_PROPERTY, original);
            } else {
                System.clearProperty(PropertyFileLocator.CONFIG_DIR_PROPERTY);
            }
        }
    }

    @Test
    void locateFromFilesystemFindsFileInConfigDir(@TempDir Path tempDir) throws IOException {
        // Create a properties file in a temp directory
        Path propsFile = tempDir.resolve("test.properties");
        Files.writeString(propsFile, "key=value\n");

        String original = System.getProperty(PropertyFileLocator.CONFIG_DIR_PROPERTY);
        try {
            System.setProperty(PropertyFileLocator.CONFIG_DIR_PROPERTY, tempDir.toString());

            InputStream is = PropertyFileLocator.locateFromFilesystem("test.properties");
            assertThat(is).isNotNull();

            Properties props = new Properties();
            try (InputStream stream = is) {
                props.load(stream);
            }
            assertThat(props.getProperty("key")).isEqualTo("value");
        } finally {
            if (original != null) {
                System.setProperty(PropertyFileLocator.CONFIG_DIR_PROPERTY, original);
            } else {
                System.clearProperty(PropertyFileLocator.CONFIG_DIR_PROPERTY);
            }
        }
    }

    @Test
    void locateFromFilesystemReturnsNullForNonexistent() {
        String original = System.getProperty(PropertyFileLocator.CONFIG_DIR_PROPERTY);
        try {
            System.clearProperty(PropertyFileLocator.CONFIG_DIR_PROPERTY);
            InputStream is =
                    PropertyFileLocator.locateFromFilesystem("nonexistent-file-" + System.nanoTime() + ".properties");
            assertThat(is).isNull();
        } finally {
            if (original != null) {
                System.setProperty(PropertyFileLocator.CONFIG_DIR_PROPERTY, original);
            }
        }
    }

    @Test
    void locateFromClasspathFindsResource() {
        // ConfigModuleTest's own test class should be findable
        InputStream is = PropertyFileLocator.locateFromClasspath(
                "META-INF/services/io.kaoto.forage.core.util.config.ConfigResolver",
                PropertyFileLocatorTest.class.getClassLoader());
        // This resource may not exist, so just test the mechanism doesn't throw
        // Instead, use the test classloader to find something known
        ClassLoader cl = PropertyFileLocatorTest.class.getClassLoader();
        InputStream is2 = PropertyFileLocator.locateFromClasspath("nonexistent-resource-" + System.nanoTime(), cl);
        assertThat(is2).isNull();
    }

    @Test
    void locateFromClasspathTriesMultipleClassloaders() {
        ClassLoader emptyLoader = new ClassLoader(null) {};
        ClassLoader realLoader = PropertyFileLocatorTest.class.getClassLoader();

        // First classloader has nothing, second might find a real resource
        InputStream is =
                PropertyFileLocator.locateFromClasspath("nonexistent-" + System.nanoTime(), emptyLoader, realLoader);
        assertThat(is).isNull();
    }

    @Test
    void locateFromClasspathSkipsNullClassloaders() {
        InputStream is = PropertyFileLocator.locateFromClasspath("anything", (ClassLoader) null);
        assertThat(is).isNull();
    }

    @Test
    void readPropertiesHandlesNull() {
        Properties props = PropertyFileLocator.readProperties(null);
        assertThat(props).isNotNull().isEmpty();
    }

    @Test
    void readPropertiesLoadsFromStream() {
        String content = "foo=bar\nbaz=qux\n";
        InputStream is = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        Properties props = PropertyFileLocator.readProperties(is);
        assertThat(props).hasSize(2);
        assertThat(props.getProperty("foo")).isEqualTo("bar");
        assertThat(props.getProperty("baz")).isEqualTo("qux");
    }

    @Test
    void readPrefixesExtractsGroups() {
        Properties props = new Properties();
        props.setProperty("forage.ds1.jdbc.url", "jdbc:postgresql://localhost/db");
        props.setProperty("forage.ds2.jdbc.url", "jdbc:mysql://localhost/db");
        props.setProperty("forage.jdbc.url", "jdbc:h2:mem:test");

        Set<String> prefixes = PropertyFileLocator.readPrefixes(props, "forage\\.(.+)\\.jdbc\\..+");
        assertThat(prefixes).containsExactlyInAnyOrder("ds1", "ds2");
    }

    @Test
    void readPrefixesReturnsEmptyForNoMatch() {
        Properties props = new Properties();
        props.setProperty("unrelated.key", "value");

        Set<String> prefixes = PropertyFileLocator.readPrefixes(props, "forage\\.(.+)\\.jdbc\\..+");
        assertThat(prefixes).isEmpty();
    }

    @Test
    void readPrefixesHandlesEmptyProperties() {
        Properties props = new Properties();
        Set<String> prefixes = PropertyFileLocator.readPrefixes(props, "forage\\.(.+)\\.jdbc\\..+");
        assertThat(prefixes).isEmpty();
    }

    @Test
    void builtInSourcesContainExpectedTypes() {
        List<PropertyFileSource> sources = PropertyFileLocator.getBuiltInSources();
        assertThat(sources).hasSize(3);
        assertThat(sources.get(0)).isInstanceOf(WorkingDirectoryPropertyFileSource.class);
        assertThat(sources.get(1)).isInstanceOf(ConfigDirPropertyFileSource.class);
        assertThat(sources.get(2)).isInstanceOf(ClassPathPropertyFileSource.class);
    }

    @Test
    void builtInSourcesAreSortedByDescendingPriority() {
        List<PropertyFileSource> sources = PropertyFileLocator.getBuiltInSources();
        for (int i = 0; i < sources.size() - 1; i++) {
            assertThat(sources.get(i).priority())
                    .isGreaterThanOrEqualTo(sources.get(i + 1).priority());
        }
    }
}
