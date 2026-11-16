package io.kaoto.forage.plugin;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import io.kaoto.forage.core.common.ForageModuleDescriptor;
import io.kaoto.forage.core.common.QuarkusConfigHook;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.jdbc.common.DataSourceFactoryConfig;
import io.kaoto.forage.jdbc.common.JdbcModuleDescriptor;
import io.kaoto.forage.jdbc.common.JdbcQuarkusConfigHook;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

class QuarkusConfigExportTest {

    private static final String DS1 =
            """
            forage.ds1.jdbc.db.kind=postgresql
            forage.ds1.jdbc.url=jdbc:postgresql://localhost:5432/db
            forage.ds1.jdbc.username=test
            forage.ds1.jdbc.password=secret
            forage.ds1.jdbc.pool.min.size=4
            """;

    @TempDir
    Path dir;

    @AfterEach
    void tearDown() {
        System.clearProperty("forage.ds1.jdbc.username");
        System.clearProperty("forage.ds1.jdbc.idempotent.repository.enabled");
        System.clearProperty("forage.ds1.jdbc.properties.ssl");
        ConfigStore.getInstance().reload();
    }

    @Test
    void translatesPlainNamedDataSource() {
        QuarkusConfigExport.Result result = QuarkusConfigExport.translate(properties(DS1));

        assertThat(result.modules()).containsExactly("jdbc");
        assertThat(result.properties())
                .containsEntry("quarkus.datasource.\"ds1\".db-kind", "postgresql")
                .containsEntry("quarkus.datasource.\"ds1\".jdbc.url", "jdbc:postgresql://localhost:5432/db")
                .containsEntry("quarkus.datasource.\"ds1\".username", "test")
                .containsEntry("quarkus.datasource.\"ds1\".password", "secret")
                .containsEntry("quarkus.datasource.\"ds1\".jdbc.min-size", "4");
        assertThat(result.forageKeys()).hasSize(5).allMatch(key -> key.startsWith("forage.ds1.jdbc."));
        assertThat(result.messages())
                .singleElement()
                .asString()
                .contains("QUARKUS_DATASOURCE__DS1__JDBC_URL")
                .contains("FORAGE_DS1_JDBC_*");
    }

    @Test
    void ignoresExportTimeOverrides() {
        System.setProperty("forage.ds1.jdbc.username", "leaked");

        QuarkusConfigExport.Result result = QuarkusConfigExport.translate(properties(DS1));

        assertThat(result.properties()).containsEntry("quarkus.datasource.\"ds1\".username", "test");
        assertThat(result.messages()).singleElement().asString().contains("Ignored");
    }

    @Test
    void keepsModuleWhenOneDataSourceUsesTransactions() {
        QuarkusConfigExport.Result result = QuarkusConfigExport.translate(
                properties(
                        DS1
                                + """
                forage.ds2.jdbc.db.kind=h2
                forage.ds2.jdbc.url=jdbc:h2:mem:x
                forage.ds2.jdbc.username=sa
                forage.ds2.jdbc.password=sa
                forage.ds2.jdbc.transaction.enabled=true
                """));

        assertNotTranslated(result, "'ds2' uses forage.ds2.jdbc.transaction.enabled");
    }

    @Test
    void translatesDriverProperties() {
        QuarkusConfigExport.Result result =
                QuarkusConfigExport.translate(properties(DS1 + "forage.ds1.jdbc.properties.ssl=true\n"));

        assertThat(result.properties())
                .containsEntry("quarkus.datasource.\"ds1\".jdbc.additional-jdbc-properties.\"ssl\"", "true");
    }

    @Test
    void ignoresExportTimeDriverProperties() {
        System.setProperty("forage.ds1.jdbc.properties.ssl", "true");

        QuarkusConfigExport.Result result = QuarkusConfigExport.translate(properties(DS1));

        assertThat(result.properties().keySet()).noneMatch(key -> key.contains("additional-jdbc-properties"));
        assertThat(System.getProperty("forage.ds1.jdbc.properties.ssl")).isEqualTo("true");
    }

    @Test
    void keepsPlaceholders() {
        assertNotTranslated(
                QuarkusConfigExport.translate(properties(DS1 + "forage.ds1.jdbc.password={{env:DB_PASSWORD}}\n")),
                "placeholders");
    }

