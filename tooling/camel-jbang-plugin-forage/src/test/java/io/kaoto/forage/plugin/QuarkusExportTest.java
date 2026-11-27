package io.kaoto.forage.plugin;

import javax.tools.ToolProvider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;
import io.kaoto.forage.core.common.ForageModuleDescriptor;
import io.kaoto.forage.core.common.RuntimeType;
import io.kaoto.forage.core.util.config.Config;
import io.kaoto.forage.core.util.config.ConfigHelper;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.core.util.config.PropertyFileLocator;
import io.kaoto.forage.jdbc.common.JdbcModuleDescriptor;
import io.kaoto.forage.jms.common.JmsModuleDescriptor;
import io.smallrye.config.EnvConfigSource;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code camel export --runtime=quarkus}: Camel JBang writes the exported resources, then calls getDependencies, which
 * appends the quarkus.* keys. The exported app reads every value an export without translation reads.
 */
class QuarkusExportTest {

    private static final String JDBC_FILE = "forage-datasource-factory.properties";
    private static final String JMS_FILE = "forage-connectionfactory.properties";
    private static final String SKIP = "forage.quarkus.translation.skip";
    private static final String DS1 =
            """
            forage.ds1.jdbc.db.kind=postgresql
            forage.ds1.jdbc.url=jdbc:postgresql://localhost:5432/db
            forage.ds1.jdbc.username=test
            forage.ds1.jdbc.password=secret
            forage.ds1.jdbc.pool.min.size=4
            """;
    private static final String ARTEMIS = "forage.jms.kind=artemis\nforage.jms.broker.url=tcp://localhost:61616\n";

    @TempDir
    Path project;

    /** The export build directory. */
    @TempDir
    Path build;

    /** The exported resources. */
    Path resources;

    /** The exported resources of the export without translation. */
    @TempDir
    Path before;

    @BeforeEach
    void setUp() throws IOException {
        resources = Files.createDirectories(build.resolve("src/main/resources"));
        System.setProperty("forage.config.dir", project.toString());
        // the silent run of the export translated both modules
        System.setProperty(ForagePlugin.FRESH_RUN, "jdbc,jms");
    }

    @AfterEach
    void tearDown() {
        List.of(
                        "forage.config.dir",
                        ForagePlugin.FRESH_RUN,
                        ForagePlugin.EXPORT_PACKAGE,
                        SKIP,
                        "forage.home.dir",
                        "forage.ds1.jdbc.username")
                .forEach(System::clearProperty);
        ConfigStore.getInstance().reload();
    }

    /** Values from the exported files only (not system properties), written as overridable expressions. */
    @Test
    void translatesPlainNamedDataSource(@TempDir Path home) throws IOException {
        System.setProperty("forage.home.dir", home.toString());
        System.setProperty("forage.ds1.jdbc.username", "leaked");
        Files.writeString(resources.resolve(JDBC_FILE), DS1);

        QuarkusExport.Result result = QuarkusExport.translate(resources, new Properties());

        assertThat(result.modules()).containsExactly("jdbc");
        assertThat(result.properties())
                .containsEntry(
                        "quarkus.datasource.\"ds1\".db-kind",
                        "${FORAGE_DS1_JDBC_DB_KIND:${forage.ds1.jdbc.db.kind:postgresql}}")
                .containsEntry(
                        "quarkus.datasource.\"ds1\".username",
                        "${FORAGE_DS1_JDBC_USERNAME:${forage.ds1.jdbc.username:test}}")
                .containsEntry(
                        "quarkus.datasource.\"ds1\".jdbc.min-size",
                        "${FORAGE_DS1_JDBC_POOL_MIN_SIZE:${forage.ds1.jdbc.pool.min.size:4}}")
                .containsEntry(
                        "quarkus.datasource.\"ds1\".jdbc.max-size",
                        "${FORAGE_DS1_JDBC_POOL_MAX_SIZE:${forage.ds1.jdbc.pool.max.size:20}}")
                .containsEntry(
                        "quarkus.datasource.\"ds1\".jdbc.acquisition-timeout",
                        "${FORAGE_DS1_JDBC_POOL_ACQUISITION_TIMEOUT_SECONDS:${forage.ds1.jdbc.pool.acquisition.timeout.seconds:5}}S")
                .doesNotContainKey("quarkus.config.locations")
                .noneSatisfy((key, value) -> assertThat(key).contains("additional-jdbc-properties"));
        assertThat(result.notes())
                .singleElement()
                .asString()
                .contains(
                        "jdbc exported as quarkus.*",
                        JDBC_FILE + " not read at runtime",
                        "FORAGE_*",
                        "config/application.properties",
                        "FORAGE_QUARKUS_TRANSLATION_SKIP=true");
    }

    /** The exported app would not read a module file of the Forage home directory: the module stays forage.*. */
    @Test
    void keepsModuleOfAHomeDirectoryFileOnForage(@TempDir Path home) throws IOException {
        Files.writeString(home.resolve(JDBC_FILE), "forage.ds1.jdbc.properties.a=1\n");
        System.setProperty("forage.home.dir", home.toString());

        assertStaysForage(DS1, "read from the Forage home directory");
    }

