package io.kaoto.forage.jms.common;

import java.util.Map;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.core.util.config.MissingConfigException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JmsModuleDescriptorTest {

    @BeforeEach
    void setUp() {
        System.setProperty("forage.jms.kind", "artemis");
        System.setProperty("forage.jms.broker.url", "tcp://localhost:61616");
        System.setProperty("forage.jms.pool.block.if.full.timeout.millis", "-1");
        System.setProperty("forage.jms.pool.idle.timeout.millis", "60000");
        System.setProperty("forage.jms.pool.expiry.timeout.millis", "45000");
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("forage.jms.kind");
        System.clearProperty("forage.jms.broker.url");
        System.clearProperty("forage.jms.pool.block.if.full.timeout.millis");
        System.clearProperty("forage.jms.pool.idle.timeout.millis");
        System.clearProperty("forage.jms.pool.expiry.timeout.millis");
    }

    @Test
    void blockIfFullTimeoutSentinelNotCorrupted() {
        ConnectionFactoryConfig config = new ConnectionFactoryConfig();
        Map<String, String> props = new JmsModuleDescriptor().translateProperties(null, config);

        // -1 (infinite sentinel) must not be divided by 1000 and must be passed through as-is
        assertThat(props.get("quarkus.pooled-jms.block-if-session-pool-is-full-timeout"))
                .isEqualTo("-1");
    }

    @Test
    void pooledJmsTimeoutsArePassedThroughAsMillis() {
        ConnectionFactoryConfig config = new ConnectionFactoryConfig();
        Map<String, String> props = new JmsModuleDescriptor().translateProperties(null, config);

        // quarkus-pooled-jms passes connection-idle-timeout raw to pooled-jms setConnectionIdleTimeout(int),
        // which expects MILLISECONDS. The old seconds conversion would have produced "60" (i.e. 60 ms).
        assertThat(props.get("quarkus.pooled-jms.connection-idle-timeout")).isEqualTo("60000");
        assertThat(props.get("quarkus.pooled-jms.connection-idle-timeout")).isNotEqualTo("60");

        // connection-check-interval is also a millis value and must be passed through unchanged
        assertThat(props.get("quarkus.pooled-jms.connection-check-interval")).isEqualTo("45000");
    }

    @Test
    void jdbcObjectStoreIsTranslatedWithANonTransactionalStoreDataSource() {
        Map<String, String> objectStore = Map.of(
                "forage.jms.transaction.enabled", "true",
                "forage.jms.transaction.object.store.type", "jdbc",
                "forage.jms.transaction.object.store.datasource", "txlog",
                "forage.jms.transaction.object.store.create.table", "true");
        objectStore.forEach(System::setProperty);
        try {
            Map<String, String> props =
                    new JmsModuleDescriptor().translateProperties(null, new ConnectionFactoryConfig());

            assertThat(props)
                    .containsEntry("quarkus.transaction-manager.object-store.type", "jdbc")
                    .containsEntry("quarkus.transaction-manager.object-store.datasource", "txlog")
                    .containsEntry("quarkus.transaction-manager.object-store.create-table", "true")
                    .containsEntry("quarkus.transaction-manager.object-store.drop-table", "false")
                    .containsEntry("quarkus.transaction-manager.object-store.table-prefix", "forage_")
                    .containsEntry("quarkus.datasource.\"txlog\".jdbc.transactions", "disabled");
        } finally {
            objectStore.keySet().forEach(System::clearProperty);
            ConfigStore.getInstance().reload();
        }
    }

    @Test
    void jdbcObjectStoreWithoutDataSourceFailsTranslation() {
        Map<String, String> objectStore = Map.of(
                "forage.jms.transaction.enabled", "true",
                "forage.jms.transaction.object.store.type", "jdbc");
        objectStore.forEach(System::setProperty);
        try {
            ConnectionFactoryConfig config = new ConnectionFactoryConfig();

            assertThatThrownBy(() -> new JmsModuleDescriptor().translateProperties(null, config))
                    .isInstanceOf(MissingConfigException.class)
                    .hasMessageContaining("forage.jms.transaction.object.store.datasource");
        } finally {
            objectStore.keySet().forEach(System::clearProperty);
            ConfigStore.getInstance().reload();
        }
    }

    @Test
    void fileSystemObjectStoreEmitsNoJdbcStoreProperties() {
        System.setProperty("forage.jms.transaction.enabled", "true");
        try {
            Map<String, String> props =
                    new JmsModuleDescriptor().translateProperties(null, new ConnectionFactoryConfig());

            assertThat(props).containsEntry("quarkus.transaction-manager.object-store.type", "file-system");
            assertThat(props).doesNotContainKey("quarkus.transaction-manager.object-store.datasource");
            assertThat(props.keySet()).noneMatch(key -> key.endsWith(".jdbc.transactions"));
        } finally {
            System.clearProperty("forage.jms.transaction.enabled");
            ConfigStore.getInstance().reload();
        }
    }
}
