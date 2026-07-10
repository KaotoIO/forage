package io.kaoto.forage.integration.tests;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.citrusframework.AbstractTestActionBuilder;
import org.citrusframework.actions.AbstractTestAction;
import org.citrusframework.camel.jbang.CamelJBang;
import org.citrusframework.context.TestContext;
import org.citrusframework.exceptions.CitrusRuntimeException;
import org.citrusframework.jbang.ProcessAndOutput;
import org.citrusframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends a message to a running Camel integration via {@code camel cmd send}, addressing the
 * integration by the PID of its {@code Running} process instead of by name.
 *
 * <p>Workaround for CAMEL-24002 (Camel JBang 4.20): with exported runtimes (spring-boot, quarkus)
 * the launcher process leaves a {@code Terminated} status entry under the same integration name,
 * and {@code camel cmd send <name>} fails with "matches 2 running Camel integrations" while still
 * exiting 0 — so the Citrus {@code CamelCmdSendAction} reports success although nothing was sent.
 *
 * <p>This action:
 * <ul>
 *   <li>waits until exactly the integration's {@code Running} process (ready 1/1) is visible,
 *       which also protects against sending before an exported runtime finished starting;</li>
 *   <li>sends by PID, so leftover {@code Terminated} entries cannot make the name ambiguous;</li>
 *   <li>validates the command output and fails loudly on {@code Send timeout} or any output
 *       without the {@code Sent} success marker, instead of trusting the exit code.</li>
 * </ul>
 */
public class ForageCmdSendAction extends AbstractTestAction {

    private static final Logger LOG = LoggerFactory.getLogger(ForageCmdSendAction.class);

    private static final long READY_TIMEOUT_MILLIS = 60_000;
    private static final long READY_POLL_MILLIS = 1_000;

    private final String integrationName;
    private final String endpoint;
    private final String body;
    private final Map<String, String> headers;

    private ForageCmdSendAction(Builder builder) {
        super("forage-cmd-send", builder);
        this.integrationName = builder.integrationName;
        this.endpoint = builder.endpoint;
        this.body = builder.body;
        this.headers = builder.headers;
    }

    @Override
    public void doExecute(TestContext context) {
        String name = context.replaceDynamicContentInString(integrationName);
        long pid = waitForRunningPid(name);

        List<String> args = new ArrayList<>();
        args.add(String.valueOf(pid));
        args.add("--endpoint=" + context.replaceDynamicContentInString(endpoint));
        // JBangSupport re-splits arguments on whitespace, so values with spaces must be quoted
        args.add("--body=" + StringUtils.quote(context.replaceDynamicContentInString(body), true));
        headers.forEach((k, v) ->
                args.add("--header=" + StringUtils.quote(k + "=" + context.replaceDynamicContentInString(v), true)));

        LOG.info("Sending message to Camel integration '{}' (pid: {}) endpoint {}", name, pid, endpoint);
        ProcessAndOutput result = CamelJBang.camel().send(args.toArray(String[]::new));
        String output = result.getOutput();

        // camel cmd send exits 0 even on failure, so validate the output instead
        if (output == null || !output.contains("Sent") || output.contains("Send timeout")) {
            throw new CitrusRuntimeException(
                    "camel cmd send did not confirm delivery to integration '%s' (pid: %d). Output: %s"
                            .formatted(name, pid, output));
        }
        LOG.info("Message delivered to Camel integration '{}' (pid: {})", name, pid);
    }

    /**
     * Resolves the PID of the integration's process that is both {@code Running} and fully ready
     * ({@code 1/1}), retrying until it appears. Leftover {@code Terminated} launcher entries with
     * the same name are ignored.
     */
    private long waitForRunningPid(String name) {
        long deadline = System.currentTimeMillis() + READY_TIMEOUT_MILLIS;
        while (true) {
            for (Map<String, String> row : CamelJBang.camel().getAll()) {
                if (name.equals(row.get("name"))
                        && "Running".equals(row.get("status"))
                        && "1/1".equals(row.get("ready"))) {
                    return Long.parseLong(row.get("pid"));
                }
            }
            if (System.currentTimeMillis() > deadline) {
                throw new CitrusRuntimeException("No running Camel integration named '%s' found within %d ms"
                        .formatted(name, READY_TIMEOUT_MILLIS));
            }
            try {
                Thread.sleep(READY_POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CitrusRuntimeException("Interrupted while waiting for integration '%s'".formatted(name), e);
            }
        }
    }

    public static final class Builder extends AbstractTestActionBuilder<ForageCmdSendAction, Builder> {

        private String integrationName;
        private String endpoint;
        private String body;
        private final Map<String, String> headers = new LinkedHashMap<>();

        public Builder integration(String integrationName) {
            this.integrationName = integrationName;
            return this;
        }

        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public Builder body(String body) {
            this.body = body;
            return this;
        }

        public Builder header(String name, String value) {
            this.headers.put(name, value);
            return this;
        }

        @Override
        public ForageCmdSendAction build() {
            return new ForageCmdSendAction(this);
        }
    }
}
