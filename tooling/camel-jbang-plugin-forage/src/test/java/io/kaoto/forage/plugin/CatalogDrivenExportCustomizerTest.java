package io.kaoto.forage.plugin;

import java.io.File;
import java.io.FileWriter;
import java.util.Set;
import io.kaoto.forage.core.common.RuntimeType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
        // Write properties to temp dir
        File propsFile = new File(tempDir, "application.properties");
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
