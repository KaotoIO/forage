package io.kaoto.forage.policy.factory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.camel.CamelContext;
import org.apache.camel.NamedNode;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.RoutePolicyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.core.policy.RoutePolicyProvider;

/**
 * Default implementation of Camel's RoutePolicyFactory that delegates to
 * RoutePolicyProvider implementations discovered via ServiceLoader.
 *
 * <p>This factory reads the configuration for each route to determine which
 * policies to apply. The configuration follows the pattern:
 * {@code forage.route.policy.<routeId>.name=schedule,flip}
 *
 * <p><strong>Configuration Flow:</strong>
 * <ol>
 *   <li>Camel calls {@link #createRoutePolicy(CamelContext, String, NamedNode)}</li>
 *   <li>Factory reads {@code forage.route.policy.<routeId>.name}</li>
 *   <li>For each policy name, look up provider in RoutePolicyRegistry</li>
 *   <li>Provider creates configured RoutePolicy instance</li>
 *   <li>Return policy (or null if none configured)</li>
 * </ol>
 *
 * <p><strong>Behavior Rules:</strong>
 * <ul>
 *   <li>Unknown policy names: log WARNING, skip, continue with others</li>
 *   <li>Multiple policies: apply in order (last wins on conflict)</li>
 *   <li>No policies configured: return null</li>
 * </ul>
 *
 * @see RoutePolicyFactory
 * @see RoutePolicyProvider
 * @see RoutePolicyRegistry
 * @since 1.0
 */
public class DefaultCamelForageRoutePolicyFactory implements RoutePolicyFactory {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultCamelForageRoutePolicyFactory.class);

    private static final String CONFIG_PREFIX = "forage.route.policy";

    private final RoutePolicyRegistry registry;
    private final RoutePolicyFactoryConfig config;

    /**
     * Creates a new DefaultCamelForageRoutePolicyFactory with a new registry and config.
     */
    public DefaultCamelForageRoutePolicyFactory() {
        this(new RoutePolicyRegistry(), new RoutePolicyFactoryConfig());
    }

    /**
     * Creates a new DefaultCamelForageRoutePolicyFactory with the given registry and config.
     *
     * @param config the factory configuration
     */
    DefaultCamelForageRoutePolicyFactory(RoutePolicyFactoryConfig config) {
        this(new RoutePolicyRegistry(), config);
    }

    /**
     * Creates a new DefaultCamelForageRoutePolicyFactory with the given registry and config.
     *
     * @param registry the policy registry
     * @param config the factory configuration
     */
    public DefaultCamelForageRoutePolicyFactory(RoutePolicyRegistry registry, RoutePolicyFactoryConfig config) {
        this.registry = registry;
        this.config = config;
    }

    @Override
    public RoutePolicy createRoutePolicy(CamelContext camelContext, String routeId, NamedNode route) {
        LOG.debug("Creating route policy for route: {}", routeId);

        Optional<String> policyNamesOpt = config.getPolicyNames(routeId);

        if (policyNamesOpt.isEmpty()) {
            LOG.debug("No policies configured for route: {}", routeId);
            return null;
        }

        String policyNamesStr = policyNamesOpt.get();
        String[] policyNames = policyNamesStr.split(",");
        List<RoutePolicy> policies = new ArrayList<>();

        for (String policyName : policyNames) {
            String trimmedName = policyName.trim();
            if (trimmedName.isEmpty()) {
                continue;
            }

            Optional<RoutePolicyProvider> providerOpt = registry.getProvider(trimmedName);

            if (providerOpt.isEmpty()) {
                LOG.warn(
                        "Unknown route policy '{}' for route '{}'. " + "Policy will be skipped. Available policies: {}",
                        trimmedName,
                        routeId,
                        registry.getAllProviders().stream()
                                .map(RoutePolicyProvider::name)
                                .toList());
                continue;
            }

            RoutePolicyProvider provider = providerOpt.get();
            String configPrefix = CONFIG_PREFIX + "." + routeId + "." + trimmedName;

            LOG.debug("Creating policy '{}' for route '{}' with config prefix: {}", trimmedName, routeId, configPrefix);

            try {
                RoutePolicy policy = provider.create(configPrefix);
                if (policy != null) {
                    policies.add(policy);
                    LOG.info("Applied route policy '{}' to route '{}'", trimmedName, routeId);
                }
            } catch (Exception e) {
                LOG.warn("Failed to create policy '{}' for route '{}': {}", trimmedName, routeId, e.getMessage());
                LOG.debug("Policy creation failure details", e);
            }
        }

        if (policies.isEmpty()) {
            LOG.debug("No valid policies created for route: {}", routeId);
            return null;
        }

        if (policies.size() == 1) {
            return policies.get(0);
        }

        // For multiple policies, return the last one (last wins semantics)
        // Future enhancement: could return a CompositeRoutePolicy
        LOG.info(
                "Multiple policies configured for route '{}'. Applying last-wins semantics: {}",
                routeId,
                policies.get(policies.size() - 1).getClass().getSimpleName());
        return policies.get(policies.size() - 1);
    }
}
