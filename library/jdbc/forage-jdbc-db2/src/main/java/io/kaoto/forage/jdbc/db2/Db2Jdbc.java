package io.kaoto.forage.jdbc.db2;

import java.util.LinkedHashMap;
import java.util.Map;
import io.kaoto.forage.core.annotations.ForageBean;
import io.kaoto.forage.jdbc.common.DataSourceFactoryConfig;
import io.kaoto.forage.jdbc.common.PooledDataSource;
import com.ibm.db2.jcc.DB2Driver;
import com.ibm.db2.jcc.DB2XADataSource;

/**
 * IBM DB2 implementation extending PooledJdbc.
 * Provides DB2-specific connection provider configuration.
 */
@ForageBean(
        value = "db2",
        components = {"camel-sql", "camel-jdbc", "camel-spring-jdbc"},
        description = "IBM DB2 database",
        feature = "javax.sql.DataSource")
public class Db2Jdbc extends PooledDataSource {

    @Override
    protected Class<?> getConnectionProviderClass() {
        if (getConfig().transactionEnabled()) {
            return DB2XADataSource.class;
        } else {
            return DB2Driver.class;
        }
    }

    /**
     * In XA mode, sends the server, port, database and driver properties from the JDBC URL as
     * separate properties. {@link DB2XADataSource} has no URL setter, and Agroal skips the URL
     * when there is no setter for it.
     */
    @Override
    protected Map<String, String> connectionProperties(DataSourceFactoryConfig config) {
        Map<String, String> properties = new LinkedHashMap<>();
        if (config.transactionEnabled()) {
            properties.putAll(Db2JdbcUrl.toDataSourceProperties(config.jdbcUrl()));
        }
        properties.putAll(super.connectionProperties(config));
        return properties;
    }

    /**
     * In XA mode, runs a statement on each new connection. The DB2 driver sends special registers
     * set as datasource properties (such as {@code currentSchema}) with the first statement.
     * Agroal opens and closes a logical connection during set-up without running one, and the
     * driver then resets those registers to their defaults.
     */
    @Override
    protected String initialSql(DataSourceFactoryConfig config) {
        return config.transactionEnabled() ? "VALUES 1" : super.initialSql(config);
    }

    @Override
    public String getTestQuery() {
        return "SELECT service_level, fixpack_num, bld_level FROM TABLE (sysproc.env_get_inst_info()) as A";
    }
}
