package io.kaoto.forage.plugin;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import io.kaoto.forage.catalog.model.ConfigEntry;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;
import io.kaoto.forage.core.common.RuntimeType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the CatalogDrivenExportCustomizer.
 */
class CatalogDrivenExportCustomizerTest {

    @TempDir
    File tempDir;

    private String originalUserDir;

    @BeforeEach
    void setUp() {
        originalUserDir = System.getProperty("user.dir");
    }

    @AfterEach
    void tearDown() {
        System.setProperty("user.dir", originalUserDir);
    }

    private CatalogDrivenExportCustomizer createCustomizer(String propertiesContent) throws Exception {
        return createCustomizer("application.properties", propertiesContent);
    }

    private CatalogDrivenExportCustomizer createCustomizer(String fileName, String propertiesContent) throws Exception {
        // Write properties to temp dir
        File propsFile = new File(tempDir, fileName);
        try (FileWriter writer = new FileWriter(propsFile)) {
            writer.write(propertiesContent);
        }
        System.setProperty("user.dir", tempDir.getAbsolutePath());

        // Create a fresh customizer (clears cached state)
        return new CatalogDrivenExportCustomizer();
    }

    @Test
    void testNoProperties_isNotEnabled() throws Exception {
        // Empty temp dir, no properties
        System.setProperty("user.dir", tempDir.getAbsolutePath());
        CatalogDrivenExportCustomizer customizer = new CatalogDrivenExportCustomizer();
        assertThat(customizer.isEnabled()).isFalse();
        assertThat(customizer.resolveRuntimeDependencies(RuntimeType.main)).isEmpty();
    }

    @Test
    void testJdbcPostgresql_mainRuntime() throws Exception {
        CatalogDrivenExportCustomizer customizer =
                createCustomizer("forage.jdbc.db.kind=postgresql\nforage.jdbc.url=jdbc:postgresql://localhost/test\n");
        assertThat(customizer.isEnabled()).isTrue();

        Set<String> deps = customizer.resolveRuntimeDependencies(RuntimeType.main);
        // Should include the base JDBC GAV and the postgresql bean GAV
        assertThat(deps).anyMatch(d -> d.contains("forage-jdbc:"));
        assertThat(deps).anyMatch(d -> d.contains("forage-jdbc-postgresql:"));
    }

    @Test
    void testJdbcPostgresql_quarkusRuntime() throws Exception {
        CatalogDrivenExportCustomizer customizer =
                createCustomizer("forage.jdbc.db.kind=postgresql\nforage.jdbc.url=jdbc:postgresql://localhost/test\n");

        Set<String> deps = customizer.resolveRuntimeDependencies(RuntimeType.quarkus);
        // Should include quarkus variant GAV
        assertThat(deps).anyMatch(d -> d.contains("forage-quarkus-jdbc"));
        // Should include camel-quarkus-sql (additional dependency)
        assertThat(deps).anyMatch(d -> d.contains("camel-quarkus-sql"));
        // Should include quarkus-jdbc-postgresql (bean runtime dependency)
        assertThat(deps).anyMatch(d -> d.contains("quarkus-jdbc-postgresql"));
    }

    @Test
    void testJdbcWithTransaction_quarkusRuntime() throws Exception {
        CatalogDrivenExportCustomizer customizer =
                createCustomizer("forage.jdbc.db.kind=postgresql\nforage.jdbc.url=jdbc:postgresql://localhost/test\n"
                        + "forage.jdbc.transaction.enabled=true\n");

        Set<String> deps = customizer.resolveRuntimeDependencies(RuntimeType.quarkus);
        // Should include narayana-jta for quarkus transactions
        assertThat(deps).anyMatch(d -> d.contains("quarkus-narayana-jta"));
    }

    @Test
    void testJms_mainRuntime() throws Exception {
        CatalogDrivenExportCustomizer customizer =
                createCustomizer("forage.jms.kind=artemis\nforage.jms.broker.url=tcp://localhost:61616\n");

        Set<String> deps = customizer.resolveRuntimeDependencies(RuntimeType.main);
        // Should include base JMS GAV and artemis bean GAV
        assertThat(deps).anyMatch(d -> d.contains("forage-jms:"));
        assertThat(deps).anyMatch(d -> d.contains("forage-jms-artemis:"));
    }

