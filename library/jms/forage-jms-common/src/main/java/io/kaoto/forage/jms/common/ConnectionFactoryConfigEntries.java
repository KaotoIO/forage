package io.kaoto.forage.jms.common;

import io.kaoto.forage.core.util.config.ConfigEntries;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigTag;

public final class ConnectionFactoryConfigEntries extends ConfigEntries {

    // JMS connection configuration
    public static final ConfigModule JMS_KIND = ConfigModule.ofBeanName(
            ConnectionFactoryConfig.class,
            "forage.jms.kind",
            "The JMS broker kind/type",
            "JMS Kind",
            true,
            ConfigTag.COMMON,
            "jakarta.jms.ConnectionFactory");

    public static final ConfigModule BROKER_URL = ConfigModule.of(
            ConnectionFactoryConfig.class,
            "forage.jms.broker.url",
            "The JMS broker URL",
            "Broker URL",
            null,
            "string",
            false,
            ConfigTag.COMMON);

    public static final ConfigModule USERNAME = ConfigModule.of(
            ConnectionFactoryConfig.class,
            "forage.jms.username",
            "The JMS broker username",
            "Username",
            null,
            "string",
            false,
            ConfigTag.SECURITY);

    public static final ConfigModule PASSWORD = ConfigModule.of(
            ConnectionFactoryConfig.class,
            "forage.jms.password",
            "The JMS broker password",
            "Password",
            null,
            "password",
            false,
            ConfigTag.SECURITY);

    public static final ConfigModule CLIENT_ID = ConfigModule.of(
            ConnectionFactoryConfig.class,
            "forage.jms.client.id",
            "The JMS client identifier",
            "Client ID",
            null,
            "string",
            false,
            ConfigTag.COMMON);

    // SSL/TLS configuration
    public static final ConfigModule SSL_ENABLED = ConfigModule.of(
            ConnectionFactoryConfig.class,
            "forage.jms.ssl.enabled",
            "Enable SSL/TLS for the JMS connection",
            "SSL Enabled",
            "false",
            "boolean",
            false,
            ConfigTag.SECURITY);

    public static final ConfigModule SSL_TRUSTSTORE_PATH = ConfigModule.of(
            ConnectionFactoryConfig.class,
            "forage.jms.ssl.truststore.path",
            "Path to the SSL truststore file",
            "Truststore Path",
            null,
            "string",
            false,
            ConfigTag.SECURITY);

    public static final ConfigModule SSL_TRUSTSTORE_PASSWORD = ConfigModule.of(
            ConnectionFactoryConfig.class,
            "forage.jms.ssl.truststore.password",
            "Password for the SSL truststore",
            "Truststore Password",
            null,
            "password",
            false,
            ConfigTag.SECURITY);

    public static final ConfigModule SSL_TRUSTSTORE_TYPE = ConfigModule.of(
            ConnectionFactoryConfig.class,
            "forage.jms.ssl.truststore.type",
            "Type of the SSL truststore (JKS, PKCS12)",
            "Truststore Type",
            "JKS",
            "string",
            false,
            ConfigTag.SECURITY);

    public static final ConfigModule SSL_KEYSTORE_PATH = ConfigModule.of(
            ConnectionFactoryConfig.class,
            "forage.jms.ssl.keystore.path",
            "Path to the SSL keystore file for mutual TLS",
            "Keystore Path",
            null,
            "string",
            false,
            ConfigTag.SECURITY);

    public static final ConfigModule SSL_KEYSTORE_PASSWORD = ConfigModule.of(
            ConnectionFactoryConfig.class,
            "forage.jms.ssl.keystore.password",
            "Password for the SSL keystore",
            "Keystore Password",
            null,
            "password",
            false,
            ConfigTag.SECURITY);

    public static final ConfigModule SSL_KEYSTORE_TYPE = ConfigModule.of(
            ConnectionFactoryConfig.class,
            "forage.jms.ssl.keystore.type",
            "Type of the SSL keystore (JKS, PKCS12)",
            "Keystore Type",
            "JKS",
            "string",
            false,
            ConfigTag.SECURITY);

    public static final ConfigModule SSL_CIPHER_SUITES = ConfigModule.of(
            ConnectionFactoryConfig.class,
            "forage.jms.ssl.cipher.suites",
            "Comma-separated list of enabled SSL cipher suites",
            "Cipher Suites",
            null,
            "string",
            false,
            ConfigTag.SECURITY);

    public static final ConfigModule SSL_PROTOCOL = ConfigModule.of(
            ConnectionFactoryConfig.class,
            "forage.jms.ssl.protocol",
            "SSL/TLS protocol version (TLSv1.2, TLSv1.3)",
            "SSL Protocol",
            "TLSv1.2",
            "string",
            false,
            ConfigTag.SECURITY);

