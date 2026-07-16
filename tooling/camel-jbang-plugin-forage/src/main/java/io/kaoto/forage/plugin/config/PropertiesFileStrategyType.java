package io.kaoto.forage.plugin.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;

/**
 * The properties file strategies selectable with {@code --strategy}.
 *
 * @since 1.6.3
 */
public enum PropertiesFileStrategyType {

    /** All configuration lives in {@code application.properties}. */
    APPLICATION("application"),

    /** Each factory has its own {@code forage-*.properties} file resolved from the catalog. */
    FORAGE("forage");

    private final String id;

    PropertiesFileStrategyType(String id) {
        this.id = id;
    }

    /**
     * @return the identifier used on the command line (e.g. {@code "application"})
     */
    public String id() {
        return id;
    }

    /**
     * Creates the strategy implementation for this type.
     *
     * @param catalog the catalog reader, required by {@link #FORAGE}
     * @return a new strategy instance, never {@code null}
     */
    public PropertiesFileStrategy create(ForageCatalogReader catalog) {
        return switch (this) {
            case APPLICATION -> new ApplicationPropertiesStrategy();
            case FORAGE -> new ForageMultiFileStrategy(Objects.requireNonNull(catalog, "catalog"));
        };
    }

    /**
     * Resolves a strategy type from its command line identifier, ignoring case.
     *
     * @param id the identifier (e.g. {@code "forage"}, {@code "Application"})
     * @return the matching type
     * @throws IllegalArgumentException if {@code id} is {@code null} or not a known strategy
     */
    public static PropertiesFileStrategyType fromId(String id) {
        if (id != null) {
            String normalized = id.trim().toLowerCase(Locale.ROOT);
            for (PropertiesFileStrategyType type : values()) {
                if (type.id.equals(normalized)) {
                    return type;
                }
            }
        }
        throw new IllegalArgumentException("Unknown properties file strategy '" + id + "'. Valid values: "
                + Arrays.stream(values()).map(PropertiesFileStrategyType::id).collect(Collectors.joining(", ")));
    }

    @Override
    public String toString() {
        return id;
    }
}
