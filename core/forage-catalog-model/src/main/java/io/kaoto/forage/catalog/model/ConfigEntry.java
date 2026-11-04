package io.kaoto.forage.catalog.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a configuration entry for a Forage factory or bean.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConfigEntry {

    @JsonProperty("name")
    private String name;

    @JsonProperty("type")
    private String type;

    @JsonProperty("description")
    private String description;

    @JsonProperty("required")
    private boolean required;

    @JsonProperty("defaultValue")
    private String defaultValue;

    @JsonProperty("example")
    private String example;

    @JsonProperty("label")
    private String label;

    @JsonProperty("configTag")
    private String configTag;

    @JsonProperty("selectsFrom")
    private String selectsFrom;

    /**
     * Internal entry ({@code @InternalConfig}) that UIs should not render; only written when true.
     */
    @JsonProperty("hidden")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean hidden;

    public ConfigEntry() {}

    public ConfigEntry(String name, String type, String description, boolean required) {
        this.name = name;
        this.type = type;
        this.description = description;
        this.required = required;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getExample() {
        return example;
    }

    public void setExample(String example) {
        this.example = example;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getConfigTag() {
        return configTag;
    }

    public void setConfigTag(String configTag) {
        this.configTag = configTag;
    }

    public String getSelectsFrom() {
        return selectsFrom;
    }

    public void setSelectsFrom(String selectsFrom) {
        this.selectsFrom = selectsFrom;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }
}
