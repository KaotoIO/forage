package io.kaoto.forage.jdbc.common;

import io.kaoto.forage.core.util.config.ConfigEntries;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigTag;

public final class DataSourceFactoryConfigEntries extends ConfigEntries {

    // Database connection configuration
    public static final ConfigModule DB_KIND = ConfigModule.ofBeanName(
            DataSourceFactoryConfig.class,
            "forage.jdbc.db.kind",
            "The database kind/type",
            "Database Kind",
            true,
            ConfigTag.COMMON,
            "javax.sql.DataSource");

    public static final ConfigModule JDBC_URL = ConfigModule.of(
            DataSourceFactoryConfig.class,
            "forage.jdbc.url",
            "The JDBC connection URL",
            "JDBC URL",
            null,
            "string",
            true,
            ConfigTag.COMMON);

    public static final ConfigModule USERNAME = ConfigModule.of(
            DataSourceFactoryConfig.class,
            "forage.jdbc.username",
            "The database username",
            "Username",
            null,
            "string",
            true,
            ConfigTag.SECURITY);

    public static final ConfigModule PASSWORD = ConfigModule.of(
            DataSourceFactoryConfig.class,
            "forage.jdbc.password",
            "The database password",
            "Password",
            null,
            "password",
            true,
            ConfigTag.SECURITY);

    /**
     * Prefix for driver-specific JDBC properties: each {@code forage.jdbc.properties.<name>=<value>}
     * key is passed to the driver as {@code <name>=<value>}. Read the keys with
     * {@link DataSourceFactoryConfig#driverProperties()}.
     */
    public static final ConfigModule DRIVER_PROPERTIES = ConfigModule.of(
            DataSourceFactoryConfig.class,
            "forage.jdbc.properties",
            "Driver-specific JDBC properties, one key per property, passed to the driver as is (for example forage.jdbc.properties.sslConnection=true)",
            "Driver Properties",
            null,
            "map",
            false,
            ConfigTag.ADVANCED);

