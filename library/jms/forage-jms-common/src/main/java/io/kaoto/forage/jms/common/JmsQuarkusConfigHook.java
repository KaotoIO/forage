package io.kaoto.forage.jms.common;

import java.util.Map;
import java.util.Set;
import io.kaoto.forage.core.common.ForageModuleDescriptor;
import io.kaoto.forage.core.common.QuarkusConfigHook;

public final class JmsQuarkusConfigHook implements QuarkusConfigHook<ConnectionFactoryConfig> {

    /** The catalog group whose dependency (quarkus-pooled-jms) a pooled JMS connection factory needs. */
    static final String POOL_GROUP = "jms-connection-pool";

    @Override
    public ForageModuleDescriptor<ConnectionFactoryConfig, ?> descriptor() {
        return new JmsModuleDescriptor();
    }

    @Override
    public Set<String> covered() {
        // not covered: client.id, ssl.*, pool.connection.timeout.millis, transaction.*, artemis.*
        return Set.of(
                "kind",
                "broker.url",
                "username",
                "password",
                "pool.enabled",
                "pool.max.connections",
                "pool.max.sessions.per.connection",
                "pool.idle.timeout.millis",
                "pool.expiry.timeout.millis",
                "pool.block.if.full",
                "pool.block.if.full.timeout.millis");
    }

    @Override
    public String unsupported(String name, ConnectionFactoryConfig config) {
        // per-broker components come from the Forage extension
        return name != null ? "uses named configurations, which need the Forage extension" : null;
    }

    @Override
    public Set<String> adjust(ConnectionFactoryConfig config, Map<String, String> translated) {
        // pooling is on by default: quarkus-pooled-jms must be added
        if (config.poolEnabled()) {
            return Set.of(POOL_GROUP);
        }
        // switched off explicitly: quarkus-pooled-jms may still come from another dependency
        translated.keySet().removeIf(key -> key.startsWith("quarkus.pooled-jms."));
        translated.put("quarkus.pooled-jms.pooling.enabled", "false");
        return Set.of();
    }
}
