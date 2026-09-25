package io.kaoto.forage.catalog.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Contains factory variants for different runtime platforms.
 * Each property represents a specific platform variant.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FactoryVariants {

    @JsonProperty("base")
    private FactoryVariant base;

    @JsonProperty("springboot")
    private FactoryVariant springboot;

    @JsonProperty("quarkus")
    private FactoryVariant quarkus;

    public FactoryVariants() {
        // Required by Jackson for deserialization
    }

    public FactoryVariant getBase() {
        return base;
    }

    public void setBase(FactoryVariant base) {
        this.base = base;
    }

    public FactoryVariant getSpringboot() {
        return springboot;
    }

    public void setSpringboot(FactoryVariant springboot) {
        this.springboot = springboot;
    }

    public FactoryVariant getQuarkus() {
        return quarkus;
    }

    public void setQuarkus(FactoryVariant quarkus) {
        this.quarkus = quarkus;
    }
}
