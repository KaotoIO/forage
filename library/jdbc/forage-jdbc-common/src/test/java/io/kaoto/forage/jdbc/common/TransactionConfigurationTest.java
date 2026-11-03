package io.kaoto.forage.jdbc.common;

import javax.sql.DataSource;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.kaoto.forage.core.jta.store.NarayanaObjectStore;
import io.kaoto.forage.core.jta.store.ObjectStoreDataSource;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.core.util.config.MissingConfigException;
import io.kaoto.forage.jdbc.common.transactions.TransactionConfiguration;
import com.arjuna.ats.arjuna.common.CoreEnvironmentBean;
import com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean;
import com.arjuna.ats.internal.arjuna.objectstore.jdbc.JDBCStore;
import com.arjuna.ats.jta.common.JTAEnvironmentBean;
import com.arjuna.common.internal.util.propertyservice.BeanPopulator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.parallel.Resources.SYSTEM_PROPERTIES;

@DisplayName("TransactionConfiguration Tests")
@ResourceLock(SYSTEM_PROPERTIES)
class TransactionConfigurationTest {

    private static final String NODE_ID_PROPERTY = "forage.jdbc.transaction.node.id";
    private static final String OBJECT_STORE_DIR_PROPERTY = "forage.jdbc.transaction.object.store.directory";

    private final List<String> systemPropertiesToClear = new ArrayList<>();

    private String originalNodeId;
    private List<String> originalRecoveryNodes;
    private List<String> originalOrphanFilters;
    private final Map<String, StoreState> originalStores = new HashMap<>();

    private record StoreState(
            String type, String dir, DataSource dataSource, String tablePrefix, boolean create, boolean drop) {}

    @BeforeEach
    void captureNarayanaState() {
        originalNodeId =
                BeanPopulator.getDefaultInstance(CoreEnvironmentBean.class).getNodeIdentifier();
        JTAEnvironmentBean jtaBean = BeanPopulator.getDefaultInstance(JTAEnvironmentBean.class);
        originalRecoveryNodes = jtaBean.getXaRecoveryNodes();
        originalOrphanFilters = jtaBean.getXaResourceOrphanFilterClassNames();
        for (String storeName : NarayanaObjectStore.STORE_NAMES) {
            ObjectStoreEnvironmentBean bean = storeBean(storeName);
            originalStores.put(
                    String.valueOf(storeName),
                    new StoreState(
                            bean.getObjectStoreType(),
                            bean.getObjectStoreDir(),
                            bean.getJdbcDataSource(),
                            bean.getTablePrefix(),
                            bean.getCreateTable(),
                            bean.getDropTable()));
        }

        jtaBean.setXaRecoveryNodes(new ArrayList<>());
    }

    @AfterEach
    void restoreNarayanaState() throws Exception {
        System.clearProperty(NODE_ID_PROPERTY);
        System.clearProperty(OBJECT_STORE_DIR_PROPERTY);
        systemPropertiesToClear.forEach(System::clearProperty);
        ConfigStore.getInstance().reload();

        if (originalNodeId != null) {
            BeanPopulator.getDefaultInstance(CoreEnvironmentBean.class).setNodeIdentifier(originalNodeId);
        }
        JTAEnvironmentBean jtaBean = BeanPopulator.getDefaultInstance(JTAEnvironmentBean.class);
        jtaBean.setXaRecoveryNodes(originalRecoveryNodes == null ? new ArrayList<>() : originalRecoveryNodes);
        jtaBean.setXaResourceOrphanFilterClassNames(originalOrphanFilters);
        for (String storeName : NarayanaObjectStore.STORE_NAMES) {
            ObjectStoreEnvironmentBean bean = storeBean(storeName);
            StoreState state = originalStores.get(String.valueOf(storeName));
            bean.setObjectStoreType(state.type());
            bean.setObjectStoreDir(state.dir());
            bean.setJdbcDataSource(state.dataSource());
            bean.setTablePrefix(state.tablePrefix());
            bean.setCreateTable(state.create());
            bean.setDropTable(state.drop());
        }
    }

