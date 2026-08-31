import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import jakarta.transaction.TransactionManager;

import org.apache.camel.builder.RouteBuilder;

/**
 * Crashes the JVM between the prepare and commit phase of an XA transaction (issue #432).
 *
 * <p>The crash-producer route enlists a {@link CrashingXAResource} <em>before</em> the JMS send,
 * so Narayana's commit order is [crasher, JMS branch]: both branches prepare, the commit decision
 * is written to the object store, then the crasher halts the JVM before the JMS branch commits.
 * The message is therefore prepared-but-uncommitted on the broker (in doubt) until the restarted
 * run's recovery manager replays the transaction log and commits it through the registered
 * JMS recovery helper.
 */
public class CrashRecoveryRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        String markerFile = getContext().resolvePropertyPlaceholders("{{crash.marker.file}}");
        // The test creates the go file only after the run action has verified the integration is
        // up; a startup-relative timer would race the verification on slow machines (the process
        // dying mid-verification fails the run action with exit code 137).
        Path goFile = Path.of(markerFile + ".go");
        boolean restartRun = Files.exists(Path.of(markerFile));

        // In the restarted run this consumer receives the message committed by crash recovery.
        from("jms:queue:recovery.output")
                .routeId("recovery-output-consumer")
                .log("Recovered message received: ${body}");

        if (restartRun) {
            log.info("Restart run detected (crash marker exists) - waiting for transaction recovery");
            return;
        }

        from("timer:crash?period=500")
                .routeId("crash-producer-route")
                // transacted must precede all other steps in the route; the filter therefore sits
                // inside the transaction (an empty tick's transaction is a cheap no-op commit)
                .transacted("PROPAGATION_REQUIRED")
                .filter(exchange -> Files.exists(goFile) && !Files.exists(Path.of(markerFile)))
                .process(exchange -> {
                    Files.createFile(Path.of(markerFile));
                    TransactionManager transactionManager =
                            com.arjuna.ats.jta.TransactionManager.transactionManager();
                    transactionManager.getTransaction().enlistResource(new CrashingXAResource());
                    exchange.getMessage().setBody("XA crash recovery test message");
                })
                .log("Enlisted crashing XAResource - sending message within the XA transaction")
                .to("jms:queue:recovery.output")
                // never reached: the crasher halts the JVM during the commit phase
                .log("UNEXPECTED: transaction committed without crashing");
    }

    /**
     * Votes {@code XA_OK} on prepare, then halts the JVM on commit. Serializable so Narayana
     * records it in the transaction log; the instance deserialized during recovery has
     * {@code live == false} (transient) and commits normally, letting the log entry resolve.
     */
    public static class CrashingXAResource implements XAResource, Serializable {

        private static final long serialVersionUID = 1L;

        private final transient boolean live;

        public CrashingXAResource() {
            this.live = true;
        }

        @Override
        public int prepare(Xid xid) {
            return XA_OK;
        }

        @Override
        public void commit(Xid xid, boolean onePhase) {
            if (live) {
                System.out.println("CrashingXAResource: halting the JVM between prepare and commit");
                Runtime.getRuntime().halt(137);
            }
        }

        @Override
        public void rollback(Xid xid) {
            // XA rollback is a no-op for this crash-test resource: the JVM halts on commit,
            // so rollback is never called in the normal test flow.
        }

        @Override
        public void start(Xid xid, int flags) {
            // XA start requires no state tracking for this crash-test-only resource.
        }

        @Override
        public void end(Xid xid, int flags) {
            // XA end requires no state tracking for this crash-test-only resource.
        }

        @Override
        public void forget(Xid xid) {
            // Heuristic forget is not applicable for this crash-test-only resource.
        }

        @Override
        public Xid[] recover(int flag) {
            return new Xid[0];
        }

        @Override
        public boolean isSameRM(XAResource xaResource) {
            return xaResource == this;
        }

        @Override
        public int getTransactionTimeout() {
            return 0;
        }

        @Override
        public boolean setTransactionTimeout(int seconds) {
            return false;
        }
    }
}
