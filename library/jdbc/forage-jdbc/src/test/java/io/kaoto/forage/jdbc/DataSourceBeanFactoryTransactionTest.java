package io.kaoto.forage.jdbc;

import javax.sql.DataSource;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.List;
import java.util.logging.Logger;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that JTA transaction policy beans are bound when only a prefixed (named)
 * JDBC configuration enables transactions (issue #230). The prefixed configuration
 * ({@code forage.ds1.jdbc.transaction.enabled=true}) comes from the test classpath's
 * {@code forage-datasource-factory.properties}; the default (unprefixed) configuration
 * has transactions disabled.
 */
class DataSourceBeanFactoryTransactionTest {

    private static final List<String> POLICY_BEANS =
            List.of("PROPAGATION_REQUIRED", "MANDATORY", "NEVER", "NOT_SUPPORTED", "REQUIRES_NEW", "SUPPORTS");

    @Test
    void prefixedTransactionEnabledBindsJtaPolicies() {
        CamelContext camelContext = new DefaultCamelContext();
        // Pre-bind the prefixed DataSource so configure() does not need a database provider
        camelContext.getRegistry().bind("ds1", dummyDataSource());

        DataSourceBeanFactory beanFactory = new DataSourceBeanFactory();
        beanFactory.setCamelContext(camelContext);
        beanFactory.configure();

        for (String name : POLICY_BEANS) {
            assertThat(camelContext.getRegistry().lookupByName(name))
                    .as("JTA policy bean '%s' should be bound when a prefixed config enables transactions", name)
                    .isNotNull();
        }
    }

    private static DataSource dummyDataSource() {
        return new DataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                throw new SQLException("test stub");
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                throw new SQLException("test stub");
            }

            @Override
            public PrintWriter getLogWriter() {
                return null;
            }

            @Override
            public void setLogWriter(PrintWriter out) {
                // No-op stub — not needed for this unit test
            }

            @Override
            public void setLoginTimeout(int seconds) {
                // No-op stub — not needed for this unit test
            }

            @Override
            public int getLoginTimeout() {
                return 0;
            }

            @Override
            public Logger getParentLogger() throws SQLFeatureNotSupportedException {
                throw new SQLFeatureNotSupportedException();
            }

            @Override
            public <T> T unwrap(Class<T> iface) throws SQLException {
                throw new SQLException("test stub");
            }

            @Override
            public boolean isWrapperFor(Class<?> iface) {
                return false;
            }
        };
    }
}