    @Test
    @DisplayName("Two datasources with different node IDs both accumulate in recovery nodes")
    void twoDataSourcesAccumulateRecoveryNodes() {
        System.setProperty(NODE_ID_PROPERTY, "node-a");

        DataSourceFactoryConfig configA = new DataSourceFactoryConfig(null);
        new TransactionConfiguration(configA, "ds-a").initializeNarayana();

        System.setProperty(NODE_ID_PROPERTY, "node-b");
        DataSourceFactoryConfig configB = new DataSourceFactoryConfig(null);
        new TransactionConfiguration(configB, "ds-b").initializeNarayana();

        JTAEnvironmentBean jtaBean = BeanPopulator.getDefaultInstance(JTAEnvironmentBean.class);
        assertThat(jtaBean.getXaRecoveryNodes()).containsExactlyInAnyOrder("node-a", "node-b");
    }

    @Test
    @DisplayName("Same node ID registered twice is not duplicated in recovery nodes")
    void sameNodeIdNotDuplicated() {
        System.setProperty(NODE_ID_PROPERTY, "shared-node");

        DataSourceFactoryConfig configA = new DataSourceFactoryConfig(null);
        new TransactionConfiguration(configA, "ds-a").initializeNarayana();

        DataSourceFactoryConfig configB = new DataSourceFactoryConfig(null);
        new TransactionConfiguration(configB, "ds-b").initializeNarayana();

        JTAEnvironmentBean jtaBean = BeanPopulator.getDefaultInstance(JTAEnvironmentBean.class);
        assertThat(jtaBean.getXaRecoveryNodes()).containsExactly("shared-node");
    }

    @Test
    @DisplayName("Node identifier is set once: the first configured value wins, later values are ignored")
    void nodeIdentifierSetOnceFirstWins() {
        CoreEnvironmentBean coreBean = BeanPopulator.getDefaultInstance(CoreEnvironmentBean.class);
        String nodeIdBefore = coreBean.getNodeIdentifier();

        System.setProperty(NODE_ID_PROPERTY, "set-once-first");
        new TransactionConfiguration(new DataSourceFactoryConfig(null), "ds-a").initializeNarayana();

        String activeNodeId = coreBean.getNodeIdentifier();
        if (nodeIdBefore == null || "1".equals(nodeIdBefore)) {
            // Node id was unset (Narayana default), so the first initialization claims it
            assertThat(activeNodeId).isEqualTo("set-once-first");
        } else {
            // Node id was already claimed earlier in this JVM, so it must be unchanged
            assertThat(activeNodeId).isEqualTo(nodeIdBefore);
        }

        // A second initialization with a different node id must not change the active one
        System.setProperty(NODE_ID_PROPERTY, "set-once-second");
        new TransactionConfiguration(new DataSourceFactoryConfig(null), "ds-b").initializeNarayana();
        assertThat(coreBean.getNodeIdentifier()).isEqualTo(activeNodeId);
    }

    @Test
    @DisplayName("Configured object store directory is applied to default, stateStore and communicationStore beans")
    void objectStoreDirectoryAppliedToAllStores(@TempDir Path tempDir) {
        String storeDir = tempDir.resolve("narayana-object-store").toString();
        System.setProperty(OBJECT_STORE_DIR_PROPERTY, storeDir);

        new TransactionConfiguration(new DataSourceFactoryConfig(null), "ds-store").initializeNarayana();

        assertThat(BeanPopulator.getDefaultInstance(ObjectStoreEnvironmentBean.class)
                        .getObjectStoreDir())
                .isEqualTo(storeDir);
        assertThat(BeanPopulator.getNamedInstance(ObjectStoreEnvironmentBean.class, "stateStore")
                        .getObjectStoreDir())
                .isEqualTo(storeDir);
        assertThat(BeanPopulator.getNamedInstance(ObjectStoreEnvironmentBean.class, "communicationStore")
                        .getObjectStoreDir())
                .isEqualTo(storeDir);
    }

    @Test
    @DisplayName("jdbc object store: all three stores use the referenced datasource and the table settings")
    void jdbcObjectStoreConfiguresAllThreeStores() {
        property("forage.ds1.jdbc.transaction.object.store.type", "jdbc");
        property("forage.ds1.jdbc.transaction.object.store.datasource", "txlog");
        property("forage.ds1.jdbc.transaction.object.store.create.table", "true");
        property("forage.ds1.jdbc.transaction.object.store.table.prefix", "app_");
        DataSource pool = stubDataSource();
        List<String> requested = new ArrayList<>();

        new TransactionConfiguration(new DataSourceFactoryConfig("ds1"), "ds1", name -> {
                    requested.add(name);
                    return pool;
                })
                .initializeNarayana();

        assertThat(requested).containsExactly("txlog");
        for (String storeName : NarayanaObjectStore.STORE_NAMES) {
            ObjectStoreEnvironmentBean bean = storeBean(storeName);
            assertThat(bean.getObjectStoreType()).as(storeName).isEqualTo(JDBCStore.class.getName());
            assertThat(((ObjectStoreDataSource) bean.getJdbcDataSource()).delegate())
                    .as(storeName)
                    .isSameAs(pool);
            assertThat(bean.getTablePrefix()).as(storeName).isEqualTo("app_");
            assertThat(bean.getCreateTable()).as(storeName).isTrue();
            assertThat(bean.getDropTable()).as(storeName).isFalse();
        }
    }

