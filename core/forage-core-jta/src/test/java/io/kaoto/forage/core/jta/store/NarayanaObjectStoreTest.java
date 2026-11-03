package io.kaoto.forage.core.jta.store;

import javax.sql.DataSource;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import io.kaoto.forage.core.jta.recovery.ForageRecoveryService;
import io.kaoto.forage.core.util.config.MissingConfigException;
import com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean;
import com.arjuna.ats.internal.arjuna.objectstore.VolatileStore;
import com.arjuna.ats.internal.arjuna.objectstore.jdbc.JDBCStore;
import com.arjuna.ats.jta.recovery.XAResourceRecoveryHelper;
import com.arjuna.common.internal.util.propertyservice.BeanPopulator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Narayana's object store environment beans are JVM singletons: every test snapshots and restores
 * all three of them.
 */
class NarayanaObjectStoreTest {

    private static final String TYPE_PROPERTY = "forage.ds1.jdbc.transaction.object.store.type";
    private static final String DATASOURCE_PROPERTY = "forage.ds1.jdbc.transaction.object.store.datasource";

    private final Map<String, BeanState> originalState = new HashMap<>();

    private record BeanState(
            String type, String dir, DataSource dataSource, String tablePrefix, boolean create, boolean drop) {}

    @BeforeEach
    void captureObjectStoreBeans() {
        for (String storeName : NarayanaObjectStore.STORE_NAMES) {
            ObjectStoreEnvironmentBean bean = bean(storeName);
            originalState.put(
                    String.valueOf(storeName),
                    new BeanState(
                            bean.getObjectStoreType(),
                            bean.getObjectStoreDir(),
                            bean.getJdbcDataSource(),
                            bean.getTablePrefix(),
                            bean.getCreateTable(),
                            bean.getDropTable()));
        }
    }

    @AfterEach
    void restoreObjectStoreBeans() {
        for (String storeName : NarayanaObjectStore.STORE_NAMES) {
            ObjectStoreEnvironmentBean bean = bean(storeName);
            BeanState state = originalState.get(String.valueOf(storeName));
            bean.setObjectStoreType(state.type());
            bean.setObjectStoreDir(state.dir());
            bean.setJdbcDataSource(state.dataSource());
            bean.setTablePrefix(state.tablePrefix());
            bean.setCreateTable(state.create());
            bean.setDropTable(state.drop());
        }
    }

    @Test
    void jdbcStoreConfiguresAllThreeStoresWithTheProvidedDataSource() {
        DataSource pool = stubDataSource();
        List<String> requested = new ArrayList<>();

        NarayanaObjectStore.configure(jdbc("txlog", "app_", true, false), name -> {
            requested.add(name);
            return pool;
        });

        assertThat(requested).containsExactly("txlog");
        for (String storeName : NarayanaObjectStore.STORE_NAMES) {
            ObjectStoreEnvironmentBean bean = bean(storeName);
            assertThat(bean.getObjectStoreType()).as(storeName).isEqualTo(JDBCStore.class.getName());
            assertThat(bean.getJdbcDataSource()).as(storeName).isInstanceOf(ObjectStoreDataSource.class);
            ObjectStoreDataSource storeDataSource = (ObjectStoreDataSource) bean.getJdbcDataSource();
            assertThat(storeDataSource.name()).isEqualTo("txlog");
            assertThat(storeDataSource.delegate()).isSameAs(pool);
            assertThat(bean.getTablePrefix()).as(storeName).isEqualTo("app_");
            assertThat(bean.getCreateTable()).as(storeName).isTrue();
            assertThat(bean.getDropTable()).as(storeName).isFalse();
        }
        // all three stores share one datasource instance, so Narayana shares one table implementation
        assertThat(bean("stateStore").getJdbcDataSource()).isSameAs(bean(null).getJdbcDataSource());
        assertThat(bean("communicationStore").getJdbcDataSource())
                .isSameAs(bean(null).getJdbcDataSource());
    }

