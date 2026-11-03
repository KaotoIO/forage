package io.kaoto.forage.jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.List;
import org.assertj.core.api.Assertions;
import io.agroal.api.AgroalDataSource;
import io.kaoto.forage.jdbc.h2.H2Jdbc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Checks that {@code forage.<name>.jdbc.properties.<key>} values reach the JDBC driver (issue #149).
 */
public class H2DriverPropertiesTest {

    private static final String PREFIX = "forage.h2props.jdbc.";
    private static final List<String> KEYS =
            List.of("db.kind", "url", "username", "password", "properties.MODE", "pool.initial.size");

    @AfterEach
    void clearProperties() {
        KEYS.forEach(key -> System.clearProperty(PREFIX + key));
    }

    @Test
    public void driverPropertiesReachTheDriver() throws Exception {
        System.setProperty(PREFIX + "db.kind", "h2");
        System.setProperty(PREFIX + "url", "jdbc:h2:mem:driverprops;DB_CLOSE_DELAY=-1");
        System.setProperty(PREFIX + "username", "sa");
        System.setProperty(PREFIX + "password", "");
        System.setProperty(PREFIX + "pool.initial.size", "0");
        System.setProperty(PREFIX + "properties.MODE", "MySQL");

        try (AgroalDataSource dataSource = (AgroalDataSource) new H2Jdbc().create("h2props")) {
            Assertions.assertThat(dataSource
                            .getConfiguration()
                            .connectionPoolConfiguration()
                            .connectionFactoryConfiguration()
                            .jdbcProperties())
                    .containsEntry("MODE", "MySQL");

            try (Connection connection = dataSource.getConnection();
                    ResultSet mode = connection
                            .createStatement()
                            .executeQuery(
                                    "SELECT SETTING_VALUE FROM INFORMATION_SCHEMA.SETTINGS WHERE SETTING_NAME = 'MODE'")) {
                Assertions.assertThat(mode.next()).isTrue();
                Assertions.assertThat(mode.getString(1)).isEqualTo("MySQL");
            }
        }
    }
}
