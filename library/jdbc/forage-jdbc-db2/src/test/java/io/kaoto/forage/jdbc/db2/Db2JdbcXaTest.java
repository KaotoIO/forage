package io.kaoto.forage.jdbc.db2;

import javax.sql.DataSource;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.AgroalConnectionFactoryConfiguration;
import com.ibm.db2.jcc.DB2Driver;
import com.ibm.db2.jcc.DB2XADataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression test for the DB2 XA datasource ignoring {@code forage.jdbc.url} (issue #149).
 * No DB2 server is needed: the test checks what reaches the driver and that a connection attempt
 * goes to the host and port from the URL.
 */
class Db2JdbcXaTest {

    private final List<String> properties = new ArrayList<>();
    private AgroalDataSource dataSource;

    @TempDir
    Path objectStore;

    @AfterEach
    void cleanUp() {
        if (dataSource != null) {
            dataSource.close();
        }
        properties.forEach(System::clearProperty);
    }

    @Test
    void xaDataSourceReceivesServerPortAndDatabaseFromUrl() throws Exception {
        int port = closedPort();
        configure(
                "db2xa",
                "jdbc:db2://127.0.0.1:" + port + "/TESTDB:sslConnection=false;blockingReadConnectionTimeout=5;",
                true);
        // Explicit driver properties are added, and win over the same property in the URL
        set("db2xa", "properties.currentSchema", "APP");
        set("db2xa", "properties.blockingReadConnectionTimeout", "10");

        dataSource = (AgroalDataSource) new Db2Jdbc().create("db2xa");

        AgroalConnectionFactoryConfiguration connectionFactory =
                dataSource.getConfiguration().connectionPoolConfiguration().connectionFactoryConfiguration();
        assertThat(connectionFactory.connectionProviderClass()).isEqualTo(DB2XADataSource.class);
        Properties jdbcProperties = connectionFactory.jdbcProperties();
        assertThat(jdbcProperties)
                .containsEntry("serverName", "127.0.0.1")
                .containsEntry("portNumber", String.valueOf(port))
                .containsEntry("databaseName", "TESTDB")
                .containsEntry("driverType", "4")
                .containsEntry("sslConnection", "false")
                .containsEntry("currentSchema", "APP")
                .containsEntry("blockingReadConnectionTimeout", "10");
        // Keeps special registers such as currentSchema across Agroal's connection set-up
        assertThat(connectionFactory.initialSql()).isEqualTo("VALUES 1");

        // Before the fix the driver had no server name, fell back to type 2 connectivity and failed
        // to load the native db2jcct2 library. Now it must try the server from the URL.
        Throwable failure = org.assertj.core.api.Assertions.catchThrowable(dataSource::getConnection);
        assertThat(failure).isNotNull();
        String messages = causeChainMessages(failure);
        assertThat(messages).doesNotContain("db2jcct2").contains("127.0.0.1");
    }

    private static String causeChainMessages(Throwable failure) {
        StringBuilder messages = new StringBuilder();
        for (Throwable t = failure; t != null; t = t.getCause()) {
            messages.append(t).append('\n');
        }
        return messages.toString();
    }

    @Test
    void nonXaDataSourceKeepsUsingTheUrl() {
        configure("db2plain", "jdbc:db2://127.0.0.1:50000/TESTDB", false);

        DataSource created = new Db2Jdbc().create("db2plain");
        dataSource = (AgroalDataSource) created;

        AgroalConnectionFactoryConfiguration connectionFactory =
                dataSource.getConfiguration().connectionPoolConfiguration().connectionFactoryConfiguration();
        assertThat(connectionFactory.connectionProviderClass()).isEqualTo(DB2Driver.class);
        assertThat(connectionFactory.jdbcUrl()).isEqualTo("jdbc:db2://127.0.0.1:50000/TESTDB");
        assertThat(connectionFactory.jdbcProperties()).isEmpty();
        assertThat(connectionFactory.initialSql()).isNullOrEmpty();
    }

    @Test
    void xaDataSourceWithUnsupportedUrlFailsFast() {
        configure("db2bad", "jdbc:postgresql://127.0.0.1:5432/TESTDB", true);

        assertThatThrownBy(() -> new Db2Jdbc().create("db2bad"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jdbc:db2://server[:port]/database");
    }

    private void configure(String prefix, String url, boolean transactions) {
        set(prefix, "db.kind", "db2");
        set(prefix, "url", url);
        set(prefix, "username", "db2inst1");
        set(prefix, "password", "secret");
        // No connections at start-up: there is no DB2 server
        set(prefix, "pool.initial.size", "0");
        set(prefix, "pool.min.size", "0");
        set(prefix, "pool.acquisition.timeout.seconds", "10");
        set(prefix, "transaction.enabled", String.valueOf(transactions));
        set(prefix, "transaction.enable.recovery", "false");
        set(prefix, "transaction.object.store.directory", objectStore.toString());
    }

    private void set(String prefix, String key, String value) {
        String name = "forage." + prefix + ".jdbc." + key;
        System.setProperty(name, value);
        properties.add(name);
    }

    private static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
