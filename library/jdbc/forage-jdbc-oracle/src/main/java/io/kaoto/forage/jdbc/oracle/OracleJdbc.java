package io.kaoto.forage.jdbc.oracle;

import io.kaoto.forage.core.annotations.ForageBean;
import io.kaoto.forage.jdbc.common.PooledDataSource;
import oracle.jdbc.OracleDriver;

/**
 * Oracle Database implementation extending PooledJdbc.
 * Provides Oracle-specific connection provider configuration.
 */
@ForageBean(
        value = "oracle",
        components = {"camel-sql", "camel-jdbc", "camel-spring-jdbc"},
        description = "Oracle database",
        feature = "javax.sql.DataSource")
public class OracleJdbc extends PooledDataSource {

    /**
     * In XA mode, returns {@link ForageOracleXADataSource}: a concrete Oracle XA datasource (the
     * driver's {@code oracle.jdbc.xa.OracleXADataSource} is abstract) that also accepts Oracle
     * connection properties without a setter.
     */
    @Override
    protected Class<?> getConnectionProviderClass() {
        if (getConfig().transactionEnabled()) {
            return ForageOracleXADataSource.class;
        } else {
            return OracleDriver.class;
        }
    }

    @Override
    public String getTestQuery() {
        return "SELECT banner FROM v$version WHERE ROWNUM = 1";
    }

    @Override
    public String createString() {
        return "CREATE TABLE CAMEL_MESSAGEPROCESSED (processorName VARCHAR2(255), messageId VARCHAR2(100), createdAt TIMESTAMP, PRIMARY KEY (processorName, messageId))";
    }
}
