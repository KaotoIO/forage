package io.kaoto.forage.jdbc.common;

import javax.sql.DataSource;

import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.agroal.api.AgroalDataSource;
import io.agroal.api.AgroalDataSourceListener;
import io.agroal.api.configuration.AgroalConnectionPoolConfiguration.ConnectionValidator;
import io.agroal.api.configuration.AgroalDataSourceConfiguration;
import io.agroal.api.configuration.supplier.AgroalConnectionFactoryConfigurationSupplier;
import io.agroal.api.configuration.supplier.AgroalConnectionPoolConfigurationSupplier;
import io.agroal.api.configuration.supplier.AgroalDataSourceConfigurationSupplier;
import io.agroal.api.security.NamePrincipal;
import io.agroal.api.security.SimplePassword;
import io.agroal.api.transaction.TransactionIntegration;
import io.agroal.narayana.NarayanaTransactionIntegration;
import io.kaoto.forage.core.jdbc.DataSourceProvider;
import io.kaoto.forage.core.jta.recovery.ForageRecoveryService;
import io.kaoto.forage.jdbc.common.idempotent.ForageIdRepository;
import io.kaoto.forage.jdbc.common.transactions.TransactionConfiguration;

/**
 * Abstract base class for pooled JDBC implementations using Agroal connection pooling.
 * Provides database-agnostic DataSource configuration with optimized pool settings.
 */
public abstract class PooledDataSource implements DataSourceProvider, ForageIdRepository {
    private static final Logger LOG = LoggerFactory.getLogger(PooledDataSource.class);

    private DataSourceFactoryConfig config;

    /**
     * Returns the connection provider class name for the specific database implementation.
     *
     * @return the JDBC driver class name
     */
    protected abstract Class<?> getConnectionProviderClass();

    /**
     * Returns the JDBC properties handed to the connection provider. By default these are the
     * configured {@link DataSourceFactoryConfig#driverProperties() driver properties}.
     *
     * <p>Agroal passes these properties to {@link java.sql.Driver#connect} when the provider is a
     * driver. When the provider is an {@link javax.sql.XADataSource}, Agroal sets each one through
     * the matching setter and converts the value to the setter's type. Subclasses override this
     * method when their XA datasource cannot take the JDBC URL as a whole.
     *
     * @param config the configuration of the datasource being created
     * @return the JDBC properties; never {@code null}
     */
    protected Map<String, String> connectionProperties(DataSourceFactoryConfig config) {
        return config.driverProperties();
    }

    /**
     * Returns the SQL that Agroal runs once on each new physical connection, or {@code null} for
     * none. Subclasses override this method to work around driver behavior at connection set-up.
     *
     * @param config the configuration of the datasource being created
     * @return the SQL statement, or {@code null}
     */
    protected String initialSql(DataSourceFactoryConfig config) {
        return null;
    }

    @Override
    public DataSource create(String id) {
        LOG.info("Creating DataSource with id: {}", id);
        return createPooledDataSource(id);
    }

