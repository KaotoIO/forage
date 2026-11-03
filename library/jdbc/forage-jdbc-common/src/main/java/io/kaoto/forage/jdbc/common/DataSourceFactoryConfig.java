package io.kaoto.forage.jdbc.common;

import io.kaoto.forage.core.jta.store.ObjectStoreSettings;
import io.kaoto.forage.core.util.config.AbstractConfig;
import io.kaoto.forage.jdbc.common.idempotent.ForageJdbcMessageIdRepository;

import static io.kaoto.forage.jdbc.common.DataSourceFactoryConfigEntries.ACQUISITION_TIMEOUT_SECONDS;
import static io.kaoto.forage.jdbc.common.DataSourceFactoryConfigEntries.AGGREGATION_REPOSITORY_ALLOW_SERIALIZED_HEADERS;
import static io.kaoto.forage.jdbc.common.DataSourceFactoryConfigEntries.AGGREGATION_REPOSITORY_DEAD_LETTER_URI;
import static io.kaoto.forage.jdbc.common.DataSourceFactoryConfigEntries.AGGREGATION_REPOSITORY_ENABLED;
import static io.kaoto.forage.jdbc.common.DataSourceFactoryConfigEntries.AGGREGATION_REPOSITORY_HEADERS_TO_STORE;
import static io.kaoto.forage.jdbc.common.DataSourceFactoryConfigEntries.AGGREGATION_REPOSITORY_MAXIMUM_REDELIVERIES;
import static io.kaoto.forage.jdbc.common.DataSourceFactoryConfigEntries.AGGREGATION_REPOSITORY_NAME;
import static io.kaoto.forage.jdbc.common.DataSourceFactoryConfigEntries.AGGREGATION_REPOSITORY_PROPAGATION_BEHAVIOUR_NAME;
import static io.kaoto.forage.jdbc.common.DataSourceFactoryConfigEntries.AGGREGATION_REPOSITORY_STORE_BODY;
import static io.kaoto.forage.jdbc.common.DataSourceFactoryConfigEntries.AGGREGATION_REPOSITORY_USE_RECOVERY;
import static io.kaoto.forage.jdbc.common.DataSourceFactoryConfigEntries.DB_KIND;
import static io.kaoto.forage.jdbc.common.DataSourceFactoryConfigEntries.ENABLE_IDEMPOTENT_REPOSITORY;
import static io.kaoto.forage.jdbc.common.DataSourceFactoryConfigEntries.IDEMPOTENT_REPOSITORY_PROCESSOR_NAME;
import static io.kaoto.forage.jdbc.common.DataSourceFactoryConfigEntries.IDEMPOTENT_REPOSITORY_TABLE_IF_NOT_EXISTS;
import static io.kaoto.forage.jdbc.common.DataSourceFactoryConfigEntries.IDEMPOTENT_REPOSITORY_TABLE_NAME;
import static io.kaoto.forage.jdbc.common.DataSourceFactoryConfigEntries.IDLE_VALIDATION_TIMEOUT_MINUTES;
import static io.kaoto.forage.jdbc.common.DataSourceFactoryConfigEntries.INITIAL_SIZE;
import static io.kaoto.forage.jdbc.common.DataSourceFactoryConfigEntries.JDBC_URL;
import static io.kaoto.forage.jdbc.common.DataSourceFactoryConfigEntries.LEAK_TIMEOUT_MINUTES;
import static io.kaoto.forage.jdbc.common.DataSourceFactoryConfigEntries.MAX_SIZE;
import static io.kaoto.forage.jdbc.common.DataSourceFactoryConfigEntries.MIN_SIZE;
import static io.kaoto.forage.jdbc.common.DataSourceFactoryConfigEntries.PASSWORD;
import static io.kaoto.forage.jdbc.common.DataSourceFactoryConfigEntries.TRANSACTION_ENABLED;
import static io.kaoto.forage.jdbc.common.DataSourceFactoryConfigEntries.TRANSACTION_ENABLE_RECOVERY;
import static io.kaoto.forage.jdbc.common.DataSourceFactoryConfigEntries.TRANSACTION_EXPIRY_SCANNERS;
import static io.kaoto.forage.jdbc.common.DataSourceFactoryConfigEntries.TRANSACTION_NODE_ID;
import static io.kaoto.forage.jdbc.common.DataSourceFactoryConfigEntries.TRANSACTION_OBJECT_STORE_CREATE_TABLE;
import static io.kaoto.forage.jdbc.common.DataSourceFactoryConfigEntries.TRANSACTION_OBJECT_STORE_DATASOURCE;
import static io.kaoto.forage.jdbc.common.DataSourceFactoryConfigEntries.TRANSACTION_OBJECT_STORE_DIRECTORY;
import static io.kaoto.forage.jdbc.common.DataSourceFactoryConfigEntries.TRANSACTION_OBJECT_STORE_DROP_TABLE;
import static io.kaoto.forage.jdbc.common.DataSourceFactoryConfigEntries.TRANSACTION_OBJECT_STORE_ID;
import static io.kaoto.forage.jdbc.common.DataSourceFactoryConfigEntries.TRANSACTION_OBJECT_STORE_TABLE_PREFIX;
import static io.kaoto.forage.jdbc.common.DataSourceFactoryConfigEntries.TRANSACTION_OBJECT_STORE_TYPE;
import static io.kaoto.forage.jdbc.common.DataSourceFactoryConfigEntries.TRANSACTION_RECOVERY_BACKOFF_SECONDS;
import static io.kaoto.forage.jdbc.common.DataSourceFactoryConfigEntries.TRANSACTION_RECOVERY_MODULES;
import static io.kaoto.forage.jdbc.common.DataSourceFactoryConfigEntries.TRANSACTION_RECOVERY_PERIOD_SECONDS;
import static io.kaoto.forage.jdbc.common.DataSourceFactoryConfigEntries.TRANSACTION_TIMEOUT_SECONDS;
import static io.kaoto.forage.jdbc.common.DataSourceFactoryConfigEntries.TRANSACTION_XA_RESOURCE_ORPHAN_FILTERS;
import static io.kaoto.forage.jdbc.common.DataSourceFactoryConfigEntries.USERNAME;
import static io.kaoto.forage.jdbc.common.DataSourceFactoryConfigEntries.VALIDATION_TIMEOUT_SECONDS;