    @Test
    void testJmsArtemis_quarkusRuntime() throws Exception {
        CatalogDrivenExportCustomizer customizer =
                createCustomizer("forage.jms.kind=artemis\nforage.jms.broker.url=tcp://localhost:61616\n");

        Set<String> deps = customizer.resolveRuntimeDependencies(RuntimeType.quarkus);
        // Should include quarkus JMS variant GAV
        assertThat(deps).anyMatch(d -> d.contains("forage-quarkus-jms"));
        // Should include camel-quarkus-jms (additional dependency)
        assertThat(deps).anyMatch(d -> d.contains("camel-quarkus-jms"));
        // Should include quarkus-artemis-jms (bean runtime dependency)
        assertThat(deps).anyMatch(d -> d.contains("quarkus-artemis-jms"));
    }

    @Test
    void testJmsWithTransaction_quarkusRuntime() throws Exception {
        CatalogDrivenExportCustomizer customizer =
                createCustomizer("forage.jms.kind=artemis\nforage.jms.broker.url=tcp://localhost:61616\n"
                        + "forage.jms.transaction.enabled=true\n");

        Set<String> deps = customizer.resolveRuntimeDependencies(RuntimeType.quarkus);
        // Should include narayana-jta and pooled-jms for JMS quarkus transactions
        assertThat(deps).anyMatch(d -> d.contains("quarkus-narayana-jta"));
        assertThat(deps).anyMatch(d -> d.contains("quarkus-pooled-jms"));
    }

    @Test
    void testJmsWithPool_quarkusRuntime() throws Exception {
        CatalogDrivenExportCustomizer customizer =
                createCustomizer("forage.jms.kind=artemis\nforage.jms.broker.url=tcp://localhost:61616\n"
                        + "forage.jms.pool.enabled=true\n");

        Set<String> deps = customizer.resolveRuntimeDependencies(RuntimeType.quarkus);
        // Should include quarkus-pooled-jms for JMS pool
        assertThat(deps).anyMatch(d -> d.contains("quarkus-pooled-jms"));
    }

    @Test
    void testMultipleFactories() throws Exception {
        CatalogDrivenExportCustomizer customizer =
                createCustomizer("forage.jdbc.db.kind=mysql\nforage.jdbc.url=jdbc:mysql://localhost/test\n"
                        + "forage.jms.kind=artemis\nforage.jms.broker.url=tcp://localhost:61616\n");

        Set<String> deps = customizer.resolveRuntimeDependencies(RuntimeType.main);
        // Should include both JDBC and JMS dependencies
        assertThat(deps).anyMatch(d -> d.contains("forage-jdbc:"));
        assertThat(deps).anyMatch(d -> d.contains("forage-jdbc-mysql:"));
        assertThat(deps).anyMatch(d -> d.contains("forage-jms:"));
        assertThat(deps).anyMatch(d -> d.contains("forage-jms-artemis:"));
    }

    @Test
    void testNamedInstance_mainRuntime() throws Exception {
        CatalogDrivenExportCustomizer customizer = createCustomizer(
                "forage.ds1.jdbc.db.kind=postgresql\nforage.ds1.jdbc.url=jdbc:postgresql://localhost/test\n");
        assertThat(customizer.isEnabled()).isTrue();

        Set<String> deps = customizer.resolveRuntimeDependencies(RuntimeType.main);
        assertThat(deps).anyMatch(d -> d.contains("forage-jdbc:"));
        assertThat(deps).anyMatch(d -> d.contains("forage-jdbc-postgresql:"));
    }

    // Route policies (#231): one factory and one module per policy, the same on every runtime