    @Test
    void sameJdbcStoreConfiguredTwiceCreatesTheDataSourceOnce() {
        AtomicInteger created = new AtomicInteger();
        ObjectStoreDataSourceProvider provider = name -> {
            created.incrementAndGet();
            return stubDataSource();
        };

        NarayanaObjectStore.configure(jdbc("txlog", "forage_", false, false), provider);
        DataSource first = bean(null).getJdbcDataSource();
        // e.g. a second XA datasource, the JMS module, or a dev-mode reload
        NarayanaObjectStore.configure(jdbc("txlog", "forage_", false, false), provider);

        assertThat(created).hasValue(1);
        assertThat(bean(null).getJdbcDataSource()).isSameAs(first);
    }

    @Test
    void conflictingJdbcStoreIsIgnoredFirstConfigurationWins() {
        AtomicInteger created = new AtomicInteger();
        ObjectStoreDataSourceProvider provider = name -> {
            created.incrementAndGet();
            return stubDataSource();
        };

        NarayanaObjectStore.configure(jdbc("txlog", "forage_", false, false), provider);
        NarayanaObjectStore.configure(jdbc("otherlog", "other_", true, false), provider);

        assertThat(created).hasValue(1);
        assertThat(((ObjectStoreDataSource) bean(null).getJdbcDataSource()).name())
                .isEqualTo("txlog");
        assertThat(bean(null).getTablePrefix()).isEqualTo("forage_");
    }

    @Test
    void jdbcStoreWithoutDataSourceFailsFast() {
        String originalType = bean(null).getObjectStoreType();

        assertThatThrownBy(() -> NarayanaObjectStore.configure(jdbc("  ", "forage_", false, false), name -> {
                    throw new AssertionError("must not be called");
                }))
                .isInstanceOf(MissingConfigException.class)
                .hasMessageContaining(DATASOURCE_PROPERTY)
                .hasMessageContaining(TYPE_PROPERTY)
                .hasMessageContaining("non-XA");

        assertThat(bean(null).getObjectStoreType()).isEqualTo(originalType);
    }

