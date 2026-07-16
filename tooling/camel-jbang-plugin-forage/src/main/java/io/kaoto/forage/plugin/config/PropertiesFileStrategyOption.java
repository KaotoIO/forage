package io.kaoto.forage.plugin.config;

import java.util.Arrays;
import java.util.Iterator;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;
import picocli.CommandLine;

/**
 * Picocli mixin providing the shared {@code --strategy} option of the {@code config} commands.
 *
 * <p>The raw value is kept as a string and parsed in {@link #resolve(ForageCatalogReader)}, so that an unknown
 * value is reported by the command as a JSON error (exit code 1) instead of a picocli usage error.
 * Values are matched case-insensitively.
 */
public class PropertiesFileStrategyOption {

    @CommandLine.Option(
            names = {"--strategy", "-s"},
            description = "Property file strategy: 'application' uses application.properties (default), "
                    + "'forage' uses the per-factory forage-*.properties files.",
            defaultValue = "application",
            completionCandidates = Candidates.class)
    String strategy;

    /**
     * Creates the strategy selected on the command line.
     *
     * @throws IllegalArgumentException if the selected strategy is unknown
     */
    PropertiesFileStrategy resolve(ForageCatalogReader catalog) {
        return PropertiesFileStrategyType.fromId(strategy).create(catalog);
    }

    static final class Candidates implements Iterable<String> {
        @Override
        public Iterator<String> iterator() {
            return Arrays.stream(PropertiesFileStrategyType.values())
                    .map(PropertiesFileStrategyType::id)
                    .iterator();
        }
    }
}