/**
 * Configuration for data source factory with JDBC connection settings and pool parameters.
 */
public class DataSourceFactoryConfig extends AbstractConfig {

    public DataSourceFactoryConfig() {
        this(null);
    }

    public DataSourceFactoryConfig(String prefix) {
        super(prefix, DataSourceFactoryConfigEntries.class);
    }

    @Override
    public String name() {
        return "forage-datasource-factory";
    }

    // Database connection methods
    public String dbKind() {
        return getRequired(DB_KIND, "Db kind is required but not configured");
    }

    public String jdbcUrl() {
        return getRequired(JDBC_URL, "JDBC URL is required but not configured");
    }

    public String username() {
        return getRequired(USERNAME, "Database username is required but not configured");
    }

    public String password() {
        return getRequired(PASSWORD, "Database password is required but not configured");
    }

    // Connection pool configuration methods
    public int initialSize() {
        return get(INITIAL_SIZE).map(Integer::parseInt).orElse(Integer.parseInt(INITIAL_SIZE.defaultValue()));
    }

    public int minSize() {
        return get(MIN_SIZE).map(Integer::parseInt).orElse(Integer.parseInt(MIN_SIZE.defaultValue()));
    }

    public int maxSize() {
        return get(MAX_SIZE).map(Integer::parseInt).orElse(Integer.parseInt(MAX_SIZE.defaultValue()));
    }

    public int acquisitionTimeoutSeconds() {
        return get(ACQUISITION_TIMEOUT_SECONDS)
                .map(Integer::parseInt)
                .orElse(Integer.parseInt(ACQUISITION_TIMEOUT_SECONDS.defaultValue()));
    }

    public int validationTimeoutSeconds() {
        return get(VALIDATION_TIMEOUT_SECONDS)
                .map(Integer::parseInt)
                .orElse(Integer.parseInt(VALIDATION_TIMEOUT_SECONDS.defaultValue()));
    }

