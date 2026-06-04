package io.kaoto.forage.plugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import io.kaoto.forage.core.util.config.ConfigStore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class QuarkusPropertyTranslatorTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void cleanUp() {
        ConfigStore.getInstance().reload();
    }

    @Nested
    class ExtractPrefixTest {

        @Test
        void defaultInstance() {
            assertThat(QuarkusPropertyTranslator.extractNamedPrefix("forage.jdbc.url", "jdbc"))
                    .isNull();
        }

        @Test
        void namedInstance() {
            assertThat(QuarkusPropertyTranslator.extractNamedPrefix("forage.ds1.jdbc.url", "jdbc"))
                    .isEqualTo("ds1");
        }

        @Test
        void modulePrefixOnly() {
            assertThat(QuarkusPropertyTranslator.extractNamedPrefix("forage.jdbc", "jdbc"))
                    .isNull();
        }

        @Test
        void namedInstanceModulePrefixOnly() {
            assertThat(QuarkusPropertyTranslator.extractNamedPrefix("forage.ds1.jdbc", "jdbc"))
                    .isEqualTo("ds1");
        }

        @Test
        void multiSegmentModulePrefix() {
            assertThat(QuarkusPropertyTranslator.extractNamedPrefix("forage.spring.rabbitmq.host", "spring.rabbitmq"))
                    .isNull();
        }

        @Test
        void multiSegmentModulePrefixWithNamedInstance() {
            assertThat(QuarkusPropertyTranslator.extractNamedPrefix(
                            "forage.mq1.spring.rabbitmq.host", "spring.rabbitmq"))
                    .isEqualTo("mq1");
        }

        @Test
        void nonForageProperty() {
            assertThat(QuarkusPropertyTranslator.extractNamedPrefix("quarkus.datasource.url", "jdbc"))
                    .isNull();
        }

        @Test
        void noMatchingModulePrefix() {
            assertThat(QuarkusPropertyTranslator.extractNamedPrefix("forage.unknown.property", "jdbc"))
                    .isNull();
        }
    }

    @Nested
    class TranslateTest {

        @Test
        void jdbcPropertiesTranslated() throws IOException {
            createPropertiesFile(
                    "application.properties",
                    "forage.jdbc.db.kind=postgresql",
                    "forage.jdbc.url=jdbc:postgresql://localhost:5432/mydb",
                    "forage.jdbc.username=admin",
                    "forage.jdbc.password=secret");

            QuarkusPropertyTranslator.TranslationResult result = QuarkusPropertyTranslator.translate(tempDir.toFile());

            assertThat(result.isEmpty()).isFalse();
            Map<String, String> qProps = flattenGroups(result);
            assertThat(qProps).containsEntry("quarkus.datasource.\"dataSource\".db-kind", "postgresql");
            assertThat(qProps)
                    .containsEntry(
                            "quarkus.datasource.\"dataSource\".jdbc.url", "jdbc:postgresql://localhost:5432/mydb");
            assertThat(qProps).containsEntry("quarkus.datasource.\"dataSource\".username", "admin");
            assertThat(qProps).containsEntry("quarkus.datasource.\"dataSource\".password", "secret");

            Set<String> translatedKeys = result.translatedForageKeys();
            assertThat(translatedKeys)
                    .contains("forage.jdbc.db.kind", "forage.jdbc.url", "forage.jdbc.username", "forage.jdbc.password");
        }

        @Test
        void namedJdbcInstancesTranslated() throws IOException {
            createPropertiesFile(
                    "application.properties",
                    "forage.jdbc.name=ds1,ds2",
                    "forage.ds1.jdbc.db.kind=postgresql",
                    "forage.ds1.jdbc.url=jdbc:postgresql://localhost/db1",
                    "forage.ds1.jdbc.username=user1",
                    "forage.ds1.jdbc.password=pass1",
                    "forage.ds2.jdbc.db.kind=mysql",
                    "forage.ds2.jdbc.url=jdbc:mysql://localhost/db2",
                    "forage.ds2.jdbc.username=user2",
                    "forage.ds2.jdbc.password=pass2");

            QuarkusPropertyTranslator.TranslationResult result = QuarkusPropertyTranslator.translate(tempDir.toFile());

            Map<String, String> qProps = flattenGroups(result);
            assertThat(qProps).containsEntry("quarkus.datasource.\"ds1\".db-kind", "postgresql");
            assertThat(qProps).containsEntry("quarkus.datasource.\"ds2\".db-kind", "mysql");

            assertThat(result.groups())
                    .extracting(QuarkusPropertyTranslator.TranslationGroup::moduleType)
                    .containsOnly("jdbc");
            assertThat(result.groups())
                    .extracting(QuarkusPropertyTranslator.TranslationGroup::prefix)
                    .contains("ds1", "ds2");
        }

        @Test
        void ollamaAgentPropertiesTranslated() throws IOException {
            createPropertiesFile(
                    "application.properties",
                    "forage.agent.model.kind=ollama",
                    "forage.agent.base.url=http://localhost:11434",
                    "forage.agent.model.name=llama3");

            QuarkusPropertyTranslator.TranslationResult result = QuarkusPropertyTranslator.translate(tempDir.toFile());

            assertThat(result.isEmpty()).isFalse();
            Map<String, String> qProps = flattenGroups(result);
            assertThat(qProps).containsEntry("quarkus.langchain4j.ollama.base-url", "http://localhost:11434");
            assertThat(qProps).containsEntry("quarkus.langchain4j.ollama.chat-model.model-id", "llama3");

            assertThat(result.groups())
                    .extracting(QuarkusPropertyTranslator.TranslationGroup::moduleType)
                    .containsOnly("agent");
        }

        @Test
        void anthropicAgentPropertiesTranslated() throws IOException {
            createPropertiesFile(
                    "application.properties",
                    "forage.agent.model.kind=anthropic",
                    "forage.agent.api.key=sk-test-key",
                    "forage.agent.model.name=claude-sonnet-4-20250514",
                    "forage.agent.temperature=0.7");

            QuarkusPropertyTranslator.TranslationResult result = QuarkusPropertyTranslator.translate(tempDir.toFile());

            assertThat(result.isEmpty()).isFalse();
            Map<String, String> qProps = flattenGroups(result);
            assertThat(qProps).containsEntry("quarkus.langchain4j.anthropic.api-key", "sk-test-key");
            assertThat(qProps)
                    .containsEntry("quarkus.langchain4j.anthropic.chat-model.model-name", "claude-sonnet-4-20250514");
            assertThat(qProps).containsEntry("quarkus.langchain4j.anthropic.chat-model.temperature", "0.7");
        }

        @Test
        void emptyDirectoryProducesEmptyResult() throws IOException {
            QuarkusPropertyTranslator.TranslationResult result = QuarkusPropertyTranslator.translate(tempDir.toFile());

            assertThat(result.isEmpty()).isTrue();
            assertThat(result.translatedForageKeys()).isEmpty();
        }

        @Test
        void nonForagePropertiesIgnored() throws IOException {
            createPropertiesFile(
                    "application.properties", "server.port=8080", "quarkus.datasource.url=jdbc:h2:mem:test");

            QuarkusPropertyTranslator.TranslationResult result = QuarkusPropertyTranslator.translate(tempDir.toFile());

            assertThat(result.isEmpty()).isTrue();
            assertThat(result.translatedForageKeys()).isEmpty();
        }
    }

    private static Map<String, String> flattenGroups(QuarkusPropertyTranslator.TranslationResult result) {
        Map<String, String> flat = new LinkedHashMap<>();
        for (QuarkusPropertyTranslator.TranslationGroup group : result.groups()) {
            flat.putAll(group.properties());
        }
        return flat;
    }

    private File createPropertiesFile(String filename, String... lines) throws IOException {
        File file = tempDir.resolve(filename).toFile();
        try (FileWriter writer = new FileWriter(file)) {
            for (String line : lines) {
                writer.write(line + "\n");
            }
        }
        return file;
    }
}
