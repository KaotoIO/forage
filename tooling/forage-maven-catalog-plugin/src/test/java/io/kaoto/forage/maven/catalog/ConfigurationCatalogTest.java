package io.kaoto.forage.maven.catalog;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import io.kaoto.forage.catalog.model.ConfigEntry;
import io.kaoto.forage.catalog.model.ConfigurationModule;
import io.kaoto.forage.catalog.model.ForageConfigurationCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for configuration catalog generation in CatalogGenerator.
 */
public class ConfigurationCatalogTest {

    @TempDir
    File tempDir;

    private CatalogGenerator generator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        Log log = new SystemStreamLog();
        generator = new CatalogGenerator(log);
        objectMapper = new ObjectMapper();
    }

    @Test
    void testComponentsWithConfigsAreIncluded() throws IOException {
        List<ForageComponent> components = new ArrayList<>();

        ForageComponent component = createComponent(
                "io.kaoto.forage",
                "forage-model-openai",
                "1.0",
                List.of(new ConfigEntry("forage.openai.api.key", "String", "API key", true)),
                Map.of("com.example.OpenAiConfig", "forage-model-openai"));

        components.add(component);

        List<File> files = generator.generateConfigurationCatalog(components, tempDir);

        assertThat(files).hasSize(1);
        File jsonFile = files.get(0);
        assertThat(jsonFile.getName()).isEqualTo("forage-configuration-catalog.json");
        assertThat(jsonFile).exists();

        ForageConfigurationCatalog catalog = objectMapper.readValue(jsonFile, ForageConfigurationCatalog.class);
        assertThat(catalog.getVersion()).isEqualTo("1.1");
        assertThat(catalog.getGeneratedBy()).isEqualTo("forage-maven-catalog-plugin");
        assertThat(catalog.getModules()).hasSize(1);

        ConfigurationModule module = catalog.getModules().get(0);
        assertThat(module.getArtifactId()).isEqualTo("forage-model-openai");
        assertThat(module.getGroupId()).isEqualTo("io.kaoto.forage");
        assertThat(module.getPropertiesFile()).isEqualTo("forage-model-openai.properties");
        assertThat(module.getConfigEntries()).hasSize(1);
        assertThat(module.getConfigEntries().get(0).getName()).isEqualTo("forage.openai.api.key");
    }

    @Test
    void testComponentsWithoutConfigsAreOmitted() throws IOException {
        List<ForageComponent> components = new ArrayList<>();

        // Component with no configs
        ForageComponent noConfigs = createComponent("io.kaoto.forage", "forage-core-common", "1.0", null, null);
        components.add(noConfigs);

        // Component with empty configs
        ForageComponent emptyConfigs = createComponent("io.kaoto.forage", "forage-core-ai", "1.0", List.of(), null);
        components.add(emptyConfigs);

        // Component with actual configs
        ForageComponent withConfigs = createComponent(
                "io.kaoto.forage",
                "forage-model-ollama",
                "1.0",
                List.of(new ConfigEntry("forage.ollama.url", "String", "Ollama URL", true)),
                Map.of("com.example.OllamaConfig", "forage-model-ollama"));
        components.add(withConfigs);

        List<File> files = generator.generateConfigurationCatalog(components, tempDir);
        ForageConfigurationCatalog catalog = objectMapper.readValue(files.get(0), ForageConfigurationCatalog.class);

        assertThat(catalog.getModules()).hasSize(1);
        assertThat(catalog.getModules().get(0).getArtifactId()).isEqualTo("forage-model-ollama");
    }

    @Test
    void testPropertiesFileResolution() throws IOException {
        List<ForageComponent> components = new ArrayList<>();

        // Component with config classes
        ForageComponent withConfigClass = createComponent(
                "io.kaoto.forage",
                "forage-vectordb-qdrant",
                "1.0",
                List.of(new ConfigEntry("forage.qdrant.host", "String", "Host", true)),
                Map.of("io.kaoto.forage.vectordb.qdrant.QdrantConfig", "forage-vectordb-qdrant"));
        components.add(withConfigClass);

        // Component without config classes
        ForageComponent noConfigClass = createComponent(
                "io.kaoto.forage",
                "forage-jdbc-common",
                "1.0",
                List.of(new ConfigEntry("forage.jdbc.url", "String", "JDBC URL", true)),
                null);
        components.add(noConfigClass);

        List<File> files = generator.generateConfigurationCatalog(components, tempDir);
        ForageConfigurationCatalog catalog = objectMapper.readValue(files.get(0), ForageConfigurationCatalog.class);

        assertThat(catalog.getModules()).hasSize(2);

        ConfigurationModule qdrantModule = catalog.getModules().stream()
                .filter(m -> m.getArtifactId().equals("forage-vectordb-qdrant"))
                .findFirst()
                .orElseThrow();
        assertThat(qdrantModule.getPropertiesFile()).isEqualTo("forage-vectordb-qdrant.properties");

        ConfigurationModule jdbcModule = catalog.getModules().stream()
                .filter(m -> m.getArtifactId().equals("forage-jdbc-common"))
                .findFirst()
                .orElseThrow();
        assertThat(jdbcModule.getPropertiesFile()).isNull();
    }

    @Test
    void testJsonSerializationRoundtrip() throws IOException {
        List<ForageComponent> components = new ArrayList<>();
        components.add(createComponent(
                "io.kaoto.forage",
                "forage-model-anthropic",
                "1.0",
                List.of(
                        new ConfigEntry("forage.anthropic.api.key", "String", "API key", true),
                        new ConfigEntry("forage.anthropic.model", "String", "Model name", false)),
                Map.of("com.example.AnthropicConfig", "forage-model-anthropic")));

        List<File> files = generator.generateConfigurationCatalog(components, tempDir);
        File jsonFile = files.get(0);

        // Deserialize
        ForageConfigurationCatalog catalog = objectMapper.readValue(jsonFile, ForageConfigurationCatalog.class);

        // Serialize again and compare
        String firstJson = objectMapper.writeValueAsString(catalog);
        ForageConfigurationCatalog roundtripped = objectMapper.readValue(firstJson, ForageConfigurationCatalog.class);

        assertThat(roundtripped.getVersion()).isEqualTo(catalog.getVersion());
        assertThat(roundtripped.getGeneratedBy()).isEqualTo(catalog.getGeneratedBy());
        assertThat(roundtripped.getModules()).hasSize(catalog.getModules().size());
        assertThat(roundtripped.getModules().get(0).getArtifactId())
                .isEqualTo(catalog.getModules().get(0).getArtifactId());
        assertThat(roundtripped.getModules().get(0).getConfigEntries())
                .hasSize(catalog.getModules().get(0).getConfigEntries().size());
    }

    @Test
    void testMultipleModulesPreservesAll() throws IOException {
        List<ForageComponent> components = new ArrayList<>();

        components.add(createComponent(
                "io.kaoto.forage",
                "forage-model-openai",
                "1.0",
                List.of(new ConfigEntry("forage.openai.api.key", "String", "Key", true)),
                Map.of("com.example.OpenAiConfig", "forage-model-openai")));

        components.add(createComponent(
                "io.kaoto.forage",
                "forage-vectordb-qdrant",
                "1.0",
                List.of(new ConfigEntry("forage.qdrant.host", "String", "Host", true)),
                Map.of("com.example.QdrantConfig", "forage-vectordb-qdrant")));

        components.add(createComponent(
                "io.kaoto.forage",
                "forage-memory-redis",
                "1.0",
                List.of(new ConfigEntry("forage.redis.url", "String", "URL", true)),
                Map.of("com.example.RedisConfig", "forage-memory-redis")));

        List<File> files = generator.generateConfigurationCatalog(components, tempDir);
        ForageConfigurationCatalog catalog = objectMapper.readValue(files.get(0), ForageConfigurationCatalog.class);

        assertThat(catalog.getModules()).hasSize(3);
        assertThat(catalog.getModules())
                .extracting(ConfigurationModule::getArtifactId)
                .containsExactly("forage-model-openai", "forage-vectordb-qdrant", "forage-memory-redis");
    }

    @Test
    void testYamlFormatGeneration() throws IOException {
        generator.setFormat("yaml");

        List<ForageComponent> components = new ArrayList<>();
        components.add(createComponent(
                "io.kaoto.forage",
                "forage-model-ollama",
                "1.0",
                List.of(new ConfigEntry("forage.ollama.url", "String", "URL", true)),
                Map.of("com.example.OllamaConfig", "forage-model-ollama")));

        List<File> files = generator.generateConfigurationCatalog(components, tempDir);
        assertThat(files).hasSize(1);
        assertThat(files.get(0).getName()).isEqualTo("forage-configuration-catalog.yaml");
    }

    @Test
    void testAllFormatGeneratesBothFiles() throws IOException {
        generator.setFormat("all");

        List<ForageComponent> components = new ArrayList<>();
        components.add(createComponent(
                "io.kaoto.forage",
                "forage-model-ollama",
                "1.0",
                List.of(new ConfigEntry("forage.ollama.url", "String", "URL", true)),
                Map.of("com.example.OllamaConfig", "forage-model-ollama")));

        List<File> files = generator.generateConfigurationCatalog(components, tempDir);
        assertThat(files).hasSize(2);
        assertThat(files)
                .extracting(File::getName)
                .containsExactlyInAnyOrder("forage-configuration-catalog.json", "forage-configuration-catalog.yaml");
    }

    private ForageComponent createComponent(
            String groupId,
            String artifactId,
            String version,
            List<ConfigEntry> configs,
            Map<String, String> configClasses) {

        ForageComponent component = new ForageComponent();
        component.setGroupId(groupId);
        component.setArtifactId(artifactId);
        component.setVersion(version);
        component.setConfigurationProperties(configs);
        component.setConfigClasses(configClasses);
        return component;
    }
}
