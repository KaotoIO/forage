package io.kaoto.forage.jdbc.mariadb;

import org.mariadb.jdbc.Driver;
import org.mariadb.jdbc.MariaDbDataSource;
import io.kaoto.forage.core.annotations.ForageBean;
import io.kaoto.forage.jdbc.common.PooledDataSource;

/**
 * MariaDB implementation extending PooledJdbc.
 * Provides MariaDB-specific connection provider configuration.
 */
@ForageBean(
        value = "mariadb",
        components = {"camel-sql", "camel-jdbc", "camel-spring-jdbc"},
        description = "MariaDB database",
        feature = "javax.sql.DataSource",
        runtimeDependencies = {"quarkus:mvn:io.quarkus:quarkus-jdbc-mariadb"})
public class MariadbJdbc extends PooledDataSource {

    @Override
    protected Class<?> getConnectionProviderClass() {
        if (getConfig().transactionEnabled()) {
            return MariaDbDataSource.class;
        } else {
            return Driver.class;
        }
    }

    @Override
    public String getTestQuery() {
        return "SELECT VERSION(), DATABASE(), USER()";
    }
}
