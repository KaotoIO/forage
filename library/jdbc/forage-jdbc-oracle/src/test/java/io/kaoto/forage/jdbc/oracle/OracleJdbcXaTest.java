package io.kaoto.forage.jdbc.oracle;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.AgroalConnectionFactoryConfiguration;
import io.agroal.pool.util.PropertyInjector;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Oracle in XA mode (issue #149): the datasource must be created, and Oracle connection properties
 * without a setter, such as {@code oracle.jdbc.ReadTimeout}, must reach the driver.
 */
class OracleJdbcXaTest {

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
    void xaDataSourceIsCreatedWithDriverProperties() {
        set("db.kind", "oracle");
        set("url", "jdbc:oracle:thin:@//127.0.0.1:1521/FREEPDB1");
        set("username", "app");
        set("password", "secret");
        set("pool.initial.size", "0");
        set("pool.min.size", "0");
        set("transaction.enabled", "true");
        set("transaction.enable.recovery", "false");
        set("transaction.object.store.directory", objectStore.toString());
        set("properties.oracle.jdbc.ReadTimeout", "5000");

        // Before the fix this failed with InstantiationException: the driver's
        // oracle.jdbc.xa.OracleXADataSource is abstract
        dataSource = (AgroalDataSource) new OracleJdbc().create("oraclexa");

        AgroalConnectionFactoryConfiguration connectionFactory =
                dataSource.getConfiguration().connectionPoolConfiguration().connectionFactoryConfiguration();
        assertThat(connectionFactory.connectionProviderClass()).isEqualTo(ForageOracleXADataSource.class);
        assertThat(connectionFactory.jdbcProperties()).containsEntry("oracle.jdbc.ReadTimeout", "5000");
    }

    @Test
    void propertiesWithoutSetterBecomeConnectionProperties() throws Exception {
        ForageOracleXADataSource xaDataSource = new ForageOracleXADataSource();
        // The same injector Agroal uses to configure an XA datasource
        PropertyInjector injector = new PropertyInjector(ForageOracleXADataSource.class);

        injector.inject(xaDataSource, "url", "jdbc:oracle:thin:@//db:1521/FREEPDB1");
        injector.inject(xaDataSource, "oracle.jdbc.ReadTimeout", "5000");
        injector.inject(xaDataSource, "v$session.program", "orders");
        injector.inject(xaDataSource, "user", "app");

        assertThat(xaDataSource.getURL()).isEqualTo("jdbc:oracle:thin:@//db:1521/FREEPDB1");
        assertThat(xaDataSource.getConnectionProperty("oracle.jdbc.ReadTimeout"))
                .isEqualTo("5000");
        assertThat(xaDataSource.getConnectionProperty("v$session.program")).isEqualTo("orders");
        // Typed setters keep precedence
        assertThat(xaDataSource.getUser()).isEqualTo("app");
        assertThat(xaDataSource.getConnectionProperty("user")).isNull();
    }

    private void set(String key, String value) {
        String name = "forage.oraclexa.jdbc." + key;
        System.setProperty(name, value);
        properties.add(name);
    }
}
