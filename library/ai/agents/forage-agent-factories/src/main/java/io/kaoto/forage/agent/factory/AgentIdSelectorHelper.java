package io.kaoto.forage.agent.factory;

import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.core.exceptions.UndefinedAgentException;

/**
 * Helper class for creating the appropriate agent selector implementation based on string identifiers.
 * Supports creating different strategies for extracting agent IDs from exchanges.
 */
public class AgentIdSelectorHelper {
    private static final Logger LOG = LoggerFactory.getLogger(AgentIdSelectorHelper.class);

    /**
     * Creates an AgentIdSource implementation based on the specified source type.
     *
     * @param config The MultiAgentConfig containing configuration for the source
     * @return An appropriate AgentIdSource implementation
     * @throws IllegalArgumentException if the source type is unknown or unsupported
     */
    private static AgentSelector create(MultiAgentConfig config) {
        String sourceType = config.multiAgentIdSource();

        return switch (sourceType.toLowerCase()) {
            case MultiAgentConfig.ROUTE_ID -> new RouteIdAgentSelector();
            case MultiAgentConfig.HEADER -> {
                String headerName = config.multiAgentIdSourceHeader();
                if (headerName == null || headerName.isBlank()) {
                    throw new IllegalArgumentException(
                            "Header name must be configured via multi.agent.id.source.header when using header source type");
                }
                yield new HeaderAgentSelector(headerName);
            }
            case MultiAgentConfig.PROPERTY -> {
                String propertyName = config.multiAgentIdSourceProperty();
                if (propertyName == null || propertyName.isBlank()) {
                    throw new IllegalArgumentException(
                            "Property name must be configured via multi.agent.id.source.property when using property source type");
                }
                yield new PropertyAgentSelector(propertyName);
            }
            case MultiAgentConfig.VARIABLE -> {
                String variableName = config.multiAgentIdSourceVariable();
                if (variableName == null || variableName.isBlank()) {
                    throw new IllegalArgumentException(
                            "Variable name must be configured via multi.agent.id.source.variable when using variable source type");
                }
                yield new VariableAgentSelector(variableName);
            }
            default ->
                throw new IllegalArgumentException("Unknown agent ID source type: " + sourceType
                        + ". Supported types are: " + MultiAgentConfig.ROUTE_ID + ", " + MultiAgentConfig.HEADER + ", "
                        + MultiAgentConfig.PROPERTY + ", " + MultiAgentConfig.VARIABLE);
        };
    }

    /**
     * Throws an appropriate UndefinedAgentException based on the agent ID source type
     * configured in the MultiAgentConfig and the extracted agent ID value from the exchange.
     *
     * @param config   The MultiAgentConfig containing the source type configuration
     * @param exchange The Exchange from which to extract the agent ID value
     * @return
     */
    public static UndefinedAgentException newUndefinedAgentException(MultiAgentConfig config, Exchange exchange) {
        String sourceType = config.multiAgentIdSource();

        return switch (sourceType.toLowerCase()) {
            case MultiAgentConfig.ROUTE_ID -> UndefinedAgentException.fromRouteId(exchange.getFromRouteId());
            case MultiAgentConfig.HEADER -> {
                String headerName = config.multiAgentIdSourceHeader();
                String headerValue = null;
                if (headerName != null && !headerName.isBlank()) {
                    headerValue = exchange.getIn().getHeader(headerName, String.class);
                }
                yield UndefinedAgentException.fromHeader(headerName, headerValue);
            }
            case MultiAgentConfig.PROPERTY -> {
                String propertyName = config.multiAgentIdSourceProperty();
                String propertyValue = null;
                if (propertyName != null && !propertyName.isBlank()) {
                    propertyValue = exchange.getProperty(propertyName, String.class);
                }
                yield UndefinedAgentException.fromProperty(propertyName, propertyValue);
            }
            case MultiAgentConfig.VARIABLE -> {
                String variableName = config.multiAgentIdSourceVariable();
                String variableValue = null;
                if (variableName != null && !variableName.isBlank()) {
                    variableValue = exchange.getVariable(variableName, String.class);
                }
                yield UndefinedAgentException.fromVariable(variableName, variableValue);
            }
            default -> {
                // Fallback to route ID if source type is unknown
                yield UndefinedAgentException.fromRouteId(exchange.getFromRouteId());
            }
        };
    }

    public static String select(MultiAgentConfig config, Exchange exchange) {
        AgentSelector agentSelector = create(config);
        String agentId = agentSelector.select(exchange);
        LOG.info("Creating Agent for {} using ID {}", exchange.getExchangeId(), agentId);
        return agentId;
    }
}
