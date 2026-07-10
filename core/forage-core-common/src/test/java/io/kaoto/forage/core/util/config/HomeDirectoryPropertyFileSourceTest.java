package io.kaoto.forage.core.util.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class HomeDirectoryPropertyFileSourceTest {

    @Test
    @DisplayName("Priority is 150 — between config dir and classpath")
    void hasExpectedPriority() {
        HomeDirectoryPropertyFileSource source = new HomeDirectoryPropertyFileSource();
        assertThat(source.priority()).isEqualTo(150);
    }

    @Test
    @DisplayName("resolveHomeDir returns $HOME/.forage when no override is set")
    void resolvesDefaultHomeDirectory() {
        String env = System.getenv(HomeDirectoryPropertyFileSource.ENV_VAR);
        if (env != null) {
            return;
        }
        String prop = System.getProperty(HomeDirectoryPropertyFileSource.PROPERTY);
        try {
            if (prop != null) {
                return;
            }
            String resolved = HomeDirectoryPropertyFileSource.resolveHomeDir();
            assertThat(resolved).isNotNull();
            assertThat(resolved).endsWith(".forage");
        } finally {
            if (prop != null) {
                System.setProperty(HomeDirectoryPropertyFileSource.PROPERTY, prop);
            } else {
                System.clearProperty(HomeDirectoryPropertyFileSource.PROPERTY);
            }
        }
    }

    @Test
    @DisplayName("Uses the injected supplier to resolve the home directory")
    void usesInjectedSupplier() throws IOException {
        Path customDir = Files.createTempDirectory("forage-home-test");
        HomeDirectoryPropertyFileSource source = new HomeDirectoryPropertyFileSource(() -> customDir.toString());
        assertThat(source.priority()).isEqualTo(150);

        Path propsFile = customDir.resolve("my-app.properties");
        Files.writeString(propsFile, "key=value\n");

        try (InputStream is = source.locate("my-app.properties")) {
            assertThat(is).isNotNull();
            String content = new String(is.readAllBytes());
            assertThat(content).contains("key=value");
        }
    }

    @Test
    @DisplayName("Returns null when the requested file does not exist in the supplied directory")
    void locateReturnsNullForMissingFile() {
        HomeDirectoryPropertyFileSource source =
                new HomeDirectoryPropertyFileSource(() -> "/nonexistent-dir-" + System.nanoTime());
        assertThat(source.locate("anything.properties")).isNull();
    }
}
