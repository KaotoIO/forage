package io.kaoto.forage.jdbc.common.transactions;

import javax.sql.DataSource;

import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.core.common.ServiceLoaderHelper;
import io.kaoto.forage.core.jdbc.DataSourceProvider;
import io.kaoto.forage.core.jta.store.ObjectStoreDataSourceProvider;
import io.kaoto.forage.core.util.config.MissingConfigException;
import io.kaoto.forage.jdbc.common.DataSourceCommonExportHelper;
import io.kaoto.forage.jdbc.common.DataSourceFactoryConfig;

/**
 * Creates the datasource of Narayana's JDBC object store from a named Forage datasource
 * configuration ({@code forage.<name>.jdbc.*}).
 *
 * <p>The referenced configuration must not have transactions enabled: Narayana writes the
 * transaction log in its own local transactions, so the log write must never be enlisted in the
 * XA transaction it records. With transactions disabled, the Forage provider for the configured
 * {@code db.kind} builds a dedicated plain Agroal pool (driver class, no transaction integration).
 * This pool is separate from the {@code <name>} bean that the bean factories may also register, and
 * lives as long as the JVM-global transaction manager. The same configuration may point at the
 * database of an XA datasource; only the pool has to be separate.
 *
 * <p>Registered as a {@link ServiceLoader} service so that the JMS module can use it too.
 */
public class JdbcObjectStoreDataSourceProvider implements ObjectStoreDataSourceProvider {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcObjectStoreDataSourceProvider.class);

    @Override
    public DataSource create(String name) {
        DataSourceFactoryConfig config = new DataSourceFactoryConfig(name);
        String keyPrefix = "forage." + name + ".jdbc.";

        String dbKind;
        try {
            dbKind = config.dbKind();
        } catch (MissingConfigException e) {
            throw new MissingConfigException("The JDBC transaction object store datasource '" + name
                    + "' is not configured: set " + keyPrefix + "db.kind, " + keyPrefix + "url, " + keyPrefix
                    + "username and " + keyPrefix + "password (without " + keyPrefix
                    + "transaction.enabled), or point the object store datasource property at another"
                    + " non-XA Forage datasource.");
        }

        if (config.transactionEnabled()) {
            throw new IllegalStateException("The JDBC transaction object store datasource '" + name
                    + "' has transactions enabled (" + keyPrefix + "transaction.enabled=true). Narayana writes the"
                    + " transaction log outside the XA transactions it records, so the object store needs a plain"
                    + " non-XA datasource: configure a separate forage.<name>.jdbc.* datasource without"
                    + " transaction.enabled (it may point at the same database) and reference that one.");
        }

        String providerClass = DataSourceCommonExportHelper.transformDbKindIntoProviderClass(dbKind);
        List<ServiceLoader.Provider<DataSourceProvider>> providers =
                ServiceLoader.load(DataSourceProvider.class).stream().toList();
        ServiceLoader.Provider<DataSourceProvider> provider =
                ServiceLoaderHelper.findProviderByClassName(providers, providerClass);
        if (provider == null) {
            String available = providers.stream().map(p -> p.type().getName()).collect(Collectors.joining(", "));
            throw new IllegalStateException(("No DataSourceProvider found for the JDBC transaction object store"
                            + " datasource '%s' of kind '%s' (expected %s). Available providers: %s")
                    .formatted(name, dbKind, providerClass, available.isEmpty() ? "none" : available));
        }

        LOG.info("Creating the non-XA {} pool '{}' for the JDBC transaction object store", dbKind, name);
        try {
            return provider.get().create(name);
        } catch (MissingConfigException e) {
            throw new MissingConfigException("The JDBC transaction object store datasource '" + name
                    + "' is incomplete (" + keyPrefix + "*): " + e.getMessage());
        }
    }
}
