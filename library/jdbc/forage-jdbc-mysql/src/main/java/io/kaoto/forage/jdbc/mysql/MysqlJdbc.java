package io.kaoto.forage.jdbc.mysql;

import io.kaoto.forage.core.annotations.ForageBean;
import io.kaoto.forage.jdbc.common.PooledDataSource;
import com.mysql.cj.jdbc.Driver;
import com.mysql.cj.jdbc.MysqlXADataSource;

/**
 * MySQL implementation extending PooledJdbc.
 * Provides MySQL-specific connection provider configuration.
 */
@ForageBean(
        value = "mysql",
        components = {"camel-sql", "camel-jdbc", "camel-spring-jdbc"},
        description = "MySQL database",
        feature = "javax.sql.DataSource",
        runtimeDependencies = {"quarkus:mvn:io.quarkus:quarkus-jdbc-mysql"})
public class MysqlJdbc extends PooledDataSource {

    @Override
    protected Class<?> getConnectionProviderClass() {
        if (getConfig().transactionEnabled()) {
            return MysqlXADataSource.class;
        } else {
            return Driver.class;
        }
    }

    @Override
    public String getTestQuery() {
        return "SELECT VERSION(), DATABASE(), USER()";
    }
}
