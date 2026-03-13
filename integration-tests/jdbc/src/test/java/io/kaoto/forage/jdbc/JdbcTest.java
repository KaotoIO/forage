package io.kaoto.forage.jdbc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.function.Consumer;
import org.citrusframework.GherkinTestActionRunner;
import org.citrusframework.annotations.CitrusResource;
import org.citrusframework.annotations.CitrusTest;
import org.citrusframework.junit.jupiter.CitrusSupport;
import org.citrusframework.spi.Resource;
import org.citrusframework.spi.Resources;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import io.kaoto.forage.integration.tests.ForageIntegrationTest;
import io.kaoto.forage.integration.tests.ForageTestCaseRunner;
import io.kaoto.forage.integration.tests.IntegrationTestSetupExtension;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@CitrusSupport
@Testcontainers
@ExtendWith(IntegrationTestSetupExtension.class)
public class JdbcTest implements ForageIntegrationTest {
    private static final Logger LOG = LoggerFactory.getLogger(JdbcTest.class);

    static final Path INPUT_FOLDER = Paths.get("target", "data", "in");
    static final String POSTGRES_IMAGE_NAME =
            ConfigProvider.getConfig().getValue("postgres.container.image", String.class);
    public static final String INTEGRATION_NAME = "jdbc-routes";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
                    DockerImageName.parse(POSTGRES_IMAGE_NAME).asCompatibleSubstituteFor("postgres"))
            .withExposedPorts(5432)
            .withUsername("test")
            .withPassword("test")
            .withDatabaseName("postgresql")
            .withInitScripts("singleTest-postgresql-initScript.sql", "aggregationTest-postgresql-InitScript.sql");

    @BeforeAll
    static void populateTemplate() throws IOException {
        Resource resource = Resources.fromClasspath(
                JdbcTest.class.getSimpleName() + "/jdbc-routes-template.camel.yaml", JdbcTest.class);
        try (InputStream is = resource.getInputStream()) {
            String content = new String(is.readAllBytes());
            // Use hardcoded path
            Files.createDirectories(INPUT_FOLDER);
            content = content.replace("${path}", INPUT_FOLDER.toAbsolutePath().toString());

            Files.writeString(
                    resource.getFile().toPath().getParent().resolve(INTEGRATION_NAME.toLowerCase() + ".camel.yaml"),
                    content);
        }
    }

    @Override
    public String runBeforeAll(ForageTestCaseRunner runner, Consumer<AutoCloseable> afterAll) {
        // running jbang forage run with required resources and required runtime
        runner.when(forageRun(INTEGRATION_NAME, "forage-datasource-factory.properties", "jdbc-routes.camel.yaml")
                .addResource(classResource("MyAggregationStrategy.java"))
                .dumpIntegrationOutput(true)
                // required if more test are using the same route
                .autoRemove(false)
                .withEnvs(Collections.singletonMap("FORAGE_JDBC_URL", postgres.getJdbcUrl())));

        return INTEGRATION_NAME;
    }

    @Test
    @CitrusTest()
    public void single(ForageTestCaseRunner runner) {

        // validation of logged message
        runner.then(camel().jbang()
                .verify()
                .integration(INTEGRATION_NAME)
                .waitForLogMessage("from jdbc default ds - [{id=1, content=postgres 1}, {id=2, content=postgres 2}]"));
    }

    @Test
    @CitrusTest()
    public void aggregationTest(ForageTestCaseRunner runner) {

        // send events to be aggregated together
        runner.when(camel().jbang()
                        .cmd()
                        .send()
                        .endpoint("direct:events")
                        .integration(INTEGRATION_NAME)
                        .body("Hello 1!")
                        .header("eventId", "1"))
                .and(camel().jbang()
                        .cmd()
                        .send()
                        .endpoint("direct:events")
                        .integration(INTEGRATION_NAME)
                        .body("Hello 2!")
                        .header("eventId", "1"))
                .and(camel().jbang()
                        .cmd()
                        .send()
                        .endpoint("direct:events")
                        .integration(INTEGRATION_NAME)
                        .body("Hello 3!")
                        .header("eventId", "1"));

        // validation of logged message
        runner.then(camel().jbang()
                .verify()
                .integration(INTEGRATION_NAME)
                .waitForLogMessage("Batch complete with 3 event"));
    }

    @Test
    @CitrusTest()
    public void idempotentTest(@CitrusResource GherkinTestActionRunner runner) throws IOException {
        Files.createDirectories(INPUT_FOLDER);

        // create a temp file with content `A`
        Path fileA = Files.write(Files.createTempFile("tempFile", ".txt"), "A".getBytes(), StandardOpenOption.WRITE);

        // copy test.txt to input folder
        Files.move(fileA, INPUT_FOLDER.resolve("test.txt"));

        // validation of logged message
        runner.then(camel().jbang()
                .verify()
                .integration(INTEGRATION_NAME)
                .waitForLogMessage("Processed file: test.txt with content: A"));

        // create a temp file with content `A`
        Path fileB = Files.write(Files.createTempFile("tempFile", ".txt"), "B".getBytes(), StandardOpenOption.WRITE);

        // copy test.txt to input folder
        Files.move(fileB, INPUT_FOLDER.resolve("test.txt"));

        String error = null;
        try {
            // failure is expected
            runner.then(camel().jbang()
                    .verify()
                    .integration(INTEGRATION_NAME)
                    .maxAttempts(3)
                    .delayBetweenAttempts(5000)
                    .waitForLogMessage("Processed file: test.txt with content: B"));
        } catch (Exception e) {
            error = e.getMessage();
        }
        Assertions.assertTrue(error != null && error.startsWith("Action timeout after"));
    }

    @Test
    @CitrusTest()
    public void validationStrictModeFailsWithInvalidProperties(ForageTestCaseRunner runner) {
        // Given: properties file with validation warnings (typo: 'usernam' instead of 'username')
        // When: running with --strict flag
        // Then: the validation should fail and prevent the route from starting

        String error = null;
        try {
            runner.when(forageRun("validation-strict-test", "forage-datasource-factory-invalid.properties", null)
                    .withArg("--strict")
                    .dumpIntegrationOutput(true));
        } catch (Exception e) {
            error = e.getMessage();
        }

        // Verify that the run failed due to validation in strict mode
        Assertions.assertNotNull(error, "Expected validation to fail with --strict flag");
        Assertions.assertTrue(
                error.contains("exit code 1") || error.contains("Failed to verify"),
                "Expected validation failure with exit code 1, got: " + error);
    }
}
