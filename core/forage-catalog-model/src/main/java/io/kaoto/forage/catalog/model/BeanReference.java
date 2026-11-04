package io.kaoto.forage.catalog.model;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Where a Camel route references a bean created by a factory: an option of a Camel catalog entry
 * (a component endpoint or an EIP) and how its value is written.
 *
 * <p>For example {@code {"catalogKind": "component", "name": "sql", "option": "dataSource",
 * "optionKind": "parameter", "valueTemplate": "#{beanRef}"}} means that a route uses the bean
 * {@code orders} with {@code to: {uri: sql, parameters: {dataSource: "#orders"}}}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"catalogKind", "name", "option", "optionKind", "valueTemplate"})
public class BeanReference {

    /** Placeholder for the bean name in {@link #getValueTemplate()}. */
    public static final String BEAN_REF_PLACEHOLDER = "{beanRef}";

    @JsonProperty("catalogKind")
    private String catalogKind;

    @JsonProperty("name")
    private String name;

    @JsonProperty("option")
    private String option;

    @JsonProperty("optionKind")
    private String optionKind;

    @JsonProperty("valueTemplate")
    private String valueTemplate;

    public BeanReference() {}

    public BeanReference(String catalogKind, String name, String option, String optionKind, String valueTemplate) {
        this.catalogKind = catalogKind;
        this.name = name;
        this.option = option;
        this.optionKind = optionKind;
        this.valueTemplate = valueTemplate;
    }

    public String getCatalogKind() {
        return catalogKind;
    }

    public void setCatalogKind(String catalogKind) {
        this.catalogKind = catalogKind;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOption() {
        return option;
    }

    public void setOption(String option) {
        this.option = option;
    }

    public String getOptionKind() {
        return optionKind;
    }

    public void setOptionKind(String optionKind) {
        this.optionKind = optionKind;
    }

    public String getValueTemplate() {
        return valueTemplate;
    }

    public void setValueTemplate(String valueTemplate) {
        this.valueTemplate = valueTemplate;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BeanReference that)) {
            return false;
        }
        return Objects.equals(catalogKind, that.catalogKind)
                && Objects.equals(name, that.name)
                && Objects.equals(option, that.option)
                && Objects.equals(optionKind, that.optionKind)
                && Objects.equals(valueTemplate, that.valueTemplate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(catalogKind, name, option, optionKind, valueTemplate);
    }

    @Override
    public String toString() {
        return catalogKind + ":" + name + "#" + option + " (" + optionKind + ") = " + valueTemplate;
    }
}
