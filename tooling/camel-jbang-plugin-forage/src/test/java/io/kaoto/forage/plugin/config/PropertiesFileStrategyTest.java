package io.kaoto.forage.plugin.config;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;
import picocli.CommandLine;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link PropertiesFileStrategy} implementations and their selection.
 */
class PropertiesFileStrategyTest {

    private static ForageCatalogReader catalog;
    private static String jdbcFileName;

    @TempDir
    File tempDir;

    @BeforeAll
    static void loadCatalog() {
        catalog = ForageCatalogReader.getInstance();
        jdbcFileName = catalog.getPropertiesFileName("jdbc").orElseThrow();
    }

    @Test
    void fromIdIsCaseInsensitive() {
        assertThat(PropertiesFileStrategyType.fromId("application")).isEqualTo(PropertiesFileStrategyType.APPLICATION);
        assertThat(PropertiesFileStrategyType.fromId("Application")).isEqualTo(PropertiesFileStrategyType.APPLICATION);
        assertThat(PropertiesFileStrategyType.fromId("FORAGE")).isEqualTo(PropertiesFileStrategyType.FORAGE);
        assertThat(PropertiesFileStrategyType.fromId(" forage ")).isEqualTo(PropertiesFileStrategyType.FORAGE);
    }

    @Test
    void fromIdRejectsNull() {
        assertThatThrownBy(() -> PropertiesFileStrategyType.fromId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("application, forage");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "app", "forge", "application.properties"})
    void fromIdRejectsUnknownValues(String id) {
        assertThatThrownBy(() -> PropertiesFileStrategyType.fromId(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown properties file strategy");
    }

    @Test
    void createReturnsMatchingImplementation() {
        assertThat(PropertiesFileStrategyType.APPLICATION.create(null))
                .isInstanceOf(ApplicationPropertiesStrategy.class);
        assertThat(PropertiesFileStrategyType.FORAGE.create(catalog)).isInstanceOf(ForageMultiFileStrategy.class);
        assertThatThrownBy(() -> PropertiesFileStrategyType.FORAGE.create(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void optionDefaultsToApplication() {
        ConfigReadCommand command = new ConfigReadCommand(new CamelJBangMain());
        new CommandLine(command).parseArgs();
        assertThat(strategyType(command)).isEqualTo(PropertiesFileStrategyType.APPLICATION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"forage", "Forage", "FORAGE"})
    void optionAcceptsAnyCase(String value) {
        ConfigWriteCommand command = new ConfigWriteCommand(new CamelJBangMain());
        new CommandLine(command).parseArgs("--strategy", value);
        assertThat(strategyType(command)).isEqualTo(PropertiesFileStrategyType.FORAGE);
    }

    @Test
    void optionRejectsUnknownValue() {
        ConfigWriteCommand command = new ConfigWriteCommand(new CamelJBangMain());
        assertThatThrownBy(() -> new CommandLine(command).parseArgs("-s", "app"))
                .isInstanceOf(CommandLine.ParameterException.class)
                .hasMessageContaining("app");
    }

    @Test
    void applicationStrategyUsesApplicationPropertiesOnly() throws Exception {
        PropertiesFileStrategy strategy = new ApplicationPropertiesStrategy();
        assertThat(strategy.getPropertiesFileName("jdbc")).isEqualTo("application.properties");
        assertThat(strategy.getPropertiesFileName("unknown")).isEqualTo("application.properties");

        assertThat(strategy.findPropertiesFiles(tempDir)).isEmpty();

        File appProps = touch(tempDir, "application.properties");
        touch(tempDir, jdbcFileName);
        touch(tempDir, "forage-custom.properties");

        assertThat(strategy.findPropertiesFiles(tempDir)).containsExactly(appProps);
    }

    @Test
    void forageStrategyResolvesFileNameFromCatalog() {
        PropertiesFileStrategy strategy = new ForageMultiFileStrategy(catalog);
        assertThat(strategy.getPropertiesFileName("jdbc"))
                .isEqualTo(jdbcFileName)
                .startsWith("forage-");
        assertThat(strategy.getPropertiesFileName("not-a-factory")).isNull();
    }

    @Test
    void forageStrategyFindsCatalogAndWildcardFiles() throws Exception {
        PropertiesFileStrategy strategy = new ForageMultiFileStrategy(catalog);

        File catalogFile = touch(tempDir, jdbcFileName);
        File wildcardFile = touch(tempDir, "forage-custom.properties");
        touch(tempDir, "application.properties");
        touch(tempDir, "other.properties");
        touch(tempDir, "forage-notes.txt");
        File nested = new File(tempDir, "nested");
        assertThat(nested.mkdirs()).isTrue();
        touch(nested, "forage-nested.properties");

        List<File> files = strategy.findPropertiesFiles(tempDir);

        assertThat(files).containsExactlyInAnyOrder(catalogFile, wildcardFile);
    }

    private static PropertiesFileStrategyType strategyType(Object command) {
        try {
            java.lang.reflect.Field field = command.getClass().getDeclaredField("strategyOption");
            field.setAccessible(true);
            return ((PropertiesFileStrategyOption) field.get(command)).type;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static File touch(File dir, String name) throws Exception {
        File file = new File(dir, name);
        Files.writeString(file.toPath(), "# test\n");
        return file;
    }
}
