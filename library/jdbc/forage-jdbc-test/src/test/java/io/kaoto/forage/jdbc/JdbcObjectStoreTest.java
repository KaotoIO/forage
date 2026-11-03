package io.kaoto.forage.jdbc;

import jakarta.transaction.TransactionManager;
import javax.sql.DataSource;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import io.agroal.api.AgroalDataSource;
import io.kaoto.forage.core.jta.store.NarayanaObjectStore;
import io.kaoto.forage.core.jta.store.ObjectStoreDataSource;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.jdbc.h2.H2Jdbc;
import com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean;
import com.arjuna.ats.arjuna.objectstore.StoreManager;
import com.arjuna.ats.internal.arjuna.objectstore.jdbc.JDBCStore;
import com.arjuna.common.internal.util.propertyservice.BeanPopulator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End to end check of the JDBC object store (issue #447) against a real database: an XA datasource
 * configured with {@code object.store.type=jdbc} makes Narayana write its transaction log through a
 * dedicated non-XA pool built from the referenced {@code forage.txlog.jdbc.*} configuration, and the
 * commit decision of a two-phase commit is visible in the store table between prepare and commit.
 *
 * <p>Narayana's store manager and environment beans are JVM singletons: the test resets the store
 * manager and restores the environment beans around each test.
 */
class JdbcObjectStoreTest {

    private static final String TXLOG_URL = "jdbc:h2:mem:forage_txlog;DB_CLOSE_DELAY=-1";
    private static final String APP_URL = "jdbc:h2:mem:forage_xa_app;DB_CLOSE_DELAY=-1";
    private static final String STORE_TABLE = "e2e_JBossTSTxTable";

    private static final Map<String, String> PROPERTIES = Map.ofEntries(
            // the non-XA datasource holding the transaction log
            Map.entry("forage.txlog.jdbc.db.kind", "h2"),
            Map.entry("forage.txlog.jdbc.url", TXLOG_URL),
            Map.entry("forage.txlog.jdbc.username", "sa"),
            Map.entry("forage.txlog.jdbc.password", ""),
            Map.entry("forage.txlog.jdbc.pool.initial.size", "1"),
            Map.entry("forage.txlog.jdbc.pool.min.size", "1"),
            Map.entry("forage.txlog.jdbc.pool.max.size", "4"),
            // the XA datasource whose transactions are logged
            Map.entry("forage.xaapp.jdbc.db.kind", "h2"),
            Map.entry("forage.xaapp.jdbc.url", APP_URL),
            Map.entry("forage.xaapp.jdbc.username", "sa"),
            Map.entry("forage.xaapp.jdbc.password", ""),
            Map.entry("forage.xaapp.jdbc.transaction.enabled", "true"),
            Map.entry("forage.xaapp.jdbc.transaction.object.store.type", "jdbc"),
            Map.entry("forage.xaapp.jdbc.transaction.object.store.datasource", "txlog"),
            Map.entry("forage.xaapp.jdbc.transaction.object.store.create.table", "true"),
            Map.entry("forage.xaapp.jdbc.transaction.object.store.table.prefix", "e2e_"));

    private final Map<String, Object[]> originalStores = new HashMap<>();
    private AgroalDataSource xaDataSource;

    @BeforeEach
    void setUp() {
        for (String storeName : NarayanaObjectStore.STORE_NAMES) {
            ObjectStoreEnvironmentBean bean = storeBean(storeName);
            originalStores.put(String.valueOf(storeName), new Object[] {
                bean.getObjectStoreType(),
                bean.getJdbcDataSource(),
                bean.getTablePrefix(),
                bean.getCreateTable(),
                bean.getDropTable()
            });
        }
        // a store opened by an earlier test in this JVM would otherwise be reused
        StoreManager.shutdown();
        PROPERTIES.forEach(System::setProperty);
    }

    @AfterEach
    void tearDown() {
        StoreManager.shutdown();
        DataSource storeDataSource = storeBean(null).getJdbcDataSource();
        if (storeDataSource instanceof ObjectStoreDataSource forage
                && forage.delegate() instanceof AgroalDataSource pool) {
            pool.close();
        }
        if (xaDataSource != null) {
            xaDataSource.close();
        }
        for (String storeName : NarayanaObjectStore.STORE_NAMES) {
            ObjectStoreEnvironmentBean bean = storeBean(storeName);
            Object[] state = originalStores.get(String.valueOf(storeName));
            bean.setObjectStoreType((String) state[0]);
            bean.setJdbcDataSource((DataSource) state[1]);
            bean.setTablePrefix((String) state[2]);
            bean.setCreateTable((Boolean) state[3]);
            bean.setDropTable((Boolean) state[4]);
        }
        PROPERTIES.keySet().forEach(System::clearProperty);
        ConfigStore.getInstance().reload();
    }

    @Test
    void twoPhaseCommitLogIsWrittenToTheReferencedDatabase() throws Exception {
        xaDataSource = (AgroalDataSource) new H2Jdbc().create("xaapp");

        // all three Narayana stores use the dedicated, non-XA pool of the referenced configuration
        for (String storeName : NarayanaObjectStore.STORE_NAMES) {
            ObjectStoreEnvironmentBean bean = storeBean(storeName);
            assertThat(bean.getObjectStoreType()).as(storeName).isEqualTo(JDBCStore.class.getName());
            assertThat(bean.getJdbcDataSource()).as(storeName).isInstanceOf(ObjectStoreDataSource.class);
        }
        ObjectStoreDataSource storeDataSource =
                (ObjectStoreDataSource) storeBean(null).getJdbcDataSource();
        assertThat(storeDataSource.name()).isEqualTo("txlog");
        assertThat(storeDataSource.delegate())
                .isInstanceOf(AgroalDataSource.class)
                .isNotSameAs(xaDataSource);
        assertThat(((AgroalDataSource) storeDataSource.delegate())
                        .getConfiguration()
                        .connectionPoolConfiguration()
                        .transactionIntegration()
                        .getClass()
                        .getName())
                .doesNotContain("Narayana");

        // Narayana opens the JDBC store, which creates the table (create.table=true)
        assertThat(StoreManager.getRecoveryStore()).isInstanceOf(JDBCStore.class);
        assertThat(transactionLogRecords()).isZero();

        try (Connection connection = xaDataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS orders (id INT PRIMARY KEY, item VARCHAR(64))");
        }

        // two resources force a real two-phase commit (no one-phase optimisation): the H2 XA branch
        // and a probe that looks at the store table while it is asked to commit
        CommitProbe probe = new CommitProbe(JdbcObjectStoreTest::transactionLogRecords);
        TransactionManager transactionManager = com.arjuna.ats.jta.TransactionManager.transactionManager();
        transactionManager.begin();
        try {
            transactionManager.getTransaction().enlistResource(probe);
            try (Connection connection = xaDataSource.getConnection();
                    Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO orders VALUES (1, 'jdbc object store')");
            }
            transactionManager.commit();
        } catch (Exception e) {
            if (transactionManager.getTransaction() != null) {
                transactionManager.rollback();
            }
            throw e;
        }

        // the commit decision was persisted in the database before the branches committed ...
        assertThat(probe.storeRowsAtCommit).isEqualTo(1);
        // ... and removed once the transaction completed
        assertThat(transactionLogRecords()).isZero();
        try (Connection connection = DriverManager.getConnection(APP_URL, "sa", "");
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT item FROM orders WHERE id = 1")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isEqualTo("jdbc object store");
        }
    }

    @Test
    void secondXaDataSourceReusesTheConfiguredStore() {
        xaDataSource = (AgroalDataSource) new H2Jdbc().create("xaapp");
        DataSource first = storeBean(null).getJdbcDataSource();

        // e.g. a dev-mode reload: the same configuration must not build a second store pool
        try (AgroalDataSource again = (AgroalDataSource) new H2Jdbc().create("xaapp")) {
            assertThat(storeBean(null).getJdbcDataSource()).isSameAs(first);
            assertThat(List.of(
                            storeBean("stateStore").getJdbcDataSource(),
                            storeBean("communicationStore").getJdbcDataSource()))
                    .allMatch(dataSource -> dataSource == first);
        }
    }

    /**
     * Transaction log records in the store table. The table is shared by the three stores, so other
     * record types (e.g. the communication store's TransactionStatusManager entry) are excluded.
     */
    private static int transactionLogRecords() throws SQLException {
        try (Connection connection = DriverManager.getConnection(TXLOG_URL, "sa", "");
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT COUNT(*) FROM " + STORE_TABLE + " WHERE TypeName LIKE '%AtomicAction%'")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static ObjectStoreEnvironmentBean storeBean(String storeName) {
        return storeName == null
                ? BeanPopulator.getDefaultInstance(ObjectStoreEnvironmentBean.class)
                : BeanPopulator.getNamedInstance(ObjectStoreEnvironmentBean.class, storeName);
    }

    /** Votes OK on prepare and records how many log rows exist when it is asked to commit. */
    static final class CommitProbe implements XAResource, Serializable {

        private static final long serialVersionUID = 1L;

        private final transient Callable<Integer> storeRows;
        transient volatile int storeRowsAtCommit = -1;

        CommitProbe(Callable<Integer> storeRows) {
            this.storeRows = storeRows;
        }

        @Override
        public int prepare(Xid xid) {
            return XA_OK;
        }

        @Override
        public void commit(Xid xid, boolean onePhase) throws XAException {
            if (onePhase) {
                throw new IllegalStateException("expected a two-phase commit");
            }
            try {
                storeRowsAtCommit = storeRows.call();
            } catch (Exception e) {
                XAException xaException = new XAException(XAException.XAER_RMERR);
                xaException.initCause(e);
                throw xaException;
            }
        }

        @Override
        public void rollback(Xid xid) {
            // nothing to undo
        }

        @Override
        public void start(Xid xid, int flags) {
            // no branch state
        }

        @Override
        public void end(Xid xid, int flags) {
            // no branch state
        }

        @Override
        public void forget(Xid xid) {
            // no heuristic outcomes
        }

        @Override
        public Xid[] recover(int flag) {
            return new Xid[0];
        }

        @Override
        public boolean isSameRM(XAResource xaResource) {
            return xaResource == this;
        }

        @Override
        public int getTransactionTimeout() {
            return 0;
        }

        @Override
        public boolean setTransactionTimeout(int seconds) {
            return false;
        }
    }
}
