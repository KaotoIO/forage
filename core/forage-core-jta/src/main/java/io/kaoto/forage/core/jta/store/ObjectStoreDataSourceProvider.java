package io.kaoto.forage.core.jta.store;

import javax.sql.DataSource;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Creates the datasource of Narayana's JDBC object store from the name configured in
 * {@code object.store.datasource}.
 *
 * <p>The returned datasource must be a plain, non-XA pool: Narayana writes the transaction log in
 * its own local transactions, outside the XA transactions the log is recording. Implementations
 * must reject a reference to an XA-enabled configuration.
 *
 * <p>Implemented by {@code forage-jdbc-common} and discovered through {@link ServiceLoader}, so
 * that modules without a JDBC dependency (JMS) can store their transaction log in a database.
 */
@FunctionalInterface
public interface ObjectStoreDataSourceProvider {

    /**
     * Creates a new non-XA datasource for the named configuration.
     *
     * @param name the configured datasource name
     * @return a new datasource, never {@code null}
     * @throws io.kaoto.forage.core.util.config.MissingConfigException if the named datasource is not configured
     * @throws IllegalStateException if the named datasource cannot back the object store (e.g. XA-enabled)
     */
    DataSource create(String name);

    /**
     * Finds the provider on the classpath (thread context class loader first, then the class loader
     * of this interface).
     *
     * @throws IllegalStateException if no provider is available
     */
    static ObjectStoreDataSourceProvider discover() {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        if (tccl != null) {
            Iterator<ObjectStoreDataSourceProvider> it = ServiceLoader.load(ObjectStoreDataSourceProvider.class, tccl)
                    .iterator();
            if (it.hasNext()) {
                return it.next();
            }
        }
        Iterator<ObjectStoreDataSourceProvider> it = ServiceLoader.load(
                        ObjectStoreDataSourceProvider.class, ObjectStoreDataSourceProvider.class.getClassLoader())
                .iterator();
        if (it.hasNext()) {
            return it.next();
        }
        throw new IllegalStateException("The JDBC transaction object store needs the Forage JDBC module to create"
                + " its datasource, but no " + ObjectStoreDataSourceProvider.class.getName()
                + " implementation is on the classpath. Add forage-jdbc and the driver module of the database"
                + " (for example forage-jdbc-postgresql); with Camel JBang, configuring the referenced"
                + " forage.<name>.jdbc.* datasource is enough for the dependencies to be added.");
    }
}
