package io.kaoto.forage.maven.catalog;

import java.util.List;
import java.util.Map;
import io.kaoto.forage.catalog.model.ConfigEntry;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a single Forage component in the catalog.
 */
public class ForageComponent {

    @JsonProperty("artifactId")
    private String artifactId;

    @JsonProperty("groupId")
    private String groupId;

    @JsonProperty("version")
    private String version;

    @JsonProperty("configurationProperties")
    private List<ConfigEntry> configurationProperties;

    @JsonProperty("capabilities")
    private Map<String, Object> capabilities;

    private List<ScannedBean> beans;

    private List<ScannedFactory> factories;

    private Map<String, String> configClasses; // Not serialized to JSON, just for internal use

    public ForageComponent() {
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

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public List<ConfigEntry> getConfigurationProperties() {
        return configurationProperties;
    }

    public void setConfigurationProperties(List<ConfigEntry> configurationProperties) {
        this.configurationProperties = configurationProperties;
    }

    public Map<String, Object> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(Map<String, Object> capabilities) {
        this.capabilities = capabilities;
    }

    public List<ScannedBean> getBeans() {
        return beans;
    }

    public void setBeans(List<ScannedBean> beans) {
        this.beans = beans;
    }

    public List<ScannedFactory> getFactories() {
        return factories;
    }

    public void setFactories(List<ScannedFactory> factories) {
        this.factories = factories;
    }

    public Map<String, String> getConfigClasses() {
        return configClasses;
    }

    public void setConfigClasses(Map<String, String> configClasses) {
        this.configClasses = configClasses;
    }
}
