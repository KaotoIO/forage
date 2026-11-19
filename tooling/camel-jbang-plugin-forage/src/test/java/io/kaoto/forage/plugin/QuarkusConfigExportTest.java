package io.kaoto.forage.plugin;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfigBuilder;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuarkusConfigExportTest {

    private static final String MODULE = "forage-datasource-factory.properties";
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
        List.of("forage.ds1.jdbc.username", "forage.ds1.jdbc.properties.ssl", "forage.home.dir")
                .forEach(System::clearProperty);
        ConfigStore.getInstance().reload();
    }

    /** Only the project values: not system properties, driver properties of the home directory, ... */
    @Test
    void translatesPlainNamedDataSource(@TempDir Path home) throws IOException {
        Files.writeString(home.resolve(MODULE), "forage.ds1.jdbc.properties.a=1\n");
        System.setProperty("forage.home.dir", home.toString());
        System.setProperty("forage.ds1.jdbc.username", "leaked");
        System.setProperty("forage.ds1.jdbc.properties.ssl", "true");

        QuarkusConfigExport.Result result = translate(DS1);

        assertThat(result.modules()).containsExactly("jdbc");
        assertThat(result.forageKeys()).hasSize(5);
        assertThat(result.properties())
                .containsEntry("quarkus.datasource.\"ds1\".db-kind", "postgresql")
                .containsEntry("quarkus.datasource.\"ds1\".username", "test")
                .containsEntry("quarkus.datasource.\"ds1\".jdbc.min-size", "4")
                .noneSatisfy((key, value) -> assertThat(key).contains("additional-jdbc-properties"));
        assertThat(result.messages())
                .singleElement()
                .asString()
                .contains("QUARKUS_DATASOURCE__DS1__JDBC_URL", "Ignored");
        assertThat(System.getProperty("forage.ds1.jdbc.properties.ssl")).isEqualTo("true");
    }

    /** What needs the Forage runtime keeps the whole module on forage.* (";" = new line). */
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "forage.ds2.jdbc.db.kind=h2;forage.ds2.jdbc.transaction.enabled=true | ds2' uses forage.ds2.jdbc.transaction",
                "forage.ds1.jdbc.password={{env:DB_PASSWORD}} | placeholders",
                "%prod.forage.ds1.jdbc.url=jdbc:postgresql://prod/db | profile-specific",
                "forage.jdbc.url=jdbc:postgresql://localhost/db | default and named",
                "forage.ds1.jdbc.pool.idle.validation.timeout.minutes=9 | not covered",
                "quarkus.datasource.ds1.db-kind=mysql | has quarkus.datasource.ds1.db-kind=mysql",
                "%prod.quarkus.datasource.ds1.db-kind=mysql | has %prod.quarkus.datasource.ds1.db-kind=mysql",
                "@forage.jms.kind=artemis;forage.jms.broker.url=x;forage.jms.artemis.call.timeout=1 | not covered",
                "@forage.mq1.jms.kind=artemis;forage.mq1.jms.broker.url=x | named configurations",
                "@forage.jms.kind=ibmmq;forage.jms.broker.url=localhost(1414) | no Quarkus equivalent",
                "@forage.jdbc.url=jdbc:postgresql://localhost/db | jdbc':"
            })
    void keepsModuleOnForage(String lines, String reason) {
        // "@": without DS1
        String application = lines.startsWith("@") ? lines.substring(1) : DS1 + lines;
        QuarkusConfigExport.Result result = translate(application.replace(";", "\n"));

        assertThat(result.properties()).isEmpty();
        assertThat(result.modules()).isEmpty();
        assertThat(result.messages()).singleElement().asString().contains("stays as forage.*", reason);
    }

    /** Quarkus reads empty values as not set and expands ${, $$ and \$: translated or not, in any profile. */
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "forage.ds1.jdbc.properties.ssl= | empty value of forage.ds1.jdbc.properties.ssl ",
                "forage.ds1.jdbc.transaction.enabled=true;forage.ds1.jdbc.username= | empty value of forage.ds1.jdbc.username ",
                "%dev.forage.ds1.jdbc.url=ok;%prod.forage.ds1.jdbc.url= | empty value of %prod.forage.ds1.jdbc.url ",
                "forage.jms.kind=artemis;forage.jms.password= | empty value of forage.jms.password ",
                "forage.ds1.jdbc.url=x${user.home}y | read forage.ds1.jdbc.url differently",
                "forage.ds1.jdbc.transaction.enabled=true;forage.ds1.jdbc.url=a$$b | read forage.ds1.jdbc.url differently",
                "forage.ds1.jdbc.url=a\\\\$b | read forage.ds1.jdbc.url differently",
                "%prod.forage.ds1.jdbc.url=ok;%dev,test.forage.ds1.jdbc.url=${x} | read %dev,test.forage.ds1.jdbc.url differently",
                "forage.mq1.jms.kind=artemis;forage.mq1.jms.password=${x} | read forage.mq1.jms.password differently"
            })
    void failsOnValuesQuarkusReadsDifferently(String lines, String message) {
        assertThatThrownBy(() -> translate(DS1 + lines.replace(";", "\n")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(message);
    }

    @Test
    void failsOnAForageValueOfAProfileFile() throws IOException {
        Files.writeString(dir.resolve("application-prod.properties"), "forage.ds1.jdbc.url=x\n");

        assertThatThrownBy(() -> exported(dir))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("forage.* keys in application-prod.properties: move [forage.ds1.jdbc.url] to"
                        + " application.properties with a %prod. prefix");
    }

    /** application.properties wins over the module file; user keys stay; other files are not Forage's. */
    @Test
    void writeKeepsUserKeysAndRemovesForageKeys() throws IOException {
        Files.writeString(
                dir.resolve("application.properties"),
                """
                camel.main.name=demo
                quarkus.datasource."ds1".jdbc.max-size=50
                quarkus.datasource.ds1.jdbc.min-size=7
                forage.ds1.jdbc.url=jdbc:h2:\\
                  mem:fromapp
                # comment \\
                """
                        .replace("\n", "\r\n"));
        Files.writeString(dir.resolve(MODULE), "# datasource\n" + DS1);
        Files.writeString(dir.resolve("other.properties"), "forage.ds1.jdbc.url=jdbc:h2:mem:other\n");

        QuarkusConfigExport.write(QuarkusConfigExport.translate(exported(dir)), dir);

        Properties application = read(dir.resolve("application.properties"));
        assertThat(application)
                .containsEntry("camel.main.name", "demo")
                .containsEntry("quarkus.datasource.\"ds1\".jdbc.max-size", "50")
                .containsEntry("quarkus.datasource.ds1.jdbc.min-size", "7")
                .doesNotContainKey("quarkus.datasource.\"ds1\".jdbc.min-size")
                .containsEntry("quarkus.datasource.\"ds1\".jdbc.url", "jdbc:h2:mem:fromapp");
        // one line separator; continuation lines go with their key
        assertThat(Files.readString(dir.resolve("application.properties")))
                .startsWith("camel.main.name=demo\nquarkus.datasource.\"ds1\".jdbc.max-size=50\n"
                        + "quarkus.datasource.ds1.jdbc.min-size=7\n# comment \\\n")
                .doesNotContain("\r", "forage.");
        assertThat(Files.readString(dir.resolve(MODULE))).isEqualTo("# datasource\n");
        assertThat(Files.readString(dir.resolve("other.properties"))).contains("jdbc:h2:mem:other");

        Files.writeString(dir.resolve(MODULE), DS1);
        QuarkusConfigExport.write(QuarkusConfigExport.translate(exported(dir)), dir);
        assertThat(dir.resolve(MODULE)).doesNotExist();
    }

    /** Quarkus reads every written value as translated: escapes, non-ASCII, a plain $; keys like {@code a\=b}. */
    @Test
    void writeRoundTripsSpecialCharacters() throws IOException {
        String password = " lead\\back\nnew\ttab#!=:é😀 a$b$}c$ trail ";
        Files.writeString(
                dir.resolve("application.properties"),
                DS1.replace(
                                "password=secret",
                                "password=\\ lead\\\\back\\nnew\\ttab#!=:\\u00e9\\ud83d\\ude00 a$b$}c$ trail ")
                        + "forage.ds1.jdbc.properties.a\\=b\\ c=\\ v\n");

        QuarkusConfigExport.Result result = QuarkusConfigExport.translate(exported(dir));
        QuarkusConfigExport.write(result, dir);

        Properties application = read(dir.resolve("application.properties"));
        var quarkus = new SmallRyeConfigBuilder()
                .addDefaultInterceptors()
                .withSources(new PropertiesConfigSource(application, "app", 250))
                .build();
        assertThat(quarkus.getConfigValue("quarkus.datasource.\"ds1\".password").getValue())
                .isEqualTo(password);
        assertThat(application)
                .containsEntry("quarkus.datasource.\"ds1\".jdbc.additional-jdbc-properties.\"a=b c\"", " v");
        assertThat(application.stringPropertyNames()).noneMatch(key -> key.startsWith("forage."));
        assertThat(Files.readString(dir.resolve("application.properties"), StandardCharsets.US_ASCII))
                .isEqualTo(Files.readString(dir.resolve("application.properties"), StandardCharsets.UTF_8));
    }

    /**
     * Quarkus sees the value the Forage runtime reads from the project (ISO-8859-1): Camel JBang copies the module file
     * byte for byte and rewrites application.properties as UTF-8 with the values it read. Other lines keep their bytes.
     */
    @ParameterizedTest
    @CsvSource({
        "latin1, 70e47373",
        "utf8, 70c3a47373",
        "escape, 705c75303065347373",
        "pair, 705c75643833645c75646530307373"
    })
    void writeKeepsTheValueTheRuntimeReads(String name, String hexValue) throws IOException {
        byte[] line = ("forage.ds1.jdbc.password="
                        + new String(HexFormat.of().parseHex(hexValue), StandardCharsets.ISO_8859_1) + "\n")
                .getBytes(StandardCharsets.ISO_8859_1);
        Properties runtime = new Properties();
        runtime.load(new ByteArrayInputStream(line));
        String value = runtime.getProperty("forage.ds1.jdbc.password");
        String other = "# démo\n" + DS1.replace("forage.ds1.jdbc.password=secret\n", "");

        // in the module file
        Files.write(dir.resolve(MODULE), concat(other.getBytes(StandardCharsets.ISO_8859_1), line));
        Files.writeString(dir.resolve("application.properties"), "camel.main.name=démo\n");
        QuarkusConfigExport.write(QuarkusConfigExport.translate(exported(dir)), dir);
        assertThat(read(dir.resolve("application.properties")))
                .containsEntry("quarkus.datasource.\"ds1\".password", value);
        assertThat(Files.readAllBytes(dir.resolve(MODULE))).isEqualTo("# démo\n".getBytes(StandardCharsets.ISO_8859_1));
        assertThat(Files.readString(dir.resolve("application.properties"))).startsWith("camel.main.name=démo\n");

        // in application.properties, as Camel JBang exports it
        Files.delete(dir.resolve(MODULE));
        Files.writeString(dir.resolve("application.properties"), DS1.replace("secret", value), StandardCharsets.UTF_8);
        QuarkusConfigExport.write(QuarkusConfigExport.translate(exported(dir)), dir);
        assertThat(read(dir.resolve("application.properties")))
                .containsEntry("quarkus.datasource.\"ds1\".password", value);
    }

    private static QuarkusConfigExport.Result translate(String application) {
        return QuarkusConfigExport.translate(project(application));
    }

    private static QuarkusConfigExport.Project project(String application) {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(application));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return new QuarkusConfigExport.Project(properties, Map.of());
    }

    private static QuarkusConfigExport.Project exported(Path dir) throws IOException {
        return QuarkusConfigExport.load(dir, QuarkusConfigExport.EXPORTED_APPLICATION);
    }

    /** As Quarkus reads application.properties: UTF-8. */
    static Properties read(Path file) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] all = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, all, a.length, b.length);
        return all;
    }
}
