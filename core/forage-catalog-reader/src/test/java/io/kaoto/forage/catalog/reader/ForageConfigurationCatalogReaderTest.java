package io.kaoto.forage.catalog.reader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import io.kaoto.forage.catalog.model.ConfigEntry;
import io.kaoto.forage.catalog.model.ConfigurationModule;
import io.kaoto.forage.catalog.model.ForageConfigurationCatalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ForageConfigurationCatalogReaderTest {

    private static final String TEST_CATALOG_JSON =
            """
            {
              "version": "1.0-test",
              "generatedBy": "test",
              "timestamp": 1234567890,
              "modules": [
                {
                  "artifactId": "forage-model-openai",
                  "groupId": "io.kaoto.forage",
                  "propertiesFile": "forage-agent.properties",
                  "configEntries": [
                    {
                      "name": "forage.openai.api.key",
                      "type": "string",
                      "description": "OpenAI API key",
                      "required": true,
                      "defaultValue": null,
                      "example": "sk-..."
                    },
                    {
                      "name": "forage.openai.model.name",
                      "type": "string",
                      "description": "Model name to use",
                      "required": false,
                      "defaultValue": "gpt-4o",
                      "example": "gpt-4o-mini"
                    }
                  ]
                },
                {
                  "artifactId": "forage-jdbc-postgresql",
                  "groupId": "io.kaoto.forage",
                  "propertiesFile": "forage-jdbc.properties",
                  "configEntries": [
                    {
                      "name": "forage.postgresql.url",
                      "type": "string",
                      "description": "PostgreSQL JDBC URL",
                      "required": true
                    },
                    {
                      "name": "forage.postgresql.username",
                      "type": "string",
                      "description": "Database username",
                      "required": false,
                      "defaultValue": "postgres"
                    }
                  ]
                }
              ]
            }
            """;

    private ForageConfigurationCatalogReader reader;

    @BeforeEach
    void setUp() throws IOException {
        InputStream is = new ByteArrayInputStream(TEST_CATALOG_JSON.getBytes(StandardCharsets.UTF_8));
        reader = ForageConfigurationCatalogReader.fromInputStream(is);
    }

    /** A catalog of a later Forage version may add fields. */
    @Test
    void ignoresUnknownFields() throws IOException {
        String json = TEST_CATALOG_JSON
                .replaceFirst("\\{", "{\"futureField\": 1,")
                .replace("\"name\": \"forage.", "\"futureEntryField\": true, \"name\": \"forage.");
        ForageConfigurationCatalogReader later = ForageConfigurationCatalogReader.fromInputStream(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        assertThat(later.isValidConfig("forage.openai.api.key")).isTrue();
    }

    @Test
    void testIsValidConfigReturnsTrue() {
        assertThat(reader.isValidConfig("forage.openai.api.key")).isTrue();
        assertThat(reader.isValidConfig("forage.postgresql.url")).isTrue();
    }

    @Test
    void testIsValidConfigReturnsFalse() {
        assertThat(reader.isValidConfig("forage.nonexistent.key")).isFalse();
        assertThat(reader.isValidConfig(null)).isFalse();
    }

    @Test
    void testGetConfigEntry() {
        Optional<ConfigEntry> entry = reader.getConfigEntry("forage.openai.api.key");
        assertThat(entry).isPresent();
        assertThat(entry.get().getType()).isEqualTo("string");
        assertThat(entry.get().getDescription()).isEqualTo("OpenAI API key");
        assertThat(entry.get().isRequired()).isTrue();
        assertThat(entry.get().getDefaultValue()).isNull();
    }

    @Test
    void testGetConfigEntryWithDefaults() {
        Optional<ConfigEntry> entry = reader.getConfigEntry("forage.openai.model.name");
        assertThat(entry).isPresent();
        assertThat(entry.get().isRequired()).isFalse();
        assertThat(entry.get().getDefaultValue()).isEqualTo("gpt-4o");
        assertThat(entry.get().getExample()).isEqualTo("gpt-4o-mini");
    }

    @Test
    void testGetConfigEntryNotFound() {
        Optional<ConfigEntry> entry = reader.getConfigEntry("forage.nonexistent.key");
        assertThat(entry).isEmpty();
    }

    @Test
    void testGetModule() {
        Optional<ConfigurationModule> module = reader.getModule("forage-model-openai");
        assertThat(module).isPresent();
        assertThat(module.get().getGroupId()).isEqualTo("io.kaoto.forage");
        assertThat(module.get().getPropertiesFile()).isEqualTo("forage-agent.properties");
        assertThat(module.get().getConfigEntries()).hasSize(2);
    }

    @Test
    void testGetModuleNotFound() {
        Optional<ConfigurationModule> module = reader.getModule("nonexistent-module");
        assertThat(module).isEmpty();
    }

    @Test
    void testGetAllModules() {
        List<ConfigurationModule> modules = reader.getAllModules();
        assertThat(modules).hasSize(2);
        assertThat(modules.stream().map(ConfigurationModule::getArtifactId))
                .containsExactly("forage-model-openai", "forage-jdbc-postgresql");
    }

    @Test
    void testGetCatalog() {
        ForageConfigurationCatalog catalog = reader.getCatalog();
        assertThat(catalog).isNotNull();
        assertThat(catalog.getVersion()).isEqualTo("1.0-test");
        assertThat(catalog.getGeneratedBy()).isEqualTo("test");
        assertThat(catalog.getTimestamp()).isEqualTo(1234567890L);
    }
}