    @Test
    void keepsProfileSpecificValues() {
        assertNotTranslated(
                QuarkusConfigExport.translate(
                        properties(DS1 + "%prod.forage.ds1.jdbc.url=jdbc:postgresql://prod/db\n")),
                "profile-specific");
    }

    @Test
    void keepsIncompleteConfiguration() {
        assertNotTranslated(
                QuarkusConfigExport.translate(properties("forage.jdbc.url=jdbc:postgresql://localhost/db\n")),
                "'jdbc'");
    }

    @Test
    void translatesDefaultArtemisConnectionFactory() {
        QuarkusConfigExport.Result result = QuarkusConfigExport.translate(
                properties(
                        """
                forage.jms.kind=artemis
                forage.jms.broker.url=tcp://localhost:61616
                forage.jms.username=artemis
                forage.jms.password=artemis
                """));

        assertThat(result.modules()).containsExactly("jms");
        assertThat(result.properties())
                .containsEntry("quarkus.artemis.url", "tcp://localhost:61616")
                .containsEntry("quarkus.artemis.username", "artemis");
        assertThat(result.forageKeys()).hasSize(4);
        assertThat(result.groups()).containsExactly("jms-connection-pool");
    }

    @Test
    void dropsPoolPropertiesWhenPoolingDisabled() {
        QuarkusConfigExport.Result result = QuarkusConfigExport.translate(
                properties(
                        """
                forage.jms.kind=artemis
                forage.jms.broker.url=tcp://localhost:61616
                forage.jms.pool.enabled=false
                forage.jms.pool.max.connections=5
                """));

        assertThat(result.modules()).containsExactly("jms");
        assertThat(result.properties()).containsKey("quarkus.artemis.url");
        assertThat(result.properties().keySet()).noneMatch(key -> key.startsWith("quarkus.pooled-jms."));
        assertThat(result.groups()).isEmpty();
    }

    @Test
    void keepsUnknownJmsKind() {
        assertNotTranslated(
                QuarkusConfigExport.translate(
                        properties(
                                """
                        forage.jms.kind=foo
                        forage.jms.broker.url=tcp://localhost:61616
                        """)),
                "'jms'");
    }

    @Test
    void keepsIdempotentRepositoryWithoutTransactions() {
        assertNotTranslated(
                QuarkusConfigExport.translate(properties(DS1 + "forage.ds1.jdbc.idempotent.repository.enabled=true\n")),
                "'ds1' uses forage.ds1.jdbc.idempotent.repository.enabled");
    }

    @Test
    void keepsProjectIdempotentRepositoryDisabledAtExportTime() {
        System.setProperty("forage.ds1.jdbc.idempotent.repository.enabled", "false");

        assertNotTranslated(
                QuarkusConfigExport.translate(properties(DS1 + "forage.ds1.jdbc.idempotent.repository.enabled=true\n")),
                "'ds1' uses forage.ds1.jdbc.idempotent.repository.enabled");
    }

