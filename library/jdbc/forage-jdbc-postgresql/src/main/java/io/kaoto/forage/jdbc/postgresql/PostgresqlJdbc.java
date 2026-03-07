package io.kaoto.forage.jdbc.postgresql;

import org.postgresql.Driver;
import org.postgresql.xa.PGXADataSource;
import io.kaoto.forage.core.annotations.ForageBean;
import io.kaoto.forage.jdbc.common.PooledDataSource;

/**
 * PostgreSQL implementation extending PooledJdbc.
 * Provides PostgreSQL-specific connection provider configuration.
 */
@ForageBean(
        value = "postgresql",
        components = {"camel-sql", "camel-jdbc", "camel-spring-jdbc"},
        description = "PostgreSQL database",
        feature = "javax.sql.DataSource",
        runtimeDependencies = {"quarkus:mvn:io.quarkus:quarkus-jdbc-postgresql"})
public class PostgresqlJdbc extends PooledDataSource {

    @Override
    protected Class<?> getConnectionProviderClass() {
        if (getConfig().transactionEnabled()) {
            return PGXADataSource.class;
        } else {
            return Driver.class;
        }
    }

    @Override
    public String getTestQuery() {
        return "SELECT version(), current_database(), current_user";
    }
}
