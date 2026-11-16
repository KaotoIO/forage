package io.kaoto.forage.config.editor;

import java.io.File;
import java.nio.file.Files;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class ForageConfigurationFilesTest {

    private final ForageCatalogReader catalog = ForageCatalogReader.getInstance();

    @TempDir
    File tempDir;

    @Test
    void allIncludesEveryProfile() throws Exception {
        writeFile("application.properties", "camel.main.profile=dev\n");
        writeFile("application-dev.properties", "forage.ds.jdbc.db.kind=h2\n");
        writeFile("application-prod.properties", "forage.ds.jdbc.db.kind=h2\n");
        writeFile("forage-custom.properties", "forage.ds2.jdbc.db.kind=h2\n");
        writeFile("other.properties", "forage.ds3.jdbc.db.kind=h2\n");

        assertThat(ForageConfigurationFiles.all(tempDir, catalog))
                .containsExactly(
                        new File(tempDir, "application.properties"),
                        new File(tempDir, "application-dev.properties"),
                        new File(tempDir, "application-prod.properties"),
                        new File(tempDir, "forage-custom.properties"));
    }

    @Test
    void missingDirectoryHasNoFiles() throws Exception {
        File missing = new File(tempDir, "missing");

        assertThat(ForageConfigurationFiles.all(missing, catalog)).isEmpty();
        assertThat(ForageConfigurationFiles.forageProperties(missing, catalog)).isEmpty();
        assertThat(ForageConfigurationFiles.applicationProperties(missing)).isNull();
    }

    @Test
    void catalogFileNamesIncludeBeanFiles() {
        // JMS Artemis has a properties file of its own, next to the JMS factory one
        assertThat(ForageConfigurationFiles.catalogFileNames(catalog))
                .contains("forage-connectionfactory.properties", "forage-jms-artemis.properties");
    }

    private void writeFile(String name, String content) throws Exception {
        Files.writeString(new File(tempDir, name).toPath(), content);
    }
}
