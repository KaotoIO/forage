package io.kaoto.forage.plugin.config;

import java.util.Arrays;
import java.util.Iterator;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;
import picocli.CommandLine;

/**
 * Picocli mixin providing the shared {@code --strategy} option of the {@code config} commands.
 *
 * <p>Values are matched case-insensitively; unknown values are rejected by picocli.
 */
public class PropertiesFileStrategyOption {

    @CommandLine.Option(
            names = {"--strategy", "-s"},
            description = "Property file strategy: 'application' uses application.properties (default), "
                    + "'forage' uses the per-factory forage-*.properties files.",
            defaultValue = "application",
            converter = TypeConverter.class,
            completionCandidates = Candidates.class)
    PropertiesFileStrategyType type = PropertiesFileStrategyType.APPLICATION;

    /**
     * Creates the strategy selected on the command line.
     */
    PropertiesFileStrategy resolve(ForageCatalogReader catalog) {
        return type.create(catalog);
    }

    static final class TypeConverter implements CommandLine.ITypeConverter<PropertiesFileStrategyType> {
        @Override
        public PropertiesFileStrategyType convert(String value) {
            try {
                return PropertiesFileStrategyType.fromId(value);
            } catch (IllegalArgumentException e) {
                throw new CommandLine.TypeConversionException(e.getMessage());
            }
        }
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
