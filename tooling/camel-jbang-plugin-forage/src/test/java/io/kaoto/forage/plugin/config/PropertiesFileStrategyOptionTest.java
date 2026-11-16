package io.kaoto.forage.plugin.config;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;
import io.kaoto.forage.config.editor.PropertiesFileStrategyType;
import picocli.CommandLine;

import static io.kaoto.forage.plugin.config.ConfigCommandTestSupport.getStrategy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the {@code --strategy} option shared by the {@code config} commands.
 */
class PropertiesFileStrategyOptionTest {

    @Test
    void optionDefaultsToApplication() throws Exception {
        ConfigReadCommand command = new ConfigReadCommand(new CamelJBangMain());
        new CommandLine(command).parseArgs();
        assertThat(getStrategy(command)).isEqualTo("application");
    }

    @ParameterizedTest
    @ValueSource(strings = {"forage", "Forage", "FORAGE"})
    void optionAcceptsAnyCase(String value) throws Exception {
        ConfigWriteCommand command = new ConfigWriteCommand(new CamelJBangMain());
        new CommandLine(command).parseArgs("--strategy", value);
        assertThat(PropertiesFileStrategyType.fromId(getStrategy(command)))
                .isEqualTo(PropertiesFileStrategyType.FORAGE);
    }

    @Test
    void optionDefersValidationToTheCommand() throws Exception {
        // picocli accepts any value so that the command can report it as a JSON error
        ConfigWriteCommand command = new ConfigWriteCommand(new CamelJBangMain());
        new CommandLine(command).parseArgs("-s", "app");
        assertThat(getStrategy(command)).isEqualTo("app");

        PropertiesFileStrategyOption option = new PropertiesFileStrategyOption();
        option.strategy = "app";
        assertThatThrownBy(() -> option.resolve(ForageCatalogReader.getInstance()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown properties file strategy 'app'");
    }
}