    @Test
    void unknownStoreTypeFailsFastListingTheSupportedTypes() {
        ObjectStoreSettings settings = new ObjectStoreSettings(
                "jdbcc", "ObjectStore", "txlog", false, false, "forage_", TYPE_PROPERTY, DATASOURCE_PROPERTY);

        assertThatThrownBy(() -> NarayanaObjectStore.configure(settings, name -> stubDataSource()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'jdbcc'")
                .hasMessageContaining(TYPE_PROPERTY)
                .hasMessageContaining("file-system, jdbc, volatile");
    }

    @Test
    void storeTypeIsCaseInsensitiveAndBlankMeansFileSystem() {
        assertThat(ObjectStoreType.fromValue(" JDBC ", TYPE_PROPERTY)).isEqualTo(ObjectStoreType.JDBC);
        assertThat(ObjectStoreType.fromValue("File-System", TYPE_PROPERTY)).isEqualTo(ObjectStoreType.FILE_SYSTEM);
        assertThat(ObjectStoreType.fromValue(null, TYPE_PROPERTY)).isEqualTo(ObjectStoreType.FILE_SYSTEM);
        assertThat(ObjectStoreType.fromValue("", TYPE_PROPERTY)).isEqualTo(ObjectStoreType.FILE_SYSTEM);
    }

    @Test
    void fileSystemStoreDoesNotReplaceAConfiguredJdbcStore(@TempDir Path dir) {
        NarayanaObjectStore.configure(jdbc("txlog", "forage_", false, false), name -> stubDataSource());
        String jdbcStoreDir = bean(null).getObjectStoreDir();

        // e.g. a JMS configuration left on the default file-system store
        NarayanaObjectStore.configure(fileSystem(dir.toString()));

        for (String storeName : NarayanaObjectStore.STORE_NAMES) {
            assertThat(bean(storeName).getObjectStoreType()).as(storeName).isEqualTo(JDBCStore.class.getName());
        }
        assertThat(bean(null).getObjectStoreDir()).isEqualTo(jdbcStoreDir);
    }

    @Test
    void fileSystemStoreSetsTheDirectoryOfAllThreeStores(@TempDir Path dir) {
        NarayanaObjectStore.configure(fileSystem(dir.toString()));

        for (String storeName : NarayanaObjectStore.STORE_NAMES) {
            assertThat(bean(storeName).getObjectStoreDir()).as(storeName).isEqualTo(dir.toString());
        }
    }

    @Test
    void volatileStoreConfiguresAllThreeStores() {
        NarayanaObjectStore.configure(new ObjectStoreSettings(
                "volatile", "ObjectStore", null, false, false, "forage_", TYPE_PROPERTY, DATASOURCE_PROPERTY));

        for (String storeName : NarayanaObjectStore.STORE_NAMES) {
            assertThat(bean(storeName).getObjectStoreType()).as(storeName).isEqualTo(VolatileStore.class.getName());
        }
    }

    @Test
    void dataSourceCreationFailureLeavesTheStoreUntouched() {
        String originalType = bean(null).getObjectStoreType();

        assertThatThrownBy(() -> NarayanaObjectStore.configure(jdbc("txlog", "forage_", false, false), name -> {
                    throw new IllegalStateException("datasource 'txlog' has transactions enabled");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("transactions enabled");

        assertThat(bean(null).getObjectStoreType()).isEqualTo(originalType);
        assertThat(bean(null).getJdbcDataSource()).isNull();
    }

    @Test
    void jdbcStoreWithoutAProviderOnTheClasspathFailsFast() {
        // forage-core-jta ships no ObjectStoreDataSourceProvider (forage-jdbc-common does)
        assertThatThrownBy(() -> NarayanaObjectStore.configure(jdbc("txlog", "forage_", false, false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("forage-jdbc");
    }

    @Test
    void switchingTheStoreAfterTheRecoveryManagerStartedFails(@TempDir Path dir) {
        // the recovery manager opens the store it finds: keep it in a temporary directory
        NarayanaObjectStore.configure(fileSystem(dir.toString()));
        ForageRecoveryService recoveryService = ForageRecoveryService.getInstance();
        recoveryService.registerHelper("test:object-store", noopHelper());
        try {
            assertThat(recoveryService.isRecoveryManagerStarted()).isTrue();

            assertThatThrownBy(() -> NarayanaObjectStore.configure(
                            jdbc("txlog", "forage_", false, false), name -> stubDataSource()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("recovery manager is already running")
                    .hasMessageContaining(TYPE_PROPERTY);
            assertThat(bean(null).getObjectStoreType()).isNotEqualTo(JDBCStore.class.getName());
        } finally {
            recoveryService.shutdown();
        }
        assertThat(recoveryService.isRecoveryManagerStarted()).isFalse();
    }

    private static ObjectStoreSettings jdbc(String dataSource, String prefix, boolean create, boolean drop) {
        return new ObjectStoreSettings(
                "jdbc", "ObjectStore", dataSource, create, drop, prefix, TYPE_PROPERTY, DATASOURCE_PROPERTY);
    }

    private static ObjectStoreSettings fileSystem(String dir) {
        return new ObjectStoreSettings(
                "file-system", dir, null, false, false, "forage_", TYPE_PROPERTY, DATASOURCE_PROPERTY);
    }

    private static ObjectStoreEnvironmentBean bean(String storeName) {
        return storeName == null
                ? BeanPopulator.getDefaultInstance(ObjectStoreEnvironmentBean.class)
                : BeanPopulator.getNamedInstance(ObjectStoreEnvironmentBean.class, storeName);
    }

    private static DataSource stubDataSource() {
        return (DataSource) Proxy.newProxyInstance(
                NarayanaObjectStoreTest.class.getClassLoader(), new Class<?>[] {DataSource.class}, (p, m, a) -> {
                    return switch (m.getName()) {
                        case "toString" -> "stub-datasource";
                        case "hashCode" -> System.identityHashCode(p);
                        case "equals" -> p == a[0];
                        default -> throw new UnsupportedOperationException(m.getName());
                    };
                });
    }

    private static XAResourceRecoveryHelper noopHelper() {
        return new XAResourceRecoveryHelper() {
            @Override
            public boolean initialise(String param) {
                return true;
            }

            @Override
            public javax.transaction.xa.XAResource[] getXAResources() {
                return new javax.transaction.xa.XAResource[0];
            }
        };
    }
}
