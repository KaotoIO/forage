package io.kaoto.forage.jdbc.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Function;
import io.agroal.api.AgroalDataSource;
import io.kaoto.forage.core.common.AuxiliaryBeanDescriptor;
import io.kaoto.forage.core.common.ForageModuleDescriptor;
import io.kaoto.forage.core.common.ServiceLoaderHelper;
import io.kaoto.forage.core.jdbc.DataSourceProvider;
import io.kaoto.forage.core.jta.store.ObjectStoreSettings;
import io.kaoto.forage.core.jta.store.ObjectStoreType;
import io.kaoto.forage.core.util.config.MissingConfigException;
import io.kaoto.forage.jdbc.common.aggregation.ForageAggregationRepository;
import io.kaoto.forage.jdbc.common.idempotent.ForageIdRepository;
import io.kaoto.forage.jdbc.common.idempotent.ForageJdbcMessageIdRepository;

/**
 * Module descriptor for Forage JDBC. Captures all JDBC-specific knowledge:
 * prefix discovery, provider resolution, Quarkus property translation, and auxiliary bean creation.
 *
 * @since 1.1
 */
public class JdbcModuleDescriptor implements ForageModuleDescriptor<DataSourceFactoryConfig, DataSourceProvider> {

    @Override
    public String modulePrefix() {
        return "jdbc";
    }

    @Override
    public DataSourceFactoryConfig createConfig(String prefix) {
        return prefix == null ? new DataSourceFactoryConfig() : new DataSourceFactoryConfig(prefix);
    }

    @Override
    public Class<DataSourceProvider> providerClass() {
        return DataSourceProvider.class;
    }

    @Override
    public String resolveProviderClassName(DataSourceFactoryConfig config) {
        return DataSourceCommonExportHelper.transformDbKindIntoProviderClass(config.dbKind());
    }

    @Override
    public String defaultBeanName() {
        return "dataSource";
    }

    @Override
    public Class<?> primaryBeanClass() {
        return AgroalDataSource.class;
    }

    @Override
    public boolean transactionEnabled(DataSourceFactoryConfig config) {
        return config.transactionEnabled();
    }

    @Override
    public Map<String, String> translateProperties(String prefix, DataSourceFactoryConfig config) {
        Map<String, String> props = new HashMap<>();

        String quarkusPrefix = "quarkus.datasource.";
        String effectivePrefix = prefix != null ? prefix : defaultBeanName();
        quarkusPrefix += "\"" + effectivePrefix + "\".";

        putIfNotEmpty(props, quarkusPrefix + "db-kind", config.dbKind());
        putIfNotEmpty(props, quarkusPrefix + "password", config.password());
        putIfNotEmpty(props, quarkusPrefix + "username", config.username());
        putIfNotEmpty(props, quarkusPrefix + "jdbc.url", config.jdbcUrl());
        props.put(quarkusPrefix + "jdbc.initial-size", String.valueOf(config.initialSize()));
        props.put(quarkusPrefix + "jdbc.min-size", String.valueOf(config.minSize()));
        props.put(quarkusPrefix + "jdbc.max-size", String.valueOf(config.maxSize()));
        props.put(quarkusPrefix + "jdbc.acquisition-timeout", config.acquisitionTimeoutSeconds() + "S");
        props.put(quarkusPrefix + "jdbc.validation-query-timeout", config.validationTimeoutSeconds() + "S");
        props.put(quarkusPrefix + "jdbc.leak-detection-interval", config.leakTimeoutMinutes() + "M");

        if (config.transactionEnabled()) {
            props.put(quarkusPrefix + "jdbc.transaction-isolation-level", "READ_COMMITTED");
            props.put(
                    "quarkus.transaction-manager.default-transaction-timeout",
                    config.transactionTimeoutSeconds() + "S");
            putIfNotEmpty(props, "quarkus.transaction-manager.node-name", config.transactionNodeId());
            props.put(
                    "quarkus.transaction-manager.enable-recovery", String.valueOf(config.transactionEnableRecovery()));
            putIfNotEmpty(props, "quarkus.transaction-manager.recovery-modules", config.transactionRecoveryModules());
            putIfNotEmpty(
                    props,
                    "quarkus.transaction-manager.xa-resource-orphan-filters",
                    config.transactionXaResourceOrphanFilters());
            putIfNotEmpty(
                    props,
                    "quarkus.transaction-manager.object-store.directory",
                    config.transactionObjectStoreDirectory());
            putIfNotEmpty(props, "quarkus.transaction-manager.object-store.type", config.transactionObjectStoreType());
            putIfNotEmpty(
                    props,
                    "quarkus.transaction-manager.object-store.datasource",
                    config.transactionObjectStoreDataSource());
            props.put(
                    "quarkus.transaction-manager.object-store.create-table",
                    String.valueOf(config.transactionObjectStoreCreateTable()));
            props.put(
                    "quarkus.transaction-manager.object-store.drop-table",
                    String.valueOf(config.transactionObjectStoreDropTable()));
            putIfNotEmpty(
                    props,
                    "quarkus.transaction-manager.object-store.table-prefix",
                    config.transactionObjectStoreTablePrefix());
            props.putAll(jdbcObjectStoreDataSourceProperties(config.transactionObjectStoreSettings()));
        }

        return props;
    }