    @Test
    @DisplayName("jdbc object store without a datasource fails fast naming the missing property")
    void jdbcObjectStoreWithoutDataSourceFailsFast() {
        property("forage.ds1.jdbc.transaction.object.store.type", "jdbc");
        String typeBefore = storeBean(null).getObjectStoreType();

        assertThatThrownBy(() ->
                        new TransactionConfiguration(new DataSourceFactoryConfig("ds1"), "ds1").initializeNarayana())
                .isInstanceOf(MissingConfigException.class)
                .hasMessageContaining("forage.ds1.jdbc.transaction.object.store.datasource")
                .hasMessageContaining("non-XA");

        // no silent fallback to (or change of) the store
        assertThat(storeBean(null).getObjectStoreType()).isEqualTo(typeBefore);
    }

    @Test
    @DisplayName("jdbc object store referencing an unconfigured datasource fails fast")
    void jdbcObjectStoreWithUnconfiguredDataSourceFailsFast() {
        property("forage.ds1.jdbc.transaction.object.store.type", "jdbc");
        property("forage.ds1.jdbc.transaction.object.store.datasource", "missinglog");

        assertThatThrownBy(() ->
                        new TransactionConfiguration(new DataSourceFactoryConfig("ds1"), "ds1").initializeNarayana())
                .isInstanceOf(MissingConfigException.class)
                .hasMessageContaining("'missinglog' is not configured")
                .hasMessageContaining("forage.missinglog.jdbc.db.kind");
        assertThat(storeBean(null).getObjectStoreType()).isNotEqualTo(JDBCStore.class.getName());
    }

    @Test
    @DisplayName("jdbc object store referencing an XA (transactional) datasource is rejected")
    void jdbcObjectStoreWithXaDataSourceIsRejected() {
        property("forage.ds1.jdbc.transaction.object.store.type", "jdbc");
        property("forage.ds1.jdbc.transaction.object.store.datasource", "xalog");
        property("forage.xalog.jdbc.db.kind", "postgresql");
        property("forage.xalog.jdbc.url", "jdbc:postgresql://localhost/test");
        property("forage.xalog.jdbc.transaction.enabled", "true");

        assertThatThrownBy(() ->
                        new TransactionConfiguration(new DataSourceFactoryConfig("ds1"), "ds1").initializeNarayana())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'xalog' has transactions enabled")
                .hasMessageContaining("non-XA");
        assertThat(storeBean(null).getObjectStoreType()).isNotEqualTo(JDBCStore.class.getName());
    }

    @Test
    @DisplayName("Unknown object store type fails fast listing the supported types")
    void unknownObjectStoreTypeFailsFast() {
        property("forage.jdbc.transaction.object.store.type", "database");

        assertThatThrownBy(() -> new TransactionConfiguration(new DataSourceFactoryConfig(null), "dataSource")
                        .initializeNarayana())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'database'")
                .hasMessageContaining("forage.jdbc.transaction.object.store.type")
                .hasMessageContaining("file-system, jdbc, volatile");
    }

    private void property(String name, String value) {
        System.setProperty(name, value);
        systemPropertiesToClear.add(name);
    }

    private static ObjectStoreEnvironmentBean storeBean(String storeName) {
        return storeName == null
                ? BeanPopulator.getDefaultInstance(ObjectStoreEnvironmentBean.class)
                : BeanPopulator.getNamedInstance(ObjectStoreEnvironmentBean.class, storeName);
    }

    private static DataSource stubDataSource() {
        return (DataSource) Proxy.newProxyInstance(
                TransactionConfigurationTest.class.getClassLoader(), new Class<?>[] {DataSource.class}, (p, m, a) -> {
                    return switch (m.getName()) {
                        case "toString" -> "stub-datasource";
                        case "hashCode" -> System.identityHashCode(p);
                        case "equals" -> p == a[0];
                        default -> throw new UnsupportedOperationException(m.getName());
                    };
                });
    }
}
