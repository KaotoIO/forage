package io.kaoto.forage.plugin.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import picocli.CommandLine;

/**
 * Base class for the {@code config} commands: shares the {@code --strategy} option, the catalog and strategy
 * resolution, and the JSON error output.
 */
abstract class AbstractConfigCommand extends CamelCommand {

    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @CommandLine.Mixin
    private PropertiesFileStrategyOption strategyOption = new PropertiesFileStrategyOption();

    protected ForageCatalogReader catalog;

    protected PropertiesFileStrategy strategy;

    protected AbstractConfigCommand(CamelJBangMain main) {
        super(main);
    }

    /**
     * Loads the catalog and resolves the strategy selected with {@code --strategy}.
     *
     * @return the error message if the selected strategy is unknown, empty otherwise
     */
    protected Optional<String> initCatalogAndStrategy() {
        catalog = ForageCatalogReader.getInstance();
        try {
            strategy = strategyOption.resolve(catalog);
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.of(e.getMessage());
        }
    }

    /**
     * Prints a JSON error response.
     *
     * @return the exit code {@code 1}
     */
    protected int outputError(String message) throws JsonProcessingException {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("success", false);
        error.put("error", message);
        printer().println(OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(error));
        return 1;
    }
}