    /**
     * Creates a pooled DataSource with the given configuration.
     *
     * @param id the configuration ID for logging
     * @return configured pooled DataSource
     * @throws RuntimeException if DataSource creation fails
     */
    protected AgroalDataSource createPooledDataSource(String id) {
        config = new DataSourceFactoryConfig(id);

        LOG.info(
                "DataSource configuration - JDBC URL: {}, Username: {}, Initial Size: {}, Min Size: {}, Max Size: {}, "
                        + "Acquisition Timeout: {}s, Validation Timeout: {}s, Leak Timeout: {}min, Idle Validation Timeout: {}min, "
                        + "Transaction Timeout: {}s",
                config.jdbcUrl(),
                config.username(),
                config.initialSize(),
                config.minSize(),
                config.maxSize(),
                config.acquisitionTimeoutSeconds(),
                config.validationTimeoutSeconds(),
                config.leakTimeoutMinutes(),
                config.idleValidationTimeoutMinutes(),
                config.transactionTimeoutSeconds());

        AgroalDataSourceConfigurationSupplier configSupplier = new AgroalDataSourceConfigurationSupplier();
        configSupplier.metricsEnabled(true);

        // Configure connection factory
        AgroalConnectionFactoryConfigurationSupplier connectionFactoryConfig =
                configSupplier.connectionPoolConfiguration().connectionFactoryConfiguration();

        connectionFactoryConfig
                .jdbcUrl(config.jdbcUrl())
                .connectionProviderClass(getConnectionProviderClass())
                .principal(new NamePrincipal(config.username()))
                .credential(new SimplePassword(config.password()));

        Map<String, String> connectionProperties = connectionProperties(config);
        if (!connectionProperties.isEmpty()) {
            // Values can hold secrets (e.g. trust store passwords), so log the names only
            LOG.info("DataSource JDBC properties for id {}: {}", id, connectionProperties.keySet());
            connectionProperties.forEach(connectionFactoryConfig::jdbcProperty);
        }
        String initialSql = initialSql(config);
        if (initialSql != null) {
            connectionFactoryConfig.initialSql(initialSql);
        }

        // Configure connection pool settings
        AgroalConnectionPoolConfigurationSupplier poolConfig = configSupplier.connectionPoolConfiguration();

        poolConfig
                .initialSize(config.initialSize())
                .minSize(config.minSize())
                .maxSize(config.maxSize())
                .acquisitionTimeout(Duration.ofSeconds(config.acquisitionTimeoutSeconds()))
                .validationTimeout(Duration.ofSeconds(config.validationTimeoutSeconds()))
                .leakTimeout(Duration.ofMinutes(config.leakTimeoutMinutes()))
                .idleValidationTimeout(Duration.ofMinutes(config.idleValidationTimeoutMinutes()))
                .connectionValidator(ConnectionValidator.defaultValidator());

        if (config.transactionEnabled()) {
            String instanceId = id == null ? "dataSource" : id;
            new TransactionConfiguration(config, instanceId).initializeNarayana();

            jakarta.transaction.TransactionManager transactionManager =
                    com.arjuna.ats.jta.TransactionManager.transactionManager();
            var synchronizationRegistry =
                    new com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionSynchronizationRegistryImple();
            if (config.transactionEnableRecovery()) {
                // The recovery-aware integration registers the datasource's XAResource with the
                // recovery registry (and removes it when the datasource closes), so Narayana can
                // resolve in-doubt branches after a crash. See issue #432.
                poolConfig.transactionIntegration(new NarayanaTransactionIntegration(
                        transactionManager,
                        synchronizationRegistry,
                        "forage-jdbc-" + instanceId,
                        false,
                        ForageRecoveryService.getInstance().scopedRegistry(recoveryKey(instanceId))));
            } else {
                poolConfig.transactionIntegration(
                        new NarayanaTransactionIntegration(transactionManager, synchronizationRegistry));
            }
        } else {
            poolConfig.transactionIntegration(TransactionIntegration.none());
        }

        // Build the configuration
        AgroalDataSourceConfiguration dsConfig = configSupplier.get();

        LOG.info("Pooled DataSource initialized successfully for id: {}", id);
        try {
            return AgroalDataSource.from(dsConfig, new WarningLogger(id));
        } catch (Exception e) {
            LOG.error("Failed to create DataSource for id: {}", id, e);
            throw new RuntimeException("Failed to create DataSource", e);
        }
    }

    /**
     * Key under which a datasource instance's XA recovery integration is registered with
     * {@link ForageRecoveryService}. Used by the bean factories to deregister on reload/stop.
     */
    public static String recoveryKey(String instanceId) {
        return "jdbc:" + instanceId;
    }

    protected DataSourceFactoryConfig getConfig() {
        return config;
    }

    /**
     * Logs Agroal's warnings. Agroal reports problems such as a JDBC property the XA datasource
     * has no setter for only to its listeners, so without one they would be lost.
     */
    private static final class WarningLogger implements AgroalDataSourceListener {
        private final String id;

        WarningLogger(String id) {
            this.id = id;
        }

        @Override
        public void onWarning(String message) {
            LOG.warn("DataSource {}: {}", id, message);
        }

        @Override
        public void onWarning(Throwable throwable) {
            LOG.warn("DataSource {}: {}", id, throwable.getMessage(), throwable);
        }
    }
}
