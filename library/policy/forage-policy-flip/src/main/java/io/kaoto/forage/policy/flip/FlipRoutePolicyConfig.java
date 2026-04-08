package io.kaoto.forage.policy.flip;

import io.kaoto.forage.core.util.config.AbstractConfig;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.core.util.config.MissingConfigException;

/**
 * Configuration class for the flip route policy.
 *
 * <p>Supports configuration of:
 * <ul>
 *   <li>paired-route: ID of the paired route to flip with (required)</li>
 *   <li>initially-active: Whether this route should be active initially (default: true)</li>
 * </ul>
 *
 * @since 1.0
 */
public class FlipRoutePolicyConfig extends AbstractConfig {

    public FlipRoutePolicyConfig() {
        this(null);
    }

    public FlipRoutePolicyConfig(String prefix) {
        super(prefix, FlipRoutePolicyConfigEntries.class);
    }

    @Override
    public String name() {
        return "forage-policy-flip";
    }

    /**
     * Returns the ID of the paired route.
     *
     * @return the paired route ID
     * @throws MissingConfigException if not configured
     */
    public String pairedRouteId() {
        ConfigModule module = FlipRoutePolicyConfigEntries.pairedRoute(super.prefix());
        ConfigStore.getInstance().load(module);
        return ConfigStore.getInstance()
                .get(module)
                .or(() -> ConfigStore.getInstance().getDirect(module.propertyName()))
                .orElseThrow(() -> new MissingConfigException("Missing paired-route configuration for flip policy"));
    }

    /**
     * Returns whether this route should be active initially.
     *
     * @return true if this route should start active, false otherwise
     */
    public boolean initiallyActive() {
        ConfigModule module = FlipRoutePolicyConfigEntries.initiallyActive(super.prefix());
        ConfigStore.getInstance().load(module);
        return ConfigStore.getInstance()
                .get(module)
                .or(() -> ConfigStore.getInstance().getDirect(module.propertyName()))
                .map(Boolean::parseBoolean)
                .orElse(true);
    }
}
