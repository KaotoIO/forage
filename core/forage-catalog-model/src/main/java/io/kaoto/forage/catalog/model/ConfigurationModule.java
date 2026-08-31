package io.kaoto.forage.catalog.model;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a Forage module and its configuration entries.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConfigurationModule {

    @JsonProperty("artifactId")
    private String artifactId;

    @JsonProperty("groupId")
    private String groupId;

    @JsonProperty("propertiesFile")
    private String propertiesFile;

    @JsonProperty("configEntries")
    private List<ConfigEntry> configEntries;

    public ConfigurationModule() {
        // Required by Jackson for deserialization
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getPropertiesFile() {
        return propertiesFile;
    }

    public void setPropertiesFile(String propertiesFile) {
        this.propertiesFile = propertiesFile;
    }

    public List<ConfigEntry> getConfigEntries() {
        return configEntries;
    }

    public void setConfigEntries(List<ConfigEntry> configEntries) {
        this.configEntries = configEntries;
    }
}