    // Connection pool configuration
    public static final ConfigModule POOL_ENABLED = ConfigModule.of(
            ConnectionFactoryConfig.class,
            "forage.jms.pool.enabled",
            "Enable connection pooling",
            "Pool Enabled",
            "true",
            "boolean",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule MAX_CONNECTIONS = ConfigModule.of(
            ConnectionFactoryConfig.class,
            "forage.jms.pool.max.connections",
            "Maximum number of connections in the pool",
            "Max Connections",
            "10",
            "integer",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule MAX_SESSIONS_PER_CONNECTION = ConfigModule.of(
            ConnectionFactoryConfig.class,
            "forage.jms.pool.max.sessions.per.connection",
            "Maximum number of sessions per connection",
            "Max Sessions",
            "500",
            "integer",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule IDLE_TIMEOUT_MILLIS = ConfigModule.of(
            ConnectionFactoryConfig.class,
            "forage.jms.pool.idle.timeout.millis",
            "Idle timeout for connections (milliseconds)",
            "Idle Timeout",
            "30000",
            "long",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule EXPIRY_TIMEOUT_MILLIS = ConfigModule.of(
            ConnectionFactoryConfig.class,
            "forage.jms.pool.expiry.timeout.millis",
            "Expiry timeout for connections (milliseconds)",
            "Expiry Timeout",
            "0",
            "long",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule CONNECTION_TIMEOUT_MILLIS = ConfigModule.of(
            ConnectionFactoryConfig.class,
            "forage.jms.pool.connection.timeout.millis",
            "Connection timeout (milliseconds)",
            "Connection Timeout",
            "30000",
            "long",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule BLOCK_IF_FULL = ConfigModule.of(
            ConnectionFactoryConfig.class,
            "forage.jms.pool.block.if.full",
            "Block when pool is full",
            "Block If Full",
            "true",
            "boolean",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule BLOCK_IF_FULL_TIMEOUT_MILLIS = ConfigModule.of(
            ConnectionFactoryConfig.class,
            "forage.jms.pool.block.if.full.timeout.millis",
            "Timeout when blocking on full pool (milliseconds, -1 for infinite)",
            "Block Timeout",
            "-1",
            "long",
            false,
            ConfigTag.ADVANCED);

    // Transaction configuration
    public static final ConfigModule TRANSACTION_ENABLED = ConfigModule.of(
            ConnectionFactoryConfig.class,
            "forage.jms.transaction.enabled",
            "Enable transaction management",
            "Transaction Enabled",
            "false",
            "boolean",
            false,
            ConfigTag.COMMON);

    public static final ConfigModule TRANSACTION_TIMEOUT_SECONDS = ConfigModule.of(
            ConnectionFactoryConfig.class,
            "forage.jms.transaction.timeout.seconds",
            "Timeout for transactions (seconds)",
            "Transaction Timeout",
            "30",
            "integer",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule TRANSACTION_NODE_ID = ConfigModule.of(
            ConnectionFactoryConfig.class,
            "forage.jms.transaction.node.id",
            "The transaction node identifier",
            "Transaction Node ID",
            null,
            "string",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule TRANSACTION_OBJECT_STORE_ID = ConfigModule.of(
            ConnectionFactoryConfig.class,
            "forage.jms.transaction.object.store.id",
            "The transaction object store identifier",
            "Object Store ID",
            null,
            "string",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule TRANSACTION_ENABLE_RECOVERY = ConfigModule.of(
            ConnectionFactoryConfig.class,
            "forage.jms.transaction.enable.recovery",
            "Enable transaction recovery",
            "Enable Recovery",
            "false",
            "boolean",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule TRANSACTION_RECOVERY_PERIOD_SECONDS = ConfigModule.of(
            ConnectionFactoryConfig.class,
            "forage.jms.transaction.recovery.period.seconds",
            "Interval between periodic transaction recovery scans, in seconds",
            "Recovery Period",
            "120",
            "integer",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule TRANSACTION_RECOVERY_BACKOFF_SECONDS = ConfigModule.of(
            ConnectionFactoryConfig.class,
            "forage.jms.transaction.recovery.backoff.seconds",
            "Delay between the first and second pass of a recovery scan, in seconds",
            "Recovery Backoff",
            "10",
            "integer",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule TRANSACTION_RECOVERY_MODULES = ConfigModule.of(
            ConnectionFactoryConfig.class,
            "forage.jms.transaction.recovery.modules",
            "Comma-separated list of transaction recovery modules",
            "Recovery Modules",
            "com.arjuna.ats.internal.arjuna.recovery.AtomicActionRecoveryModule,"
                    + "com.arjuna.ats.internal.jta.recovery.arjunacore.XARecoveryModule",
            "string",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule TRANSACTION_EXPIRY_SCANNERS = ConfigModule.of(
            ConnectionFactoryConfig.class,
            "forage.jms.transaction.expiry.scanners",
            "Comma-separated list of transaction expiry scanners",
            "Expiry Scanners",
            "com.arjuna.ats.internal.arjuna.recovery.ExpiredTransactionStatusManagerScanner",
            "string",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule TRANSACTION_XA_RESOURCE_ORPHAN_FILTERS = ConfigModule.of(
            ConnectionFactoryConfig.class,
            "forage.jms.transaction.xa.resource.orphan.filters",
            "Comma-separated list of XA resource orphan filters",
            "XA Orphan Filters",
            "com.arjuna.ats.internal.jta.recovery.arjunacore.JTATransactionLogXAResourceOrphanFilter,"
                    + "com.arjuna.ats.internal.jta.recovery.arjunacore.JTANodeNameXAResourceOrphanFilter,"
                    + "com.arjuna.ats.internal.jta.recovery.arjunacore.JTAActionStatusServiceXAResourceOrphanFilter",
            "string",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule TRANSACTION_OBJECT_STORE_DIRECTORY = ConfigModule.of(
            ConnectionFactoryConfig.class,
            "forage.jms.transaction.object.store.directory",
            "Directory for transaction object store",
            "Object Store Directory",
            "ObjectStore",
            "string",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule TRANSACTION_OBJECT_STORE_TYPE = ConfigModule.of(
            ConnectionFactoryConfig.class,
            "forage.jms.transaction.object.store.type",
            "Type of transaction object store: file-system, jdbc (transaction log in a database table, no persistent volume needed) or volatile (in memory, tests only)",
            "Object Store Type",
            "file-system",
            "string",
            false,
            ConfigTag.ADVANCED);

    // The object store is JVM-global and shared with the JDBC module: these mirror the
    // forage.jdbc.transaction.object.store.* properties and must be configured identically.
    public static final ConfigModule TRANSACTION_OBJECT_STORE_DATASOURCE = ConfigModule.of(
            ConnectionFactoryConfig.class,
            "forage.jms.transaction.object.store.datasource",
            "Name of the non-XA Forage datasource holding the transaction log table, as in forage.NAME.jdbc.*; required when the object store type is jdbc",
            "Object Store DataSource",
            null,
            "string",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule TRANSACTION_OBJECT_STORE_CREATE_TABLE = ConfigModule.of(
            ConnectionFactoryConfig.class,
            "forage.jms.transaction.object.store.create.table",
            "Create the JDBC object store table at startup if it does not exist",
            "Create Table",
            "false",
            "boolean",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule TRANSACTION_OBJECT_STORE_DROP_TABLE = ConfigModule.of(
            ConnectionFactoryConfig.class,
            "forage.jms.transaction.object.store.drop.table",
            "Drop the JDBC object store table at startup, discarding pending transaction logs (tests only)",
            "Drop Table",
            "false",
            "boolean",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule TRANSACTION_OBJECT_STORE_TABLE_PREFIX = ConfigModule.of(
            ConnectionFactoryConfig.class,
            "forage.jms.transaction.object.store.table.prefix",
            "Prefix of the JDBC object store table name; the table is PREFIX + JBossTSTxTable",
            "Table Prefix",
            "forage_",
            "string",
            false,
            ConfigTag.ADVANCED);

    static {
        initModules(
                ConnectionFactoryConfigEntries.class,
                JMS_KIND,
                BROKER_URL,
                USERNAME,
                PASSWORD,
                CLIENT_ID,
                SSL_ENABLED,
                SSL_TRUSTSTORE_PATH,
                SSL_TRUSTSTORE_PASSWORD,
                SSL_TRUSTSTORE_TYPE,
                SSL_KEYSTORE_PATH,
                SSL_KEYSTORE_PASSWORD,
                SSL_KEYSTORE_TYPE,
                SSL_CIPHER_SUITES,
                SSL_PROTOCOL,
                POOL_ENABLED,
                MAX_CONNECTIONS,
                MAX_SESSIONS_PER_CONNECTION,
                IDLE_TIMEOUT_MILLIS,
                EXPIRY_TIMEOUT_MILLIS,
                CONNECTION_TIMEOUT_MILLIS,
                BLOCK_IF_FULL,
                BLOCK_IF_FULL_TIMEOUT_MILLIS,
                TRANSACTION_ENABLED,
                TRANSACTION_TIMEOUT_SECONDS,
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
                TRANSACTION_OBJECT_STORE_TABLE_PREFIX);
    }
}
