package io.kaoto.forage.core.annotations;

import java.util.Optional;

/**
 * Enumeration of supported factory types that create different kinds of beans.
 */
public enum FactoryType {
    /** Factory that creates Agent beans */
    AGENT("org.apache.camel.component.langchain4j.agent.api.Agent", "forage-agent-factories"),

    /** Factory that creates DataSource beans for JDBC */
    DATA_SOURCE("javax.sql.DataSource", "forage-jdbc-common"),

    /** Factory that creates ConnectionFactory beans for JMS */
    CONNECTION_FACTORY("jakarta.jms.ConnectionFactory", "forage-jms-common"),

    /** Factory that creates CxfEndpoint beans for CXF SOAP */
    CXF_ENDPOINT("org.apache.camel.component.cxf.jaxws.CxfEndpoint", "forage-cxf-common");

    private final String displayName;

    private final String configArtifactId;

    FactoryType(String displayName, String configArtifactId) {
        this.displayName = displayName;
        this.configArtifactId = configArtifactId;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get the artifact ID of the module containing common configuration for this factory type.
     *
     * @return the config artifact ID (e.g., "forage-jdbc-common", "forage-agent-factories")
     */
    public String getConfigArtifactId() {
        return configArtifactId;
    }

    /**
     * Find a FactoryType by its display name.
     *
     * @param displayName the display name to search for
     * @return an Optional containing the matching FactoryType, or empty if not found
     */
    public static Optional<FactoryType> fromDisplayName(String displayName) {
        if (displayName == null) {
            return Optional.empty();
        }
        for (FactoryType type : values()) {
            if (type.displayName.equals(displayName)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        return displayName;
    }
}
