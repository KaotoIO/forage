package io.kaoto.forage.jdbc.h2;

import org.h2.Driver;
import org.h2.jdbcx.JdbcDataSource;
import io.kaoto.forage.core.annotations.ForageBean;
import io.kaoto.forage.jdbc.common.PooledDataSource;

/**
 * H2 Database implementation extending PooledJdbc.
 * Provides H2-specific connection provider configuration.
 */
@ForageBean(
        value = "h2",
        components = {"camel-sql", "camel-jdbc", "camel-spring-jdbc"},
        description = "H2 database",
        feature = "javax.sql.DataSource",
        runtimeDependencies = {"quarkus:mvn:io.quarkus:quarkus-jdbc-h2"})
public class H2Jdbc extends PooledDataSource {

    @Override
    protected Class<?> getConnectionProviderClass() {
        if (getConfig().transactionEnabled()) {
            return JdbcDataSource.class;
        } else {
            return Driver.class;
        }
    }

    @Override
    public String getTestQuery() {
        return "SELECT H2VERSION(), SCHEMA(), USER()";
    }
}
