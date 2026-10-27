package io.kaoto.forage.jms;

import jakarta.jms.Connection;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Session;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.citrusframework.TestAction;
import org.citrusframework.annotations.CitrusTest;
import org.citrusframework.junit.jupiter.CitrusSupport;
import org.citrusframework.spi.Resource;
import org.citrusframework.spi.Resources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.activemq.ArtemisContainer;
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
 * XA crash recovery end to end (issue #432): the integration is killed (JVM halt) between the
 * prepare and commit phase of an XA transaction, restarted with the same transaction object store,
 * and the in-doubt JMS branch must be committed by the Narayana recovery manager — the message
 * appears on the output queue only through recovery.
 *
 * <p>Plain Camel only: on Quarkus recovery is owned by quarkus-narayana-jta (covered by property
 * translation), and the crash/restart choreography of this test relies on the single-JVM process
 * model of {@code camel forage run} with the main runtime.
 */
@CitrusSupport
@ExtendWith({IntegrationTestSetupExtension.class, RuntimeConditionExtension.class})
@DisableOnQuarkus
@DisableOnSpringBoot
public class JmsArtemisRecoveryTest implements ForageIntegrationTest {
    private static final Logger LOG = LoggerFactory.getLogger(JmsArtemisRecoveryTest.class);

    private static final String CRASH_RUN = "jms-recovery-crash";
    private static final String RESTART_RUN = "jms-recovery-restart";

    @Override
    public String runBeforeAll(ForageTestCaseRunner runner, Consumer<AutoCloseable> afterAll) {
        ArtemisContainer artemis = JmsContainers.artemis();
        String brokerUrl = "tcp://" + artemis.getHost() + ":" + artemis.getMappedPort(61616);

        // Both runs must share the object store (the transaction log written before the crash
        // is what recovery replays) and the crash marker (tells the restarted run not to crash
        // again). Absolute paths keep this independent of the integration's working directory.
        Path recoveryDir = createRecoveryDir(afterAll);
        Path objectStoreDir = recoveryDir.resolve("tx-object-store");
        Path crashMarker = recoveryDir.resolve("crash.marker");

        Resource properties = PropertiesTemplateHelper.createFromTemplate(
                classResource("forage-connectionfactory.properties.template"),
                Map.of(
                        "forage\\.jms\\.broker\\.url=.*",
                                Matcher.quoteReplacement("forage.jms.broker.url=" + brokerUrl),
                        "OBJECT_STORE_DIR_PLACEHOLDER", Matcher.quoteReplacement(objectStoreDir.toString()),
                        "CRASH_MARKER_PLACEHOLDER", Matcher.quoteReplacement(crashMarker.toString())),
                afterAll);
        // The route file must sit next to the templated properties: the run action derives the
        // integration's working directory from its resource files, and both runs must use the
        // same one.
        PropertiesTemplateHelper.copyIntoSameDirectory(properties, classResource("CrashRecoveryRoute.java"), afterAll);
        Resource route = Resources.create(properties
                .file()
                .toPath()
                .getParent()
                .resolve("CrashRecoveryRoute.java")
                .toFile());

        // First run: prepares the XA transaction and halts the JVM before the JMS branch commits.
        runner.when(camel().cli()
                .custom("forage", "run")
                .processName(CRASH_RUN)
                .addResource(properties)
                .addResource(route)
                .dumpIntegrationOutput(true));
        registerIntegrationCleanup(runner, CRASH_RUN, afterAll);

        // The crash is the integration halting itself: trigger it only now that the run action
        // has verified the integration is up (a startup-relative timer races that verification
        // on slow machines), then wait for the process to die.
        runner.run((TestAction) context -> {
            long pid = Long.parseLong(
                    context.getVariables().get(CRASH_RUN + ":pid").toString());
            try {
                Files.createFile(Path.of(crashMarker + ".go"));
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to create the crash go file", e);
            }
            awaitProcessDeath(pid);
            if (!Files.exists(crashMarker)) {
                throw new IllegalStateException(
                        "Integration process died without reaching the crash point (no crash marker written)");
            }
            LOG.info("Integration crashed between prepare and commit (pid {})", pid);
        });

        // In-doubt guard: the message must NOT be visible before recovery. This also protects the
        // enlistment-order assumption — if the JMS branch had committed before the halt, the
        // message would already be on the queue and recovery would have nothing to prove.
        assertMessageNotVisible(brokerUrl);

        // Second run: same properties, same object store. The recovery manager must replay the
        // transaction log and commit the in-doubt JMS branch through the registered helper.
        runner.when(camel().cli()
                .custom("forage", "run")
                .processName(RESTART_RUN)
                .addResource(properties)
                .addResource(route)
                .dumpIntegrationOutput(true));

        return RESTART_RUN;
    }

    @Test
    @CitrusTest
    public void inDoubtBranchIsCommittedByRecovery(ForageTestCaseRunner runner) {
        // logged by the route's consumer once the recovery manager commits the in-doubt branch
        // (recovery scan period is 5s, so this resolves within the first scans)
        runner.then(camel().cli()
                .verify()
                .integration(RESTART_RUN)
                .waitForLogMessage("Recovered message received: XA crash recovery test message"));
    }

    private static Path createRecoveryDir(Consumer<AutoCloseable> afterAll) {
        try {
            Path recoveryDir = Files.createTempDirectory("forage-recovery-");
            afterAll.accept(() -> {
                try (var paths = Files.walk(recoveryDir)) {
                    paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            LOG.warn("Could not delete {}", path, e);
                        }
                    });
                }
            });
            return recoveryDir;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create recovery temp directory", e);
        }
    }

    private static void awaitProcessDeath(long pid) {
        long deadline = System.currentTimeMillis() + 90_000;
        while (System.currentTimeMillis() < deadline) {
            boolean alive = ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
            if (!alive) {
                return;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for the integration to crash", e);
            }
        }
        throw new IllegalStateException("Integration process " + pid + " did not crash within 90s");
    }

    private static void assertMessageNotVisible(String brokerUrl) {
        try (ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(brokerUrl);
                Connection connection = connectionFactory.createConnection("artemis", "artemis");
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            connection.start();
            MessageConsumer consumer = session.createConsumer(session.createQueue("recovery.output"));
            Message message = consumer.receive(2_000);
            if (message != null) {
                throw new IllegalStateException("The message is visible on recovery.output before recovery ran — "
                        + "the JMS branch was not left in doubt by the crash");
            }
        } catch (jakarta.jms.JMSException e) {
            throw new IllegalStateException("Failed to probe recovery.output", e);
        }
    }
}
