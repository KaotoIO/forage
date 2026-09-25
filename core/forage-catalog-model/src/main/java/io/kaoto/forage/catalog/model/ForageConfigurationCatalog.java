package io.kaoto.forage.catalog.model;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Root catalog data structure containing all discovered Forage configuration modules.
 * Unlike ForageCatalog which only includes configs associated with factories,
 * this catalog captures ALL configuration entries across all modules.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ForageConfigurationCatalog {

    @JsonProperty("version")
    private String version;

    @JsonProperty("generatedBy")
    private String generatedBy;

    @JsonProperty("timestamp")
    private long timestamp;

    @JsonProperty("modules")
    private List<ConfigurationModule> modules;

    public ForageConfigurationCatalog() {
        // Required by Jackson for deserialization
    }

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

    public List<ConfigurationModule> getModules() {
        return modules;
    }

    public void setModules(List<ConfigurationModule> modules) {
        this.modules = modules;
    }
}