    public int leakTimeoutMinutes() {
        return get(LEAK_TIMEOUT_MINUTES)
                .map(Integer::parseInt)
                .orElse(Integer.parseInt(LEAK_TIMEOUT_MINUTES.defaultValue()));
    }

    public int idleValidationTimeoutMinutes() {
        return get(IDLE_VALIDATION_TIMEOUT_MINUTES)
                .map(Integer::parseInt)
                .orElse(Integer.parseInt(IDLE_VALIDATION_TIMEOUT_MINUTES.defaultValue()));
    }

    // Transaction configuration methods
    public int transactionTimeoutSeconds() {
        return get(TRANSACTION_TIMEOUT_SECONDS)
                .map(Integer::parseInt)
                .orElse(Integer.parseInt(TRANSACTION_TIMEOUT_SECONDS.defaultValue()));
    }

    public boolean transactionEnabled() {
        return get(TRANSACTION_ENABLED)
                .map(Boolean::parseBoolean)
                .orElse(Boolean.parseBoolean(TRANSACTION_ENABLED.defaultValue()));
    }

    public String transactionNodeId() {
        return get(TRANSACTION_NODE_ID).orElse(null);
    }

    public String transactionObjectStoreId() {
        return get(TRANSACTION_OBJECT_STORE_ID).orElse(null);
    }

    public boolean transactionEnableRecovery() {
        return get(TRANSACTION_ENABLE_RECOVERY)
                .map(Boolean::parseBoolean)
                .orElse(Boolean.parseBoolean(TRANSACTION_ENABLE_RECOVERY.defaultValue()));
    }

    public int transactionRecoveryPeriodSeconds() {
        return get(TRANSACTION_RECOVERY_PERIOD_SECONDS)
                .map(Integer::parseInt)
                .orElse(Integer.parseInt(TRANSACTION_RECOVERY_PERIOD_SECONDS.defaultValue()));
    }

    public int transactionRecoveryBackoffSeconds() {
        return get(TRANSACTION_RECOVERY_BACKOFF_SECONDS)
                .map(Integer::parseInt)
                .orElse(Integer.parseInt(TRANSACTION_RECOVERY_BACKOFF_SECONDS.defaultValue()));
    }

    public String transactionRecoveryModules() {
        return get(TRANSACTION_RECOVERY_MODULES).orElse(TRANSACTION_RECOVERY_MODULES.defaultValue());
    }

    public String transactionExpiryScanners() {
        return get(TRANSACTION_EXPIRY_SCANNERS).orElse(TRANSACTION_EXPIRY_SCANNERS.defaultValue());
    }

    public String transactionXaResourceOrphanFilters() {
        return get(TRANSACTION_XA_RESOURCE_ORPHAN_FILTERS)
                .orElse(TRANSACTION_XA_RESOURCE_ORPHAN_FILTERS.defaultValue());
    }

    public String transactionObjectStoreDirectory() {
        return get(TRANSACTION_OBJECT_STORE_DIRECTORY).orElse(TRANSACTION_OBJECT_STORE_DIRECTORY.defaultValue());
    }

    public String transactionObjectStoreType() {
        return get(TRANSACTION_OBJECT_STORE_TYPE).orElse(TRANSACTION_OBJECT_STORE_TYPE.defaultValue());
    }

    public String transactionObjectStoreDataSource() {
        return get(TRANSACTION_OBJECT_STORE_DATASOURCE).orElse(null);
    }

    public boolean transactionObjectStoreCreateTable() {
        return get(TRANSACTION_OBJECT_STORE_CREATE_TABLE)
                .map(Boolean::parseBoolean)
                .orElse(Boolean.parseBoolean(TRANSACTION_OBJECT_STORE_CREATE_TABLE.defaultValue()));
    }

    public boolean transactionObjectStoreDropTable() {
        return get(TRANSACTION_OBJECT_STORE_DROP_TABLE)
                .map(Boolean::parseBoolean)
                .orElse(Boolean.parseBoolean(TRANSACTION_OBJECT_STORE_DROP_TABLE.defaultValue()));
    }

