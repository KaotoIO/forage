package io.kaoto.forage.jdbc.common.idempotent;

import javax.sql.DataSource;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import io.kaoto.forage.jdbc.common.DataSourceFactoryConfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.Resources.SYSTEM_PROPERTIES;

@DisplayName("ForageJdbcMessageIdRepository Tests")
@ResourceLock(SYSTEM_PROPERTIES)
class ForageJdbcMessageIdRepositoryTest {

    @Test
    @DisplayName("Constructor sets a DataSourceTransactionManager-backed template when transactions are disabled")
    void constructorSetsTransactionTemplateWhenTransactionsDisabled() {
        DataSource dataSource = new StubDataSource();
        DataSourceFactoryConfig config = new DataSourceFactoryConfig(null);

        ForageJdbcMessageIdRepository repo =
                new ForageJdbcMessageIdRepository(config, dataSource, new ForageIdRepository() {});

        assertThat(repo.getTransactionTemplate()).isNotNull();
        assertThat(repo.getTransactionTemplate().getTransactionManager())
                .isInstanceOf(DataSourceTransactionManager.class);
    }

    private static class StubDataSource implements DataSource {
        @Override
        public Connection getConnection() throws SQLException {
            return null;
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return null;
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            // No-op stub — not needed for this unit test
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            // No-op stub — not needed for this unit test
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return null;
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return null;
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return false;
        }
    }
}
