package io.kaoto.forage.core.util.config;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class ConfigDirPropertyFileSourceTest {

    @Test
    void priorityIs200() {
        assertThat(new ConfigDirPropertyFileSource().priority()).isEqualTo(200);
    }

    @Test
    void returnsNullWhenNoConfigDir() {
        String original = System.getProperty(PropertyFileLocator.CONFIG_DIR_PROPERTY);
        try {
            System.clearProperty(PropertyFileLocator.CONFIG_DIR_PROPERTY);
            ConfigDirPropertyFileSource source = new ConfigDirPropertyFileSource();
            InputStream is = source.locate("nonexistent-file-" + System.nanoTime() + ".properties");
            assertThat(is).isNull();
        } finally {
            if (original != null) {
                System.setProperty(PropertyFileLocator.CONFIG_DIR_PROPERTY, original);
            }
        }
    }

    @Test
    void findsFileInConfigDir(@TempDir Path tempDir) throws Exception {
        Path propsFile = tempDir.resolve("test-config-dir.properties");
        Files.writeString(propsFile, "key=from-config-dir\n");

        String original = System.getProperty(PropertyFileLocator.CONFIG_DIR_PROPERTY);
        try {
            System.setProperty(PropertyFileLocator.CONFIG_DIR_PROPERTY, tempDir.toString());

            ConfigDirPropertyFileSource source = new ConfigDirPropertyFileSource();
            InputStream is = source.locate("test-config-dir.properties");
            assertThat(is).isNotNull();

            Properties props = new Properties();
            try (InputStream stream = is) {
                props.load(stream);
            }
            assertThat(props.getProperty("key")).isEqualTo("from-config-dir");
        } finally {
            if (original != null) {
                System.setProperty(PropertyFileLocator.CONFIG_DIR_PROPERTY, original);
            } else {
                System.clearProperty(PropertyFileLocator.CONFIG_DIR_PROPERTY);
            }
        }
    }
}
