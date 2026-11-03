package io.kaoto.forage.jms.common;

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
import io.kaoto.forage.jms.common.transactions.TransactionConfiguration;
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

@DisplayName("JMS TransactionConfiguration Tests")
@ResourceLock(SYSTEM_PROPERTIES)
class TransactionConfigurationTest {

    private static final String NODE_ID_PROPERTY = "forage.jms.transaction.node.id";
    private static final String OBJECT_STORE_DIR_PROPERTY = "forage.jms.transaction.object.store.directory";

    private String originalNodeId;
    private List<String> originalRecoveryNodes;
    private List<String> originalOrphanFilters;
    private String originalDefaultStoreDir;
    private String originalStateStoreDir;
    private String originalCommunicationStoreDir;

    private final List<String> systemPropertiesToClear = new ArrayList<>();
    private final Map<String, JdbcStoreState> originalJdbcStores = new HashMap<>();

    private record JdbcStoreState(
            String type, DataSource dataSource, String tablePrefix, boolean create, boolean drop) {}

    @BeforeEach
    void captureNarayanaState() {
        originalNodeId =
                BeanPopulator.getDefaultInstance(CoreEnvironmentBean.class).getNodeIdentifier();
        JTAEnvironmentBean jtaBean = BeanPopulator.getDefaultInstance(JTAEnvironmentBean.class);
        originalRecoveryNodes = jtaBean.getXaRecoveryNodes();
        originalOrphanFilters = jtaBean.getXaResourceOrphanFilterClassNames();
        originalDefaultStoreDir = BeanPopulator.getDefaultInstance(ObjectStoreEnvironmentBean.class)
                .getObjectStoreDir();
        originalStateStoreDir = BeanPopulator.getNamedInstance(ObjectStoreEnvironmentBean.class, "stateStore")
                .getObjectStoreDir();
        originalCommunicationStoreDir = BeanPopulator.getNamedInstance(
                        ObjectStoreEnvironmentBean.class, "communicationStore")
                .getObjectStoreDir();

        jtaBean.setXaRecoveryNodes(new ArrayList<>());
    }

    @AfterEach
    void restoreNarayanaState() throws Exception {
        System.clearProperty(NODE_ID_PROPERTY);
        System.clearProperty(OBJECT_STORE_DIR_PROPERTY);

        if (originalNodeId != null) {
            BeanPopulator.getDefaultInstance(CoreEnvironmentBean.class).setNodeIdentifier(originalNodeId);
        }
        JTAEnvironmentBean jtaBean = BeanPopulator.getDefaultInstance(JTAEnvironmentBean.class);
        jtaBean.setXaRecoveryNodes(originalRecoveryNodes == null ? new ArrayList<>() : originalRecoveryNodes);
        jtaBean.setXaResourceOrphanFilterClassNames(originalOrphanFilters);
        BeanPopulator.getDefaultInstance(ObjectStoreEnvironmentBean.class).setObjectStoreDir(originalDefaultStoreDir);
        BeanPopulator.getNamedInstance(ObjectStoreEnvironmentBean.class, "stateStore")
                .setObjectStoreDir(originalStateStoreDir);
        BeanPopulator.getNamedInstance(ObjectStoreEnvironmentBean.class, "communicationStore")
                .setObjectStoreDir(originalCommunicationStoreDir);

        systemPropertiesToClear.forEach(System::clearProperty);
        ConfigStore.getInstance().reload();
        for (String storeName : NarayanaObjectStore.STORE_NAMES) {
            ObjectStoreEnvironmentBean bean = storeBean(storeName);
            JdbcStoreState state = originalJdbcStores.get(String.valueOf(storeName));
            bean.setObjectStoreType(state.type());
            bean.setJdbcDataSource(state.dataSource());
            bean.setTablePrefix(state.tablePrefix());
            bean.setCreateTable(state.create());
            bean.setDropTable(state.drop());
        }
    }

    @BeforeEach
    void captureJdbcStoreState() {
        for (String storeName : NarayanaObjectStore.STORE_NAMES) {
            ObjectStoreEnvironmentBean bean = storeBean(storeName);
            originalJdbcStores.put(
                    String.valueOf(storeName),
                    new JdbcStoreState(
                            bean.getObjectStoreType(),
                            bean.getJdbcDataSource(),
                            bean.getTablePrefix(),
                            bean.getCreateTable(),
                            bean.getDropTable()));
        }
    }

