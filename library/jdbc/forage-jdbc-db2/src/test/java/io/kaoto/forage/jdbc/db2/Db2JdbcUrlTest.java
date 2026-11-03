package io.kaoto.forage.jdbc.db2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Db2JdbcUrlTest {

    @Test
    void type4UrlWithPort() {
        assertThat(Db2JdbcUrl.toDataSourceProperties("jdbc:db2://db.example.com:50000/SAMPLE"))
                .containsExactly(
                        entry("serverName", "db.example.com"),
                        entry("portNumber", "50000"),
                        entry("driverType", "4"),
                        entry("databaseName", "SAMPLE"));
    }

    @Test
    void type4UrlWithoutPort() {
        assertThat(Db2JdbcUrl.toDataSourceProperties("jdbc:db2://localhost/SAMPLE"))
                .containsExactly(
                        entry("serverName", "localhost"), entry("driverType", "4"), entry("databaseName", "SAMPLE"));
    }

    @Test
    void type4UrlWithDriverProperties() {
        assertThat(Db2JdbcUrl.toDataSourceProperties(
                        "jdbc:db2://db1:50001/SAMPLE:sslConnection=true;clientRerouteAlternateServerName=db2,db3;"
                                + "clientRerouteAlternatePortNumber=50001,50001; enableSeamlessFailover = 1 ;"))
                .containsExactly(
                        entry("serverName", "db1"),
                        entry("portNumber", "50001"),
                        entry("driverType", "4"),
                        entry("databaseName", "SAMPLE"),
                        entry("sslConnection", "true"),
                        entry("clientRerouteAlternateServerName", "db2,db3"),
                        entry("clientRerouteAlternatePortNumber", "50001,50001"),
                        entry("enableSeamlessFailover", "1"));
    }

    @Test
    void driverPropertyValueMayContainEquals() {
        assertThat(Db2JdbcUrl.toDataSourceProperties("jdbc:db2://db1:50000/SAMPLE:sslTrustStorePassword=a=b;"))
                .containsEntry("sslTrustStorePassword", "a=b");
    }

    @Test
    void type4UrlWithIpv6Address() {
        assertThat(Db2JdbcUrl.toDataSourceProperties("jdbc:db2://[2001:db8::1]:50000/SAMPLE"))
                .containsEntry("serverName", "2001:db8::1")
                .containsEntry("portNumber", "50000")
                .containsEntry("databaseName", "SAMPLE");
    }

    @Test
    void type2Url() {
        assertThat(Db2JdbcUrl.toDataSourceProperties("jdbc:db2:SAMPLE:currentSchema=APP;"))
                .containsExactly(
                        entry("driverType", "2"), entry("databaseName", "SAMPLE"), entry("currentSchema", "APP"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "jdbc:postgresql://localhost:5432/db",
                "jdbc:db2://localhost:50000",
                "jdbc:db2://localhost:50000/",
                "jdbc:db2://:50000/SAMPLE",
                "jdbc:db2://localhost:port/SAMPLE",
                "jdbc:db2://[2001:db8::1/SAMPLE",
                "jdbc:db2://localhost:50000/SAMPLE:noValue;",
                " "
            })
    void rejectsUrlsThatCannotBeMapped(String url) {
        assertThatThrownBy(() -> Db2JdbcUrl.toDataSourceProperties(url))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("localhost")
                .hasMessageContaining("DB2");
    }

    @Test
    void errorMessageDoesNotLeakDriverProperties() {
        assertThatThrownBy(() -> Db2JdbcUrl.toDataSourceProperties("jdbc:db2://h:1/D:password;"))
                .hasMessageNotContaining("password");
    }

    private static java.util.Map.Entry<String, String> entry(String key, String value) {
        return java.util.Map.entry(key, value);
    }
}
