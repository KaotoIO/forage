package io.kaoto.forage.plugin.config;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Properties;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import io.kaoto.forage.core.common.VersionHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ConfigWriteCommand.
 */
class ConfigWriteCommandTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    File tempDir;

    private StringPrinter printer;

    @BeforeEach
    void setUp() {
        printer = new StringPrinter();
    }

    @Test
    void testWritePostgresqlConfig() throws Exception {
        // Test scenario 1: PostgreSQL JDBC configuration with all features enabled
        String jsonInput =
                """
                {
                  "forage.jdbc.db.kind": "postgresql",
                  "forage.jdbc.url": "jdbc:postgresql://localhost:5432/",
                  "forage.jdbc.username": "test",
                  "forage.jdbc.password": "test",
                  "forage.jdbc.transaction.enabled": "true",
                  "forage.jdbc.aggregation.repository.enabled": "true",
                  "forage.jdbc.idempotent.repository.enabled": "true",
                  "kind": "postgresql",
                  "forage.bean.name": "myPG"
                }
                """;

        ConfigWriteCommand command = new ConfigWriteCommand(new CamelJBangMain().withPrinter(printer));
        setField(command, "input", jsonInput);
        setField(command, "directory", tempDir);
        setField(command, "strategy", "application");

        int exitCode = command.doCall();

        assertThat(exitCode).isEqualTo(0);

        // Verify output - when dependencies are included, output has "factories" structure
        String output = printer.getOutput();
        assertThat(output).isNotEmpty();

        JsonNode result = OBJECT_MAPPER.readTree(output);
        assertThat(result.get("success").asBoolean()).isTrue();
        // With dependencies, the structure is { success, factories: { jdbc: {...}, dependencies: {...} } }
        JsonNode factories = result.get("factories");
        assertThat(factories).isNotNull();
        assertThat(factories.get("jdbc").get("beanName").asText()).isEqualTo("myPG");

        // Verify properties file was created
        File propsFile = new File(tempDir, "application.properties");
        assertThat(propsFile).exists();

        // Load and verify properties
        Properties props = new Properties();
        props.load(Files.newInputStream(propsFile.toPath()));

        assertThat(props.getProperty("forage.myPG.jdbc.db.kind")).isEqualTo("postgresql");
        assertThat(props.getProperty("forage.myPG.jdbc.url")).isEqualTo("jdbc:postgresql://localhost:5432/");
        assertThat(props.getProperty("forage.myPG.jdbc.username")).isEqualTo("test");
        assertThat(props.getProperty("forage.myPG.jdbc.password")).isEqualTo("test");
        assertThat(props.getProperty("forage.myPG.jdbc.transaction.enabled")).isEqualTo("true");
        assertThat(props.getProperty("forage.myPG.jdbc.aggregation.repository.enabled"))
                .isEqualTo("true");
        assertThat(props.getProperty("forage.myPG.jdbc.idempotent.repository.enabled"))
                .isEqualTo("true");

        // Verify dependencies are written
        assertThat(props.getProperty("camel.jbang.dependencies")).contains("io.kaoto.forage:forage-jdbc-postgresql:");
        assertThat(props.getProperty("camel.jbang.dependencies.main")).contains("io.kaoto.forage:forage-jdbc:");
        assertThat(props.getProperty("camel.jbang.dependencies.spring-boot"))
                .contains("io.kaoto.forage:forage-jdbc-starter:");
        assertThat(props.getProperty("camel.jbang.dependencies.quarkus"))
                .contains("io.kaoto.forage:forage-quarkus-jdbc-deployment:");
    }

    @Test
    void testWriteInfinispanConfig() throws Exception {
        // Test scenario 2: Infinispan chat memory configuration
        String jsonInput =
                """
                {
                  "forage.infinispan.server-list": "localhost:11223",
                  "kind": "infinispan",
                  "forage.bean.name": "myMemory"
                }
                """;

        ConfigWriteCommand command = new ConfigWriteCommand(new CamelJBangMain().withPrinter(printer));
        setField(command, "input", jsonInput);
        setField(command, "directory", tempDir);
        setField(command, "strategy", "application");

        int exitCode = command.doCall();

        assertThat(exitCode).isEqualTo(0);

        // Verify output
        String output = printer.getOutput();
        assertThat(output).isNotEmpty();

        JsonNode result = OBJECT_MAPPER.readTree(output);
        assertThat(result.get("success").asBoolean()).isTrue();
        // Infinispan maps to "multi" factory type (agent factory), check the factories structure
        JsonNode factories = result.get("factories");
        if (factories != null) {
            // Has dependencies - look for the factory in factories
            assertThat(factories.has("multi") || factories.has("dependencies")).isTrue();
        } else {
            // Single result - beanName at top level
            assertThat(result.get("beanName").asText()).isEqualTo("myMemory");
        }

        // Verify properties file was created
        File propsFile = new File(tempDir, "application.properties");
        assertThat(propsFile).exists();

        // Load and verify properties
        Properties props = new Properties();
        props.load(Files.newInputStream(propsFile.toPath()));

        assertThat(props.getProperty("forage.myMemory.infinispan.server-list")).isEqualTo("localhost:11223");
    }

    @Test
    void testWriteH2Config() throws Exception {
        // Test scenario 3: H2 JDBC configuration
        String jsonInput =
                """
                {
                  "forage.jdbc.db.kind": "h2",
                  "forage.jdbc.url": "127.0.0.1",
                  "forage.jdbc.username": "username",
                  "forage.jdbc.password": "password",
                  "kind": "h2",
                  "forage.bean.name": "myH2"
                }
                """;

        ConfigWriteCommand command = new ConfigWriteCommand(new CamelJBangMain().withPrinter(printer));
        setField(command, "input", jsonInput);
        setField(command, "directory", tempDir);
        setField(command, "strategy", "application");

        int exitCode = command.doCall();

        assertThat(exitCode).isEqualTo(0);

        // Verify output - with dependencies, output has "factories" structure
        String output = printer.getOutput();
        assertThat(output).isNotEmpty();

        JsonNode result = OBJECT_MAPPER.readTree(output);
        assertThat(result.get("success").asBoolean()).isTrue();
        JsonNode factories = result.get("factories");
        assertThat(factories).isNotNull();
        assertThat(factories.get("jdbc").get("beanName").asText()).isEqualTo("myH2");

        // Verify properties file was created
        File propsFile = new File(tempDir, "application.properties");
        assertThat(propsFile).exists();

        // Load and verify properties
        Properties props = new Properties();
        props.load(Files.newInputStream(propsFile.toPath()));

        assertThat(props.getProperty("forage.myH2.jdbc.db.kind")).isEqualTo("h2");
        assertThat(props.getProperty("forage.myH2.jdbc.url")).isEqualTo("127.0.0.1");
        assertThat(props.getProperty("forage.myH2.jdbc.username")).isEqualTo("username");
        assertThat(props.getProperty("forage.myH2.jdbc.password")).isEqualTo("password");
    }

    @Test
    void testWriteIbmMqConfig() throws Exception {
        // Test scenario 4: IBM MQ JMS configuration
        String jsonInput =
                """
                {
                  "forage.jms.kind": "ibmmq",
                  "forage.jms.broker.url": "127.0.0.1",
                  "forage.jms.username": "user",
                  "kind": "ibmmq",
                  "forage.bean.name": "myIBMMQ"
                }
                """;

        ConfigWriteCommand command = new ConfigWriteCommand(new CamelJBangMain().withPrinter(printer));
        setField(command, "input", jsonInput);
        setField(command, "directory", tempDir);
        setField(command, "strategy", "application");

        int exitCode = command.doCall();

        assertThat(exitCode).isEqualTo(0);

        // Verify output - with dependencies, output has "factories" structure
        String output = printer.getOutput();
        assertThat(output).isNotEmpty();

        JsonNode result = OBJECT_MAPPER.readTree(output);
        assertThat(result.get("success").asBoolean()).isTrue();
        JsonNode factories = result.get("factories");
        assertThat(factories).isNotNull();
        assertThat(factories.get("jms").get("beanName").asText()).isEqualTo("myIBMMQ");

        // Verify properties file was created
        File propsFile = new File(tempDir, "application.properties");
        assertThat(propsFile).exists();

        // Load and verify properties
        Properties props = new Properties();
        props.load(Files.newInputStream(propsFile.toPath()));

        assertThat(props.getProperty("forage.myIBMMQ.jms.kind")).isEqualTo("ibmmq");
        assertThat(props.getProperty("forage.myIBMMQ.jms.broker.url")).isEqualTo("127.0.0.1");
        assertThat(props.getProperty("forage.myIBMMQ.jms.username")).isEqualTo("user");

        // Verify JMS dependencies are written
        assertThat(props.getProperty("camel.jbang.dependencies")).contains("io.kaoto.forage:forage-jms-ibmmq:");
        assertThat(props.getProperty("camel.jbang.dependencies.main")).contains("io.kaoto.forage:forage-jms:");
        assertThat(props.getProperty("camel.jbang.dependencies.spring-boot"))
                .contains("io.kaoto.forage:forage-jms-starter:");
        assertThat(props.getProperty("camel.jbang.dependencies.quarkus"))
                .contains("io.kaoto.forage:forage-quarkus-jms-deployment:");
    }

    @Test
    void testWriteOllamaConfig() throws Exception {
        // Test scenario 5: Ollama model configuration
        String jsonInput =
                """
                {
                  "forage.ollama.model.name": "granite4:3b",
                  "forage.ollama.log.requests": "true",
                  "forage.ollama.log.responses": "true",
                  "kind": "ollama",
                  "forage.bean.name": "test"
                }
                """;

        ConfigWriteCommand command = new ConfigWriteCommand(new CamelJBangMain().withPrinter(printer));
        setField(command, "input", jsonInput);
        setField(command, "directory", tempDir);
        setField(command, "strategy", "application");

        int exitCode = command.doCall();

        assertThat(exitCode).isEqualTo(0);

        // Verify output
        String output = printer.getOutput();
        assertThat(output).isNotEmpty();

        JsonNode result = OBJECT_MAPPER.readTree(output);
        assertThat(result.get("success").asBoolean()).isTrue();
        // Ollama maps to "multi" factory type, check for factories structure
        JsonNode factories = result.get("factories");
        if (factories != null) {
            // Has dependencies - look for the factory in factories
            assertThat(factories.has("multi") || factories.has("dependencies")).isTrue();
        } else {
            // Single result - beanName at top level
            assertThat(result.get("beanName").asText()).isEqualTo("test");
        }

        // Verify properties file was created
        File propsFile = new File(tempDir, "application.properties");
        assertThat(propsFile).exists();

        // Load and verify properties
        Properties props = new Properties();
        props.load(Files.newInputStream(propsFile.toPath()));

        assertThat(props.getProperty("forage.test.ollama.model.name")).isEqualTo("granite4:3b");
        assertThat(props.getProperty("forage.test.ollama.log.requests")).isEqualTo("true");
        assertThat(props.getProperty("forage.test.ollama.log.responses")).isEqualTo("true");
    }

    @Test
    void testDependenciesPreserveExisting() throws Exception {
        // Test that existing dependencies are preserved when adding new forage dependencies

        // Pre-create application.properties with existing dependencies
        File propsFile = new File(tempDir, "application.properties");
        String existingContent =
                """
                # Existing configuration
                some.existing.property=value
                camel.jbang.dependencies=com.example:existing-lib:1.0,com.other:another-lib:2.0
                camel.jbang.dependencies.main=com.example:existing-main:1.0
                """;
        Files.writeString(propsFile.toPath(), existingContent);

        // Write PostgreSQL config
        String jsonInput =
                """
                {
                  "forage.jdbc.db.kind": "postgresql",
                  "forage.jdbc.url": "jdbc:postgresql://localhost:5432/",
                  "forage.jdbc.username": "test",
                  "forage.jdbc.password": "test",
                  "kind": "postgresql",
                  "forage.bean.name": "myPG"
                }
                """;

        ConfigWriteCommand command = new ConfigWriteCommand(new CamelJBangMain().withPrinter(printer));
        setField(command, "input", jsonInput);
        setField(command, "directory", tempDir);
        setField(command, "strategy", "application");

        int exitCode = command.doCall();
        assertThat(exitCode).isEqualTo(0);

        // Verify properties file
        Properties props = new Properties();
        props.load(Files.newInputStream(propsFile.toPath()));

        // Verify existing properties are preserved
        assertThat(props.getProperty("some.existing.property")).isEqualTo("value");

        // Verify existing dependencies are preserved AND new ones are added
        String baseDeps = props.getProperty("camel.jbang.dependencies");
        assertThat(baseDeps).contains("com.example:existing-lib:1.0");
        assertThat(baseDeps).contains("com.other:another-lib:2.0");
        assertThat(baseDeps).contains("io.kaoto.forage:forage-jdbc-postgresql:");

        String mainDeps = props.getProperty("camel.jbang.dependencies.main");
        assertThat(mainDeps).contains("com.example:existing-main:1.0");
        assertThat(mainDeps).contains("io.kaoto.forage:forage-jdbc:");

        // Verify new dependency properties are added
        assertThat(props.getProperty("camel.jbang.dependencies.spring-boot"))
                .contains("io.kaoto.forage:forage-jdbc-starter:");
        assertThat(props.getProperty("camel.jbang.dependencies.quarkus"))
                .contains("io.kaoto.forage:forage-quarkus-jdbc-deployment:");
    }

    @Test
    void testAddGeminiAgentThenJmsToExistingJdbcConfig() throws Exception {
        // Test adding a Google Gemini agent config to an existing JDBC configuration,
        // then adding JMS configuration. This verifies that dependencies are properly merged
        // across multiple ConfigWriteCommand invocations.

        // Pre-create application.properties with existing JDBC configuration and dependencies
        File propsFile = new File(tempDir, "application.properties");
        String existingContent =
                """
                # Existing JDBC configuration
                forage.myPG.jdbc.db.kind=postgresql
                forage.myPG.jdbc.url=jdbc:postgresql://localhost:5432/
                forage.myPG.jdbc.username=test
                forage.myPG.jdbc.password=test

                # Existing dependencies
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

        // Step 1: Add Google Gemini agent configuration
        String geminiInput =
                """
                {
                  "forage.google.api.key": "test-api-key",
                  "forage.google.model.name": "gemini-pro",
                  "kind": "google-gemini",
                  "forage.bean.name": "myGemini"
                }
                """;

        ConfigWriteCommand geminiCommand = new ConfigWriteCommand(new CamelJBangMain().withPrinter(printer));
        setField(geminiCommand, "input", geminiInput);
        setField(geminiCommand, "directory", tempDir);
        setField(geminiCommand, "strategy", "application");

        int exitCode1 = geminiCommand.doCall();
        assertThat(exitCode1).isEqualTo(0);

        // Verify properties after adding Gemini
        Properties propsAfterGemini = new Properties();
        propsAfterGemini.load(Files.newInputStream(propsFile.toPath()));

        // Verify existing JDBC properties are preserved
        assertThat(propsAfterGemini.getProperty("forage.myPG.jdbc.db.kind")).isEqualTo("postgresql");
        assertThat(propsAfterGemini.getProperty("forage.myPG.jdbc.url")).isEqualTo("jdbc:postgresql://localhost:5432/");
        assertThat(propsAfterGemini.getProperty("forage.myPG.jdbc.username")).isEqualTo("test");
        assertThat(propsAfterGemini.getProperty("forage.myPG.jdbc.password")).isEqualTo("test");

        // Verify Gemini properties are added
        assertThat(propsAfterGemini.getProperty("forage.myGemini.google.api.key"))
                .isEqualTo("test-api-key");
        assertThat(propsAfterGemini.getProperty("forage.myGemini.google.model.name"))
                .isEqualTo("gemini-pro");

        // Verify base dependencies contain PostgreSQL and Google Gemini
        String baseDepsAfterGemini = propsAfterGemini.getProperty("camel.jbang.dependencies");
        assertThat(baseDepsAfterGemini)
                .contains("io.kaoto.forage:forage-jdbc-postgresql:%s".formatted(VersionHelper.VERSION));
        assertThat(baseDepsAfterGemini).contains("io.kaoto.forage:forage-model-google-gemini:");

        // Verify main dependencies still contain JDBC
        String mainDepsAfterGemini = propsAfterGemini.getProperty("camel.jbang.dependencies.main");
        assertThat(mainDepsAfterGemini).contains("io.kaoto.forage:forage-jdbc:%s".formatted(VersionHelper.VERSION));

        // Step 2: Add JMS Artemis configuration using ConfigWriteCommand
        printer = new StringPrinter(); // Reset printer for second command

        String jmsInput =
                """
                {
                  "forage.jms.kind": "artemis",
                  "forage.jms.broker.url": "tcp://localhost:61616",
                  "forage.jms.username": "admin",
                  "forage.jms.password": "admin",
                  "kind": "artemis",
                  "forage.bean.name": "myArtemis"
                }
                """;

        ConfigWriteCommand jmsCommand = new ConfigWriteCommand(new CamelJBangMain().withPrinter(printer));
        setField(jmsCommand, "input", jmsInput);
        setField(jmsCommand, "directory", tempDir);
        setField(jmsCommand, "strategy", "application");

        int exitCode2 = jmsCommand.doCall();
        assertThat(exitCode2).isEqualTo(0);

        // Verify final properties file contains all configurations
        Properties finalProps = new Properties();
        finalProps.load(Files.newInputStream(propsFile.toPath()));

        // Verify JDBC properties are still preserved
        assertThat(finalProps.getProperty("forage.myPG.jdbc.db.kind")).isEqualTo("postgresql");
        assertThat(finalProps.getProperty("forage.myPG.jdbc.url")).isEqualTo("jdbc:postgresql://localhost:5432/");
        assertThat(finalProps.getProperty("forage.myPG.jdbc.username")).isEqualTo("test");
        assertThat(finalProps.getProperty("forage.myPG.jdbc.password")).isEqualTo("test");

        // Verify Gemini properties are still preserved
        assertThat(finalProps.getProperty("forage.myGemini.google.api.key")).isEqualTo("test-api-key");
        assertThat(finalProps.getProperty("forage.myGemini.google.model.name")).isEqualTo("gemini-pro");

        // Verify JMS properties are added
        assertThat(finalProps.getProperty("forage.myArtemis.jms.kind")).isEqualTo("artemis");
        assertThat(finalProps.getProperty("forage.myArtemis.jms.broker.url")).isEqualTo("tcp://localhost:61616");
        assertThat(finalProps.getProperty("forage.myArtemis.jms.username")).isEqualTo("admin");
        assertThat(finalProps.getProperty("forage.myArtemis.jms.password")).isEqualTo("admin");

        // Verify base dependencies contain PostgreSQL, Google Gemini, and Artemis
        String finalBaseDeps = finalProps.getProperty("camel.jbang.dependencies");
        assertThat(finalBaseDeps)
                .contains("io.kaoto.forage:forage-jdbc-postgresql:%s".formatted(VersionHelper.VERSION));
        assertThat(finalBaseDeps).contains("io.kaoto.forage:forage-model-google-gemini:");
        assertThat(finalBaseDeps).contains("io.kaoto.forage:forage-jms-artemis:");

        // Verify main dependencies contain both JDBC and JMS
        String finalMainDeps = finalProps.getProperty("camel.jbang.dependencies.main");
        assertThat(finalMainDeps).contains("io.kaoto.forage:forage-jdbc:%s".formatted(VersionHelper.VERSION));
        assertThat(finalMainDeps).contains("io.kaoto.forage:forage-jms:");

        // Verify spring-boot dependencies contain both JDBC and JMS starters
        String finalSpringBootDeps = finalProps.getProperty("camel.jbang.dependencies.spring-boot");
        assertThat(finalSpringBootDeps)
                .contains("io.kaoto.forage:forage-jdbc-starter:%s".formatted(VersionHelper.VERSION));
        assertThat(finalSpringBootDeps).contains("io.kaoto.forage:forage-jms-starter:");

        // Verify quarkus dependencies contain both JDBC and JMS
        String finalQuarkusDeps = finalProps.getProperty("camel.jbang.dependencies.quarkus");
        assertThat(finalQuarkusDeps)
                .contains("io.kaoto.forage:forage-quarkus-jdbc-deployment:%s".formatted(VersionHelper.VERSION));
        assertThat(finalQuarkusDeps).contains("io.kaoto.forage:forage-quarkus-jms-deployment:");
    }

    @Test
    void testWriteConfigWithEmptyInput() throws Exception {
        // Test error handling for empty input
        ConfigWriteCommand command = new ConfigWriteCommand(new CamelJBangMain().withPrinter(printer));
        setField(command, "input", "");
        setField(command, "directory", tempDir);
        setField(command, "strategy", "application");

        int exitCode = command.doCall();

        assertThat(exitCode).isEqualTo(1);

        String output = printer.getOutput();
        JsonNode result = OBJECT_MAPPER.readTree(output);
        assertThat(result.get("success").asBoolean()).isFalse();
        assertThat(result.get("error").asText()).contains("No JSON input provided");
    }

    @Test
    void testWriteConfigWithEmptyJson() throws Exception {
        // Test error handling for empty JSON object
        ConfigWriteCommand command = new ConfigWriteCommand(new CamelJBangMain().withPrinter(printer));
        setField(command, "input", "{}");
        setField(command, "directory", tempDir);
        setField(command, "strategy", "application");

        int exitCode = command.doCall();

        assertThat(exitCode).isEqualTo(1);

        String output = printer.getOutput();
        JsonNode result = OBJECT_MAPPER.readTree(output);
        assertThat(result.get("success").asBoolean()).isFalse();
        assertThat(result.get("error").asText()).contains("Empty configuration provided");
    }

    /**
     * Helper method to set private fields on the command object using reflection.
     */
    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
