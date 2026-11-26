package io.kaoto.forage.catalog.model;

import java.util.Map;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Marks the config entry whose value selects the bean (the kind) of a module the Quarkus export translates. The
 * selected bean needs Quarkus runtime dependencies in the catalog.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuarkusSelector {

    /** The kinds translated, each with the Quarkus keys it adds; {@code null}: any kind. */
    @JsonProperty("kinds")
    private Map<String, Map<String, String>> kinds;

    /** Only the default configuration is translated, not named ones. Only written when {@code true}. */
    @JsonProperty("defaultOnly")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean defaultOnly;

    public QuarkusSelector() {}

    public QuarkusSelector(Map<String, Map<String, String>> kinds, boolean defaultOnly) {
        this.kinds = kinds;
        this.defaultOnly = defaultOnly;
    }

    public Map<String, Map<String, String>> getKinds() {
        return kinds;
    }

    public void setKinds(Map<String, Map<String, String>> kinds) {
        this.kinds = kinds;
    }

    public boolean isDefaultOnly() {
        return defaultOnly;
    }

    public void setDefaultOnly(boolean defaultOnly) {
        this.defaultOnly = defaultOnly;
    }
}