    public String transactionObjectStoreTablePrefix() {
        return get(TRANSACTION_OBJECT_STORE_TABLE_PREFIX).orElse(TRANSACTION_OBJECT_STORE_TABLE_PREFIX.defaultValue());
    }

    /**
     * The object store settings of this configuration, including the fully qualified property
     * names (e.g. {@code forage.ds1.jdbc.transaction.object.store.datasource}) for error messages.
     */
    public ObjectStoreSettings transactionObjectStoreSettings() {
        return new ObjectStoreSettings(
                transactionObjectStoreType(),
                transactionObjectStoreDirectory(),
                transactionObjectStoreDataSource(),
                transactionObjectStoreCreateTable(),
                transactionObjectStoreDropTable(),
                transactionObjectStoreTablePrefix(),
                TRANSACTION_OBJECT_STORE_TYPE.asNamed(prefix()).propertyName(),
                TRANSACTION_OBJECT_STORE_DATASOURCE.asNamed(prefix()).propertyName());
    }

    public String aggregationRepositoryName() {
        return get(AGGREGATION_REPOSITORY_NAME).filter(name -> !name.isBlank()).orElse(null);
    }

    public String aggregationRepositoryHeadersToStore() {
        return get(AGGREGATION_REPOSITORY_HEADERS_TO_STORE).orElse(null);
    }

    public Boolean aggregationRepositoryStoreBody() {
        return get(AGGREGATION_REPOSITORY_STORE_BODY).map(Boolean::parseBoolean).orElse(null);
    }

    public String aggregationRepositoryDeadLetterUri() {
        return get(AGGREGATION_REPOSITORY_DEAD_LETTER_URI).orElse(null);
    }

    public Boolean aggregationRepositoryAllowSerializedHeaders() {
        return get(AGGREGATION_REPOSITORY_ALLOW_SERIALIZED_HEADERS)
                .map(Boolean::parseBoolean)
                .orElse(null);
    }

    public Integer aggregationRepositoryMaximumRedeliveries() {
        return get(AGGREGATION_REPOSITORY_MAXIMUM_REDELIVERIES)
                .map(Integer::parseInt)
                .orElse(null);
    }

    public Boolean aggregationRepositoryUseRecovery() {
        return get(AGGREGATION_REPOSITORY_USE_RECOVERY)
                .map(Boolean::parseBoolean)
                .orElse(null);
    }

    public String aggregationRepositoryPropagationBehaviourName() {
        return get(AGGREGATION_REPOSITORY_PROPAGATION_BEHAVIOUR_NAME).orElse(null);
    }

    public boolean aggregationRepositoryEnabled() {
        return get(AGGREGATION_REPOSITORY_ENABLED)
                .map(Boolean::parseBoolean)
                .orElse(Boolean.parseBoolean(AGGREGATION_REPOSITORY_ENABLED.defaultValue()));
    }

    public boolean enableIdempotentRepository() {
        return get(ENABLE_IDEMPOTENT_REPOSITORY)
                .map(Boolean::parseBoolean)
                .orElse(Boolean.parseBoolean(ENABLE_IDEMPOTENT_REPOSITORY.defaultValue()));
    }

    public String idempotentRepositoryTableName() {
        return get(IDEMPOTENT_REPOSITORY_TABLE_NAME)
                .filter(name -> !name.isBlank())
                .orElse(ForageJdbcMessageIdRepository.DEFAULT_TABLENAME);
    }

    public boolean enableIdempotentRepositoryTableCreate() {
        return get(IDEMPOTENT_REPOSITORY_TABLE_IF_NOT_EXISTS)
                .map(Boolean::parseBoolean)
                .orElse(Boolean.parseBoolean(IDEMPOTENT_REPOSITORY_TABLE_IF_NOT_EXISTS.defaultValue()));
    }

    public String idempotentRepositoryProcessorName() {
        return get(IDEMPOTENT_REPOSITORY_PROCESSOR_NAME).orElse("FORAGE_PROCESSOR_" + idempotentRepositoryTableName());
    }
}