    private static final String FLIP_POLICY_PROPERTIES = "forage.route.policy.enabled=true\n"
            + "forage.route.policy.route-primary.name=flip\n"
            + "forage.route.policy.route-primary.flip.paired-route=route-secondary\n"
            + "forage.route.policy.route-primary.flip.initially-active=true\n"
            + "forage.route.policy.route-secondary.name=flip\n"
            + "forage.route.policy.route-secondary.flip.paired-route=route-primary\n"
            + "forage.route.policy.route-secondary.flip.initially-active=false\n";

    @ParameterizedTest
    @EnumSource(RuntimeType.class)
    void testFlipRoutePolicy_allRuntimes(RuntimeType runtime) throws Exception {
        // The test plan's file name: picked up as a forage-*.properties file
        CatalogDrivenExportCustomizer customizer = createCustomizer("forage-policy.properties", FLIP_POLICY_PROPERTIES);
        assertThat(customizer.isEnabled()).isTrue();

        Set<String> deps = customizer.resolveRuntimeDependencies(runtime);
        assertThat(deps).anyMatch(d -> d.contains("forage-policy-factory:"));
        assertThat(deps).anyMatch(d -> d.contains("forage-policy-flip:"));
        assertThat(deps).noneMatch(d -> d.contains("forage-policy-schedule:"));
    }

    @ParameterizedTest
    @EnumSource(RuntimeType.class)
    void testScheduleRoutePolicy_allRuntimes(RuntimeType runtime) throws Exception {
        CatalogDrivenExportCustomizer customizer =
                createCustomizer("forage.route.policy.scheduled-route.name=schedule\n"
                        + "forage.route.policy.scheduled-route.schedule.start-time=00:00\n"
                        + "forage.route.policy.scheduled-route.schedule.stop-time=23:59\n");

        Set<String> deps = customizer.resolveRuntimeDependencies(runtime);
        assertThat(deps).anyMatch(d -> d.contains("forage-policy-factory:"));
        assertThat(deps).anyMatch(d -> d.contains("forage-policy-schedule:"));
        assertThat(deps).noneMatch(d -> d.contains("forage-policy-flip:"));
    }

    @Test
    void testRoutePolicyList_resolvesEveryPolicy() throws Exception {
        // A route ID with dots, and several policies for one route
        CatalogDrivenExportCustomizer customizer =
                createCustomizer("forage.route.policy.orders.v2.name=schedule, flip\n");

        Set<String> deps = customizer.resolveRuntimeDependencies(RuntimeType.MAIN);
        assertThat(deps).anyMatch(d -> d.contains("forage-policy-factory:"));
        assertThat(deps).anyMatch(d -> d.contains("forage-policy-schedule:"));
        assertThat(deps).anyMatch(d -> d.contains("forage-policy-flip:"));
    }

    @Test
    void testCommaSeparatedValueOfSingleValueSelector_resolvesNoBean() throws Exception {
        // db.kind is not multi-value: "postgresql,mysql" is one unknown kind, as the runtime sees it
        CatalogDrivenExportCustomizer customizer = createCustomizer("forage.jdbc.db.kind=postgresql,mysql\n");

        Set<String> deps = customizer.resolveRuntimeDependencies(RuntimeType.MAIN);
        assertThat(deps).anyMatch(d -> d.contains("forage-jdbc:"));
        assertThat(deps).noneMatch(d -> d.contains("forage-jdbc-postgresql:"));
        assertThat(deps).noneMatch(d -> d.contains("forage-jdbc-mysql:"));
    }

