package io.kaoto.forage.catalog.model;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Root catalog data structure containing all discovered Forage factories.
 * This is the main entry point for the Forage catalog.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ForageCatalog {

    @JsonProperty("version")
    private String version;

    @JsonProperty("generatedBy")
    private String generatedBy;

    @JsonProperty("timestamp")
    private long timestamp;

    @JsonProperty("runtimeVersions")
    private Map<String, String> runtimeVersions;

    @JsonProperty("factories")
    private List<ForageFactory> factories;

    public ForageCatalog() {}

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getGeneratedBy() {
        return generatedBy;
    }

    public void setGeneratedBy(String generatedBy) {
        this.generatedBy = generatedBy;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, String> getRuntimeVersions() {
        return runtimeVersions;
    }

    public void setRuntimeVersions(Map<String, String> runtimeVersions) {
        this.runtimeVersions = runtimeVersions;
    }

    public List<ForageFactory> getFactories() {
        return factories;
    }

    public void setFactories(List<ForageFactory> factories) {
        this.factories = factories;
    }
}
