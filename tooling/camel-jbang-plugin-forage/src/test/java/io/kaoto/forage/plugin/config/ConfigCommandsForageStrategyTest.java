package io.kaoto.forage.plugin.config;

import java.io.File;
import java.nio.file.Files;
import java.util.Properties;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static io.kaoto.forage.plugin.config.ConfigCommandTestSupport.setField;
import static io.kaoto.forage.plugin.config.ConfigCommandTestSupport.setStrategy;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests {@code config read} and {@code config write} with {@code --strategy forage}, the strategy validation and
 * which instance a delete removes, across files.
 */
class ConfigCommandsForageStrategyTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String PG_JSON =
            """
            {
              "forage.jdbc.db.kind": "postgresql",
              "forage.jdbc.url": "jdbc:postgresql://localhost:5432/",
              "kind": "postgresql",
              "forage.bean.name": "%s"
            }
            """;

    private static String jdbcFileName;

    @TempDir
    File tempDir;

    private StringPrinter printer;

    @BeforeAll
    static void loadCatalog() {
        jdbcFileName =
                ForageCatalogReader.getInstance().getPropertiesFileName("jdbc").orElseThrow();
    }

    @Test
    void writeUsesCatalogFileAndWritesNoDependencies() throws Exception {
        Result result = write(PropertiesFileStrategyType.FORAGE, PG_JSON.formatted("myPG"));

        assertThat(result.exitCode()).isZero();
        File forageFile = new File(tempDir, jdbcFileName);
        assertThat(result.json()
                        .get("factories")
                        .get("jdbc")
                        .get("propertiesFile")
                        .asText())
                .isEqualTo(forageFile.getAbsolutePath());

        Properties forageProps = load(forageFile);
        assertThat(forageProps.getProperty("forage.myPG.jdbc.db.kind")).isEqualTo("postgresql");
        assertThat(forageProps.getProperty("camel.jbang.dependencies")).isNull();
        // The Forage plugin resolves the dependencies: application.properties is not needed
        assertThat(new File(tempDir, "application.properties")).doesNotExist();
    }

    @Test
    void writeAndDeleteRemoveForageDependenciesOfOlderVersions() throws Exception {
        File appFile = new File(tempDir, "application.properties");
        Files.writeString(
                appFile.toPath(),
                """
                camel.jbang.dependencies=com.example:lib:1.0,io.kaoto.forage:forage-jdbc-postgresql:1.0.0
                camel.jbang.dependencies.main=io.kaoto.forage:forage-jdbc:1.0.0
                """);

        Result result = write(PropertiesFileStrategyType.FORAGE, PG_JSON.formatted("myPG"));

        assertThat(result.exitCode()).isZero();
        assertThat(result.json().get("dependencyCleanup").get("count").asInt()).isEqualTo(2);
        assertThat(Files.readString(appFile.toPath())).isEqualTo("camel.jbang.dependencies=com.example:lib:1.0\n");

        // Nothing left to remove on delete
        JsonNode deleted = delete("myPG", PropertiesFileStrategyType.FORAGE).json();
        assertThat(deleted.get("success").asBoolean()).isTrue();
        assertThat(deleted.get("results").has("dependencyCleanup")).isFalse();
        assertThat(Files.readString(appFile.toPath())).isEqualTo("camel.jbang.dependencies=com.example:lib:1.0\n");
    }

    @Test
    void writeFailsForFactoryWithoutDedicatedFile() throws Exception {
        // WebSearch is in the catalog but has no properties file of its own
        Result result = write(PropertiesFileStrategyType.FORAGE, "{\"forage.tavily.api.key\": \"secret\"}");

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.json().get("success").asBoolean()).isFalse();
        assertThat(result.json().get("error").asText()).contains("--strategy application");
        assertThat(tempDir.listFiles()).isEmpty();

        // The application strategy can still store it
        Result appResult = write(PropertiesFileStrategyType.APPLICATION, "{\"forage.tavily.api.key\": \"secret\"}");
        assertThat(appResult.exitCode()).isZero();
        assertThat(load(new File(tempDir, "application.properties")).getProperty("forage.tavily.api.key"))
                .isEqualTo("secret");
    }

    @Test
    void readListsBeansFromCatalogAndWildcardFilesOnly() throws Exception {
        Files.writeString(new File(tempDir, jdbcFileName).toPath(), "forage.ds1.jdbc.db.kind=postgresql\n");
        Files.writeString(new File(tempDir, "forage-custom.properties").toPath(), "forage.ds2.jdbc.db.kind=mysql\n");
        Files.writeString(new File(tempDir, "application.properties").toPath(), "forage.ds3.jdbc.db.kind=h2\n");

        JsonNode result = read(PropertiesFileStrategyType.FORAGE.id()).json();

        assertThat(result.get("beanCount").asInt()).isEqualTo(2);
        assertThat(result.get("beans").findValuesAsText("name")).containsExactlyInAnyOrder("ds1", "ds2");
    }

    @Test
    void invalidStrategyIsReportedAsJsonError() throws Exception {
        Result readResult = read("app");
        assertThat(readResult.exitCode()).isEqualTo(1);
        assertThat(readResult.json().get("success").asBoolean()).isFalse();
        assertThat(readResult.json().get("error").asText()).contains("Unknown properties file strategy 'app'");

        ConfigWriteCommand write = new ConfigWriteCommand(main());
        setField(write, "input", PG_JSON.formatted("myPG"));
        Result writeResult = run(write, "forge");
        assertThat(writeResult.exitCode()).isEqualTo(1);
        assertThat(writeResult.json().get("error").asText()).contains("Unknown properties file strategy 'forge'");
        assertThat(tempDir.listFiles()).isEmpty();
    }

    @Test
    void strategyIsCaseInsensitive() throws Exception {
        Result result = read("FORAGE");
        assertThat(result.exitCode()).isZero();
        assertThat(result.json().get("success").asBoolean()).isTrue();
    }

    @Test
    void deleteRemovesWhateverReadLists() throws Exception {
        // A file only matched by the forage-*.properties wildcard, not by a catalog file name
        File customFile = new File(tempDir, "forage-custom.properties");
        Files.writeString(customFile.toPath(), "forage.custom.jdbc.db.kind=postgresql\n");

        assertThat(read(PropertiesFileStrategyType.FORAGE.id())
                        .json()
                        .get("beans")
                        .findValuesAsText("name"))
                .contains("custom");

        JsonNode result = delete("custom", PropertiesFileStrategyType.FORAGE).json();

        assertThat(result.get("success").asBoolean()).isTrue();
        assertThat(result.get("results").has("forage-custom.properties")).isTrue();
        assertThat(load(customFile).getProperty("forage.custom.jdbc.db.kind")).isNull();
    }

    @Test
    void deleteDoesNotScanFilesOfTheOtherStrategy() throws Exception {
        File appFile = new File(tempDir, "application.properties");
        Files.writeString(appFile.toPath(), "forage.myPG.jdbc.db.kind=postgresql\n");

        JsonNode result = delete("myPG", PropertiesFileStrategyType.FORAGE).json();

        assertThat(result.get("success").asBoolean()).isFalse();
        assertThat(load(appFile).getProperty("forage.myPG.jdbc.db.kind")).isEqualTo("postgresql");
    }

    @Test
    void deleteOnlyTouchesTheFilesOfItsStrategy() throws Exception {
        // One PostgreSQL instance in application.properties, another in the forage file
        assertThat(write(PropertiesFileStrategyType.APPLICATION, PG_JSON.formatted("appPG"))
                        .exitCode())
                .isZero();
        assertThat(write(PropertiesFileStrategyType.FORAGE, PG_JSON.formatted("foragePG"))
                        .exitCode())
                .isZero();

        assertThat(delete("foragePG", PropertiesFileStrategyType.FORAGE).exitCode())
                .isZero();
        assertThat(load(new File(tempDir, jdbcFileName)).getProperty("forage.foragePG.jdbc.db.kind"))
                .isNull();
        File appFile = new File(tempDir, "application.properties");
        assertThat(load(appFile).getProperty("forage.appPG.jdbc.db.kind")).isEqualTo("postgresql");

        assertThat(delete("appPG", PropertiesFileStrategyType.APPLICATION).exitCode())
                .isZero();
        assertThat(load(appFile).getProperty("forage.appPG.jdbc.db.kind")).isNull();
    }

    @Test
    void deleteNamedInstanceKeepsTheDefaultInstance() throws Exception {
        // A default (unnamed) RabbitMQ instance has no instance segment and no *.kind property
        assertThat(write(PropertiesFileStrategyType.APPLICATION, "{\"forage.rabbitmq.host\": \"a\"}")
                        .exitCode())
                .isZero();
        assertThat(write(
                                PropertiesFileStrategyType.APPLICATION,
                                "{\"forage.rabbitmq.host\": \"b\", \"forage.bean.name\": \"second\"}")
                        .exitCode())
                .isZero();

        assertThat(delete("second", PropertiesFileStrategyType.APPLICATION).exitCode())
                .isZero();

        Properties appProps = load(new File(tempDir, "application.properties"));
        assertThat(appProps.getProperty("forage.rabbitmq.host")).isEqualTo("a");
        assertThat(appProps.getProperty("forage.second.rabbitmq.host")).isNull();
    }

    @Test
    void deleteKeepsOtherInstancesOfProfileFiles() throws Exception {
        assertThat(write(PropertiesFileStrategyType.APPLICATION, PG_JSON.formatted("myPG"))
                        .exitCode())
                .isZero();
        File prodFile = new File(tempDir, "application-prod.properties");
        Files.writeString(prodFile.toPath(), "forage.prodPG.jdbc.db.kind=postgresql\n");

        assertThat(delete("myPG", PropertiesFileStrategyType.APPLICATION).exitCode())
                .isZero();

        assertThat(load(new File(tempDir, "application.properties")).getProperty("forage.myPG.jdbc.db.kind"))
                .isNull();
        assertThat(load(prodFile).getProperty("forage.prodPG.jdbc.db.kind")).isEqualTo("postgresql");
    }

    @Test
    void deleteRemovesOnlyTheDeletedAgent() throws Exception {
        writeAgents();

        assertThat(delete("myOllama", PropertiesFileStrategyType.APPLICATION).exitCode())
                .isZero();

        Properties appProps = load(new File(tempDir, "application.properties"));
        assertThat(appProps.getProperty("forage.myOllama.ollama.model.name")).isNull();
        assertThat(appProps.getProperty("forage.myG.google.api.key")).isEqualTo("x");
        assertThat(appProps.getProperty("forage.myG.google.model.name")).isEqualTo("gemini");
    }

    @Test
    void deleteInstanceNamedLikeABeanOnlyRemovesItsOwnKeys() throws Exception {
        // A Gemini agent (bean prefix "google", unlike its bean name) plus a DataSource called "ollama"
        writeGeminiAgent();
        assertThat(write(PropertiesFileStrategyType.APPLICATION, PG_JSON.formatted("ollama"))
                        .exitCode())
                .isZero();

        assertThat(delete("ollama", PropertiesFileStrategyType.APPLICATION).exitCode())
                .isZero();

        Properties appProps = load(new File(tempDir, "application.properties"));
        assertThat(appProps.getProperty("forage.ollama.jdbc.db.kind")).isNull();
        assertThat(appProps.getProperty("forage.myG.google.api.key")).isEqualTo("x");
    }

    @Test
    void deleteNamedBeanInstanceKeepsTheDefaultBeanInstance() throws Exception {
        writeAgents();
        File appFile = new File(tempDir, "application.properties");
        Files.writeString(appFile.toPath(), "forage.ollama.model.name=llama3\n" + Files.readString(appFile.toPath()));

        assertThat(delete("myOllama", PropertiesFileStrategyType.APPLICATION).exitCode())
                .isZero();

        Properties appProps = load(appFile);
        assertThat(appProps.getProperty("forage.ollama.model.name")).isEqualTo("llama3");
        assertThat(appProps.getProperty("forage.myOllama.ollama.model.name")).isNull();
    }

    @Test
    void dottedInstanceNamesAreParsedLikeTheRuntime() throws Exception {
        assertThat(write(PropertiesFileStrategyType.APPLICATION, PG_JSON.formatted("myPG"))
                        .exitCode())
                .isZero();
        File appFile = new File(tempDir, "application.properties");
        Files.writeString(
                appFile.toPath(), "forage.my.ds.jdbc.url=jdbc:h2:mem:x\n" + Files.readString(appFile.toPath()));

        // "my" is not an instance: my.ds must survive
        assertThat(delete("my", PropertiesFileStrategyType.APPLICATION).exitCode())
                .isEqualTo(1);
        assertThat(load(appFile).getProperty("forage.my.ds.jdbc.url")).isEqualTo("jdbc:h2:mem:x");

        // The dotted instance itself can be deleted
        assertThat(delete("my.ds", PropertiesFileStrategyType.APPLICATION).exitCode())
                .isZero();
        Properties appProps = load(appFile);
        assertThat(appProps.getProperty("forage.my.ds.jdbc.url")).isNull();
        assertThat(appProps.getProperty("forage.myPG.jdbc.db.kind")).isEqualTo("postgresql");
    }

    @Test
    void rejectedWriteDoesNotCreateTheDirectory() throws Exception {
        File missingDir = new File(tempDir, "missing");
        ConfigWriteCommand command = new ConfigWriteCommand(main());
        setField(command, "input", "{\"forage.tavily.api.key\": \"secret\"}");
        setField(command, "directory", missingDir);
        setStrategy(command, PropertiesFileStrategyType.FORAGE);

        assertThat(command.doCall()).isEqualTo(1);
        assertThat(missingDir).doesNotExist();
    }

    @Test
    void deleteRemovesEveryKeyOfAnInstanceNamedLikeABeanPrefix() throws Exception {
        // An OpenAI instance named "azure": forage.azure.openai.api.key is also the default Azure OpenAI entry
        File appFile = new File(tempDir, "application.properties");
        Files.writeString(appFile.toPath(), "forage.azure.openai.api.key=k\nforage.azure.openai.model.name=gpt\n");

        JsonNode result =
                delete("azure", PropertiesFileStrategyType.APPLICATION).json();

        assertThat(result.get("success").asBoolean()).isTrue();
        assertThat(result.get("results")
                        .get("application.properties")
                        .get("deletedProperties")
                        .asInt())
                .isEqualTo(2);
        assertThat(load(appFile)).isEmpty();
    }

    @Test
    void writeRejectsWebSearchKeysUnderTheForageStrategy() throws Exception {
        // google.custom.search.* belongs to WebSearch, not to the Gemini agent bean that also uses "google"
        String json = "{\"forage.google.custom.search.api.key\": \"k\", \"forage.google.custom.search.csi\": \"c\"}";

        Result result = write(PropertiesFileStrategyType.FORAGE, json);
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.json().get("error").asText()).contains("WebSearch has no dedicated properties file");
        assertThat(tempDir.listFiles()).isEmpty();

        assertThat(write(PropertiesFileStrategyType.APPLICATION, json).exitCode())
                .isZero();
        Properties appProps = load(new File(tempDir, "application.properties"));
        assertThat(appProps.getProperty("forage.google.custom.search.api.key")).isEqualTo("k");
    }

    @Test
    void deleteFindsEntriesWrittenWithAnySeparator() throws Exception {
        File appFile = new File(tempDir, "application.properties");
        Files.writeString(
                appFile.toPath(),
                """
                # DataSources
                forage.ds1.jdbc.db.kind: postgresql
                forage.ds1.jdbc.url jdbc:h2:mem:x
                forage.ds1.jdbc.password = long\\
                    -secret
                forage.ds2.jdbc.db.kind=h2
                """);
        assertThat(read(PropertiesFileStrategyType.APPLICATION.id())
                        .json()
                        .get("beans")
                        .findValuesAsText("name"))
                .contains("ds1", "ds2");

        JsonNode result = delete("ds1", PropertiesFileStrategyType.APPLICATION).json();

        assertThat(result.get("success").asBoolean()).isTrue();
        assertThat(Files.readString(appFile.toPath())).isEqualTo("# DataSources\nforage.ds2.jdbc.db.kind=h2\n");
    }

    @Test
    void deleteInMissingDirectoryReportsNoConfiguration() throws Exception {
        for (PropertiesFileStrategyType type : PropertiesFileStrategyType.values()) {
            ConfigWriteCommand command = new ConfigWriteCommand(main());
            setField(command, "delete", true);
            setField(command, "instanceName", "x");
            setField(command, "directory", new File(tempDir, "missing"));
            setStrategy(command, type);

            assertThat(command.doCall()).as(type.id()).isEqualTo(1);
            assertThat(OBJECT_MAPPER.readTree(printer.getOutput()).get("error").asText())
                    .as(type.id())
                    .isEqualTo("No configuration found for instance 'x'");
        }
    }

    @Test
    void deleteRemovesProfileOverridesOfTheInstance() throws Exception {
        assertThat(write(PropertiesFileStrategyType.APPLICATION, PG_JSON.formatted("myPG"))
                        .exitCode())
                .isZero();
        File devFile = new File(tempDir, "application-dev.properties");
        Files.writeString(devFile.toPath(), "camel.main.name=dev\nforage.myPG.jdbc.url=jdbc:postgresql://dev/db\n");

        JsonNode result = delete("myPG", PropertiesFileStrategyType.APPLICATION).json();

        assertThat(result.get("success").asBoolean()).isTrue();
        assertThat(result.get("results").has("application-dev.properties")).isTrue();
        assertThat(Files.readString(devFile.toPath())).isEqualTo("camel.main.name=dev\n");
        assertThat(load(new File(tempDir, "application.properties")).getProperty("forage.myPG.jdbc.db.kind"))
                .isNull();
    }

    @Test
    void deleteRemovesTypoKeysOfAnInstanceNamedLikeABean() throws Exception {
        // An agent named "ollama" with a typo in one key, next to a default Ollama chat model
        File appFile = new File(tempDir, "application.properties");
        Files.writeString(
                appFile.toPath(),
                """
                forage.ollama.agent.model.kind=ollama
                forage.ollama.agent.modle.nmae=llama3
                forage.ollama.model.name=llama3
                """);

        JsonNode result =
                delete("ollama", PropertiesFileStrategyType.APPLICATION).json();

        assertThat(result.get("success").asBoolean()).isTrue();
        assertThat(Files.readString(appFile.toPath())).isEqualTo("forage.ollama.model.name=llama3\n");
    }

    @Test
    void writeEscapesKeysAndValues() throws Exception {
        String json = "{\"forage.jdbc.url\": \"jdbc:h2:file:C:\\\\data\\\\db\", \"forage.bean.name\": \"ds1\"}";

        assertThat(write(PropertiesFileStrategyType.APPLICATION, json).exitCode())
                .isZero();
        // Written again, to go through the update path too
        assertThat(write(PropertiesFileStrategyType.APPLICATION, json).exitCode())
                .isZero();

        assertThat(load(new File(tempDir, "application.properties")).getProperty("forage.ds1.jdbc.url"))
                .isEqualTo("jdbc:h2:file:C:\\data\\db");
    }

    private void writeAgents() throws Exception {
        assertThat(write(
                                PropertiesFileStrategyType.APPLICATION,
                                """
                                {"forage.ollama.model.name": "llama3", "kind": "ollama", "forage.bean.name": "myOllama"}
                                """)
                        .exitCode())
                .isZero();
        writeGeminiAgent();
        assertThat(load(new File(tempDir, "application.properties")).getProperty("forage.myOllama.ollama.model.name"))
                .isEqualTo("llama3");
    }

    private void writeGeminiAgent() throws Exception {
        assertThat(write(
                                PropertiesFileStrategyType.APPLICATION,
                                """
                                {"forage.google.api.key": "x", "forage.google.model.name": "gemini",
                                 "kind": "google-gemini", "forage.bean.name": "myG"}
                                """)
                        .exitCode())
                .isZero();
        assertThat(load(new File(tempDir, "application.properties")).getProperty("forage.myG.google.api.key"))
                .isEqualTo("x");
    }

    private Result write(PropertiesFileStrategyType type, String json) throws Exception {
        ConfigWriteCommand command = new ConfigWriteCommand(main());
        setField(command, "input", json);
        return run(command, type.id());
    }

    private Result read(String strategy) throws Exception {
        return run(new ConfigReadCommand(main()), strategy);
    }

    private Result delete(String name, PropertiesFileStrategyType type) throws Exception {
        ConfigWriteCommand command = new ConfigWriteCommand(main());
        setField(command, "delete", true);
        setField(command, "instanceName", name);
        return run(command, type.id());
    }

    private CamelJBangMain main() {
        printer = new StringPrinter();
        return new CamelJBangMain().withPrinter(printer);
    }

    private Result run(CamelCommand command, String strategy) throws Exception {
        setField(command, "directory", tempDir);
        setStrategy(command, strategy);
        int exitCode = command.doCall();
        return new Result(exitCode, OBJECT_MAPPER.readTree(printer.getOutput()));
    }

    private static Properties load(File file) throws Exception {
        Properties props = new Properties();
        try (var in = Files.newInputStream(file.toPath())) {
            props.load(in);
        }
        return props;
    }

    private record Result(int exitCode, JsonNode json) {}
}
