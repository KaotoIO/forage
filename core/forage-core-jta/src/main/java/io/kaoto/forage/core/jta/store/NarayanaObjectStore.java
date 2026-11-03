package io.kaoto.forage.core.jta.store;

import javax.sql.DataSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.core.jta.recovery.ForageRecoveryService;
import io.kaoto.forage.core.util.config.MissingConfigException;
import com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean;
import com.arjuna.ats.internal.arjuna.objectstore.VolatileStore;
import com.arjuna.ats.internal.arjuna.objectstore.jdbc.JDBCStore;
import com.arjuna.common.internal.util.propertyservice.BeanPopulator;

/**
 * Applies the Forage object store settings to Narayana, shared by the JDBC and JMS modules.
 *
 * <p>Narayana keeps one {@link ObjectStoreEnvironmentBean} per store: the default instance (the
 * transaction log, also read by recovery), {@code stateStore} and {@code communicationStore}. They
 * are JVM-global singletons, so every Forage configuration with transactions enabled configures
 * the same store. The rules, applied under the default bean's monitor (shared across modules):
 *
 * <ul>
 *   <li>{@code jdbc} and {@code volatile} replace Narayana's file store. The first such store
 *       configured wins: a later configuration asking for a different store logs a warning and is
 *       ignored, the same as the node identifier handling.</li>
 *   <li>{@code file-system}, the default, never replaces a {@code jdbc} or {@code volatile} store; it
 *       only sets the store directory while the file store is in use.</li>
 *   <li>Once the recovery manager has started, its recovery modules have opened the store. Switching
 *       the store type after that point fails instead of being silently ineffective.</li>
 *   <li>On Quarkus, quarkus-narayana-jta owns the object store (Forage translates the settings to
 *       {@code quarkus.transaction-manager.object-store.*}), so nothing is changed here.</li>
 * </ul>
 */
public final class NarayanaObjectStore {

    private static final Logger LOG = LoggerFactory.getLogger(NarayanaObjectStore.class);

    /** Base name of the JDBC store table; the configured table prefix is prepended. */
    public static final String JDBC_STORE_TABLE = "JBossTSTxTable";

    /** The object store environment bean instances Narayana reads: default, state and communication. */
    public static final List<String> STORE_NAMES =
            Collections.unmodifiableList(Arrays.asList(null, "stateStore", "communicationStore"));

    private NarayanaObjectStore() {}

    /**
     * Configures the object store, discovering the {@link ObjectStoreDataSourceProvider} on the
     * classpath when the {@code jdbc} store is requested.
     */
    public static void configure(ObjectStoreSettings settings) {
        configure(settings, null);
    }

    /**
     * Configures the object store.
     *
     * @param settings the object store settings of one Forage configuration
     * @param dataSources creates the JDBC store datasource; {@code null} to discover it on the classpath
     * @throws IllegalArgumentException if the store type is not supported
     * @throws MissingConfigException if the {@code jdbc} store has no datasource configured
     * @throws IllegalStateException if the store cannot be switched or its datasource cannot be created
     */
    public static void configure(ObjectStoreSettings settings, ObjectStoreDataSourceProvider dataSources) {
        ObjectStoreType requested = settings.storeType();

        if (ForageRecoveryService.getInstance().isQuarkusManaged()) {
            LOG.debug(
                    "quarkus-narayana-jta owns the transaction object store; not applying the '{}' store of {}",
                    requested,
                    settings.typeProperty());
            return;
        }

        ObjectStoreEnvironmentBean defaultBean = bean(null);
        synchronized (defaultBean) {
            ObjectStoreType current = currentType(defaultBean);
            switch (requested) {
                case FILE_SYSTEM -> configureFileSystem(settings, current);
                case VOLATILE -> configureVolatile(settings, current);
                case JDBC -> configureJdbc(settings, current, defaultBean, dataSources);
                default -> throw new IllegalStateException("Unhandled object store type " + requested);
            }
        }
    }

    private static void configureFileSystem(ObjectStoreSettings settings, ObjectStoreType current) {
        if (current != ObjectStoreType.FILE_SYSTEM) {
            LOG.info(
                    "The Narayana transaction object store is already configured as '{}' (JVM-global); keeping it,"
                            + " the file-system store settings of {} do not apply",
                    current,
                    settings.typeProperty());
            return;
        }
        for (String storeName : STORE_NAMES) {
            bean(storeName).setObjectStoreDir(settings.directory());
        }
        LOG.debug("Transaction object store: file-system, directory {}", settings.directory());
    }

    private static void configureVolatile(ObjectStoreSettings settings, ObjectStoreType current) {
        if (current == ObjectStoreType.VOLATILE) {
            return;
        }
        if (current == ObjectStoreType.JDBC) {
            LOG.warn(
                    "The Narayana transaction object store is already configured as 'jdbc' (JVM-global); ignoring"
                            + " the 'volatile' store requested by {}",
                    settings.typeProperty());
            return;
        }
        ensureStoreNotOpened(current, ObjectStoreType.VOLATILE, settings);
        for (String storeName : STORE_NAMES) {
            bean(storeName).setObjectStoreType(VolatileStore.class.getName());
        }
        LOG.info(
                "Transaction object store: volatile ({}). Transaction logs are kept in memory only, crash recovery"
                        + " is not possible",
                settings.typeProperty());
    }

