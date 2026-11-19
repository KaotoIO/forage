package io.kaoto.forage.jdbc.common;

import java.util.Set;
import io.kaoto.forage.core.common.ForageModuleDescriptor;
import io.kaoto.forage.core.common.QuarkusConfigHook;

public final class JdbcQuarkusConfigHook implements QuarkusConfigHook<DataSourceFactoryConfig> {

    @Override
    public ForageModuleDescriptor<DataSourceFactoryConfig, ?> descriptor() {
        return new JdbcModuleDescriptor();
    }

    @Override
    public Set<String> covered() {
        // not covered: pool.idle.validation.timeout.minutes, transaction.*, the aggregation/idempotent repositories
        return Set.of(
                "db.kind",
                "url",
                "username",
                "password",
                "pool.initial.size",
                "pool.min.size",
                "pool.max.size",
                "pool.acquisition.timeout.seconds",
                "pool.validation.timeout.seconds",
                "pool.leak.timeout.minutes",
                "properties.");
    }

    @Override
    public Set<String> dependencyKeys() {
        // the JDBC driver is added for the Forage db.kind
        return Set.of("db-kind");
    }
}
