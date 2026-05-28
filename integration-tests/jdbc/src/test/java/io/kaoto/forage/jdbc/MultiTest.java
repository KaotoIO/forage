package io.kaoto.forage.jdbc;

import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import org.citrusframework.annotations.CitrusTest;
import org.citrusframework.junit.jupiter.CitrusSupport;
import org.citrusframework.spi.Resource;
import org.eclipse.microprofile.config.ConfigProvider;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import io.kaoto.forage.integration.tests.ForageIntegrationTest;
import io.kaoto.forage.integration.tests.ForageTestCaseRunner;
import io.kaoto.forage.integration.tests.IntegrationTestSetupExtension;
import io.kaoto.forage.integration.tests.PropertiesTemplateHelper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Test class starts route only once, before all tests are executed.
 */
@CitrusSupport
@Testcontainers
@ExtendWith(IntegrationTestSetupExtension.class)
public class MultiTest implements ForageIntegrationTest {

    static final String POSTGRES_IMAGE_NAME =
            ConfigProvider.getConfig().getValue("postgres.container.image", String.class);
    static final String MYSQL_IMAGE_NAME = ConfigProvider.getConfig().getValue("mysql.container.image", String.class);

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
                    DockerImageName.parse(POSTGRES_IMAGE_NAME).asCompatibleSubstituteFor("postgres"))
            .withExposedPorts(5432)
            .withUsername("test")
            .withPassword("test")
            .withDatabaseName("postgresql")
            .withInitScript("singleTest-postgresql-initScript.sql");

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(
                    DockerImageName.parse(MYSQL_IMAGE_NAME).asCompatibleSubstituteFor("mysql"))
            .withExposedPorts(3306)
            .withInitScript("multiITest-mysql-initScript.sql");

    @Override
    public String runBeforeAll(ForageTestCaseRunner runner, Consumer<AutoCloseable> afterAll) {
        // Load template properties and replace testcontainer-specific values
        Resource dynamicProperties = PropertiesTemplateHelper.createFromTemplate(
                classResource("forage-datasource-factory.properties.template"),
                Map.of(
                        "forage\\.ds1\\.jdbc\\.url=.*",
                        Matcher.quoteReplacement("forage.ds1.jdbc.url=" + postgres.getJdbcUrl()),
                        "forage\\.ds2\\.jdbc\\.url=.*",
                        Matcher.quoteReplacement("forage.ds2.jdbc.url=" + mysql.getJdbcUrl())),
                afterAll);

        // running jbang forage run with dynamically modified properties
        runner.when(camel().jbang()
                .custom("forage", "run")
                .processName("route")
                .addResource(dynamicProperties)
                .addResource(classResource("route.camel.yaml"))
                // required if more test are using the same route
                .autoRemove(false)
                .dumpIntegrationOutput(true));

        return "route";
    }

    @Test
    @CitrusTest()
    public void postgresql(ForageTestCaseRunner runner) {
        // validation of logged message
        runner.then(camel().jbang()
                .verify()
                .integration("route")
                .waitForLogMessage("from jdbc postgresql - [{id=1, content=postgres 1}, {id=2, content=postgres 2}]"));
    }

    @Test
    @CitrusTest()
    public void mysql(ForageTestCaseRunner runner) {
        // validation of logged message
        runner.then(camel().jbang()
                .verify()
                .integration("route")
                .waitForLogMessage("from sql mysql - [{id=1, content=mysql 1}, {id=2, content=mysql 2}]"));
    }
}
