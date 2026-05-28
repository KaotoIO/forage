package io.kaoto.forage.jms;

import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import org.citrusframework.annotations.CitrusTest;
import org.citrusframework.junit.jupiter.CitrusSupport;
import org.citrusframework.spi.Resource;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import io.kaoto.forage.integration.tests.ForageIntegrationTest;
import io.kaoto.forage.integration.tests.ForageTestCaseRunner;
import io.kaoto.forage.integration.tests.IntegrationTestSetupExtension;
import io.kaoto.forage.integration.tests.PropertiesTemplateHelper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;

@CitrusSupport
@Testcontainers
@ExtendWith(IntegrationTestSetupExtension.class)
@DisabledOnOs(OS.MAC)
public class JmsIbmMqTest implements ForageIntegrationTest {
    private static final Logger LOG = LoggerFactory.getLogger(JmsIbmMqTest.class);

    private static final String IBMMQ_IMAGE_NAME =
            ConfigProvider.getConfig().getValue("ibmmq.container.image", String.class);
    public static final String INTEGRATION_NAME = "jms-routes";
    private static final int IBMMQ_PORT = 1414;
    private static final String QUEUE_MANAGER_NAME = "QM1";
    private static final String USER = "app";
    private static final String PASSWORD = "passw0rd";
    private static final String MESSAGING_CHANNEL = "DEV.APP.SVRCONN";
    private static final String MQSC_COMMAND_FILE_NAME = "99-auth.mqsc";
    private static final String MQSC_FILE_CONTAINER_PATH = "/etc/mqm/" + MQSC_COMMAND_FILE_NAME;

    @Container
    static GenericContainer<?> ibmmq = new GenericContainer<>(DockerImageName.parse(IBMMQ_IMAGE_NAME))
            .withExposedPorts(IBMMQ_PORT)
            .withEnv(Map.of("LICENSE", "accept", "MQ_QMGR_NAME", QUEUE_MANAGER_NAME))
            .withCopyToContainer(Transferable.of(PASSWORD), "/run/secrets/mqAdminPassword")
            .withCopyToContainer(Transferable.of(PASSWORD), "/run/secrets/mqAppPassword")
            .withCopyToContainer(Transferable.of(mqscConfig()), MQSC_FILE_CONTAINER_PATH)
            // AMQ5806I is a message code for queue manager start
            .waitingFor(Wait.forLogMessage(".*AMQ5806I.*", 1));

    /**
     * By default, the user does have access just to predefined queues, this will add permissions to access
     * all standard queues + topics and a special system queue.
     *
     * @return mqsc config string
     */
    private static String mqscConfig() {
        return "SET AUTHREC PROFILE('*') PRINCIPAL('" + USER + "') OBJTYPE(TOPIC) AUTHADD(ALL)\n"
                + "SET AUTHREC PROFILE('*') PRINCIPAL('" + USER + "') OBJTYPE(QUEUE) AUTHADD(ALL)\n"
                + "SET AUTHREC PROFILE('SYSTEM.DEFAULT.MODEL.QUEUE') OBJTYPE(QUEUE) PRINCIPAL('" + USER
                + "') AUTHADD(ALL)\n"
                + "SET AUTHREC PROFILE('input.queue') PRINCIPAL('app') OBJTYPE(QMGR) AUTHADD(CONNECT,INQ)\n"
                + "SET AUTHREC PROFILE('input.queue') PRINCIPAL('app') OBJTYPE(QUEUE) AUTHADD(PUT,GET,INQ,BROWSE)\n"
                + "SET AUTHREC PROFILE('output.queue') PRINCIPAL('app') OBJTYPE(QUEUE) AUTHADD(PUT,GET,INQ,BROWSE)";
    }

    @Override
    public String runBeforeAll(ForageTestCaseRunner runner, Consumer<AutoCloseable> afterAll) {
        // create queueu
        IBMMQDestinations destinations =
                new IBMMQDestinations(ibmmq.getHost(), ibmmq.getMappedPort(IBMMQ_PORT), QUEUE_MANAGER_NAME);
        destinations.createQueue("input.queue");
        destinations.createQueue("output.queue");
        destinations.createQueue("DLQ");
        destinations.createQueue("DLQ2");

        // Load template properties and replace testcontainer-specific values
        String brokerUrl = "mq://%s:%d/%s/%s"
                .formatted(ibmmq.getHost(), ibmmq.getMappedPort(1414), MESSAGING_CHANNEL, QUEUE_MANAGER_NAME);
        Resource dynamicProperties = PropertiesTemplateHelper.createFromTemplate(
                classResource("forage-connectionfactory.properties.template"),
                Map.of(
                        "forage\\.jms\\.broker\\.url=.*",
                        Matcher.quoteReplacement("forage.jms.broker.url=" + brokerUrl)),
                afterAll);

        // running jbang forage run with dynamically modified properties
        runner.when(camel().jbang()
                .custom("forage", "run")
                .processName(INTEGRATION_NAME)
                .addResource(dynamicProperties)
                .addResource(classResource("route-ibm.camel.yaml"))
                .dumpIntegrationOutput(true));

        return INTEGRATION_NAME;
    }

    @Test
    @CitrusTest()
    public void ibmMqTransactional(ForageTestCaseRunner runner) {
        // validation of logged message
        runner.then(camel().jbang()
                .verify()
                .integration(INTEGRATION_NAME)
                .waitForLogMessage("Successfully processed message: Transactional message"));
    }
}
