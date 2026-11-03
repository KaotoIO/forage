package io.kaoto.forage.jms;

import java.util.Map;
import org.eclipse.microprofile.config.ConfigProvider;
import org.testcontainers.activemq.ArtemisContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

/**
 * Singleton Testcontainers shared by all test classes and runtime suites in this module.
 *
 * <p>The three runtime suites (plain, Quarkus, Spring Boot) run the same test classes within one
 * JVM, so class-scoped {@code @Container} fields restarted each broker once per class per suite
 * (IBM MQ 3 times per run at ~23s each). Containers here start lazily on first use and live until
 * the JVM exits, where Testcontainers' Ryuk reaps them (#434).
 *
 * <p>Because brokers survive across suites, tests must tolerate pre-existing broker state:
 * destinations may already exist and the DLQ accumulates messages, so assertions have to verify
 * each suite's freshly started integration processes (e.g. via their logs), not broker contents.
 */
final class JmsContainers {

    static final String ARTEMIS_IMAGE_NAME =
            ConfigProvider.getConfig().getValue("activemq.artemis.container.image", String.class);
    static final String IBMMQ_IMAGE_NAME = ConfigProvider.getConfig().getValue("ibmmq.container.image", String.class);
    static final String POSTGRES_IMAGE_NAME =
            ConfigProvider.getConfig().getValue("postgres.container.image", String.class);

    static final int IBMMQ_PORT = 1414;
    static final String IBMMQ_QUEUE_MANAGER_NAME = "QM1";
    static final String IBMMQ_USER = "app";
    static final String IBMMQ_PASSWORD = "passw0rd";
    static final String IBMMQ_MESSAGING_CHANNEL = "DEV.APP.SVRCONN";
    private static final String MQSC_COMMAND_FILE_NAME = "99-auth.mqsc";
    private static final String MQSC_FILE_CONTAINER_PATH = "/etc/mqm/" + MQSC_COMMAND_FILE_NAME;

    private static final ArtemisContainer ARTEMIS = new ArtemisContainer(
                    DockerImageName.parse(ARTEMIS_IMAGE_NAME).asCompatibleSubstituteFor("apache/activemq-artemis"))
            .withExposedPorts(61616, 8161)
            .withUser("artemis")
            .withPassword("artemis")
            .withEnv("JAVA_ARGS", "-Dbrokerconfig.maxDiskUsage=-1");

    private static final GenericContainer<?> IBMMQ = new GenericContainer<>(DockerImageName.parse(IBMMQ_IMAGE_NAME))
            .withExposedPorts(IBMMQ_PORT)
            .withEnv(Map.of("LICENSE", "accept", "MQ_QMGR_NAME", IBMMQ_QUEUE_MANAGER_NAME))
            .withCopyToContainer(Transferable.of(IBMMQ_PASSWORD), "/run/secrets/mqAdminPassword")
            .withCopyToContainer(Transferable.of(IBMMQ_PASSWORD), "/run/secrets/mqAppPassword")
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
        return "SET AUTHREC PROFILE('*') PRINCIPAL('" + IBMMQ_USER + "') OBJTYPE(TOPIC) AUTHADD(ALL)\n"
                + "SET AUTHREC PROFILE('*') PRINCIPAL('" + IBMMQ_USER + "') OBJTYPE(QUEUE) AUTHADD(ALL)\n"
                + "SET AUTHREC PROFILE('SYSTEM.DEFAULT.MODEL.QUEUE') OBJTYPE(QUEUE) PRINCIPAL('" + IBMMQ_USER
                + "') AUTHADD(ALL)\n"
                + "SET AUTHREC PROFILE('input.queue') PRINCIPAL('app') OBJTYPE(QMGR) AUTHADD(CONNECT,INQ)\n"
                + "SET AUTHREC PROFILE('input.queue') PRINCIPAL('app') OBJTYPE(QUEUE) AUTHADD(PUT,GET,INQ,BROWSE)\n"
                + "SET AUTHREC PROFILE('output.queue') PRINCIPAL('app') OBJTYPE(QUEUE) AUTHADD(PUT,GET,INQ,BROWSE)";
    }

    // holds the Narayana transaction log of the JDBC object store recovery test (#447)
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
                    DockerImageName.parse(POSTGRES_IMAGE_NAME).asCompatibleSubstituteFor("postgres"))
            .withExposedPorts(5432)
            .withUsername("test")
            .withPassword("test")
            .withDatabaseName("txlog");

    private JmsContainers() {}

    static synchronized PostgreSQLContainer<?> postgres() {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
        return POSTGRES;
    }

    static synchronized ArtemisContainer artemis() {
        if (!ARTEMIS.isRunning()) {
            ARTEMIS.start();
        }
        return ARTEMIS;
    }

    static synchronized GenericContainer<?> ibmmq() {
        if (!IBMMQ.isRunning()) {
            IBMMQ.start();
        }
        return IBMMQ;
    }
}
