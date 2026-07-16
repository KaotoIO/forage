package io.kaoto.forage.plugin.config;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Properties;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests {@code config read} and {@code config write} with {@code --strategy forage}.
 */
class ConfigCommandsForageStrategyTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static String jdbcFileName;

    @TempDir
    File tempDir;

    @BeforeAll
    static void loadCatalog() {
        jdbcFileName =
                ForageCatalogReader.getInstance().getPropertiesFileName("jdbc").orElseThrow();
    }

    @Test
    void writeUsesCatalogFileAndKeepsDependenciesInApplicationProperties() throws Exception {
        JsonNode result = write(
                """
                {
                  "forage.jdbc.db.kind": "postgresql",
                  "forage.jdbc.url": "jdbc:postgresql://localhost:5432/",
                  "kind": "postgresql",
                  "forage.bean.name": "myPG"
                }
                """);

        assertThat(result.get("success").asBoolean()).isTrue();
        File forageFile = new File(tempDir, jdbcFileName);
        assertThat(result.get("factories").get("jdbc").get("propertiesFile").asText())
                .isEqualTo(forageFile.getAbsolutePath());

        Properties forageProps = load(forageFile);
        assertThat(forageProps.getProperty("forage.myPG.jdbc.db.kind")).isEqualTo("postgresql");
        assertThat(forageProps.getProperty("camel.jbang.dependencies")).isNull();

        Properties appProps = load(new File(tempDir, "application.properties"));
        assertThat(appProps.getProperty("forage.myPG.jdbc.db.kind")).isNull();
        assertThat(appProps.getProperty("camel.jbang.dependencies")).contains("forage-jdbc-postgresql");
    }

    @Test
    void readListsBeansFromCatalogAndWildcardFilesOnly() throws Exception {
        Files.writeString(new File(tempDir, jdbcFileName).toPath(), "forage.ds1.jdbc.db.kind=postgresql\n");
        Files.writeString(new File(tempDir, "forage-custom.properties").toPath(), "forage.ds2.jdbc.db.kind=mysql\n");
        Files.writeString(new File(tempDir, "application.properties").toPath(), "forage.ds3.jdbc.db.kind=h2\n");

        JsonNode result = read(PropertiesFileStrategyType.FORAGE);

        assertThat(result.get("beanCount").asInt()).isEqualTo(2);
        assertThat(result.get("beans").findValuesAsText("name")).containsExactlyInAnyOrder("ds1", "ds2");
    }

    @Test
    void deleteRemovesWhateverReadLists() throws Exception {
        // A file only matched by the forage-*.properties wildcard, not by a catalog file name
        File customFile = new File(tempDir, "forage-custom.properties");
        Files.writeString(customFile.toPath(), "forage.custom.jdbc.db.kind=postgresql\n");

        assertThat(read(PropertiesFileStrategyType.FORAGE).get("beans").findValuesAsText("name"))
                .contains("custom");

        JsonNode result = delete("custom", PropertiesFileStrategyType.FORAGE);

        assertThat(result.get("success").asBoolean()).isTrue();
        assertThat(result.get("results").has("forage-custom.properties")).isTrue();
        assertThat(load(customFile).getProperty("forage.custom.jdbc.db.kind")).isNull();
    }

    @Test
    void deleteDoesNotScanFilesOfTheOtherStrategy() throws Exception {
        File appFile = new File(tempDir, "application.properties");
        Files.writeString(appFile.toPath(), "forage.myPG.jdbc.db.kind=postgresql\n");

        JsonNode result = delete("myPG", PropertiesFileStrategyType.FORAGE);

        assertThat(result.get("success").asBoolean()).isFalse();
        assertThat(load(appFile).getProperty("forage.myPG.jdbc.db.kind")).isEqualTo("postgresql");
    }

    @Test
    void deleteKeepsDependenciesStillNeededByTheOtherFamily() throws Exception {
        // One PostgreSQL instance in application.properties, another in the forage file
        writeWithStrategy(PropertiesFileStrategyType.APPLICATION, "appPG");
        writeWithStrategy(PropertiesFileStrategyType.FORAGE, "foragePG");

        File appFile = new File(tempDir, "application.properties");
        assertThat(load(appFile).getProperty("camel.jbang.dependencies")).contains("forage-jdbc-postgresql");

        JsonNode result = delete("foragePG", PropertiesFileStrategyType.FORAGE);
        assertThat(result.get("success").asBoolean()).isTrue();
        assertThat(result.get("results").has("dependencyCleanup")).isFalse();

        Properties appProps = load(appFile);
        assertThat(appProps.getProperty("forage.appPG.jdbc.db.kind")).isEqualTo("postgresql");
        assertThat(appProps.getProperty("camel.jbang.dependencies")).contains("forage-jdbc-postgresql");
        assertThat(appProps.getProperty("camel.jbang.dependencies.main")).contains("forage-jdbc:");

        // Deleting the last instance removes the dependencies
        delete("appPG", PropertiesFileStrategyType.APPLICATION);
        appProps = load(appFile);
        assertThat(appProps.getProperty("camel.jbang.dependencies")).isNull();
        assertThat(appProps.getProperty("camel.jbang.dependencies.main")).isNull();
    }

    private void writeWithStrategy(PropertiesFileStrategyType type, String beanName) throws Exception {
        JsonNode result = run(
                new ConfigWriteCommand(main()),
                type,
                "input",
                """
                {
                  "forage.jdbc.db.kind": "postgresql",
                  "forage.jdbc.url": "jdbc:postgresql://localhost:5432/",
                  "kind": "postgresql",
                  "forage.bean.name": "%s"
                }
                """
                        .formatted(beanName));
        assertThat(result.get("success").asBoolean()).isTrue();
    }

    private JsonNode write(String json) throws Exception {
        return run(new ConfigWriteCommand(main()), PropertiesFileStrategyType.FORAGE, "input", json);
    }

    private JsonNode read(PropertiesFileStrategyType type) throws Exception {
        return run(new ConfigReadCommand(main()), type, null, null);
    }

    private JsonNode delete(String name, PropertiesFileStrategyType type) throws Exception {
        ConfigWriteCommand command = new ConfigWriteCommand(main());
        setField(command, "delete", true);
        return run(command, type, "instanceName", name);
    }

    private StringPrinter printer;

    private CamelJBangMain main() {
        printer = new StringPrinter();
        return new CamelJBangMain().withPrinter(printer);
    }

    private JsonNode run(Object command, PropertiesFileStrategyType type, String field, String value) throws Exception {
        setField(command, "directory", tempDir);
        Field option = command.getClass().getDeclaredField("strategyOption");
        option.setAccessible(true);
        ((PropertiesFileStrategyOption) option.get(command)).type = type;
        if (field != null) {
            setField(command, field, value);
        }
        if (command instanceof ConfigWriteCommand write) {
            write.doCall();
        } else {
            ((ConfigReadCommand) command).doCall();
        }
        return OBJECT_MAPPER.readTree(printer.getOutput());
    }

    private static Properties load(File file) throws Exception {
        Properties props = new Properties();
        try (var in = Files.newInputStream(file.toPath())) {
            props.load(in);
        }
        return props;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
