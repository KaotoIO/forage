package io.kaoto.forage.core.jta.store;

import javax.sql.DataSource;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * The datasource Forage hands to Narayana's JDBC object store. It carries the settings the store
 * was configured with, so later configurations can tell whether they ask for the store that is
 * already in place, and delegates everything else to the underlying non-XA pool.
 */
public final class ObjectStoreDataSource implements DataSource {

    private final ObjectStoreSettings settings;
    private final DataSource delegate;

    ObjectStoreDataSource(ObjectStoreSettings settings, DataSource delegate) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    /** The configured datasource name ({@code object.store.datasource}). */
    public String name() {
        return settings.dataSource();
    }

    /** The settings the JDBC store was configured with. */
    public ObjectStoreSettings settings() {
        return settings;
    }

    /** The pool the object store writes through. */
    public DataSource delegate() {
        return delegate;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return delegate.getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return delegate.getConnection(username, password);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        delegate.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        delegate.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return delegate.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return delegate.getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        return delegate.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || delegate.isWrapperFor(iface);
    }
}
