package io.kaoto.forage.core.jta.store;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * The Narayana transaction object store implementations Forage can configure, by the value used in
 * the {@code forage.*.transaction.object.store.type} properties.
 */
public enum ObjectStoreType {

    /** Narayana's default file store, rooted at {@code object.store.directory}. */
    FILE_SYSTEM("file-system"),

    /** Narayana's JDBC store: the transaction log lives in database tables (issue #447). */
    JDBC("jdbc"),

    /** In-memory store: no durability, nothing survives a restart. For tests only. */
    VOLATILE("volatile");

    private final String value;

    ObjectStoreType(String value) {
        this.value = value;
    }

    /** The property value selecting this store type. */
    public String value() {
        return value;
    }

    /**
     * Parses an object store type property value, case-insensitively. A blank value selects the
     * {@link #FILE_SYSTEM} default.
     *
     * @param value the configured value
     * @param propertyName the property the value was read from, for the error message
     * @throws IllegalArgumentException if the value names no supported store type
     */
    public static ObjectStoreType fromValue(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            return FILE_SYSTEM;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (ObjectStoreType type : values()) {
            if (type.value.equals(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported transaction object store type '" + value + "' in '"
                + propertyName + "'. Supported types: " + supportedValues());
    }

    /** Comma-separated list of the supported property values. */
    public static String supportedValues() {
        return Arrays.stream(values()).map(ObjectStoreType::value).collect(Collectors.joining(", "));
    }

    @Override
    public String toString() {
        return value;
    }
}
