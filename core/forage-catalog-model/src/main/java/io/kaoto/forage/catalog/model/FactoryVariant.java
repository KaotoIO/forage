package io.kaoto.forage.catalog.model;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a platform-specific variant of a Forage factory.
 * Each factory can have multiple variants for different platforms (base, springboot, quarkus).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FactoryVariant {

    @JsonProperty("className")
    private String className;

    @JsonProperty("gav")
    private String gav;

    @JsonProperty("additionalDependencies")
    private List<String> additionalDependencies;

    public FactoryVariant() {}

    public FactoryVariant(String className, String gav) {
        this.className = className;
        this.gav = gav;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getGav() {
        return gav;
    }

    public void setGav(String gav) {
        this.gav = gav;
    }

    public List<String> getAdditionalDependencies() {
        return additionalDependencies;
    }

    public void setAdditionalDependencies(List<String> additionalDependencies) {
        this.additionalDependencies = additionalDependencies;
    }

    /**
     * Creates a GAV string from individual components.
     */
    public static String createGav(String groupId, String artifactId, String version) {
        return groupId + ":" + artifactId + ":" + version;
    }

    @Override
    public String toString() {
        return "FactoryVariant{" + "className='" + className + '\'' + ", gav='" + gav + '\''
                + ", additionalDependencies=" + additionalDependencies + '}';
    }
}
