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

    /**
     * Whether the value may list several entries, comma-separated (e.g., several beans for a bean-name
     * entry). Only written to the catalog when true.
     */
    @JsonProperty("multiValue")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean multiValue;

    /**
     * The Quarkus key the export writes for this entry ({@code {name}}: the quoted configuration name; ending with
     * {@code .}: a map). Only written for entries with a plain Quarkus equivalent.
     */
    @JsonProperty("quarkusProperty")
    private String quarkusProperty;

    /** The unit appended to the Quarkus value ({@code S} or {@code M}). Only written when there is one. */
    @JsonProperty("quarkusUnit")
    private String quarkusUnit;

    /** The entry has no effect on Quarkus: the export leaves it untranslated. Only written when {@code true}. */
    @JsonProperty("quarkusIgnored")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean quarkusIgnored;

    /**
     * The entry has no Quarkus key of its own but the export handles it (e.g. a catalog condition). Only written when
     * {@code true}.
     */
    @JsonProperty("quarkusHandled")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean quarkusHandled;

    /** The entry selects the bean (the kind) of a module the export translates. Only written on that entry. */
    @JsonProperty("quarkusSelector")
    private QuarkusSelector quarkusSelector;

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

    public boolean isMultiValue() {
        return multiValue;
    }

    public void setMultiValue(boolean multiValue) {
        this.multiValue = multiValue;
    }

    public String getQuarkusProperty() {
        return quarkusProperty;
    }

    public void setQuarkusProperty(String quarkusProperty) {
        this.quarkusProperty = quarkusProperty;
    }

    public String getQuarkusUnit() {
        return quarkusUnit;
    }

    public void setQuarkusUnit(String quarkusUnit) {
        this.quarkusUnit = quarkusUnit;
    }

    public boolean isQuarkusIgnored() {
        return quarkusIgnored;
    }

    public void setQuarkusIgnored(boolean quarkusIgnored) {
        this.quarkusIgnored = quarkusIgnored;
    }

    public boolean isQuarkusHandled() {
        return quarkusHandled;
    }

    public void setQuarkusHandled(boolean quarkusHandled) {
        this.quarkusHandled = quarkusHandled;
    }

    public QuarkusSelector getQuarkusSelector() {
        return quarkusSelector;
    }

    public void setQuarkusSelector(QuarkusSelector quarkusSelector) {
        this.quarkusSelector = quarkusSelector;
    }
}