    // Connection pool configuration
    public static final ConfigModule INITIAL_SIZE = ConfigModule.of(
            DataSourceFactoryConfig.class,
            "forage.jdbc.pool.initial.size",
            "Initial size of the connection pool",
            "Initial Pool Size",
            "5",
            "integer",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule MIN_SIZE = ConfigModule.of(
            DataSourceFactoryConfig.class,
            "forage.jdbc.pool.min.size",
            "Minimum size of the connection pool",
            "Min Pool Size",
            "2",
            "integer",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule MAX_SIZE = ConfigModule.of(
            DataSourceFactoryConfig.class,
            "forage.jdbc.pool.max.size",
            "Maximum size of the connection pool",
            "Max Pool Size",
            "20",
            "integer",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule ACQUISITION_TIMEOUT_SECONDS = ConfigModule.of(
            DataSourceFactoryConfig.class,
            "forage.jdbc.pool.acquisition.timeout.seconds",
            "Timeout for acquiring a connection from the pool (seconds)",
            "Acquisition Timeout",
            "5",
            "integer",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule VALIDATION_TIMEOUT_SECONDS = ConfigModule.of(
            DataSourceFactoryConfig.class,
            "forage.jdbc.pool.validation.timeout.seconds",
            "Timeout for validating a connection (seconds)",
            "Validation Timeout",
            "3",
            "integer",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule LEAK_TIMEOUT_MINUTES = ConfigModule.of(
            DataSourceFactoryConfig.class,
            "forage.jdbc.pool.leak.timeout.minutes",
            "Timeout for detecting connection leaks (minutes)",
            "Leak Timeout",
            "10",
            "integer",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule IDLE_VALIDATION_TIMEOUT_MINUTES = ConfigModule.of(
            DataSourceFactoryConfig.class,
            "forage.jdbc.pool.idle.validation.timeout.minutes",
            "Timeout for validating idle connections (minutes)",
            "Idle Validation Timeout",
            "3",
            "integer",
            false,
            ConfigTag.ADVANCED);

    // Transaction configuration
    public static final ConfigModule TRANSACTION_ENABLED = ConfigModule.of(
            DataSourceFactoryConfig.class,
            "forage.jdbc.transaction.enabled",
            "Enable transaction management",
            "Transaction Enabled",
            "false",
            "boolean",
            false,
            ConfigTag.COMMON);

    public static final ConfigModule TRANSACTION_TIMEOUT_SECONDS = ConfigModule.of(
            DataSourceFactoryConfig.class,
            "forage.jdbc.transaction.timeout.seconds",
            "Timeout for transactions (seconds)",
            "Transaction Timeout",
            "30",
            "integer",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule TRANSACTION_NODE_ID = ConfigModule.of(
            DataSourceFactoryConfig.class,
            "forage.jdbc.transaction.node.id",
            "The transaction node identifier",
            "Transaction Node ID",
            null,
            "string",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule TRANSACTION_OBJECT_STORE_ID = ConfigModule.of(
            DataSourceFactoryConfig.class,
            "forage.jdbc.transaction.object.store.id",
            "The transaction object store identifier",
            "Object Store ID",
            null,
            "string",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule TRANSACTION_ENABLE_RECOVERY = ConfigModule.of(
            DataSourceFactoryConfig.class,
            "forage.jdbc.transaction.enable.recovery",
            "Enable transaction recovery",
            "Enable Recovery",
            "false",
            "boolean",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule TRANSACTION_RECOVERY_PERIOD_SECONDS = ConfigModule.of(
            DataSourceFactoryConfig.class,
            "forage.jdbc.transaction.recovery.period.seconds",
            "Interval between periodic transaction recovery scans, in seconds",
            "Recovery Period",
            "120",
            "integer",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule TRANSACTION_RECOVERY_BACKOFF_SECONDS = ConfigModule.of(
            DataSourceFactoryConfig.class,
            "forage.jdbc.transaction.recovery.backoff.seconds",
            "Delay between the first and second pass of a recovery scan, in seconds",
            "Recovery Backoff",
            "10",
            "integer",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule TRANSACTION_RECOVERY_MODULES = ConfigModule.of(
            DataSourceFactoryConfig.class,
            "forage.jdbc.transaction.recovery.modules",
            "Comma-separated list of transaction recovery modules",
            "Recovery Modules",
            "com.arjuna.ats.internal.arjuna.recovery.AtomicActionRecoveryModule,"
                    + "com.arjuna.ats.internal.jta.recovery.arjunacore.XARecoveryModule",
            "string",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule TRANSACTION_EXPIRY_SCANNERS = ConfigModule.of(
            DataSourceFactoryConfig.class,
            "forage.jdbc.transaction.expiry.scanners",
            "Comma-separated list of transaction expiry scanners",
            "Expiry Scanners",
            "com.arjuna.ats.internal.arjuna.recovery.ExpiredTransactionStatusManagerScanner",
            "string",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule TRANSACTION_XA_RESOURCE_ORPHAN_FILTERS = ConfigModule.of(
            DataSourceFactoryConfig.class,
            "forage.jdbc.transaction.xa.resource.orphan.filters",
            "Comma-separated list of XA resource orphan filters",
            "XA Orphan Filters",
            "com.arjuna.ats.internal.jta.recovery.arjunacore.JTATransactionLogXAResourceOrphanFilter,"
                    + "com.arjuna.ats.internal.jta.recovery.arjunacore.JTANodeNameXAResourceOrphanFilter,"
                    + "com.arjuna.ats.internal.jta.recovery.arjunacore.JTAActionStatusServiceXAResourceOrphanFilter",
            "string",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule TRANSACTION_OBJECT_STORE_DIRECTORY = ConfigModule.of(
            DataSourceFactoryConfig.class,
            "forage.jdbc.transaction.object.store.directory",
            "Directory for transaction object store",
            "Object Store Directory",
            "ObjectStore",
            "string",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule TRANSACTION_OBJECT_STORE_TYPE = ConfigModule.of(
            DataSourceFactoryConfig.class,
            "forage.jdbc.transaction.object.store.type",
            "Type of transaction object store: file-system, jdbc (transaction log in a database table, no persistent volume needed) or volatile (in memory, tests only)",
            "Object Store Type",
            "file-system",
            "string",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule TRANSACTION_OBJECT_STORE_DATASOURCE = ConfigModule.of(
            DataSourceFactoryConfig.class,
            "forage.jdbc.transaction.object.store.datasource",
            "Name of the non-XA Forage datasource holding the transaction log table, as in forage.NAME.jdbc.*; required when the object store type is jdbc",
            "Object Store DataSource",
            null,
            "string",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule TRANSACTION_OBJECT_STORE_CREATE_TABLE = ConfigModule.of(
            DataSourceFactoryConfig.class,
            "forage.jdbc.transaction.object.store.create.table",
            "Create the JDBC object store table at startup if it does not exist",
            "Create Table",
            "false",
            "boolean",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule TRANSACTION_OBJECT_STORE_DROP_TABLE = ConfigModule.of(
            DataSourceFactoryConfig.class,
            "forage.jdbc.transaction.object.store.drop.table",
            "Drop the JDBC object store table at startup, discarding pending transaction logs (tests only)",
            "Drop Table",
            "false",
            "boolean",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule TRANSACTION_OBJECT_STORE_TABLE_PREFIX = ConfigModule.of(
            DataSourceFactoryConfig.class,
            "forage.jdbc.transaction.object.store.table.prefix",
            "Prefix of the JDBC object store table name; the table is PREFIX + JBossTSTxTable",
            "Table Prefix",
            "forage_",
            "string",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule AGGREGATION_REPOSITORY_NAME = ConfigModule.of(
            DataSourceFactoryConfig.class,
            "forage.jdbc.aggregation.repository.name",
            "Name of the aggregation repository",
            "Repository Name",
            null,
            "string",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule AGGREGATION_REPOSITORY_HEADERS_TO_STORE = ConfigModule.of(
            DataSourceFactoryConfig.class,
            "forage.jdbc.aggregation.repository.headers.to.store",
            "Comma-separated list of headers to store",
            "Headers to Store",
            null,
            "string",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule AGGREGATION_REPOSITORY_STORE_BODY = ConfigModule.of(
            DataSourceFactoryConfig.class,
            "forage.jdbc.aggregation.repository.store.body",
            "Store message body in repository",
            "Store Body",
            null,
            "boolean",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule AGGREGATION_REPOSITORY_DEAD_LETTER_URI = ConfigModule.of(
            DataSourceFactoryConfig.class,
            "forage.jdbc.aggregation.repository.dead.letter.uri",
            "Dead letter queue URI for failed aggregations",
            "Dead Letter URI",
            null,
            "string",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule AGGREGATION_REPOSITORY_ALLOW_SERIALIZED_HEADERS = ConfigModule.of(
            DataSourceFactoryConfig.class,
            "forage.jdbc.aggregation.repository.allow.serialized.headers",
            "Allow serialized headers in repository",
            "Allow Serialized Headers",
            null,
            "boolean",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule AGGREGATION_REPOSITORY_MAXIMUM_REDELIVERIES = ConfigModule.of(
            DataSourceFactoryConfig.class,
            "forage.jdbc.aggregation.repository.maximum.redeliveries",
            "Maximum number of redelivery attempts",
            "Max Redeliveries",
            null,
            "integer",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule AGGREGATION_REPOSITORY_USE_RECOVERY = ConfigModule.of(
            DataSourceFactoryConfig.class,
            "forage.jdbc.aggregation.repository.use.recovery",
            "Enable recovery for aggregation repository",
            "Use Recovery",
            null,
            "boolean",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule AGGREGATION_REPOSITORY_PROPAGATION_BEHAVIOUR_NAME = ConfigModule.of(
            DataSourceFactoryConfig.class,
            "forage.jdbc.aggregation.repository.propagation.behaviour.name",
            "Transaction propagation behaviour name",
            "Propagation Behaviour",
            null,
            "string",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule AGGREGATION_REPOSITORY_ENABLED = ConfigModule.of(
            DataSourceFactoryConfig.class,
            "forage.jdbc.aggregation.repository.enabled",
            "Enable aggregation repository (requires transactions to be enabled)",
            "Aggregation Repository Enabled",
            "false",
            "boolean",
            false,
            ConfigTag.COMMON);

    public static final ConfigModule ENABLE_IDEMPOTENT_REPOSITORY = ConfigModule.of(
            DataSourceFactoryConfig.class,
            "forage.jdbc.idempotent.repository.enabled",
            "Enable idempotent repository",
            "Idempotent Enabled (Transactions are required, please enable transactions too)",
            "false",
            "boolean",
            false,
            ConfigTag.COMMON);

    public static final ConfigModule IDEMPOTENT_REPOSITORY_TABLE_NAME = ConfigModule.of(
            DataSourceFactoryConfig.class,
            "forage.jdbc.idempotent.repository.table.name",
            "Table name for idempotent repository",
            "Idempotent Table Name",
            "CAMEL_MESSAGEPROCESSED",
            "string",
            false,
            ConfigTag.COMMON);

    public static final ConfigModule IDEMPOTENT_REPOSITORY_TABLE_IF_NOT_EXISTS = ConfigModule.of(
            DataSourceFactoryConfig.class,
            "forage.jdbc.idempotent.repository.table.create",
            "Create idempotent table if not exists",
            "Create Table",
            "true",
            "boolean",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule IDEMPOTENT_REPOSITORY_PROCESSOR_NAME = ConfigModule.of(
            DataSourceFactoryConfig.class,
            "forage.jdbc.idempotent.repository.processor.name",
            "Processor name for idempotent repository",
            "Processor Name",
            null,
            "string",
            false,
            ConfigTag.ADVANCED);

    static {
        initModules(
                DataSourceFactoryConfigEntries.class,
                DB_KIND,
                JDBC_URL,
                USERNAME,
                PASSWORD,
                DRIVER_PROPERTIES,
                INITIAL_SIZE,
                MIN_SIZE,
                MAX_SIZE,
                ACQUISITION_TIMEOUT_SECONDS,
                VALIDATION_TIMEOUT_SECONDS,
                LEAK_TIMEOUT_MINUTES,
                IDLE_VALIDATION_TIMEOUT_MINUTES,
                TRANSACTION_TIMEOUT_SECONDS,
                TRANSACTION_ENABLED,
                TRANSACTION_NODE_ID,
                TRANSACTION_OBJECT_STORE_ID,
                TRANSACTION_ENABLE_RECOVERY,
                TRANSACTION_RECOVERY_PERIOD_SECONDS,
                TRANSACTION_RECOVERY_BACKOFF_SECONDS,
                TRANSACTION_RECOVERY_MODULES,
                TRANSACTION_EXPIRY_SCANNERS,
                TRANSACTION_XA_RESOURCE_ORPHAN_FILTERS,
                TRANSACTION_OBJECT_STORE_DIRECTORY,
                TRANSACTION_OBJECT_STORE_TYPE,
                TRANSACTION_OBJECT_STORE_DATASOURCE,
                TRANSACTION_OBJECT_STORE_CREATE_TABLE,
                TRANSACTION_OBJECT_STORE_DROP_TABLE,
                TRANSACTION_OBJECT_STORE_TABLE_PREFIX,
                AGGREGATION_REPOSITORY_NAME,
                AGGREGATION_REPOSITORY_HEADERS_TO_STORE,
                AGGREGATION_REPOSITORY_STORE_BODY,
                AGGREGATION_REPOSITORY_DEAD_LETTER_URI,
                AGGREGATION_REPOSITORY_ALLOW_SERIALIZED_HEADERS,
                AGGREGATION_REPOSITORY_MAXIMUM_REDELIVERIES,
                AGGREGATION_REPOSITORY_USE_RECOVERY,
                AGGREGATION_REPOSITORY_PROPAGATION_BEHAVIOUR_NAME,
                AGGREGATION_REPOSITORY_ENABLED,
                ENABLE_IDEMPOTENT_REPOSITORY,
                IDEMPOTENT_REPOSITORY_TABLE_NAME,
                IDEMPOTENT_REPOSITORY_TABLE_IF_NOT_EXISTS,
                IDEMPOTENT_REPOSITORY_PROCESSOR_NAME);
    }
}