    @Test
    @DisplayName("jdbc object store: the JMS module configures all three stores with the referenced datasource")
    void jdbcObjectStoreConfiguresAllThreeStores() {
        property("forage.broker1.jms.transaction.object.store.type", "jdbc");
        property("forage.broker1.jms.transaction.object.store.datasource", "txlog");
        property("forage.broker1.jms.transaction.object.store.create.table", "true");
        property("forage.broker1.jms.transaction.object.store.table.prefix", "jms_");
        DataSource pool = stubDataSource();
        List<String> requested = new ArrayList<>();

        new TransactionConfiguration(new ConnectionFactoryConfig("broker1"), "broker1", name -> {
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
            assertThat(bean.getTablePrefix()).as(storeName).isEqualTo("jms_");
            assertThat(bean.getCreateTable()).as(storeName).isTrue();
            assertThat(bean.getDropTable()).as(storeName).isFalse();
        }
    }

    @Test
    @DisplayName("jdbc object store without a datasource fails fast naming the missing property")
    void jdbcObjectStoreWithoutDataSourceFailsFast() {
        property("forage.broker1.jms.transaction.object.store.type", "jdbc");
        String typeBefore = storeBean(null).getObjectStoreType();

        assertThatThrownBy(() -> new TransactionConfiguration(new ConnectionFactoryConfig("broker1"), "broker1")
                        .initializeNarayana())
                .isInstanceOf(MissingConfigException.class)
                .hasMessageContaining("forage.broker1.jms.transaction.object.store.datasource");

        assertThat(storeBean(null).getObjectStoreType()).isEqualTo(typeBefore);
    }

    @Test
    @DisplayName("jdbc object store without the Forage JDBC module on the classpath fails fast")
    void jdbcObjectStoreWithoutJdbcModuleFailsFast() {
        // forage-jms-common does not depend on forage-jdbc-common, which provides the datasource
        property("forage.jms.transaction.object.store.type", "jdbc");
        property("forage.jms.transaction.object.store.datasource", "txlog");

        assertThatThrownBy(() -> new TransactionConfiguration(new ConnectionFactoryConfig(null), "connectionFactory")
                        .initializeNarayana())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("forage-jdbc");
        assertThat(storeBean(null).getObjectStoreType()).isNotEqualTo(JDBCStore.class.getName());
    }

    @Test
    @DisplayName("Unknown object store type fails fast listing the supported types")
    void unknownObjectStoreTypeFailsFast() {
        property("forage.jms.transaction.object.store.type", "jdbcc");

        assertThatThrownBy(() -> new TransactionConfiguration(new ConnectionFactoryConfig(null), "connectionFactory")
                        .initializeNarayana())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("forage.jms.transaction.object.store.type")
                .hasMessageContaining("file-system, jdbc, volatile");
    }

    @Test
    @DisplayName("JDBC object store properties default to no datasource, no DDL and the forage_ prefix")
    void jdbcObjectStorePropertyDefaults() {
        ConnectionFactoryConfig config = new ConnectionFactoryConfig(null);

        assertThat(config.transactionObjectStoreDataSource()).isNull();
        assertThat(config.transactionObjectStoreCreateTable()).isFalse();
        assertThat(config.transactionObjectStoreDropTable()).isFalse();
        assertThat(config.transactionObjectStoreTablePrefix()).isEqualTo("forage_");
        assertThat(config.transactionObjectStoreSettings().dataSourceProperty())
                .isEqualTo("forage.jms.transaction.object.store.datasource");
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

    @Test
    @DisplayName("Two connection factories with different node IDs both accumulate in recovery nodes")
    void twoConnectionFactoriesAccumulateRecoveryNodes() {
        System.setProperty(NODE_ID_PROPERTY, "jms-node-a");
        new TransactionConfiguration(new ConnectionFactoryConfig(null), "cf-a").initializeNarayana();

        System.setProperty(NODE_ID_PROPERTY, "jms-node-b");
        new TransactionConfiguration(new ConnectionFactoryConfig(null), "cf-b").initializeNarayana();

        JTAEnvironmentBean jtaBean = BeanPopulator.getDefaultInstance(JTAEnvironmentBean.class);
        assertThat(jtaBean.getXaRecoveryNodes()).containsExactlyInAnyOrder("jms-node-a", "jms-node-b");
    }

    @Test
    @DisplayName("Node identifier is set once: the first configured value wins, later values are ignored")
    void nodeIdentifierSetOnceFirstWins() {
        CoreEnvironmentBean coreBean = BeanPopulator.getDefaultInstance(CoreEnvironmentBean.class);
        String nodeIdBefore = coreBean.getNodeIdentifier();

        System.setProperty(NODE_ID_PROPERTY, "jms-set-once-first");
        new TransactionConfiguration(new ConnectionFactoryConfig(null), "cf-a").initializeNarayana();

        String activeNodeId = coreBean.getNodeIdentifier();
        if (nodeIdBefore == null || "1".equals(nodeIdBefore)) {
            // Node id was unset (Narayana default), so the first initialization claims it
            assertThat(activeNodeId).isEqualTo("jms-set-once-first");
        } else {
            // Node id was already claimed earlier in this JVM, so it must be unchanged
            assertThat(activeNodeId).isEqualTo(nodeIdBefore);
        }

        // A second initialization with a different node id must not change the active one
        System.setProperty(NODE_ID_PROPERTY, "jms-set-once-second");
        new TransactionConfiguration(new ConnectionFactoryConfig(null), "cf-b").initializeNarayana();
        assertThat(coreBean.getNodeIdentifier()).isEqualTo(activeNodeId);
    }

    @Test
    @DisplayName("Configured object store directory is applied to default, stateStore and communicationStore beans")
    void objectStoreDirectoryAppliedToAllStores(@TempDir Path tempDir) {
        String storeDir = tempDir.resolve("narayana-object-store").toString();
        System.setProperty(OBJECT_STORE_DIR_PROPERTY, storeDir);

        new TransactionConfiguration(new ConnectionFactoryConfig(null), "cf-store").initializeNarayana();

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
}