    private static void configureJdbc(
            ObjectStoreSettings settings,
            ObjectStoreType current,
            ObjectStoreEnvironmentBean defaultBean,
            ObjectStoreDataSourceProvider dataSources) {
        String name = settings.dataSource();
        if (name == null) {
            throw new MissingConfigException("The transaction object store type is 'jdbc' (" + settings.typeProperty()
                    + ") but " + settings.dataSourceProperty() + " is not set. Set it to the name of a non-XA Forage"
                    + " datasource (forage.<name>.jdbc.*) that holds the transaction log table.");
        }

        if (current == ObjectStoreType.JDBC) {
            DataSource active = defaultBean.getJdbcDataSource();
            if (active instanceof ObjectStoreDataSource store) {
                if (store.settings().sameJdbcStoreAs(settings)) {
                    LOG.debug("JDBC transaction object store already configured with datasource '{}'", name);
                } else {
                    LOG.warn(
                            "The Narayana JDBC transaction object store is already configured ({}) and is JVM-global;"
                                    + " ignoring the different settings of {} ({}). Configure the object store"
                                    + " identically on every Forage configuration with transactions enabled.",
                            store.settings().describeJdbcStore(),
                            settings.dataSourceProperty(),
                            settings.describeJdbcStore());
                }
            } else {
                LOG.warn(
                        "The Narayana JDBC object store is already configured outside Forage (for example through"
                                + " Narayana properties); leaving it untouched and ignoring {}",
                        settings.dataSourceProperty());
            }
            return;
        }
        if (current == ObjectStoreType.VOLATILE) {
            LOG.warn(
                    "The Narayana transaction object store is already configured as 'volatile' (JVM-global); ignoring"
                            + " the 'jdbc' store requested by {}",
                    settings.typeProperty());
            return;
        }

        ensureStoreNotOpened(current, ObjectStoreType.JDBC, settings);

        ObjectStoreDataSourceProvider provider =
                dataSources != null ? dataSources : ObjectStoreDataSourceProvider.discover();
        DataSource pool = provider.create(name);
        if (pool == null) {
            throw new IllegalStateException("No datasource was created for the JDBC transaction object store '" + name
                    + "' (" + settings.dataSourceProperty() + ")");
        }
        ObjectStoreDataSource storeDataSource = new ObjectStoreDataSource(settings, pool);

        // The three stores share the datasource and table (rows are keyed by record type), exactly
        // like quarkus-narayana-jta configures them. The type is set last, so the store is never
        // selected before its datasource is in place.
        for (String storeName : STORE_NAMES) {
            ObjectStoreEnvironmentBean bean = bean(storeName);
            bean.setJdbcDataSource(storeDataSource);
            bean.setTablePrefix(settings.tablePrefix());
            bean.setCreateTable(settings.createTable());
            bean.setDropTable(settings.dropTable());
            bean.setObjectStoreType(JDBCStore.class.getName());
        }

        LOG.info(
                "Transaction object store: jdbc, datasource '{}', table {} (create.table={}, drop.table={})",
                name,
                tableName(settings.tablePrefix()),
                settings.createTable(),
                settings.dropTable());
        if (settings.dropTable()) {
            LOG.warn(
                    "drop.table is enabled for the JDBC transaction object store: the table {} is dropped at startup,"
                            + " discarding the transaction logs that crash recovery needs. Use it for tests only.",
                    tableName(settings.tablePrefix()));
        }
    }

    /** The JDBC store table name for the given prefix. */
    public static String tableName(String tablePrefix) {
        return (tablePrefix == null ? "" : tablePrefix) + JDBC_STORE_TABLE;
    }

    private static void ensureStoreNotOpened(
            ObjectStoreType current, ObjectStoreType requested, ObjectStoreSettings settings) {
        if (ForageRecoveryService.getInstance().isRecoveryManagerStarted()) {
            throw new IllegalStateException("Cannot switch the Narayana transaction object store from '" + current
                    + "' to '" + requested + "' as configured by " + settings.typeProperty()
                    + ": the recovery manager is already running and has opened the '" + current + "' store."
                    + " The object store is shared by the whole JVM, so every Forage JDBC and JMS configuration"
                    + " with transactions enabled must configure the same store"
                    + " (forage.<name>.jdbc.transaction.object.store.* and"
                    + " forage.<name>.jms.transaction.object.store.*).");
        }
    }

    private static ObjectStoreType currentType(ObjectStoreEnvironmentBean bean) {
        String storeType = bean.getObjectStoreType();
        if (JDBCStore.class.getName().equals(storeType)) {
            return ObjectStoreType.JDBC;
        }
        if (VolatileStore.class.getName().equals(storeType)) {
            return ObjectStoreType.VOLATILE;
        }
        return ObjectStoreType.FILE_SYSTEM;
    }

    private static ObjectStoreEnvironmentBean bean(String storeName) {
        return storeName == null
                ? BeanPopulator.getDefaultInstance(ObjectStoreEnvironmentBean.class)
                : BeanPopulator.getNamedInstance(ObjectStoreEnvironmentBean.class, storeName);
    }
}
