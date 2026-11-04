package io.kaoto.forage.catalog.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * A JSON Schema (Draft-07) describing the configuration properties of a factory or bean, in the
 * shape of the camel-catalog {@code propertiesSchema}, so that a JSON Schema form renderer such as
 * Kaoto's {@code KaotoForm} can render it directly.
 *
 * <p>Property names are the full property keys of the default (unnamed) instance, for example
 * {@code forage.jdbc.url}. The keys of a named instance are derived with the factory
 * {@code prefixPattern}.
 */
@JsonPropertyOrder({"$schema", "type", "required", "properties"})
public class PropertiesSchema {

    /** The JSON Schema dialect of every {@code propertiesSchema}. */
    public static final String DRAFT_07 = "http://json-schema.org/draft-07/schema#";

    @JsonProperty("$schema")
    private String schema = DRAFT_07;

    @JsonProperty("type")
    private String type = "object";

    @JsonProperty("required")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<String> required = new ArrayList<>();

    @JsonProperty("properties")
    private Map<String, PropertySchema> properties = new LinkedHashMap<>();

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<String> getRequired() {
        return required;
    }

    public void setRequired(List<String> required) {
        this.required = required;
    }

    public Map<String, PropertySchema> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, PropertySchema> properties) {
        this.properties = properties;
    }
}