    /** What needs the Forage runtime keeps the whole module on forage.* (";" = new line, "@": without DS1). */
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "forage.ds1.jdbc.transaction.enabled=true | forage.ds1.jdbc.transaction.enabled has no Quarkus",
                "forage.ds2.jdbc.db.kind=h2;forage.ds2.jdbc.aggregation.repository.name=r | has no Quarkus",
                "forage.ds1.jdbc.password={{env:DB_PASSWORD}} | cannot be written as a Quarkus expression",
                "forage.ds1.jdbc.url=x${x:dflt}y | cannot be written as a Quarkus expression",
                "forage.ds1.jdbc.password=a}b | cannot be written as a Quarkus expression",
                "forage.ds1.jdbc.password=:secret | cannot be written as a Quarkus expression",
                "forage.ds1.jdbc.properties.a\\:b=1 | cannot be written as a Quarkus expression",
                "%prod.forage.ds1.jdbc.url=jdbc:postgresql://prod/db | profile-specific",
                "forage.jdbc.url=jdbc:postgresql://localhost/db | default and named",
                "forage.ds1.jdbc.pool.idle.validation.timeout.minutes=9 | has no Quarkus",
                "forage.ds1.jdbc.db.kind=oracle | the kind of forage.ds1.jdbc.* has no Quarkus equivalent",
                "@forage.jdbc.url=jdbc:postgresql://localhost/db | forage.jdbc.db.kind is not set",
                "@forage.jms.kind=artemis;forage.jms.broker.url=x;forage.jms.artemis.call.timeout=1 | has no Quarkus",
                "@forage.jms.kind=artemis;forage.jms.broker.url=x;forage.jms.ssl.enabled=true | has no Quarkus",
                "@forage.jms.kind=artemis;forage.jms.broker.url=x;forage.jms.transaction.enabled=true | has no Quarkus",
                "@forage.mq1.jms.kind=artemis;forage.mq1.jms.broker.url=x | named configurations have no Quarkus",
                "@forage.jms.kind=ibmmq;forage.jms.broker.url=localhost(1414) | the kind of forage.jms.* has no Quarkus",
                "@forage.jms.kind=artemis;forage.jms.broker.url=${x} | cannot be written as a Quarkus expression"
            })
    void keepsModuleOnForage(String lines, String reason) throws IOException {
        assertStaysForage((lines.startsWith("@") ? lines.substring(1) : DS1 + lines).replace(";", "\n"), reason);
    }

    /** Quarkus reads a module file value as with the Forage Quarkus runtime ($$ is a $); empty as not set. */
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "forage.ds1.jdbc.properties.ssl= | quarkus.datasource.\"ds1\".jdbc.additional-jdbc-properties.\"ssl\"",
                "forage.ds1.jdbc.url=a$$b | quarkus.datasource.\"ds1\".jdbc.url",
                "forage.ds1.jdbc.url=ab$ | quarkus.datasource.\"ds1\".jdbc.url",
                "forage.ds1.jdbc.url=a:b\\\\c | quarkus.datasource.\"ds1\".jdbc.url",
                "forage.ds1.jdbc.url=a\\\\$b | quarkus.datasource.\"ds1\".jdbc.url",
                "forage.ds1.jdbc.password=pa$$word | quarkus.datasource.\"ds1\".password",
                "forage.ds1.jdbc.password=\\\\$x$ | quarkus.datasource.\"ds1\".password",
                "forage.ds1.jdbc.password=\\$x | quarkus.datasource.\"ds1\".password",
                "forage.ds1.jdbc.password=$ | quarkus.datasource.\"ds1\".password",
                "forage.ds1.jdbc.password=a$b | quarkus.datasource.\"ds1\".password",
                "forage.ds1.jdbc.url=ab\\\\ | quarkus.datasource.\"ds1\".jdbc.url",
                "forage.ds1.jdbc.properties.p=a\\\\\\\\$$b | quarkus.datasource.\"ds1\".jdbc.additional-jdbc-properties.\"p\"",
                "forage.ds1.jdbc.password= | quarkus.datasource.\"ds1\".password",
                "@forage.jms.kind=artemis;forage.jms.broker.url=x;forage.jms.password= | quarkus.artemis.password"
            })
    void readsTheValueOfTheForageKey(String lines, String key) throws IOException {
        String file = lines.startsWith("@") ? JMS_FILE : JDBC_FILE;
        String content = (lines.startsWith("@") ? lines.substring(1) : DS1 + lines).replace(";", "\n");
        // the required password may be empty, not missing
        Files.writeString(resources.resolve(file), content);

        QuarkusExport.export(resources);

        SmallRyeConfig quarkus = quarkus(resources, Map.of(), Map.of());
        String last = lines.substring(lines.lastIndexOf(';') + 1);
        String forage = read(resources.resolve(file)).getProperty(last.substring(0, last.indexOf('=')));
        assertThat(quarkus.getOptionalValue(key, String.class).orElse("")).isEqualTo(forageQuarkusReads(forage));
    }

    /**
     * What the exported app of {@code camel export} without translation reads for a module file value (checked with a
     * real exported Quarkus app): the Forage Quarkus runtime hands the value to Quarkus, which expands it.
     */
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"pa$$word | pa$word", "\\$x | $x", "$ | $", "a$b | a$b"})
    void readsAModuleFileValueAsTheForageQuarkusRuntime(String value, String read) throws IOException {
        Files.writeString(resources.resolve(JDBC_FILE), DS1.replace("=secret", "=" + value));

        QuarkusExport.export(resources);

        assertThat(quarkus(resources, Map.of(), Map.of())
                        .getConfigValue("quarkus.datasource.\"ds1\".password")
                        .getValue())
                .isEqualTo(read)
                .isEqualTo(
                        forageQuarkusReads(read(resources.resolve(JDBC_FILE)).getProperty("forage.ds1.jdbc.password")));
    }

    /** A forage.* key in a shipped application-<profile>.properties keeps the module on forage.*. */
    @Test
    void keepsModuleOfAProfileFileOnForage() throws IOException {
        Files.writeString(resources.resolve("application-dev.properties"), "forage.ds1.jdbc.url=jdbc:h2:mem:dev\n");

        assertStaysForage(DS1, "profile-specific values in application-dev.properties");
    }

    /** A non-ASCII module file: Quarkus reads each value as Forage does (ISO-8859-1). */
    @ParameterizedTest
    @CsvSource({"70e47373, p\u00e4ss", "70c3a47373, p\u00c3\u00a4ss"})
    void translatesANonAsciiModuleFileAsForageReadsIt(String hexValue, String password) throws IOException {
        Files.write(
                resources.resolve(JDBC_FILE),
                concat(
                        DS1.replace("forage.ds1.jdbc.password=secret\n", "").getBytes(StandardCharsets.ISO_8859_1),
                        concat(
                                "forage.ds1.jdbc.password=".getBytes(StandardCharsets.ISO_8859_1),
                                HexFormat.of().parseHex(hexValue))));

        assertThat(QuarkusExport.export(resources).modules()).containsExactly("jdbc");

        assertThat(quarkus(resources, Map.of(), Map.of())
                        .getConfigValue("quarkus.datasource.\"ds1\".password")
                        .getValue())
                .isEqualTo(password);
    }

    /** A key of a bean file (forage-jms-artemis.properties) without Quarkus equivalent keeps the module forage.*. */
    @Test
    void keepsModuleOfABeanFileKeyOnForage() throws IOException {
        Files.writeString(resources.resolve(JMS_FILE), ARTEMIS);
        Files.writeString(resources.resolve("forage-jms-artemis.properties"), "forage.jms.artemis.call.timeout=1\n");

        assertStaysForage(null, "forage.jms.artemis.call.timeout has no Quarkus equivalent");
    }

    /** A value of application.properties: Quarkus reads it as in its Forage key, as the Forage Quarkus runtime does. */
    @Test
    void readsAnApplicationValueAsQuarkusReadsTheForageKey() throws IOException {
        Files.writeString(resources.resolve("application.properties"), DS1.replace("=secret", "=pa$$word"));

        QuarkusExport.export(resources);

        SmallRyeConfig quarkus = quarkus(resources, Map.of(), Map.of());
        assertThat(quarkus.getConfigValue("quarkus.datasource.\"ds1\".password").getValue())
                .isEqualTo(quarkus.getConfigValue("forage.ds1.jdbc.password").getValue())
                .isEqualTo("pa$word");
    }

    /** A datasource name with a dash: a quoted Quarkus name, overridden by the FORAGE_* name Forage reads. */
    @Test
    void translatesADataSourceNameWithADash() throws IOException {
        Files.writeString(resources.resolve(JDBC_FILE), DS1.replace("forage.ds1.", "forage.my-ds."));

        assertThat(QuarkusExport.export(resources).modules()).containsExactly("jdbc");

        assertThat(quarkus(resources, Map.of("FORAGE_MY-DS_JDBC_URL", "jdbc:postgresql://env/db"), Map.of())
                        .getConfigValue("quarkus.datasource.\"my-ds\".jdbc.url")
                        .getValue())
                .isEqualTo("jdbc:postgresql://env/db");
        assertThat(quarkus(resources, Map.of(), Map.of())
                        .getConfigValue("quarkus.datasource.\"my-ds\".username")
                        .getValue())
                .isEqualTo("test");
    }

    /** FORAGE_* wins over -Dforage.*, as in Forage. */
    @Test
    void environmentWinsOverSystemProperty() throws IOException {
        Files.writeString(resources.resolve(JDBC_FILE), DS1);

        QuarkusExport.export(resources);

        assertThat(quarkus(
                                resources,
                                Map.of("FORAGE_DS1_JDBC_URL", "jdbc:postgresql://env/db"),
                                Map.of("forage.ds1.jdbc.url", "jdbc:postgresql://sys/db"))
                        .getConfigValue("quarkus.datasource.\"ds1\".jdbc.url")
                        .getValue())
                .isEqualTo("jdbc:postgresql://env/db");
    }

    /**
     * The module files are not configuration of the exported app: nothing lists them in quarkus.config.locations, so
     * their non-Forage keys have no effect.
     */
    @Test
    void doesNotLoadTheModuleFilesAsConfiguration() throws IOException {
        Files.writeString(resources.resolve(JDBC_FILE), DS1);
        Files.writeString(resources.resolve("application.properties"), "camel.main.name=app\n");

        QuarkusExport.export(resources);

        assertThat(read(resources.resolve("application.properties")))
                .doesNotContainKey("quarkus.config.locations")
                .containsEntry("camel.main.name", "app");
    }

    /** As Forage, application.properties wins; a key set differently in both files keeps the module forage.*. */
    @Test
    void keepsAKeyOfBothFilesWithDifferentValuesOnForage() throws IOException {
        Files.writeString(resources.resolve(JDBC_FILE), DS1);

        assertStaysForage(
                "forage.ds1.jdbc.url=jdbc:postgresql://other/db\n",
                "forage.ds1.jdbc.url differs between " + JDBC_FILE + " and application.properties");
    }

    /** Only the exported resources: not a file of the project directory, not a non-Forage file. */
    @Test
    void readsOnlyTheExportedModuleFiles() throws IOException {
        Files.writeString(project.resolve(JDBC_FILE), DS1 + "forage.ds1.jdbc.transaction.enabled=true\n");
        Files.writeString(resources.resolve("other.properties"), "forage.ds1.jdbc.transaction.enabled=true\n");
        Files.writeString(resources.resolve("application.properties"), DS1);

        assertThat(QuarkusExport.export(resources).modules()).containsExactly("jdbc");
    }

    /** User keys stay (also quoted differently), the Forage keys stay, quarkus.config.locations is kept. */
    @Test
    void writeKeepsUserKeysAndForageKeys() throws IOException {
        String application =
                """
                camel.main.name=demo
                quarkus.datasource."ds1".jdbc.max-size=50
                quarkus.datasource.ds1.jdbc.min-size=7
                quarkus.config.locations=my.properties
                forage.ds1.jdbc.url=jdbc:h2:\\
                  mem:fromapp
                # comment \\
                """
                        .replace("\n", "\r\n");
        Files.writeString(resources.resolve("application.properties"), application);
        Files.writeString(
                resources.resolve(JDBC_FILE),
                DS1.replace("forage.ds1.jdbc.url=jdbc:postgresql://localhost:5432/db\n", ""));

        QuarkusExport.export(resources);

        assertThat(Files.readString(resources.resolve("application.properties")))
                .startsWith(application);
        assertThat(Files.readString(resources.resolve(JDBC_FILE)))
                .isEqualTo(DS1.replace("forage.ds1.jdbc.url=jdbc:postgresql://localhost:5432/db\n", ""));
        SmallRyeConfig quarkus = quarkus(resources, Map.of(), Map.of());
        assertThat(quarkus.getConfigValue("quarkus.datasource.\"ds1\".jdbc.max-size")
                        .getValue())
                .isEqualTo("50");
        assertThat(quarkus.getConfigValue("quarkus.datasource.ds1.jdbc.min-size")
                        .getValue())
                .isEqualTo("7");
        assertThat(Files.readString(resources.resolve("application.properties")))
                .containsOnlyOnce("quarkus.datasource.\"ds1\".jdbc.max-size");
        assertThat(read(resources.resolve("application.properties")))
                .containsEntry("quarkus.datasource.\"ds1\".jdbc.min-size", "7")
                .containsEntry("quarkus.config.locations", "my.properties");
        assertThat(quarkus.getConfigValue("quarkus.datasource.\"ds1\".jdbc.url").getValue())
                .isEqualTo("jdbc:h2:mem:fromapp");
    }

    /** Quarkus reads every written value as Forage does: escapes, non-ASCII, a plain $; keys like {@code a\=b}. */
    @Test
    void writeRoundTripsSpecialCharacters() throws IOException {
        String password = " lead\\back\nnew\ttab#!=:é😀 a$b$c$ trail ";
        Files.writeString(
                resources.resolve("application.properties"),
                DS1.replace(
                                "password=secret",
                                "password=\\ lead\\\\back\\nnew\\ttab#!=:\\u00e9\\ud83d\\ude00 a$b$c$ trail ")
                        + "forage.ds1.jdbc.properties.a\\=b\\ c=\\ v\n");

        QuarkusExport.export(resources);

        SmallRyeConfig quarkus = quarkus(resources, Map.of(), Map.of());
        assertThat(quarkus.getConfigValue("quarkus.datasource.\"ds1\".password").getValue())
                .isEqualTo(password);
        assertThat(quarkus.getConfigValue("quarkus.datasource.\"ds1\".jdbc.additional-jdbc-properties.\"a=b c\"")
                        .getValue())
                .isEqualTo(" v");
        assertThat(Files.readString(resources.resolve("application.properties"), StandardCharsets.UTF_8))
                .isEqualTo(new String(
                        Files.readAllBytes(resources.resolve("application.properties")), StandardCharsets.UTF_8));
    }

    /** pool.enabled=false: no quarkus-pooled-jms, no Forage artifact, the pool keys as the Forage translation. */
    @Test
    void translatesAnUnpooledConnectionFactory() throws IOException {
        Files.writeString(project.resolve(JMS_FILE), ARTEMIS + "forage.jms.pool.enabled=false\n");
        Files.copy(project.resolve(JMS_FILE), resources.resolve(JMS_FILE));

        Set<String> dependencies = ForagePlugin.dependencies(RuntimeType.QUARKUS, resources);

        assertThat(dependencies).noneMatch(d -> d.contains("quarkus-pooled-jms") || d.contains("io.kaoto.forage"));
        assertThat(dependencies).anyMatch(d -> d.contains("quarkus-artemis-jms"));
        assertThat(quarkus(resources, Map.of(), Map.of())
                        .getConfigValue("quarkus.artemis.url")
                        .getValue())
                .isEqualTo("tcp://localhost:61616");
    }

    /**
     * The pool keys of docs/jms (pool.connection.timeout.millis, no effect on any runtime): translated, the timeout
     * left untranslated with a note, no Forage artifact.
     */
    @Test
    void translatesAConnectionFactoryWithAnIgnoredKey() throws IOException {
        Files.writeString(
                project.resolve(JMS_FILE),
                ARTEMIS + "forage.jms.pool.enabled=true\nforage.jms.pool.max.connections=5\n"
                        + "forage.jms.pool.connection.timeout.millis=30000\n");
        Files.copy(project.resolve(JMS_FILE), resources.resolve(JMS_FILE));

        QuarkusExport.Result result = QuarkusExport.translate(resources, new Properties());

        assertThat(result.modules()).containsExactly("jms");
        assertThat(result.properties())
                .containsEntry(
                        "quarkus.pooled-jms.max-connections",
                        "${FORAGE_JMS_POOL_MAX_CONNECTIONS:${forage.jms.pool.max.connections:5}}")
                .noneSatisfy((key, value) -> assertThat(value).contains("connection.timeout"));
        assertThat(result.notes())
                .anySatisfy(note -> assertThat(note).contains("jms exported as quarkus.*"))
                .anySatisfy(note -> assertThat(note)
                        .contains("forage.jms.pool.connection.timeout.millis not translated, no effect on Quarkus"));
        assertThat(ForagePlugin.dependencies(RuntimeType.QUARKUS, resources))
                .noneMatch(d -> d.contains("io.kaoto.forage"))
                .anyMatch(d -> d.contains("quarkus-pooled-jms"));
    }

    /** The empty H2 password of the JDBC test plan: translated, Quarkus reads it as not set. */
    @Test
    void translatesAnEmptyPassword() throws IOException {
        Files.writeString(
                resources.resolve(JDBC_FILE),
                "forage.ds1.jdbc.db.kind=h2\nforage.ds1.jdbc.url=jdbc:h2:mem:db\nforage.ds1.jdbc.username=sa\n"
                        + "forage.ds1.jdbc.password=\n");

        assertThat(QuarkusExport.export(resources).modules()).containsExactly("jdbc");

        SmallRyeConfig quarkus = quarkus(resources, Map.of(), Map.of());
        assertThat(quarkus.getOptionalValue("quarkus.datasource.\"ds1\".password", String.class))
                .isEmpty();
        assertThat(quarkus.getConfigValue("quarkus.datasource.\"ds1\".jdbc.url").getValue())
                .isEqualTo("jdbc:h2:mem:db");
    }

    /**
     * The silent run of the export: Camel JBang writes its dependencies into the run data the export reads back, so a
     * module the export translates has no Forage artifact there either. It reads the project, not a work directory left
     * over from an earlier export, and writes nothing.
     */
    @Test
    void silentRunLeavesOutTheForageArtifactsOfTranslatedModules() throws IOException {
        Files.writeString(project.resolve(JDBC_FILE), DS1);
        Files.writeString(project.resolve(JMS_FILE), ARTEMIS);
        Files.writeString(resources.resolve(JDBC_FILE), "forage.ds1.jdbc.transaction.enabled=true\n");

        ForagePlugin.runStarted();
        Set<String> run = ForagePlugin.dependencies(RuntimeType.QUARKUS, resources);

        assertThat(run)
                .noneMatch(d -> d.contains("io.kaoto.forage"))
                .anyMatch(d -> d.contains("quarkus-jdbc-postgresql"))
                .anyMatch(d -> d.contains("quarkus-artemis-jms"));
        assertThat(resources.resolve("application.properties")).doesNotExist();
        assertThat(System.getProperty(ForagePlugin.FRESH_RUN)).isEqualTo("jdbc,jms");
    }

    /** All or nothing per module: the silent run keeps every Forage artifact of a module the export keeps forage.*. */
    @Test
    void silentRunKeepsTheForageArtifactsOfUntranslatedModules() throws IOException {
        Files.writeString(project.resolve(JDBC_FILE), DS1 + "forage.ds1.jdbc.transaction.enabled=true\n");
        Files.writeString(project.resolve(JMS_FILE), ARTEMIS);
        // a left-over translatable JDBC file in the work directory is not read by the run
        Files.writeString(resources.resolve(JDBC_FILE), DS1);
        Files.writeString(resources.resolve(JMS_FILE), ARTEMIS);

        ForagePlugin.runStarted();
        Set<String> run = ForagePlugin.dependencies(RuntimeType.QUARKUS, resources);
        assertThat(System.getProperty(ForagePlugin.FRESH_RUN)).isEqualTo("jms");
        Set<String> exported = ForagePlugin.dependencies(RuntimeType.QUARKUS, resources);

        System.setProperty(SKIP, "true");
        Set<String> jdbcForage = new TreeSet<>(ForagePlugin.dependencies(RuntimeType.QUARKUS, resources));
        jdbcForage.removeIf(d -> !d.startsWith("mvn:io.kaoto.forage:") || d.contains("jms"));
        assertThat(jdbcForage).isNotEmpty();
        assertThat(run).containsAll(jdbcForage).noneMatch(d -> d.contains("forage-jms"));
        // the export, reading the left-over translatable JDBC file, translates only what the run did
        assertThat(exported).containsAll(jdbcForage);
        assertThat(Files.readString(resources.resolve("application.properties")))
                .contains("quarkus.artemis.url")
                .doesNotContain("quarkus.datasource");
    }

    /**
     * Modules without an exact Quarkus equivalent declare no Quarkus metadata: the export keeps them forage.* with
     * their Forage artifacts.
     */
    @Test
    void keepsModulesWithoutQuarkusMetadataOnForage() throws IOException {
        assertThat(QuarkusExport.table(ForageCatalogReader.getInstance())).containsOnlyKeys("jdbc", "jms");
        Map<String, String> files = Map.of(
                "forage-agent-factory.properties",
                "forage.agent.model.kind=ollama\nforage.agent.model.name=granite4:3b\n",
                "forage-cxf.properties",
                "forage.cxf.address=http://localhost:8080/hello\n",
                "forage-influxdb.properties",
                "forage.influxdb.url=http://localhost:8086\n",
                "forage-spring-rabbitmq.properties",
                "forage.rabbitmq.host=localhost\n",
                "forage-security-tls.properties",
                "forage.tls.keystore.path=keystore.p12\nforage.tls.keystore.type=PKCS12\n");
        for (var file : files.entrySet()) {
            Files.writeString(project.resolve(file.getKey()), file.getValue());
            Files.writeString(resources.resolve(file.getKey()), file.getValue());
        }

        ForagePlugin.runStarted();
        Set<String> run = ForagePlugin.dependencies(RuntimeType.QUARKUS, resources);
        assertThat(System.getProperty(ForagePlugin.FRESH_RUN)).isEmpty();
        Set<String> exported = ForagePlugin.dependencies(RuntimeType.QUARKUS, resources);
        System.setProperty(SKIP, "true");
        Set<String> untranslated = ForagePlugin.dependencies(RuntimeType.QUARKUS, resources);

        assertThat(resources.resolve("application.properties")).doesNotExist();
        assertThat(run).isEqualTo(untranslated);
        assertThat(exported).isEqualTo(untranslated);
        for (String module : List.of("agent", "cxf", "influxdb", "rabbitmq", "tls")) {
            assertThat(untranslated).anyMatch(d -> d.startsWith("mvn:io.kaoto.forage:") && d.contains(module));
        }
    }

    /** A configured module the catalog lists in quarkusUnsupported: the note gives its reason, others get none. */
    @Test
    void notesTheReasonOfModulesWithoutQuarkusEquivalent() throws IOException {
        Map<String, String> reasons = ForageCatalogReader.getInstance().getQuarkusUnsupported();
        Files.writeString(
                resources.resolve("forage-cxf.properties"), "forage.cxf.address=http://localhost:8080/hello\n");
        Properties application = new Properties();
        application.setProperty("forage.admin.spring.security.roles", "admin");

        QuarkusExport.Result result = QuarkusExport.translate(resources, application);

        assertThat(result.modules()).isEmpty();
        assertThat(result.properties()).isEmpty();
        assertThat(result.notes())
                .containsExactly(
                        "Forage cxf stays as forage.* configuration: quarkus-cxf configures JAX-WS services,"
                                + " not Camel CxfEndpoint beans.",
                        "Forage spring.security stays as forage.* configuration: " + reasons.get("spring.security")
                                + ".");
    }

    /** Reused run data (no silent run in this JVM), the off switch and other runtimes: export as before. */
    @ParameterizedTest
    @CsvSource({"reused, QUARKUS", "skip, QUARKUS", "none, MAIN", "none, SPRING_BOOT"})
    void exportsAsBefore(String mode, RuntimeType runtime) throws IOException {
        Files.writeString(project.resolve(JDBC_FILE), DS1);
        Files.writeString(resources.resolve("application.properties"), DS1);
        if (mode.equals("reused")) {
            System.clearProperty(ForagePlugin.FRESH_RUN);
        } else if (mode.equals("skip")) {
            System.setProperty(SKIP, "true");
        }

        assertThat(ForagePlugin.dependencies(runtime, resources)).anyMatch(d -> d.contains("io.kaoto.forage"));
        assertThat(Files.readString(resources.resolve("application.properties")))
                .isEqualTo(DS1);
    }

    /**
     * An export reusing the run data of an earlier export (no files, another directory): the run data may lack the
     * Forage artifacts, so they come from the exported resources.
     */
    @Test
    void reusedRunDataGetsTheForageArtifactsOfTheExportedResources(@TempDir Path elsewhere) throws IOException {
        System.setProperty("forage.config.dir", elsewhere.toString());
        System.clearProperty(ForagePlugin.FRESH_RUN);
        Files.writeString(resources.resolve(JDBC_FILE), DS1);

        assertThat(ForagePlugin.dependencies(RuntimeType.QUARKUS, resources))
                .anyMatch(d -> d.startsWith("mvn:io.kaoto.forage:") && d.contains("jdbc"));
        assertThat(resources.resolve("application.properties")).doesNotExist();
    }

    /** Reused run data with the off switch: no note. */
    @Test
    void reusedRunDataWithTheOffSwitchPrintsNoNote() throws IOException {
        System.clearProperty(ForagePlugin.FRESH_RUN);
        System.setProperty(SKIP, "true");
        Files.writeString(resources.resolve(JDBC_FILE), DS1);
        PrintStream out = System.out;
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        System.setOut(new PrintStream(printed, true, StandardCharsets.UTF_8));
        try {
            assertThat(ForagePlugin.dependencies(RuntimeType.QUARKUS, resources))
                    .anyMatch(d -> d.startsWith("mvn:io.kaoto.forage:"));
        } finally {
            System.setOut(out);
        }
        assertThat(printed.toString(StandardCharsets.UTF_8)).doesNotContain("stays as forage.*");
    }

    /**
     * A user quarkus.* key, quoted or not, wins; the quoted key is written with the user value, else Quarkus misses the
     * datasource (named or default).
     */
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "ds1 | quarkus.datasource.ds1.jdbc.url",
                "ds1 | quarkus.datasource.\"ds1\".jdbc.url",
                "dataSource | quarkus.datasource.dataSource.jdbc.url",
                "dataSource | quarkus.datasource.\"dataSource\".jdbc.url"
            })
    void userQuarkusKeyWins(String name, String userKey) throws IOException {
        String module = name.equals("ds1") ? DS1 : DS1.replace("forage.ds1.", "forage.");
        String application = userKey + "=jdbc:h2:mem:user\n";
        Files.writeString(resources.resolve(JDBC_FILE), module);
        Files.writeString(resources.resolve("application.properties"), application);

        assertThat(QuarkusExport.export(resources).modules()).containsExactly("jdbc");

        String quoted = "quarkus.datasource.\"" + name + "\".jdbc.url";
        assertThat(Files.readString(resources.resolve("application.properties")))
                .startsWith(application)
                .containsOnlyOnce(quoted);
        assertThat(quarkus(resources, Map.of(), Map.of()).getConfigValue(quoted).getValue())
                .isEqualTo("jdbc:h2:mem:user");
    }

    /** Several exports in one JVM (camel shell): only the export right after its silent run is fresh. */
    @Test
    void freshRunLastsOneExport() throws IOException {
        Files.writeString(project.resolve(JDBC_FILE), DS1);
        Files.writeString(resources.resolve(JDBC_FILE), DS1);

        ForagePlugin.runStarted();
        ForagePlugin.dependencies(RuntimeType.QUARKUS, resources);
        assertThat(System.getProperty(ForagePlugin.FRESH_RUN)).isEqualTo("jdbc");
        ForagePlugin.dependencies(RuntimeType.QUARKUS, resources);
        assertThat(resources.resolve("application.properties")).content().contains("quarkus.datasource");
        assertThat(System.getProperty(ForagePlugin.FRESH_RUN)).isNull();

        // the next export reuses the run data
        Files.delete(resources.resolve("application.properties"));
        assertThat(ForagePlugin.dependencies(RuntimeType.QUARKUS, resources))
                .anyMatch(d -> d.startsWith("mvn:io.kaoto.forage:") && d.contains("jdbc"));
        assertThat(resources.resolve("application.properties")).doesNotExist();

        // a camel run leaves no fresh run behind
        ForagePlugin.runStarted();
        ForagePlugin.dependencies(RuntimeType.MAIN, resources);
        assertThat(System.getProperty(ForagePlugin.FRESH_RUN)).isNull();
    }

    /** A translated module: the export adds, in the app package, a bean warning about module files it does not read. */
    @Test
    void exportAddsTheStartupWarning() throws IOException {
        Files.writeString(project.resolve(JDBC_FILE), DS1);
        Files.writeString(resources.resolve(JDBC_FILE), DS1);
        System.setProperty(ForagePlugin.EXPORT_PACKAGE, "org.acme");

        ForagePlugin.dependencies(RuntimeType.QUARKUS, resources);

        assertThat(build.resolve("src/main/java/org/acme/" + QuarkusExport.WARNING_CLASS + ".java"))
                .content()
                .contains("package org.acme;", "List.of(\"" + JDBC_FILE + "\")");
        assertThat(System.getProperty(ForagePlugin.EXPORT_PACKAGE)).isNull();
    }

    /** The startup warning compiles, and warns once only when a module file is where Forage would read it. */
    @Test
    void startupWarningNamesTheUnreadFile(
            @TempDir Path src, @TempDir Path classes, @TempDir Path configDir, @TempDir Path home) throws Exception {
        QuarkusExport.writeWarning(src, "org.acme", new TreeSet<>(Set.of(JDBC_FILE, JMS_FILE)));
        // the Quarkus API it uses
        Map<String, String> stubs = Map.of(
                "io/quarkus/runtime/StartupEvent.java",
                "package io.quarkus.runtime; public class StartupEvent {}",
                "jakarta/enterprise/context/ApplicationScoped.java",
                "package jakarta.enterprise.context; public @interface ApplicationScoped {}",
                "jakarta/enterprise/event/Observes.java",
                "package jakarta.enterprise.event; public @interface Observes {}",
                "org/jboss/logging/Logger.java",
                """
                package org.jboss.logging;
                public class Logger {
                    public static final java.util.List<String> WARNINGS = new java.util.ArrayList<>();
                    public static Logger getLogger(Class<?> type) { return new Logger(); }
                    public void warnf(String format, Object... params) { WARNINGS.add(String.format(format, params)); }
                }
                """);
        for (var stub : stubs.entrySet()) {
            Files.createDirectories(src.resolve(stub.getKey()).getParent());
            Files.writeString(src.resolve(stub.getKey()), stub.getValue());
        }
        List<String> args = new ArrayList<>(List.of("-d", classes.toString(), "-cp", classes.toString()));
        try (Stream<Path> files = Files.walk(src)) {
            files.filter(f -> f.toString().endsWith(".java"))
                    .map(Path::toString)
                    .forEach(args::add);
        }
        assertThat(ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)))
                .isZero();
        System.setProperty("forage.config.dir", configDir.toString());
        System.setProperty("forage.home.dir", home.toString());

        try (URLClassLoader loader =
                new URLClassLoader(new URL[] {classes.toUri().toURL()}, null)) {
            Class<?> warning = loader.loadClass("org.acme." + QuarkusExport.WARNING_CLASS);
            Method onStart = warning.getDeclaredMethod("onStart", loader.loadClass("io.quarkus.runtime.StartupEvent"));
            onStart.setAccessible(true);
            Object bean = warning.getDeclaredConstructor().newInstance();
            List<?> warnings = (List<?>) loader.loadClass("org.jboss.logging.Logger")
                    .getField("WARNINGS")
                    .get(null);

            onStart.invoke(bean, (Object) null);
            assertThat(warnings).isEmpty();

            Files.writeString(configDir.resolve(JDBC_FILE), DS1);
            onStart.invoke(bean, (Object) null);
            assertThat(warnings)
                    .singleElement()
                    .asString()
                    .contains(
                            configDir.resolve(JDBC_FILE).toString(),
                            "not read",
                            "config/application.properties",
                            "FORAGE_QUARKUS_TRANSLATION_SKIP=true")
                    .doesNotContain(JMS_FILE);
        }
    }

    /**
     * Parity with the export without translation (the off switch): never fails where it works, loses no non-Forage
     * dependency and adds none, every quarkus.* key reads what the Forage runtime translation gives, and a FORAGE_* /
     * -Dforage.* override still wins. Columns: module file and application.properties of the project (";" = new line,
     * "@": the JMS module file), translated, the override ({@code env:} or {@code sys:}), the Quarkus key and the value it
     * then reads.
     */
    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "default h2 | jdbc.db.kind=h2;jdbc.url=jdbc:h2:mem:db;jdbc.username=sa;jdbc.password=p | | true"
                        + " | env:FORAGE_JDBC_URL=jdbc:h2:mem:prod | quarkus.datasource.\"dataSource\".jdbc.url | jdbc:h2:mem:prod",
                "named postgresql | ds1.jdbc.db.kind=postgresql;ds1.jdbc.username=u;ds1.jdbc.url=jdbc:postgresql://h/db;ds1.jdbc.password=p"
                        + " | | true | sys:forage.ds1.jdbc.pool.acquisition.timeout.seconds=30"
                        + " | quarkus.datasource.\"ds1\".jdbc.acquisition-timeout | 30S",
                "mysql map key | ds1.jdbc.db.kind=mysql;ds1.jdbc.username=u;ds1.jdbc.password=p | ds1.jdbc.url=jdbc:mysql://h/db;ds1.jdbc.properties.ssl=true | true"
                        + " | env:FORAGE_DS1_JDBC_PROPERTIES_SSL=false"
                        + " | quarkus.datasource.\"ds1\".jdbc.additional-jdbc-properties.\"ssl\" | false",
                "mariadb empty password | ds1.jdbc.username=u;ds1.jdbc.password= | ds1.jdbc.db.kind=mariadb;ds1.jdbc.url=jdbc:mariadb://h/db | true"
                        + " | env:FORAGE_DS1_JDBC_PASSWORD=pw | quarkus.datasource.\"ds1\".password | pw",
                "oracle | ds1.jdbc.db.kind=oracle;ds1.jdbc.username=u;ds1.jdbc.password=p;ds1.jdbc.url=jdbc:oracle:thin:@h:1521/db | | false | | |",
                "db2 | ds1.jdbc.db.kind=db2;ds1.jdbc.username=u;ds1.jdbc.password=p;ds1.jdbc.url=jdbc:db2://h/db | | false | | |",
                "mssql | ds1.jdbc.db.kind=mssql;ds1.jdbc.username=u;ds1.jdbc.password=p;ds1.jdbc.url=jdbc:sqlserver://h | | false | | |",
                "hsqldb | ds1.jdbc.db.kind=hsqldb;ds1.jdbc.username=u;ds1.jdbc.password=p;ds1.jdbc.url=jdbc:hsqldb:mem:db | | false | | |",
                "transaction | ds1.jdbc.db.kind=h2;ds1.jdbc.username=u;ds1.jdbc.password=p;ds1.jdbc.url=jdbc:h2:mem:db;ds1.jdbc.transaction.enabled=true | | false | | |",
                "value with } | ds1.jdbc.db.kind=h2;ds1.jdbc.username=u;ds1.jdbc.url=jdbc:h2:mem:db;ds1.jdbc.password=a}b | | false | | |",
                // Camel JBang writes it unescaped: both exports read 'ab'
                "backslash in application.properties | ds1.jdbc.db.kind=h2;ds1.jdbc.username=u;ds1.jdbc.url=jdbc:h2:mem:db"
                        + " | ds1.jdbc.password=a\\\\b | true | sys:forage.ds1.jdbc.password=x | quarkus.datasource.\"ds1\".password | x",
                "backslash in module file | ds1.jdbc.db.kind=h2;ds1.jdbc.username=u;ds1.jdbc.url=jdbc:h2:mem:db;ds1.jdbc.password=a\\\\b"
                        + " | | true | env:FORAGE_DS1_JDBC_USERNAME=v | quarkus.datasource.\"ds1\".username | v",
                "artemis | @jms.kind=artemis;jms.broker.url=tcp://h:61616 | | true"
                        + " | env:FORAGE_JMS_USERNAME=u | quarkus.artemis.username | u",
                "artemis pooled | @jms.kind=artemis;jms.broker.url=tcp://h:61616;jms.pool.enabled=true | | true"
                        + " | sys:forage.jms.broker.url=tcp://prod:61616 | quarkus.artemis.url | tcp://prod:61616",
                "artemis ssl | @jms.kind=artemis;jms.broker.url=tcp://h:61616;jms.ssl.enabled=true | | false | | |",
                "named artemis | @mq1.jms.kind=artemis;mq1.jms.broker.url=tcp://h:61616 | | false | | |",
                "ibmmq | @jms.kind=ibmmq;jms.broker.url=localhost(1414) | | false | | |"
            })
    void exportsAsWithoutTranslation(
            String name,
            String moduleFile,
            String application,
            boolean translated,
            String override,
            String quarkusKey,
            String overridden)
            throws IOException {
        String file = moduleFile.startsWith("@") ? JMS_FILE : JDBC_FILE;
        Files.writeString(project.resolve(file), forage(moduleFile.replaceFirst("^@", "")));
        Files.writeString(project.resolve("application.properties"), forage(application));

        System.setProperty(SKIP, "true");
        camelExport();
        Set<String> withoutTranslation = ForagePlugin.dependencies(RuntimeType.QUARKUS, resources);
        assertSameAsBefore();
        System.clearProperty(SKIP);
        // another export with the same silent run
        System.setProperty(ForagePlugin.FRESH_RUN, "jdbc,jms");
        Set<String> withTranslation = ForagePlugin.dependencies(RuntimeType.QUARKUS, resources);
        assertThat(build.resolve("src/main/java"))
                .as("startup warning")
                .matches(dir -> Files.exists(dir) == translated);

        Set<String> nonForage = new TreeSet<>(withoutTranslation);
        nonForage.removeIf(d -> d.startsWith("mvn:io.kaoto.forage:"));
        assertThat(withTranslation).containsAll(nonForage).isSubsetOf(withoutTranslation);
        if (!translated) {
            assertSameAsBefore();
            assertThat(withTranslation).isEqualTo(withoutTranslation);
            return;
        }
        assertThat(withTranslation).noneMatch(d -> d.startsWith("mvn:io.kaoto.forage:"));
        Map<String, String> forage = forageTranslation(before);
        // the app exported without translation: the Forage Quarkus runtime's source below application.properties
        SmallRyeConfig withoutTranslationReads = new SmallRyeConfigBuilder()
                .addDefaultInterceptors()
                .withSources(new PropertiesConfigSource(forage, "forage", 240))
                .withSources(new PropertiesConfigSource(read(before.resolve("application.properties")), "app", 250))
                .build();
        SmallRyeConfig quarkus = quarkus(resources, Map.of(), Map.of());
        Set<String> keys = new TreeSet<>(forage.keySet());
        read(resources.resolve("application.properties")).stringPropertyNames().stream()
                .filter(key -> key.startsWith("quarkus."))
                .forEach(keys::add);
        for (String key : keys) {
            // as Quarkus reads it: empty is not set
            assertThat(quarkus.getOptionalValue(key, String.class).orElse(null))
                    .as(key)
                    .isEqualTo(withoutTranslationReads
                            .getOptionalValue(key, String.class)
                            .orElse(null));
        }

        String[] setting = override.substring(4).split("=", 2);
        Map<String, String> value = Map.of(setting[0], setting[1]);
        boolean env = override.startsWith("env:");
        assertThat(quarkus(resources, env ? value : Map.of(), env ? Map.of() : value)
                        .getConfigValue(quarkusKey)
                        .getValue())
                .isEqualTo(overridden);
    }

    private void assertStaysForage(String application, String reason) throws IOException {
        if (application != null) {
            Files.writeString(resources.resolve("application.properties"), application);
        }
        QuarkusExport.Result result = QuarkusExport.export(resources);

        assertThat(result.modules()).isEmpty();
        assertThat(result.properties()).isEmpty();
        assertThat(result.notes()).singleElement().asString().contains("stays as forage.*", reason);
        if (application != null) {
            assertThat(Files.readString(resources.resolve("application.properties")))
                    .isEqualTo(application);
        }
    }

    /**
     * The Quarkus configuration of the exported app: application.properties, environment variables and system
     * properties (not the module files).
     */
    private static SmallRyeConfig quarkus(Path exported, Map<String, String> env, Map<String, String> sys)
            throws IOException {
        return new SmallRyeConfigBuilder()
                .addDefaultInterceptors()
                .withSources(new PropertiesConfigSource(read(exported.resolve("application.properties")), "app", 250))
                .withSources(new EnvConfigSource(env, 300))
                .withSources(new PropertiesConfigSource(sys, "sys", 400))
                .build();
    }

    /** What Quarkus reads for a value the Forage Quarkus runtime translates: it expands it. */
    private static String forageQuarkusReads(String value) {
        return new SmallRyeConfigBuilder()
                .addDefaultInterceptors()
                .withSources(new PropertiesConfigSource(Map.of("key", value), "forage", 240))
                .build()
                .getOptionalValue("key", String.class)
                .orElse("");
    }

    /** What the Forage runtime translates from the exported files: the module files, then application.properties. */
    private static Map<String, String> forageTranslation(Path exported) throws IOException {
        Properties values = new Properties();
        try (Stream<Path> files = Files.list(exported)) {
            for (Path file : files.filter(f -> f.getFileName().toString().startsWith("forage-"))
                    .toList()) {
                try (InputStream in = Files.newInputStream(file)) {
                    values.load(in);
                }
            }
        }
        values.putAll(read(exported.resolve("application.properties")));
        Map<String, String> translated = new TreeMap<>();
        for (ForageModuleDescriptor<?, ?> descriptor : List.of(new JdbcModuleDescriptor(), new JmsModuleDescriptor())) {
            String module = descriptor.modulePrefix();
            if (!PropertyFileLocator.readPrefixes(values, ConfigHelper.getDefaultPropertyRegexp(module))
                    .isEmpty()) {
                translated.putAll(translate(descriptor, null, values));
            }
            for (String name : PropertyFileLocator.readPrefixes(values, ConfigHelper.getNamedPropertyRegexp(module))) {
                translated.putAll(translate(descriptor, name, values));
            }
        }
        ConfigStore.getInstance().reload();
        return translated;
    }

    private static <C extends Config> Map<String, String> translate(
            ForageModuleDescriptor<C, ?> descriptor, String name, Properties values) {
        C config = descriptor.createConfig(name);
        values.stringPropertyNames().forEach(key -> config.register(key, values.getProperty(key)));
        return descriptor.translateProperties(name, config);
    }

    /**
     * What Camel JBang 4.22 exports: the project application.properties as read, without blank values, written as UTF-8
     * without escaping. The module files are copied as they are.
     */
    private void camelExport() throws IOException {
        Properties exported = new Properties();
        try (InputStream in = Files.newInputStream(project.resolve("application.properties"))) {
            exported.load(in);
        }
        StringBuilder content = new StringBuilder();
        exported.stringPropertyNames().stream()
                .filter(key -> !exported.getProperty(key).isBlank())
                .forEach(key -> content.append(key)
                        .append('=')
                        .append(exported.getProperty(key))
                        .append('\n'));
        Files.writeString(resources.resolve("application.properties"), content.toString(), StandardCharsets.UTF_8);
        try (Stream<Path> files = Files.list(project)) {
            for (Path file : files.filter(f -> f.getFileName().toString().startsWith("forage-"))
                    .toList()) {
                Files.copy(file, resources.resolve(file.getFileName()));
            }
        }
        try (Stream<Path> files = Files.list(resources)) {
            for (Path file : files.toList()) {
                Files.copy(file, before.resolve(file.getFileName()));
            }
        }
    }

    /** The exported resources are the ones of the export without translation. */
    private void assertSameAsBefore() throws IOException {
        try (Stream<Path> files = Files.list(before)) {
            for (Path file : files.toList()) {
                assertThat(resources.resolve(file.getFileName()))
                        .as(file.getFileName().toString())
                        .hasSameBinaryContentAs(file);
            }
        }
    }

    private static String forage(String csv) {
        return csv == null ? "" : ("forage." + csv.replace(";", "\nforage.") + "\n");
    }

    /** As Quarkus reads a properties file: UTF-8. */
    static Properties read(Path file) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] all = new byte[a.length + b.length];
        System.arraycopy(a, 0, all, 0, a.length);
        System.arraycopy(b, 0, all, a.length, b.length);
        return all;
    }
}
