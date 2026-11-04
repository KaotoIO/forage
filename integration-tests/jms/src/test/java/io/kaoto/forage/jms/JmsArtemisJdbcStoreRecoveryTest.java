package io.kaoto.forage.jms;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.stream.Stream;
import org.citrusframework.TestAction;
import org.citrusframework.annotations.CitrusTest;
import org.citrusframework.junit.jupiter.CitrusSupport;
import org.citrusframework.spi.Resource;
import org.citrusframework.spi.Resources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.activemq.ArtemisContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import io.kaoto.forage.integration.tests.DisableOnQuarkus;
import io.kaoto.forage.integration.tests.DisableOnSpringBoot;
import io.kaoto.forage.integration.tests.ForageIntegrationTest;
import io.kaoto.forage.integration.tests.ForageTestCaseRunner;
import io.kaoto.forage.integration.tests.IntegrationTestSetupExtension;
import io.kaoto.forage.integration.tests.PropertiesTemplateHelper;
import io.kaoto.forage.integration.tests.RuntimeConditionExtension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * XA crash recovery with the transaction log in a database (issue #447): the same crash scenario as
 * {@link JmsArtemisRecoveryTest} (JVM halted between prepare and commit, restart, in-doubt JMS branch
 * committed by recovery), but with {@code object.store.type=jdbc} and <em>no shared filesystem</em>
 * between the two runs: each run has its own working directory, object store directory and marker
 * files, so the restarted run can only find the commit decision in PostgreSQL.
 *
 * <p>Plain Camel only, for the same reasons as {@link JmsArtemisRecoveryTest}.
 */
@CitrusSupport
@ExtendWith({IntegrationTestSetupExtension.class, RuntimeConditionExtension.class})
@DisableOnQuarkus
@DisableOnSpringBoot
public class JmsArtemisJdbcStoreRecoveryTest implements ForageIntegrationTest {
    private static final Logger LOG = LoggerFactory.getLogger(JmsArtemisJdbcStoreRecoveryTest.class);

    private static final String CRASH_RUN = "jms-jdbc-store-crash";
    private static final String RESTART_RUN = "jms-jdbc-store-restart";
    private static final String QUEUE = "recovery.jdbc.output";

    // unquoted, so PostgreSQL folds it to lower case exactly like Narayana's own statements
    private static final String TABLE_PREFIX =
            "it447_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + "_";
    private static final String STORE_TABLE = TABLE_PREFIX + "JBossTSTxTable";

    private static PostgreSQLContainer<?> postgres;

    @Override
    public String runBeforeAll(ForageTestCaseRunner runner, Consumer<AutoCloseable> afterAll) {
        ArtemisContainer artemis = JmsContainers.artemis();
        String brokerUrl = "tcp://" + artemis.getHost() + ":" + artemis.getMappedPort(61616);
        postgres = JmsContainers.postgres();

        // Separate directories for the two runs: nothing written to disk by the crashed run is
        // visible to the restarted one.
        Path crashDir = JmsArtemisRecoveryTest.createRecoveryDir(afterAll);
        Path restartDir = JmsArtemisRecoveryTest.createRecoveryDir(afterAll);
        Path crashMarker = crashDir.resolve("crash.marker");
        Path restartMarker = restartDir.resolve("restart.marker");

        Resource[] crashResources = prepareRun(brokerUrl, crashDir, crashMarker, afterAll);
        Resource[] restartResources = prepareRun(brokerUrl, restartDir, restartMarker, afterAll);
        if (crashResources[0]
                .file()
                .getParentFile()
                .equals(restartResources[0].file().getParentFile())) {
            throw new IllegalStateException("The two runs must not share a working directory");
        }

        // First run: prepares the XA transaction and halts the JVM before the JMS branch commits.
        runner.when(camel().cli()
                .custom("forage", "run")
                .processName(CRASH_RUN)
                .addResource(crashResources[0])
                .addResource(crashResources[1])
                .addResource(crashResources[2])
                .addResource(crashResources[3])
                .dumpIntegrationOutput(true));
        registerIntegrationCleanup(runner, CRASH_RUN, afterAll);

        runner.run((TestAction) context -> {
            long pid = Long.parseLong(
                    context.getVariables().get(CRASH_RUN + ":pid").toString());
            try {
                Files.createFile(Path.of(crashMarker + ".go"));
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to create the crash go file", e);
            }
            JmsArtemisRecoveryTest.awaitProcessDeath(pid);
            if (!Files.exists(crashMarker)) {
                throw new IllegalStateException(
                        "Integration process died without reaching the crash point (no crash marker written)");
            }
            LOG.info("Integration crashed between prepare and commit (pid {})", pid);
        });

        // In-doubt guard, as in the file store test.
        JmsArtemisRecoveryTest.assertMessageNotVisible(brokerUrl, QUEUE);

        // The commit decision is in the database ...
        int records = transactionLogRecords();
        if (records < 1) {
            throw new IllegalStateException("No transaction log record in " + STORE_TABLE
                    + " after the crash: the commit decision was not written to the JDBC object store");
        }
        // ... and not on the crashed run's disk
        assertNoFileStoreLog(crashDir);
        LOG.info("{} transaction log record(s) in {} after the crash", records, STORE_TABLE);

        // The restarted run only shares the database: its marker tells the route to skip the crash.
        try {
            Files.createFile(restartMarker);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create the restart marker", e);
        }
        runner.when(camel().cli()
                .custom("forage", "run")
                .processName(RESTART_RUN)
                .addResource(restartResources[0])
                .addResource(restartResources[1])
                .addResource(restartResources[2])
                .addResource(restartResources[3])
                .dumpIntegrationOutput(true));

        return RESTART_RUN;
    }

    @Test
    @CitrusTest
    public void inDoubtBranchIsCommittedByRecoveryFromTheDatabaseLog(ForageTestCaseRunner runner) {
        // The restarted run shares nothing on disk with the crashed one: the commit decision it
        // replays can only come from the PostgreSQL table (without it, the prepared JMS branch
        // would be presumed aborted and rolled back, and no message would arrive).
        // Note: the log record itself stays in the table, as it does in the file store test: the
        // recovery thread cannot deserialize the route-compiled CrashingXAResource (ARJUNA016114),
        // so that branch of the log record is never resolved. Only the JMS branch is asserted.
        runner.then(camel().cli()
                .verify()
                .integration(RESTART_RUN)
                .waitForLogMessage("Recovered message received: XA crash recovery test message"));
    }

    /**
     * Templates the properties of one run into a fresh directory (the integration's working
     * directory) and copies the datasource properties and the shared crash route next to them.
     */
    private Resource[] prepareRun(String brokerUrl, Path runDir, Path marker, Consumer<AutoCloseable> afterAll) {
        Resource jmsProperties = PropertiesTemplateHelper.createFromTemplate(
                classResource("forage-connectionfactory.properties.template"),
                Map.of(
                        "forage\\.jms\\.broker\\.url=.*",
                                Matcher.quoteReplacement("forage.jms.broker.url=" + brokerUrl),
                        "TABLE_PREFIX_PLACEHOLDER", Matcher.quoteReplacement(TABLE_PREFIX),
                        "OBJECT_STORE_DIR_PLACEHOLDER",
                                Matcher.quoteReplacement(
                                        runDir.resolve("tx-object-store").toString())),
                afterAll);
        Resource dataSourceProperties = PropertiesTemplateHelper.createFromTemplate(
                classResource("forage-datasource-factory.properties.template"),
                "JDBC_URL_PLACEHOLDER",
                postgres.getJdbcUrl(),
                afterAll);
        PropertiesTemplateHelper.copyIntoSameDirectory(jmsProperties, dataSourceProperties, afterAll);
        PropertiesTemplateHelper.copyIntoSameDirectory(
                jmsProperties,
                Resources.fromClasspath("JmsArtemisRecoveryTest/CrashRecoveryRoute.java", JmsArtemisRecoveryTest.class),
                afterAll);

        Path workDir = jmsProperties.file().toPath().getParent();
        return new Resource[] {
            jmsProperties,
            Resources.create(
                    workDir.resolve("forage-datasource-factory.properties").toFile()),
            Resources.create(workDir.resolve("CrashRecoveryRoute.java").toFile()),
            JmsArtemisRecoveryTest.routeProperties(jmsProperties, marker, afterAll)
        };
    }

    /**
     * Transaction log records in the store table (the table is shared by Narayana's three stores, so
     * e.g. the communication store's TransactionStatusManager entries are excluded).
     */
    private static int transactionLogRecords() {
        try (Connection connection = DriverManager.getConnection(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT COUNT(*) FROM " + STORE_TABLE + " WHERE TypeName LIKE '%AtomicAction%'")) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read the JDBC object store table " + STORE_TABLE, e);
        }
    }

    private static void assertNoFileStoreLog(Path runDir) {
        Path objectStoreDir = runDir.resolve("tx-object-store");
        if (!Files.exists(objectStoreDir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(objectStoreDir)) {
            if (paths.anyMatch(path -> path.toString().contains("AtomicAction"))) {
                throw new IllegalStateException(
                        "The crashed run wrote its transaction log to the file store " + objectStoreDir);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
