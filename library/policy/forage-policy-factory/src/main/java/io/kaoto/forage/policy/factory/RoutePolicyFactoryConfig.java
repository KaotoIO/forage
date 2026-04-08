package io.kaoto.forage.policy.factory;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.core.util.config.AbstractConfig;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigStore;

/**
 * Configuration class for the route policy factory.
 *
 * <p>This class provides access to the policy configuration for each route.
 * The configuration follows the pattern:
 * {@code forage.route.policy.<routeId>.name}
 *
 * @see AbstractConfig
 * @see RoutePolicyFactoryConfigEntries
 * @since 1.0
 */
public class RoutePolicyFactoryConfig extends AbstractConfig {
    private static final Logger LOG = LoggerFactory.getLogger(RoutePolicyFactoryConfig.class);

    /**
     * Creates a new RoutePolicyFactoryConfig with no prefix.
     */
    public RoutePolicyFactoryConfig() {
        this(null);
    }

    /**
     * Creates a new RoutePolicyFactoryConfig with the given prefix.
     *
     * @param prefix optional prefix for named configurations
     */
    public RoutePolicyFactoryConfig(String prefix) {
        super(prefix, RoutePolicyFactoryConfigEntries.class);
    }

    @Override
    public String name() {
        return "forage-policy-factory";
    }

    /**
     * Registers a configuration property value.
     *
     * <p>This method extends the default behavior to handle dynamic per-route
     * policy properties (e.g., {@code forage.route.policy.<routeId>.name})
     * that are not statically registered as ConfigModules.
     *
     * @param name the property key
     * @param value the property value
     */
    @Override
    public void register(String name, String value) {
        super.register(name, value);

        String prefix = RoutePolicyFactoryConfigEntries.CONFIG_PREFIX + ".";
        if (name.startsWith(prefix)) {
            String suffix = name.substring(prefix.length());
            if (suffix.endsWith(".name")) {
                String routeId = suffix.substring(0, suffix.length() - ".name".length());
                if (!routeId.isEmpty()) {
                    ConfigModule module = RoutePolicyFactoryConfigEntries.policyNameModule(routeId);
                    LOG.debug("Registering policy name for route {}: {}", routeId, value);
                    ConfigStore.getInstance().set(module, value);
                }
            }

            // Store all per-route properties so downstream policy configs can find them
            ConfigStore.getInstance().setDirect(name, value);
        }
    }

    /**
     * Returns whether the route policy factory is enabled.
     *
     * <p>When disabled, no route policies will be created by this factory.
     * Default is true (enabled).
     *
     * <p>Configuration sources (in order of precedence):
     * <ol>
     *   <li>Environment variable: FORAGE_ROUTE_POLICY_ENABLED</li>
     *   <li>System property: forage.route.policy.enabled</li>
     *   <li>Properties file: forage.route.policy.enabled</li>
     *   <li>Default: true</li>
     * </ol>
     *
     * @return true if the factory is enabled, false otherwise
     */
    public boolean isEnabled() {
        // Load fresh from configuration sources (env vars, system properties, etc.)
        ConfigStore.getInstance().load(RoutePolicyFactoryConfigEntries.ENABLED);

        return ConfigStore.getInstance()
                .get(RoutePolicyFactoryConfigEntries.ENABLED)
                .map(Boolean::parseBoolean)
                .orElse(true);
    }

    /**
     * Returns the policy names configured for a specific route.
     *
     * <p>The policy names are comma-separated in the configuration.
     *
     * @param routeId the route ID
     * @return an Optional containing the policy names, or empty if not configured
     */
    public Optional<String> getPolicyNames(String routeId) {
        ConfigStore.getInstance().load(RoutePolicyFactoryConfigEntries.policyNameModule(routeId));

        return ConfigStore.getInstance().get(RoutePolicyFactoryConfigEntries.policyNameModule(routeId));
    }
}
