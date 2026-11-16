package io.kaoto.forage.plugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;
import io.kaoto.forage.plugin.ForagePropertyScanner.PropertyOccurrence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class ForagePropertyScannerTest {

    @TempDir
    File tempDir;

    private String originalProfile;
    private ForageCatalogReader catalog;

    @BeforeEach
    void setUp() {
        originalProfile = System.getProperty("camel.main.profile");
        System.clearProperty("camel.main.profile");
        catalog = ForageCatalogReader.getInstance();
    }

    @AfterEach
    void tearDown() {
        if (originalProfile != null) {
            System.setProperty("camel.main.profile", originalProfile);
        } else {
            System.clearProperty("camel.main.profile");
        }
    }

    @Test
    void routePolicyPropertiesGroupedUnderRoutePolicyFactory() throws Exception {
        writeFile(
                "forage-policy.properties",
                "forage.route.policy.enabled=true\n"
                        + "forage.route.policy.route-a.name=flip\n"
                        + "forage.route.policy.route-a.flip.paired-route=route-b\n");

        Map<String, Map<String, List<String>>> result = ForagePropertyScanner.scanProperties(tempDir, catalog);

        assertThat(result).containsOnlyKeys("route");
        assertThat(result.get("route"))
                .containsEntry("policy.enabled", List.of("true"))
                .containsEntry("policy.route-a.name", List.of("flip"))
                .containsEntry("policy.route-a.flip.paired-route", List.of("route-b"));
    }

    @Test
    void profilePropertiesDiscoveredViaSysProp() throws Exception {
        writeFile("application-dev.properties", "forage.jdbc.db.kind=postgresql\n");
        System.setProperty("camel.main.profile", "dev");

        Map<String, Map<String, List<String>>> result = ForagePropertyScanner.scanProperties(tempDir, catalog);

        assertThat(result).containsKey("jdbc");
        assertThat(result.get("jdbc").get("db.kind")).containsExactly("postgresql");
    }

    @Test
    void profileOverridesBase() throws Exception {
        writeFile("application.properties", "forage.jdbc.db.kind=h2\n");
        writeFile("application-dev.properties", "forage.jdbc.db.kind=postgresql\n");
        System.setProperty("camel.main.profile", "dev");

        Map<String, Map<String, List<String>>> result = ForagePropertyScanner.scanProperties(tempDir, catalog);

        assertThat(result).containsKey("jdbc");
        assertThat(result.get("jdbc").get("db.kind")).containsExactly("postgresql");
    }

    @Test
    void profileOverridesBaseWithCorrectFileAttribution() throws Exception {
        writeFile("application.properties", "forage.jdbc.db.kind=h2\nforage.jdbc.url=jdbc:h2:mem:test\n");
        writeFile("application-dev.properties", "forage.jdbc.db.kind=postgresql\n");
        System.setProperty("camel.main.profile", "dev");

        Map<String, Map<String, List<PropertyOccurrence>>> result =
                ForagePropertyScanner.scanPropertiesWithFileTracking(tempDir, catalog, false);

        List<PropertyOccurrence> kindOccurrences = result.get("jdbc").get("db.kind");
        assertThat(kindOccurrences).hasSize(1);
        assertThat(kindOccurrences.get(0).value()).isEqualTo("postgresql");
        assertThat(kindOccurrences.get(0).file().getName()).isEqualTo("application-dev.properties");

        List<PropertyOccurrence> urlOccurrences = result.get("jdbc").get("url");
        assertThat(urlOccurrences).hasSize(1);
        assertThat(urlOccurrences.get(0).value()).isEqualTo("jdbc:h2:mem:test");
        assertThat(urlOccurrences.get(0).file().getName()).isEqualTo("application.properties");
    }

    @Test
    void profileFileOnlyWithoutApplicationProperties() throws Exception {
        writeFile("application-dev.properties", "forage.jdbc.db.kind=mysql\n");
        System.setProperty("camel.main.profile", "dev");

        Map<String, Map<String, List<String>>> result = ForagePropertyScanner.scanProperties(tempDir, catalog);

        assertThat(result).containsKey("jdbc");
        assertThat(result.get("jdbc").get("db.kind")).containsExactly("mysql");
    }

    @Test
    void missingProfileFileFallsBackToBase() throws Exception {
        writeFile("application.properties", "forage.jdbc.db.kind=h2\n");
        System.setProperty("camel.main.profile", "dev");

        Map<String, Map<String, List<String>>> result = ForagePropertyScanner.scanProperties(tempDir, catalog);

        assertThat(result).containsKey("jdbc");
        assertThat(result.get("jdbc").get("db.kind")).containsExactly("h2");
    }

    @Test
    void noProfileOnlyApplicationProperties() throws Exception {
        writeFile("application.properties", "forage.jdbc.db.kind=h2\n");
        writeFile("application-dev.properties", "forage.jdbc.db.kind=postgresql\n");

        Map<String, Map<String, List<String>>> result = ForagePropertyScanner.scanProperties(tempDir, catalog);

        assertThat(result).containsKey("jdbc");
        assertThat(result.get("jdbc").get("db.kind")).containsExactly("h2");
    }

    @Test
    void profileResolvedFromApplicationProperties() throws Exception {
        writeFile("application.properties", "camel.main.profile=prod\nforage.jdbc.db.kind=h2\n");
        writeFile("application-prod.properties", "forage.jdbc.db.kind=postgresql\n");

        Map<String, Map<String, List<String>>> result = ForagePropertyScanner.scanProperties(tempDir, catalog);

        assertThat(result).containsKey("jdbc");
        assertThat(result.get("jdbc").get("db.kind")).containsExactly("postgresql");
    }

    @Test
    void forageSpecificFilesStillWork() throws Exception {
        writeFile("forage-jdbc.properties", "forage.jdbc.db.kind=postgresql\n");

        Map<String, Map<String, List<String>>> result = ForagePropertyScanner.scanProperties(tempDir, catalog);

        assertThat(result).containsKey("jdbc");
        assertThat(result.get("jdbc").get("db.kind")).containsExactly("postgresql");
    }

    @Test
    void noPropertiesFiles() throws Exception {
        Map<String, Map<String, List<String>>> result = ForagePropertyScanner.scanProperties(tempDir, catalog);

        assertThat(result).isEmpty();
    }

    private void writeFile(String name, String content) throws IOException {
        File file = new File(tempDir, name);
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }
}