    @Test
    void ignoresIdempotentRepositoryEnabledAtExportTime() {
        System.setProperty("forage.ds1.jdbc.idempotent.repository.enabled", "true");

        QuarkusConfigExport.Result result = QuarkusConfigExport.translate(properties(DS1));

        assertThat(result.modules()).containsExactly("jdbc");
        assertThat(result.properties()).containsEntry("quarkus.datasource.\"ds1\".jdbc.min-size", "4");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "client.id=c1",
                "ssl.enabled=true",
                "pool.connection.timeout.millis=5000",
                "artemis.call.timeout=100",
                "artemis.reconnect.attempts=5",
                "foo.bar=x",
                "username2=x"
            })
    void keepsUncoveredJmsKeys(String key) {
        assertNotTranslated(
                QuarkusConfigExport.translate(properties(
                        "forage.jms.kind=artemis\nforage.jms.broker.url=tcp://localhost:61616\nforage.jms." + key)),
                "'jms' uses forage.jms." + key.substring(0, key.indexOf('=')) + ", not covered");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "foo.bar=x",
                "pool.idle.validation.timeout.minutes=9",
                "aggregation.repository.enabled=true",
                "pool.max.size.extra=1"
            })
    void keepsUncoveredJdbcKeys(String key) {
        assertNotTranslated(
                QuarkusConfigExport.translate(properties(DS1 + "forage.ds1.jdbc." + key)),
                "'ds1' uses forage.ds1.jdbc." + key.substring(0, key.indexOf('=')) + ", not covered");
    }

    @ParameterizedTest
    @CsvSource({
        "jdbc, db.kind, postgresql",
        "jdbc, url, jdbc:postgresql://other/db",
        "jdbc, username, u",
        "jdbc, password, p",
        "jdbc, pool.initial.size, 4",
        "jdbc, pool.min.size, 4",
        "jdbc, pool.max.size, 7",
        "jdbc, pool.acquisition.timeout.seconds, 7",
        "jdbc, pool.validation.timeout.seconds, 7",
        "jdbc, pool.leak.timeout.minutes, 7",
        "jdbc, properties.ssl, true",
        "jms, kind, artemis",
        "jms, broker.url, tcp://other:61616",
        "jms, username, u",
        "jms, password, p",
        "jms, pool.enabled, true",
        "jms, pool.max.connections, 7",
        "jms, pool.max.sessions.per.connection, 7",
        "jms, pool.idle.timeout.millis, 7",
        "jms, pool.expiry.timeout.millis, 7",
        "jms, pool.block.if.full, true",
        "jms, pool.block.if.full.timeout.millis, 7"
    })
    void translatesEveryCoveredKey(String module, String key, String value) {
        String prefix = module.equals("jdbc") ? "forage.ds1.jdbc." : "forage.jms.";
        String base = module.equals("jdbc") ? DS1 : "forage.jms.kind=artemis\nforage.jms.broker.url=tcp://h:61616\n";

        QuarkusConfigExport.Result result =
                QuarkusConfigExport.translate(properties(base + prefix + key + "=" + value + "\n"));

        assertThat(result.modules()).containsExactly(module);
        assertThat(result.forageKeys()).contains(prefix + key);
    }

    @Test
    void ignoresKeysOutsideTheModulePrefix() {
        QuarkusConfigExport.Result result =
                QuarkusConfigExport.translate(properties(DS1 + "forage.ds1.jdbcfoo.x=1\nforage.jmsX.foo=1\n"));

        assertThat(result.modules()).containsExactly("jdbc");
        assertThat(result.forageKeys()).noneMatch(key -> key.contains("jdbcfoo") || key.contains("jmsX"));
        assertThat(result.messages()).hasSize(1);
    }

    @Test
    void keepsNamedJmsBrokers() {
        assertNotTranslated(
                QuarkusConfigExport.translate(
                        properties(
                                """
                        forage.mq1.jms.kind=artemis
                        forage.mq1.jms.broker.url=tcp://localhost:61616
                        """)),
                "named configurations");
    }

    @Test
    void keepsIbmMq() {
        assertNotTranslated(
                QuarkusConfigExport.translate(
                        properties(
                                """
                        forage.jms.kind=ibmmq
                        forage.jms.broker.url=localhost(1414)
                        """)),
                "no Quarkus equivalent");
    }

    @Test
    void translatesModuleOfAnyHook() {
        QuarkusConfigExport.Result result = QuarkusConfigExport.translate(properties(DS1), List.of(new FakeHook(null)));

        assertThat(result.modules()).containsExactly("jdbc");
        assertThat(result.properties()).containsEntry("fake", "test");
        assertThat(result.groups()).containsExactly("fake-group");
    }

    @Test
    void keepsModuleWithoutHook() {
        QuarkusConfigExport.Result result = QuarkusConfigExport.translate(properties(DS1), List.of());

        assertThat(result.properties()).isEmpty();
        assertThat(result.modules()).isEmpty();
        assertThat(result.messages()).isEmpty();
    }

    @Test
    void keepsModuleWhenHookTurnsItOff() {
        assertNotTranslated(
                QuarkusConfigExport.translate(properties(DS1), List.of(new FakeHook("is off"))), "'ds1' is off");
    }

    /** JDBC under a hook that replaces the translation. */
    private record FakeHook(String unsupported) implements QuarkusConfigHook<DataSourceFactoryConfig> {
        @Override
        public ForageModuleDescriptor<DataSourceFactoryConfig, ?> descriptor() {
            return new JdbcModuleDescriptor();
        }

        @Override
        public Set<String> covered() {
            return new JdbcQuarkusConfigHook().covered();
        }

        @Override
        public String unsupported(String name, DataSourceFactoryConfig config) {
            return unsupported;
        }

        @Override
        public Set<String> adjust(DataSourceFactoryConfig config, Map<String, String> translated) {
            translated.clear();
            translated.put("fake", config.username());
            return Set.of("fake-group");
        }
    }

    @Test
    void loadsProfileFilesAsProfileSpecific() throws IOException {
        Files.writeString(dir.resolve("forage-datasource-factory.properties"), DS1);
        Files.writeString(
                dir.resolve("application-prod.properties"), "forage.ds1.jdbc.url=jdbc:postgresql://prod/db\n");

        Properties loaded = QuarkusConfigExport.load(dir);

        assertThat(loaded).containsKeys("forage.ds1.jdbc.url", "%prod.forage.ds1.jdbc.url");
    }

    @Test
    void writeKeepsUserKeysAndRemovesForageKeys() throws IOException {
        Files.writeString(
                dir.resolve("application.properties"),
                """
                camel.main.name=demo
                quarkus.datasource."ds1".jdbc.max-size=50
                forage.ds1.jdbc.url=jdbc:postgresql://localhost:5432/db
                """);
        Files.writeString(
                dir.resolve("forage-datasource-factory.properties"),
                """
                # datasource
                forage.ds1.jdbc.db.kind=postgresql
                forage.ds1.jdbc.username=test
                forage.ds1.jdbc.password=secret
                forage.ds1.jdbc.pool.min.size=4
                """);

        QuarkusConfigExport.write(QuarkusConfigExport.translate(QuarkusConfigExport.load(dir)), dir);

        Properties application = read(dir.resolve("application.properties"));
        assertThat(application)
                .containsEntry("camel.main.name", "demo")
                .containsEntry("quarkus.datasource.\"ds1\".jdbc.max-size", "50")
                .containsEntry("quarkus.datasource.\"ds1\".jdbc.url", "jdbc:postgresql://localhost:5432/db")
                .containsEntry("quarkus.datasource.\"ds1\".jdbc.min-size", "4");
        assertThat(application.stringPropertyNames()).noneMatch(key -> key.startsWith("forage."));
        assertThat(Files.readString(dir.resolve("forage-datasource-factory.properties")))
                .isEqualTo("# datasource\n");
    }

    @Test
    void writeKeepsUnquotedUserKeys() throws IOException {
        Files.writeString(dir.resolve("application.properties"), "quarkus.datasource.ds1.jdbc.min-size=7\n");
        Files.writeString(dir.resolve("forage-datasource-factory.properties"), DS1);

        QuarkusConfigExport.write(QuarkusConfigExport.translate(QuarkusConfigExport.load(dir)), dir);

        assertThat(read(dir.resolve("application.properties")))
                .containsEntry("quarkus.datasource.ds1.jdbc.min-size", "7")
                .doesNotContainKey("quarkus.datasource.\"ds1\".jdbc.min-size")
                .containsKey("quarkus.datasource.\"ds1\".jdbc.url");
    }

    @Test
    void writeDeletesFilesLeftEmpty() throws IOException {
        Files.writeString(dir.resolve("forage-datasource-factory.properties"), DS1);

        QuarkusConfigExport.write(QuarkusConfigExport.translate(QuarkusConfigExport.load(dir)), dir);

        assertThat(dir.resolve("forage-datasource-factory.properties")).doesNotExist();
        assertThat(read(dir.resolve("application.properties")))
                .containsEntry("quarkus.datasource.\"ds1\".jdbc.min-size", "4");
    }

    @Test
    void removeKeysDropsContinuationLines() {
        List<String> kept = QuarkusConfigExport.removeKeys(
                List.of("a=1", "forage.x=one \\", "  two", "# comment \\", "b = 2"), Set.of("forage.x"));

        assertThat(kept).containsExactly("a=1", "# comment \\", "b = 2");
    }

    private static void assertNotTranslated(QuarkusConfigExport.Result result, String reason) {
        assertThat(result.properties()).isEmpty();
        assertThat(result.forageKeys()).isEmpty();
        assertThat(result.modules()).isEmpty();
        assertThat(result.messages())
                .singleElement()
                .asString()
                .contains("stays as forage.*")
                .contains(reason);
    }

    private static Properties properties(String content) {
        try {
            Properties properties = new Properties();
            properties.load(new StringReader(content));
            return properties;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Properties read(Path file) throws IOException {
        return properties(Files.readString(file));
    }
}
