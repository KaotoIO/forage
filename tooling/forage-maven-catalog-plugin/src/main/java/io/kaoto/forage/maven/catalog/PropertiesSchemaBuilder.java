package io.kaoto.forage.maven.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import io.kaoto.forage.catalog.model.ConfigEntry;
import io.kaoto.forage.catalog.model.PropertiesSchema;
import io.kaoto.forage.catalog.model.PropertySchema;

/**
 * Converts catalog {@link ConfigEntry config entries} into a JSON Schema (Draft-07)
 * {@link PropertiesSchema} in the camel-catalog {@code propertiesSchema} shape.
 *
 * <table>
 *   <caption>Mapping from config entries to JSON Schema</caption>
 *   <tr><th>config entry</th><th>JSON Schema</th></tr>
 *   <tr><td>{@code string}</td><td>{@code "type": "string"}</td></tr>
 *   <tr><td>{@code integer}, {@code int}, {@code long}</td><td>{@code "type": "integer"}</td></tr>
 *   <tr><td>{@code double}</td><td>{@code "type": "number"}</td></tr>
 *   <tr><td>{@code boolean}</td><td>{@code "type": "boolean"}</td></tr>
 *   <tr><td>{@code password}</td><td>{@code "type": "string", "format": "password"}</td></tr>
 *   <tr><td>{@code Duration}</td><td>{@code "type": "string", "format": "duration"}</td></tr>
 *   <tr><td>{@code map}</td><td>{@code "type": "object", "additionalProperties": {"type": "string"}}</td></tr>
 *   <tr><td>{@code bean-name} + {@code selectsFrom}</td><td>{@code "type": "string", "enum": [bean names]}</td></tr>
 *   <tr><td>{@code configTag}</td><td>{@code "$comment": "group:common"} (lower case, as in camel-catalog)</td></tr>
 *   <tr><td>{@code required}</td><td>schema level {@code "required"} array</td></tr>
 *   <tr><td>{@code defaultValue}</td><td>{@code "default"}, typed for integer, number and boolean</td></tr>
 *   <tr><td>{@code label} / {@code description}</td><td>{@code "title"} / {@code "description"}</td></tr>
 * </table>
 *
 * <p>Entries marked {@code hidden} are left out. Type names are matched case-insensitively; an
 * unknown type is rendered as a string (see {@link #isKnownType(String)}).
 */
final class PropertiesSchemaBuilder {

    static final String TYPE_STRING = "string";
    static final String TYPE_INTEGER = "integer";
    static final String TYPE_NUMBER = "number";
    static final String TYPE_BOOLEAN = "boolean";
    static final String TYPE_OBJECT = "object";
    static final String FORMAT_PASSWORD = "password";
    static final String FORMAT_DURATION = "duration";
    static final String GROUP_COMMENT_PREFIX = "group:";

    private static final Set<String> STRING_TYPES = Set.of("string", "bean-name", "password", "duration");
    private static final Set<String> INTEGER_TYPES = Set.of("integer", "int", "long", "short");
    private static final Set<String> NUMBER_TYPES = Set.of("double", "float", "number");

    private PropertiesSchemaBuilder() {}

    /**
     * Builds the schema of the given entries.
     *
     * @param entries the config entries, may be null
     * @param beanNamesOfFeature returns the bean names of a feature, used for {@code bean-name}
     *        entries; an empty list leaves the property without {@code enum}
     */
    static PropertiesSchema build(List<ConfigEntry> entries, Function<String, List<String>> beanNamesOfFeature) {
        PropertiesSchema schema = new PropertiesSchema();
        if (entries == null) {
            return schema;
        }
        for (ConfigEntry entry : entries) {
            if (entry.isHidden()
                    || entry.getName() == null
                    || schema.getProperties().containsKey(entry.getName())) {
                continue;
            }
            schema.getProperties().put(entry.getName(), property(entry, beanNamesOfFeature));
            if (entry.isRequired()) {
                schema.getRequired().add(entry.getName());
            }
        }
        return schema;
    }

    static PropertySchema property(ConfigEntry entry, Function<String, List<String>> beanNamesOfFeature) {
        String type = normalizedType(entry);
        PropertySchema property = new PropertySchema();
        property.setTitle(entry.getLabel());
        property.setDescription(entry.getDescription());

        switch (type) {
            case "password" -> {
                property.setType(TYPE_STRING);
                property.setFormat(FORMAT_PASSWORD);
            }
            case "duration" -> {
                property.setType(TYPE_STRING);
                property.setFormat(FORMAT_DURATION);
            }
            case "map" -> {
                property.setType(TYPE_OBJECT);
                property.setAdditionalProperties(new PropertySchema(TYPE_STRING));
            }
            case "bean-name" -> {
                property.setType(TYPE_STRING);
                if (entry.getSelectsFrom() != null) {
                    List<String> beanNames = beanNamesOfFeature.apply(entry.getSelectsFrom());
                    if (beanNames != null && !beanNames.isEmpty()) {
                        property.setEnumValues(List.copyOf(beanNames));
                    }
                }
            }
            default -> property.setType(jsonType(type));
        }

        if (entry.getDefaultValue() != null && !TYPE_OBJECT.equals(property.getType())) {
            property.setDefaultValue(typedDefault(property.getType(), entry.getDefaultValue()));
        }
        if (entry.getConfigTag() != null && !entry.getConfigTag().isBlank()) {
            property.setComment(GROUP_COMMENT_PREFIX + entry.getConfigTag().toLowerCase(Locale.ROOT));
        }
        return property;
    }

    /**
     * Returns whether the config entry type has a dedicated JSON Schema mapping.
     */
    static boolean isKnownType(String type) {
        String normalized = type == null ? TYPE_STRING : type.toLowerCase(Locale.ROOT);
        return STRING_TYPES.contains(normalized)
                || INTEGER_TYPES.contains(normalized)
                || NUMBER_TYPES.contains(normalized)
                || TYPE_BOOLEAN.equals(normalized)
                || "map".equals(normalized);
    }

    private static String normalizedType(ConfigEntry entry) {
        return entry.getType() == null ? TYPE_STRING : entry.getType().toLowerCase(Locale.ROOT);
    }

    private static String jsonType(String normalizedType) {
        if (INTEGER_TYPES.contains(normalizedType)) {
            return TYPE_INTEGER;
        }
        if (NUMBER_TYPES.contains(normalizedType)) {
            return TYPE_NUMBER;
        }
        if (TYPE_BOOLEAN.equals(normalizedType)) {
            return TYPE_BOOLEAN;
        }
        return TYPE_STRING;
    }

    /**
     * Converts a default value to the JSON type of the property, keeping the string when it does
     * not parse, so that no information is lost.
     */
    static Object typedDefault(String jsonType, String value) {
        String trimmed = value.trim();
        try {
            switch (jsonType) {
                case TYPE_INTEGER -> {
                    return Long.parseLong(trimmed);
                }
                case TYPE_NUMBER -> {
                    return new BigDecimal(trimmed);
                }
                case TYPE_BOOLEAN -> {
                    if ("true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed)) {
                        return Boolean.parseBoolean(trimmed);
                    }
                    return value;
                }
                default -> {
                    return value;
                }
            }
        } catch (NumberFormatException e) {
            return value;
        }
    }
}