    /**
     * For the {@code jdbc} object store, quarkus-narayana-jta requires the referenced datasource to
     * have its transaction integration disabled (it rejects the store datasource otherwise): returns
     * {@code quarkus.datasource."<name>".jdbc.transactions=disabled}. Applies the same rules as the
     * other runtimes: the datasource must be configured and must not be a transactional Forage
     * datasource. Empty for the other store types. (The JMS module descriptor emits the same
     * property without the Forage-datasource check, as it has no JDBC dependency.)
     */
    static Map<String, String> jdbcObjectStoreDataSourceProperties(ObjectStoreSettings settings) {
        if (settings.storeType() != ObjectStoreType.JDBC) {
            return Map.of();
        }
        String name = settings.dataSource();
        if (name == null) {
            throw new MissingConfigException("The transaction object store type is 'jdbc' ("
                    + settings.typeProperty() + ") but " + settings.dataSourceProperty() + " is not set. Set it to"
                    + " the name of a non-XA Forage datasource (forage.<name>.jdbc.*) that holds the transaction"
                    + " log table.");
        }
        DataSourceFactoryConfig referenced = new DataSourceFactoryConfig(name);
        if (referenced.transactionEnabled()) {
            throw new IllegalStateException("The JDBC transaction object store datasource '" + name
                    + "' (" + settings.dataSourceProperty() + ") has transactions enabled (forage." + name
                    + ".jdbc.transaction.enabled=true). The object store needs a non-XA datasource: configure a"
                    + " separate forage.<name>.jdbc.* datasource without transaction.enabled and reference it.");
        }
        return Map.of("quarkus.datasource.\"" + name + "\".jdbc.transactions", "disabled");
    }

    /**
     * Puts the value only when it is non-null and non-empty, so unset optional configuration
     * never leaks into the translated Quarkus properties as {@code null} or empty values.
     */
    private static void putIfNotEmpty(Map<String, String> props, String key, String value) {
        if (value != null && !value.isEmpty()) {
            props.put(key, value);
        }
    }

    @Override
    public List<AuxiliaryBeanDescriptor> auxiliaryBeans(String prefix) {
        return auxiliaryBeans(prefix, name -> {
            DataSourceFactoryConfig c = createConfig(prefix);
            // Pass the original prefix (null for the default config) so the provider reads
            // forage.jdbc.* rather than forage.dataSource.jdbc.* for the default configuration.
            ForageDataSource ds = createDataSource(c, prefix);
            return ds != null ? ds.dataSource() : null;
        });
    }

