package io.kaoto.forage.catalog.model;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The JSON Schema of a single configuration property inside a {@link PropertiesSchema}.
 *
 * <p>Only the keywords a form renderer needs are used: {@code title}, {@code description},
 * {@code type}, {@code format} ({@code password}, {@code duration}), {@code enum}, {@code default},
 * {@code additionalProperties} (for free-form key/value properties) and {@code $comment}, which
 * carries the form group as {@code group:NAME} like the camel-catalog does.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"title", "description", "type", "format", "enum", "default", "additionalProperties", "$comment"})
public class PropertySchema {

    @JsonProperty("title")
    private String title;

    @JsonProperty("description")
    private String description;

    @JsonProperty("type")
    private String type;

    @JsonProperty("format")
    private String format;

    @JsonProperty("enum")
    private List<String> enumValues;

    @JsonProperty("default")
    private Object defaultValue;

    @JsonProperty("additionalProperties")
    private PropertySchema additionalProperties;

    @JsonProperty("$comment")
    private String comment;

    public PropertySchema() {}

    public PropertySchema(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public List<String> getEnumValues() {
        return enumValues;
    }

    public void setEnumValues(List<String> enumValues) {
        this.enumValues = enumValues;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }

    public PropertySchema getAdditionalProperties() {
        return additionalProperties;
    }

    public void setAdditionalProperties(PropertySchema additionalProperties) {
        this.additionalProperties = additionalProperties;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
