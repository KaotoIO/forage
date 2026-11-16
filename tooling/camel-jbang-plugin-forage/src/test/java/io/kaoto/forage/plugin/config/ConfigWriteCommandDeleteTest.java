package io.kaoto.forage.plugin.config;

import java.io.File;
import java.nio.file.Files;
import java.util.Properties;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import io.kaoto.forage.core.common.VersionHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static io.kaoto.forage.plugin.config.ConfigCommandTestSupport.setField;
import static io.kaoto.forage.plugin.config.ConfigCommandTestSupport.setStrategy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ConfigWriteCommand delete functionality.
 */
class ConfigWriteCommandDeleteTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    File tempDir;

    private StringPrinter printer;

    @BeforeEach
    void setUp() {
        printer = new StringPrinter();
    }

    @Test
    void testDeleteSingleInstance() throws Exception {
        // Setup: Create application.properties with a single PostgreSQL configuration
        File propsFile = new File(tempDir, "application.properties");
        String existingContent =
                """
                # PostgreSQL configuration
                forage.myPG.jdbc.db.kind=postgresql
                forage.myPG.jdbc.url=jdbc:postgresql://localhost:5432/
                forage.myPG.jdbc.username=test
                forage.myPG.jdbc.password=test
                camel.jbang.dependencies=io.kaoto.forage:forage-jdbc-postgresql:%s
                camel.jbang.dependencies.main=io.kaoto.forage:forage-jdbc:%s
                camel.jbang.dependencies.spring-boot=io.kaoto.forage:forage-jdbc-starter:%s
                camel.jbang.dependencies.quarkus=io.kaoto.forage:forage-quarkus-jdbc-deployment:%s
                """
                        .formatted(
                                VersionHelper.VERSION,
                                VersionHelper.VERSION,
                                VersionHelper.VERSION,
                                VersionHelper.VERSION);
        Files.writeString(propsFile.toPath(), existingContent);

        // Execute delete command
        ConfigWriteCommand command = new ConfigWriteCommand(new CamelJBangMain().withPrinter(printer));
        setField(command, "directory", tempDir);
        setStrategy(command, PropertiesFileStrategyType.APPLICATION);
        setField(command, "delete", true);
        setField(command, "instanceName", "myPG");

        int exitCode = command.doCall();

        assertThat(exitCode).isEqualTo(0);

        // Verify output
        String output = printer.getOutput();
        JsonNode result = OBJECT_MAPPER.readTree(output);
        assertThat(result.get("success").asBoolean()).isTrue();
        assertThat(result.get("operation").asText()).isEqualTo("delete");
        assertThat(result.get("instanceName").asText()).isEqualTo("myPG");

        // Verify properties file - forage.myPG.* properties should be removed
        Properties props = new Properties();
        props.load(Files.newInputStream(propsFile.toPath()));

        assertThat(props.getProperty("forage.myPG.jdbc.db.kind")).isNull();
        assertThat(props.getProperty("forage.myPG.jdbc.url")).isNull();
        assertThat(props.getProperty("forage.myPG.jdbc.username")).isNull();
        assertThat(props.getProperty("forage.myPG.jdbc.password")).isNull();

        // Verify dependencies are cleaned up (no more JDBC instances)
        assertThat(props.getProperty("camel.jbang.dependencies")).isNull();
        assertThat(props.getProperty("camel.jbang.dependencies.main")).isNull();
        assertThat(props.getProperty("camel.jbang.dependencies.spring-boot")).isNull();
        assertThat(props.getProperty("camel.jbang.dependencies.quarkus")).isNull();
    }

    @Test
    void testDeleteOneOfMultipleSameFactoryType() throws Exception {
        // Setup: Create application.properties with PostgreSQL AND MariaDB configurations
        File propsFile = new File(tempDir, "application.properties");
        String existingContent =
                """
                # PostgreSQL configuration
                forage.myPG.jdbc.db.kind=postgresql
                forage.myPG.jdbc.url=jdbc:postgresql://localhost:5432/
                forage.myPG.jdbc.username=pguser
                forage.myPG.jdbc.password=pgpass

                # MariaDB configuration
                forage.myMariaDB.jdbc.db.kind=mariadb
                forage.myMariaDB.jdbc.url=jdbc:mariadb://localhost:3306/
                forage.myMariaDB.jdbc.username=mariauser
                forage.myMariaDB.jdbc.password=mariapass

                # Dependencies for both
                camel.jbang.dependencies=io.kaoto.forage:forage-jdbc-postgresql:%s,io.kaoto.forage:forage-jdbc-mariadb:%s
                camel.jbang.dependencies.main=io.kaoto.forage:forage-jdbc:%s
                camel.jbang.dependencies.spring-boot=io.kaoto.forage:forage-jdbc-starter:%s
                camel.jbang.dependencies.quarkus=io.kaoto.forage:forage-quarkus-jdbc-deployment:%s
                """
                        .formatted(
                                VersionHelper.VERSION,
                                VersionHelper.VERSION,
                                VersionHelper.VERSION,
                                VersionHelper.VERSION,
                                VersionHelper.VERSION);
        Files.writeString(propsFile.toPath(), existingContent);

        // Execute delete command for PostgreSQL only
        ConfigWriteCommand command = new ConfigWriteCommand(new CamelJBangMain().withPrinter(printer));
        setField(command, "directory", tempDir);
        setStrategy(command, PropertiesFileStrategyType.APPLICATION);
        setField(command, "delete", true);
        setField(command, "instanceName", "myPG");

        int exitCode = command.doCall();

        assertThat(exitCode).isEqualTo(0);

        // Verify output
        String output = printer.getOutput();
        JsonNode result = OBJECT_MAPPER.readTree(output);
        assertThat(result.get("success").asBoolean()).isTrue();
        assertThat(result.get("operation").asText()).isEqualTo("delete");

        // Verify properties file
        Properties props = new Properties();
        props.load(Files.newInputStream(propsFile.toPath()));

        // PostgreSQL properties should be removed
        assertThat(props.getProperty("forage.myPG.jdbc.db.kind")).isNull();
        assertThat(props.getProperty("forage.myPG.jdbc.url")).isNull();
        assertThat(props.getProperty("forage.myPG.jdbc.username")).isNull();
        assertThat(props.getProperty("forage.myPG.jdbc.password")).isNull();

        // MariaDB properties should be preserved
        assertThat(props.getProperty("forage.myMariaDB.jdbc.db.kind")).isEqualTo("mariadb");
        assertThat(props.getProperty("forage.myMariaDB.jdbc.url")).isEqualTo("jdbc:mariadb://localhost:3306/");
        assertThat(props.getProperty("forage.myMariaDB.jdbc.username")).isEqualTo("mariauser");
        assertThat(props.getProperty("forage.myMariaDB.jdbc.password")).isEqualTo("mariapass");

        // Factory dependencies should be preserved (MariaDB still uses jdbc factory)
        assertThat(props.getProperty("camel.jbang.dependencies.main"))
                .contains("io.kaoto.forage:forage-jdbc:%s".formatted(VersionHelper.VERSION));
        assertThat(props.getProperty("camel.jbang.dependencies.spring-boot"))
                .contains("io.kaoto.forage:forage-jdbc-starter:%s".formatted(VersionHelper.VERSION));
        assertThat(props.getProperty("camel.jbang.dependencies.quarkus"))
                .contains("io.kaoto.forage:forage-quarkus-jdbc-deployment:%s".formatted(VersionHelper.VERSION));

        // MariaDB bean dependency should be preserved
        assertThat(props.getProperty("camel.jbang.dependencies"))
                .contains("io.kaoto.forage:forage-jdbc-mariadb:%s".formatted(VersionHelper.VERSION));
    }

    @Test
    void testDeleteLastInstanceOfFactoryType() throws Exception {
        // Setup: Create application.properties with only Ollama (last bean of agent factory)
        File propsFile = new File(tempDir, "application.properties");
        String existingContent =
                """
                # Ollama configuration
                forage.myOllama.ollama.model.name=llama3
                forage.myOllama.ollama.base.url=http://localhost:11434
                forage.myOllama.ollama.log.requests=true

                # Agent factory dependencies
                camel.jbang.dependencies=io.kaoto.forage:forage-model-ollama:%s
                camel.jbang.dependencies.main=io.kaoto.forage:forage-agent:%s
                camel.jbang.dependencies.spring-boot=io.kaoto.forage:forage-agent-starter:%s
                camel.jbang.dependencies.quarkus=io.kaoto.forage:forage-quarkus-agent-deployment:%s
                """
                        .formatted(
                                VersionHelper.VERSION,
                                VersionHelper.VERSION,
                                VersionHelper.VERSION,
                                VersionHelper.VERSION);
        Files.writeString(propsFile.toPath(), existingContent);

        // Execute delete command
        ConfigWriteCommand command = new ConfigWriteCommand(new CamelJBangMain().withPrinter(printer));
        setField(command, "directory", tempDir);
        setStrategy(command, PropertiesFileStrategyType.APPLICATION);
        setField(command, "delete", true);
        setField(command, "instanceName", "myOllama");

        int exitCode = command.doCall();

        assertThat(exitCode).isEqualTo(0);

        // Verify output
        String output = printer.getOutput();
        JsonNode result = OBJECT_MAPPER.readTree(output);
        assertThat(result.get("success").asBoolean()).isTrue();
        assertThat(result.get("operation").asText()).isEqualTo("delete");

        // Verify dependency cleanup info in output
        JsonNode results = result.get("results");
        assertThat(results).isNotNull();
        JsonNode dependencyCleanup = results.get("dependencyCleanup");
        if (dependencyCleanup != null) {
            assertThat(dependencyCleanup.get("count").asInt()).isGreaterThan(0);
        }

        // Verify properties file
        Properties props = new Properties();
        props.load(Files.newInputStream(propsFile.toPath()));

        // Ollama properties should be removed
        assertThat(props.getProperty("forage.myOllama.ollama.model.name")).isNull();
        assertThat(props.getProperty("forage.myOllama.ollama.base.url")).isNull();
        assertThat(props.getProperty("forage.myOllama.ollama.log.requests")).isNull();

        // All agent-related dependencies should be removed (ollama bean GAV removed)
        String baseDeps = props.getProperty("camel.jbang.dependencies");
        if (baseDeps != null) {
            assertThat(baseDeps).doesNotContain("forage-model-ollama");
        }
    }

    @Test
    void testDeletePreservesOtherFactoryConfigs() throws Exception {
        // Setup: Create application.properties with JDBC and JMS configurations
        File propsFile = new File(tempDir, "application.properties");
        String existingContent =
                """
                # PostgreSQL configuration
                forage.myPG.jdbc.db.kind=postgresql
                forage.myPG.jdbc.url=jdbc:postgresql://localhost:5432/
                forage.myPG.jdbc.username=test
                forage.myPG.jdbc.password=test

                # Artemis JMS configuration
                forage.myArtemis.jms.kind=artemis
                forage.myArtemis.jms.broker.url=tcp://localhost:61616
                forage.myArtemis.jms.username=admin
                forage.myArtemis.jms.password=admin

                # Dependencies
                camel.jbang.dependencies=io.kaoto.forage:forage-jdbc-postgresql:%s,io.kaoto.forage:forage-jms-artemis:%s
                camel.jbang.dependencies.main=io.kaoto.forage:forage-jdbc:%s,io.kaoto.forage:forage-jms:%s
                camel.jbang.dependencies.spring-boot=io.kaoto.forage:forage-jdbc-starter:%s,io.kaoto.forage:forage-jms-starter:%s
                camel.jbang.dependencies.quarkus=io.kaoto.forage:forage-quarkus-jdbc-deployment:%s,io.kaoto.forage:forage-quarkus-jms-deployment:%s
                """
                        .formatted(
                                VersionHelper.VERSION,
                                VersionHelper.VERSION,
                                VersionHelper.VERSION,
                                VersionHelper.VERSION,
                                VersionHelper.VERSION,
                                VersionHelper.VERSION,
                                VersionHelper.VERSION,
                                VersionHelper.VERSION);
        Files.writeString(propsFile.toPath(), existingContent);

        // Execute delete command for PostgreSQL
        ConfigWriteCommand command = new ConfigWriteCommand(new CamelJBangMain().withPrinter(printer));
        setField(command, "directory", tempDir);
        setStrategy(command, PropertiesFileStrategyType.APPLICATION);
        setField(command, "delete", true);
        setField(command, "instanceName", "myPG");

        int exitCode = command.doCall();

        assertThat(exitCode).isEqualTo(0);

        // Verify properties file
        Properties props = new Properties();
        props.load(Files.newInputStream(propsFile.toPath()));

        // PostgreSQL properties should be removed
        assertThat(props.getProperty("forage.myPG.jdbc.db.kind")).isNull();
        assertThat(props.getProperty("forage.myPG.jdbc.url")).isNull();

        // JMS properties should be preserved
        assertThat(props.getProperty("forage.myArtemis.jms.kind")).isEqualTo("artemis");
        assertThat(props.getProperty("forage.myArtemis.jms.broker.url")).isEqualTo("tcp://localhost:61616");
        assertThat(props.getProperty("forage.myArtemis.jms.username")).isEqualTo("admin");
        assertThat(props.getProperty("forage.myArtemis.jms.password")).isEqualTo("admin");

        // JMS dependencies should be preserved
        String baseDeps = props.getProperty("camel.jbang.dependencies");
        assertThat(baseDeps).contains("forage-jms-artemis");

        String mainDeps = props.getProperty("camel.jbang.dependencies.main");
        assertThat(mainDeps).contains("forage-jms");

        String springBootDeps = props.getProperty("camel.jbang.dependencies.spring-boot");
        assertThat(springBootDeps).contains("forage-jms-starter");

        String quarkusDeps = props.getProperty("camel.jbang.dependencies.quarkus");
        assertThat(quarkusDeps).contains("forage-quarkus-jms-deployment");
    }

    @Test
    void testDeletePreservesNonForageDependencies() throws Exception {
        // Setup: Create application.properties with forage and custom dependencies
        File propsFile = new File(tempDir, "application.properties");
        String existingContent =
                """
                # PostgreSQL configuration
                forage.myPG.jdbc.db.kind=postgresql
                forage.myPG.jdbc.url=jdbc:postgresql://localhost:5432/
                forage.myPG.jdbc.username=test
                forage.myPG.jdbc.password=test

                # Dependencies including custom ones
                camel.jbang.dependencies=com.example:custom-lib:1.0,io.kaoto.forage:forage-jdbc-postgresql:%s,com.another:lib:2.0
                camel.jbang.dependencies.main=com.example:custom-main:1.0,io.kaoto.forage:forage-jdbc:%s
                """
                        .formatted(VersionHelper.VERSION, VersionHelper.VERSION);
        Files.writeString(propsFile.toPath(), existingContent);

        // Execute delete command
        ConfigWriteCommand command = new ConfigWriteCommand(new CamelJBangMain().withPrinter(printer));
        setField(command, "directory", tempDir);
        setStrategy(command, PropertiesFileStrategyType.APPLICATION);
        setField(command, "delete", true);
        setField(command, "instanceName", "myPG");

        int exitCode = command.doCall();

        assertThat(exitCode).isEqualTo(0);

        // Verify properties file
        Properties props = new Properties();
        props.load(Files.newInputStream(propsFile.toPath()));

        // Custom dependencies should be preserved
        String baseDeps = props.getProperty("camel.jbang.dependencies");
        assertThat(baseDeps).contains("com.example:custom-lib:1.0");
        assertThat(baseDeps).contains("com.another:lib:2.0");

        String mainDeps = props.getProperty("camel.jbang.dependencies.main");
        assertThat(mainDeps).contains("com.example:custom-main:1.0");
    }

    @Test
    void testDeleteNonExistentInstance() throws Exception {
        // Setup: Create application.properties with PostgreSQL config
        File propsFile = new File(tempDir, "application.properties");
        String existingContent =
                """
                forage.myPG.jdbc.db.kind=postgresql
                forage.myPG.jdbc.url=jdbc:postgresql://localhost:5432/
                """;
        Files.writeString(propsFile.toPath(), existingContent);

        // Execute delete command for non-existent instance
        ConfigWriteCommand command = new ConfigWriteCommand(new CamelJBangMain().withPrinter(printer));
        setField(command, "directory", tempDir);
        setStrategy(command, PropertiesFileStrategyType.APPLICATION);
        setField(command, "delete", true);
        setField(command, "instanceName", "nonExistent");

        int exitCode = command.doCall();

        assertThat(exitCode).isEqualTo(1);

        // Verify error response
        String output = printer.getOutput();
        JsonNode result = OBJECT_MAPPER.readTree(output);
        assertThat(result.get("success").asBoolean()).isFalse();
        assertThat(result.get("error").asText()).contains("No configuration found for instance 'nonExistent'");

        // Verify original properties are unchanged
        Properties props = new Properties();
        props.load(Files.newInputStream(propsFile.toPath()));
        assertThat(props.getProperty("forage.myPG.jdbc.db.kind")).isEqualTo("postgresql");
        assertThat(props.getProperty("forage.myPG.jdbc.url")).isEqualTo("jdbc:postgresql://localhost:5432/");
    }

    @Test
    void testDeleteWithoutInstanceName() throws Exception {
        // Setup: Create application.properties
        File propsFile = new File(tempDir, "application.properties");
        Files.writeString(propsFile.toPath(), "forage.myPG.jdbc.url=test");

        // Execute delete command without instance name
        ConfigWriteCommand command = new ConfigWriteCommand(new CamelJBangMain().withPrinter(printer));
        setField(command, "directory", tempDir);
        setStrategy(command, PropertiesFileStrategyType.APPLICATION);
        setField(command, "delete", true);
        // Not setting instanceName

        int exitCode = command.doCall();

        assertThat(exitCode).isEqualTo(1);

        // Verify error response
        String output = printer.getOutput();
        JsonNode result = OBJECT_MAPPER.readTree(output);
        assertThat(result.get("success").asBoolean()).isFalse();
        assertThat(result.get("error").asText()).contains("Instance name (--name) is required");
    }

    @Test
    void testDeleteFromNonExistentFile() throws Exception {
        // No properties file exists in tempDir

        // Execute delete command
        ConfigWriteCommand command = new ConfigWriteCommand(new CamelJBangMain().withPrinter(printer));
        setField(command, "directory", tempDir);
        setStrategy(command, PropertiesFileStrategyType.APPLICATION);
        setField(command, "delete", true);
        setField(command, "instanceName", "myPG");

        int exitCode = command.doCall();

        assertThat(exitCode).isEqualTo(1);

        // Verify error response
        String output = printer.getOutput();
        JsonNode result = OBJECT_MAPPER.readTree(output);
        assertThat(result.get("success").asBoolean()).isFalse();
        assertThat(result.get("error").asText()).contains("No configuration found");
    }
}