    @Override
    public List<AuxiliaryBeanDescriptor> auxiliaryBeans(String prefix, Function<String, Object> primaryLookup) {
        DataSourceFactoryConfig config = createConfig(prefix);
        List<AuxiliaryBeanDescriptor> beans = new ArrayList<>();
        String lookupName = prefix != null ? prefix : defaultBeanName();

        if (config.transactionEnabled() && config.aggregationRepositoryName() != null) {
            String repoName = config.aggregationRepositoryName();
            beans.add(new AuxiliaryBeanDescriptor(repoName, ForageAggregationRepository.class, () -> {
                DataSourceFactoryConfig c = createConfig(prefix);
                AgroalDataSource ds = lookupAgroalDataSource(primaryLookup, lookupName, repoName);
                if (ds != null) {
                    return new ForageAggregationRepository(
                            ds, com.arjuna.ats.jta.TransactionManager.transactionManager(), c);
                }
                return null;
            }));
        }

        if (config.enableIdempotentRepository()) {
            String tableName = config.idempotentRepositoryTableName();
            beans.add(new AuxiliaryBeanDescriptor(tableName, ForageJdbcMessageIdRepository.class, () -> {
                DataSourceFactoryConfig c = createConfig(prefix);
                AgroalDataSource ds = lookupAgroalDataSource(primaryLookup, lookupName, tableName);
                if (ds != null) {
                    ForageIdRepository idRepo = resolveIdRepository(c);
                    return new ForageJdbcMessageIdRepository(c, ds, idRepo);
                }
                return null;
            }));
        }

        return beans;
    }

    private static AgroalDataSource lookupAgroalDataSource(
            Function<String, Object> primaryLookup, String lookupName, String auxiliaryBeanName) {
        Object primary = primaryLookup.apply(lookupName);
        if (primary == null) {
            return null;
        }
        if (!(primary instanceof AgroalDataSource agroalDataSource)) {
            throw new IllegalStateException("Forage auxiliary repository '" + auxiliaryBeanName
                    + "' requires the Forage-managed AgroalDataSource bean '" + lookupName + "', but the resolved bean"
                    + " is of type " + primary.getClass().getName()
                    + ". Auxiliary JDBC repositories (aggregation/idempotent) can only be attached to a Forage"
                    + " AgroalDataSource.");
        }
        return agroalDataSource;
    }

    /**
     * Resolves the ForageIdRepository from the provider without creating a DataSource pool.
     * Used when the primary DataSource is already available (e.g., as a Spring bean).
     */
    ForageIdRepository resolveIdRepository(DataSourceFactoryConfig config) {
        String providerClass = resolveProviderClassName(config);
        List<ServiceLoader.Provider<DataSourceProvider>> providers =
                ServiceLoader.load(DataSourceProvider.class).stream().toList();

        ServiceLoader.Provider<DataSourceProvider> provider;
        if (providers.size() == 1) {
            provider = providers.get(0);
        } else {
            provider = ServiceLoaderHelper.findProviderByClassName(providers, providerClass);
        }

        if (provider == null) {
            return null;
        }

        DataSourceProvider dsProvider = provider.get();
        if (dsProvider instanceof ForageIdRepository forageIdRepo) {
            return forageIdRepo;
        }
        return null;
    }

    /**
     * Creates a DataSource using ServiceLoader to find the appropriate provider.
     */
    ForageDataSource createDataSource(DataSourceFactoryConfig config, String name) {
        String providerClass = resolveProviderClassName(config);
        List<ServiceLoader.Provider<DataSourceProvider>> providers =
                ServiceLoader.load(DataSourceProvider.class).stream().toList();

        ServiceLoader.Provider<DataSourceProvider> provider;
        if (providers.size() == 1) {
            provider = providers.get(0);
        } else {
            provider = ServiceLoaderHelper.findProviderByClassName(providers, providerClass);
        }

        if (provider == null) {
            return null;
        }

        DataSourceProvider dsProvider = provider.get();
        AgroalDataSource dataSource = (AgroalDataSource) dsProvider.create(name);

        ForageIdRepository forageIdRepository = null;
        if (dsProvider instanceof ForageIdRepository forageIdRepo) {
            forageIdRepository = forageIdRepo;
        }

        return new ForageDataSource(dataSource, forageIdRepository);
    }
}