    @Test
    void testOnlyRoutePolicyNameIsMultiValue() throws Exception {
        List<String> multiValueEntries = new ArrayList<>();
        for (ForageCatalogReader.FactoryMetadata factory :
                ForageCatalogReader.getInstance().getAllFactories()) {
            for (ConfigEntry entry : factory.configEntries()) {
                if (entry.isMultiValue()) {
                    multiValueEntries.add(entry.getName());
                }
            }
        }
        assertThat(multiValueEntries).containsExactly("forage.route.policy.{routeId}.name");

        // The flag is only written when true
        try (InputStream catalog = getClass().getClassLoader().getResourceAsStream("catalog/forage-catalog.json")) {
            String json = new String(catalog.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(json).contains("\"multiValue\" : true").doesNotContain("\"multiValue\" : false");
        }
    }

    @Test
    void testRoutePolicyOptions_resolvePolicyWithoutName() throws Exception {
        // Policy options alone are enough to add the policy module
        CatalogDrivenExportCustomizer customizer =
                createCustomizer("forage.route.policy.route-a.flip.paired-route=route-b\n");

        Set<String> deps = customizer.resolveRuntimeDependencies(RuntimeType.MAIN);
        assertThat(deps).anyMatch(d -> d.contains("forage-policy-factory:"));
        assertThat(deps).anyMatch(d -> d.contains("forage-policy-flip:"));
        assertThat(deps).noneMatch(d -> d.contains("forage-policy-schedule:"));
    }

    // RAG (#229): the agent variant differs per runtime, the RAG modules do not

    private static final String RAG_PROPERTIES = "forage.myAgent.agent.model.kind=ollama\n"
            + "forage.myAgent.agent.model.name=granite4:3b\n"
            + "forage.myAgent.agent.base.url=http://localhost:11434\n"
            + "forage.myAgent.agent.embedding.model.name=granite-embedding:latest\n"
            + "forage.myAgent.agent.embedding.model.base.url=http://localhost:11434\n"
            + "forage.myAgent.agent.embedding.model.timeout=PT30S\n"
            + "forage.myAgent.agent.in.memory.store.file.source=company-knowledge-base.txt\n"
            + "forage.myAgent.agent.in.memory.store.max.size=300\n"
            + "forage.myAgent.agent.in.memory.store.overlap.size=100\n"
            + "forage.myAgent.agent.rag.max.results=3\n"
            + "forage.myAgent.agent.rag.min.score=0.6\n";

    static Stream<Arguments> agentVariants() {
        return Stream.of(
                Arguments.of(RuntimeType.MAIN, "forage-agent:"),
                Arguments.of(RuntimeType.SPRING_BOOT, "forage-agent-starter:"),
                Arguments.of(RuntimeType.QUARKUS, "forage-quarkus-agent-deployment:"));
    }

    @ParameterizedTest
    @MethodSource("agentVariants")
    void testRagWithInMemoryStore_allRuntimes(RuntimeType runtime, String agentArtifact) throws Exception {
        // The properties of the RAG example (website/docs/examples/ai/rag.md)
        CatalogDrivenExportCustomizer customizer = createCustomizer("forage-agent-factory.properties", RAG_PROPERTIES);

        Set<String> deps = customizer.resolveRuntimeDependencies(runtime);
        assertThat(deps).anyMatch(d -> d.contains(agentArtifact));
        assertThat(deps).anyMatch(d -> d.contains("forage-model-ollama:"));
        assertThat(deps).anyMatch(d -> d.contains("forage-model-embeddings-ollama:"));
        assertThat(deps).anyMatch(d -> d.contains("forage-in-memory-store:"));
        assertThat(deps).anyMatch(d -> d.contains("forage-default-retrieval-augmentor:"));
    }

    @ParameterizedTest
    @MethodSource("agentVariants")
    void testRagWithoutRetrievalProperties_allRuntimes(RuntimeType runtime, String agentArtifact) throws Exception {
        // The agent builds its RAG pipeline from the embedding model and store alone: the retrieval
        // augmentor is still needed when no forage.agent.rag.* property is set
        CatalogDrivenExportCustomizer customizer = createCustomizer("forage.agent.model.kind=ollama\n"
                + "forage.agent.embedding.model.name=granite-embedding:latest\n"
                + "forage.agent.in.memory.store.file.source=company-knowledge-base.txt\n");

        Set<String> deps = customizer.resolveRuntimeDependencies(runtime);
        assertThat(deps).anyMatch(d -> d.contains(agentArtifact));
        assertThat(deps).anyMatch(d -> d.contains("forage-in-memory-store:"));
        assertThat(deps).anyMatch(d -> d.contains("forage-default-retrieval-augmentor:"));
    }

    @Test
    void testRagEnabledByEmbeddingModelOnly() throws Exception {
        // A vector database selected by embedding.store.kind is not in the catalog yet (#538), but the
        // retrieval augmentor is resolved from the embedding model property
        CatalogDrivenExportCustomizer customizer = createCustomizer(
                "forage.agent.model.kind=ollama\n" + "forage.agent.embedding.model.name=granite-embedding:latest\n");

        Set<String> deps = customizer.resolveRuntimeDependencies(RuntimeType.MAIN);
        assertThat(deps).anyMatch(d -> d.contains("forage-default-retrieval-augmentor:"));
        assertThat(deps).noneMatch(d -> d.contains("forage-in-memory-store:"));
    }

    @Test
    void testRagWithAgentNamedAfterBean() throws Exception {
        // The forage-examples RAG agent is named "ollama", like the chat model bean: the scanner keeps the
        // instance name in the property keys
        CatalogDrivenExportCustomizer customizer = createCustomizer("forage.ollama.agent.model.kind=ollama\n"
                + "forage.ollama.agent.embedding.model.name=granite-embedding:latest\n"
                + "forage.ollama.agent.in.memory.store.file.source=company-knowledge-base.txt\n");

        Set<String> deps = customizer.resolveRuntimeDependencies(RuntimeType.MAIN);
        assertThat(deps).anyMatch(d -> d.contains("forage-model-ollama:"));
        assertThat(deps).anyMatch(d -> d.contains("forage-in-memory-store:"));
        assertThat(deps).anyMatch(d -> d.contains("forage-default-retrieval-augmentor:"));
    }

    @Test
    void testEmbeddingStoreKind_resolvesInMemoryStore() throws Exception {
        CatalogDrivenExportCustomizer customizer = createCustomizer("forage.agent.model.kind=ollama\n"
                + "forage.agent.embedding.model.name=granite-embedding:latest\n"
                + "forage.agent.embedding.store.kind=in-memory-store\n");

        Set<String> deps = customizer.resolveRuntimeDependencies(RuntimeType.MAIN);
        assertThat(deps).anyMatch(d -> d.contains("forage-in-memory-store:"));
        assertThat(deps).anyMatch(d -> d.contains("forage-default-retrieval-augmentor:"));
    }

    @Test
    void testAgentWithoutRag_hasNoRagDependencies() throws Exception {
        // A provider-level embedding property does not turn the agent RAG pipeline on
        CatalogDrivenExportCustomizer customizer = createCustomizer("forage.agent.model.kind=ollama\n"
                + "forage.agent.model.name=granite4:3b\n"
                + "forage.ollama.embedding.model.name=granite-embedding:latest\n");

        Set<String> deps = customizer.resolveRuntimeDependencies(RuntimeType.MAIN);
        assertThat(deps).anyMatch(d -> d.contains("forage-model-ollama:"));
        assertThat(deps).noneMatch(d -> d.contains("forage-in-memory-store:"));
        assertThat(deps).noneMatch(d -> d.contains("forage-default-retrieval-augmentor:"));
    }

    @Test
    void testMultipleNamedInstances_springBootRuntime() throws Exception {
        // This is the MultiTest scenario: ds1=mysql, ds2=postgresql
        CatalogDrivenExportCustomizer customizer =
                createCustomizer("forage.ds1.jdbc.db.kind=mysql\nforage.ds1.jdbc.url=jdbc:mysql://localhost/test\n"
                        + "forage.ds2.jdbc.db.kind=postgresql\nforage.ds2.jdbc.url=jdbc:postgresql://localhost/test\n");
        assertThat(customizer.isEnabled()).isTrue();

        Set<String> deps = customizer.resolveRuntimeDependencies(RuntimeType.springBoot);
        // Should include BOTH db kind dependencies
        assertThat(deps).anyMatch(d -> d.contains("forage-jdbc-mysql:"));
        assertThat(deps).anyMatch(d -> d.contains("forage-jdbc-postgresql:"));
        // Should include the spring boot starter (variant-specific, not base)
        assertThat(deps).anyMatch(d -> d.contains("forage-jdbc-starter:"));
    }
}
