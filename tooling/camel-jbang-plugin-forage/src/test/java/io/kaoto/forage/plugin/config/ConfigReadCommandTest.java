package io.kaoto.forage.plugin.config;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Field;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ConfigReadCommand.
 */
class ConfigReadCommandTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    File tempDir;

    private StringPrinter printer;

    @BeforeEach
    void setUp() {
        printer = new StringPrinter();
    }

    @Test
    void testReadConfigWithMultipleBeans() throws Exception {
        // Create a test properties file with the given content
        String propertiesContent =
                """
                forage.test.ollama.model.name=granite4:3b
                forage.test.ollama.log.requests=true
                forage.test.ollama.log.responses=true
                forage.myIBMMQ.jms.kind=ibmmq
                forage.myIBMMQ.jms.broker.url=127.0.0.1
                forage.myIBMMQ.jms.username=user
                forage.myIBMMQ.jms.transaction.enabled=true
                forage.myH2.jdbc.db.kind=h2
                forage.myH2.jdbc.url=127.0.0.1
                forage.myH2.jdbc.username=username
                forage.myH2.jdbc.password=password
                forage.myMemory.infinispan.server-list=localhost:11223
                forage.myPG.jdbc.db.kind=postgresql
                forage.myPG.jdbc.url=jdbc:postgresql://localhost:5432/
                forage.myPG.jdbc.username=test
                forage.myPG.jdbc.password=test
                forage.myPG.jdbc.transaction.enabled=true
                forage.myPG.jdbc.aggregation.repository.enabled=true
                forage.myPG.jdbc.aggregation.repository.name=myAggRepo
                forage.myPG.jdbc.idempotent.repository.enabled=true
                """;

        // Write properties to application.properties file (using 'application' strategy)
        File propsFile = new File(tempDir, "application.properties");
        try (FileWriter writer = new FileWriter(propsFile)) {
            writer.write(propertiesContent);
        }

        // Create and execute the command - following Camel JBang test pattern
        ConfigReadCommand command = new ConfigReadCommand(new CamelJBangMain().withPrinter(printer));
        setField(command, "directory", tempDir);
        setField(command, "strategy", "application");

        int exitCode = command.doCall();

        assertThat(exitCode).isEqualTo(0);

        // Parse and verify the output
        String output = printer.getOutput();
        assertThat(output).isNotEmpty();

        JsonNode result = OBJECT_MAPPER.readTree(output);

        // Verify success
        assertThat(result.get("success").asBoolean()).isTrue();

        // Verify we found beans
        assertThat(result.get("beanCount").asInt()).isGreaterThan(0);

        JsonNode beans = result.get("beans");
        assertThat(beans).isNotNull();
        assertThat(beans.isArray()).isTrue();

        // Verify beans in the flat list
        boolean foundOllama = false;
        boolean foundInfinispan = false;
        boolean foundIbmMq = false;
        boolean foundH2 = false;
        boolean foundPostgresql = false;

        for (JsonNode bean : beans) {
            String name = bean.get("name").asText();
            JsonNode kindNode = bean.get("kind");
            String kind = kindNode != null ? kindNode.asText() : null;

            // Check ollama bean
            if ("test".equals(name) && "ollama".equals(kind)) {
                foundOllama = true;
                assertThat(bean.get("configuration").get("model.name").asText()).isEqualTo("granite4:3b");
                assertThat(bean.get("configuration").get("log.requests").asText())
                        .isEqualTo("true");
                assertThat(bean.get("configuration").get("log.responses").asText())
                        .isEqualTo("true");
            }

            // Check infinispan bean
            if ("myMemory".equals(name) && "infinispan".equals(kind)) {
                foundInfinispan = true;
                assertThat(bean.get("configuration").get("server-list").asText())
                        .isEqualTo("localhost:11223");
            }

            // Check IBM MQ bean
            if ("myIBMMQ".equals(name)) {
                foundIbmMq = true;
                assertThat(bean.get("kind").asText()).isEqualTo("ibmmq");
                assertThat(bean.get("configuration").get("broker.url").asText()).isEqualTo("127.0.0.1");
                assertThat(bean.get("configuration").get("username").asText()).isEqualTo("user");
                assertThat(bean.get("configuration").get("transaction.enabled").asText())
                        .isEqualTo("true");

                // Verify conditionalBeans are detected for JMS transaction
                JsonNode conditionalBeans = bean.get("conditionalBeans");
                assertThat(conditionalBeans)
                        .as("IBM MQ bean should have conditionalBeans from transaction.enabled")
                        .isNotNull();
                assertThat(conditionalBeans.isArray()).isTrue();
                assertThat(conditionalBeans.size()).isGreaterThan(0);

                // Verify JMS transaction policies are present
                boolean foundJmsPropagationRequired = false;
                for (JsonNode condBean : conditionalBeans) {
                    String condBeanName = condBean.get("name").asText();
                    String javaType = condBean.get("javaType").asText();
                    if ("PROPAGATION_REQUIRED".equals(condBeanName)
                            && "org.apache.camel.spi.TransactedPolicy".equals(javaType)) {
                        foundJmsPropagationRequired = true;
                    }
                }
                assertThat(foundJmsPropagationRequired)
                        .as("Should find PROPAGATION_REQUIRED from jms.transaction.enabled")
                        .isTrue();
            }

            // Check H2 bean
            if ("myH2".equals(name)) {
                foundH2 = true;
                assertThat(bean.get("kind").asText()).isEqualTo("h2");
                assertThat(bean.get("configuration").get("url").asText()).isEqualTo("127.0.0.1");
                assertThat(bean.get("configuration").get("username").asText()).isEqualTo("username");
                assertThat(bean.get("configuration").get("password").asText()).isEqualTo("password");
            }

            // Check PostgreSQL bean
            if ("myPG".equals(name)) {
                foundPostgresql = true;
                assertThat(bean.get("kind").asText()).isEqualTo("postgresql");
                assertThat(bean.get("configuration").get("url").asText())
                        .isEqualTo("jdbc:postgresql://localhost:5432/");
                assertThat(bean.get("configuration").get("username").asText()).isEqualTo("test");
                assertThat(bean.get("configuration").get("password").asText()).isEqualTo("test");
                assertThat(bean.get("configuration").get("transaction.enabled").asText())
                        .isEqualTo("true");
                assertThat(bean.get("configuration")
                                .get("aggregation.repository.enabled")
                                .asText())
                        .isEqualTo("true");
                assertThat(bean.get("configuration")
                                .get("idempotent.repository.enabled")
                                .asText())
                        .isEqualTo("true");

                // Verify conditionalBeans are detected based on enabled config entries
                JsonNode conditionalBeans = bean.get("conditionalBeans");
                assertThat(conditionalBeans)
                        .as("PostgreSQL bean should have conditionalBeans")
                        .isNotNull();
                assertThat(conditionalBeans.isArray()).isTrue();
                assertThat(conditionalBeans.size())
                        .as("Should have conditional beans from 3 enabled features")
                        .isGreaterThan(0);

                // Verify transaction policies are present (from transaction.enabled=true)
                boolean foundPropagationRequired = false;
                boolean foundAggregationRepo = false;
                boolean foundIdempotentRepo = false;
                for (JsonNode condBean : conditionalBeans) {
                    String condBeanName = condBean.get("name").asText();
                    String javaType = condBean.get("javaType").asText();
                    if ("PROPAGATION_REQUIRED".equals(condBeanName)) {
                        foundPropagationRequired = true;
                        assertThat(javaType).isEqualTo("org.apache.camel.spi.TransactedPolicy");
                    }
                    if (javaType.contains("JdbcAggregationRepository")) {
                        foundAggregationRepo = true;
                    }
                    if (javaType.contains("JdbcMessageIdRepository")) {
                        foundIdempotentRepo = true;
                    }
                }
                assertThat(foundPropagationRequired)
                        .as("Should find PROPAGATION_REQUIRED from transaction.enabled")
                        .isTrue();
                assertThat(foundAggregationRepo)
                        .as("Should find aggregation repository from aggregation.repository.enabled")
                        .isTrue();
                assertThat(foundIdempotentRepo)
                        .as("Should find idempotent repository from idempotent.repository.enabled")
                        .isTrue();
            }
        }

        assertThat(foundOllama).as("Should find ollama bean with name 'test'").isTrue();
        assertThat(foundInfinispan)
                .as("Should find infinispan bean with name 'myMemory'")
                .isTrue();
        assertThat(foundIbmMq).as("Should find IBM MQ bean with name 'myIBMMQ'").isTrue();
        assertThat(foundH2).as("Should find H2 bean with name 'myH2'").isTrue();
        assertThat(foundPostgresql)
                .as("Should find PostgreSQL bean with name 'myPG'")
                .isTrue();
    }

    @Test
    void testReadConfigWithFilter() throws Exception {
        // Create a test properties file
        String propertiesContent =
                """
                forage.myH2.jdbc.db.kind=h2
                forage.myH2.jdbc.url=127.0.0.1
                forage.myH2.jdbc.username=username
                forage.myH2.jdbc.password=password
                forage.myIBMMQ.jms.kind=ibmmq
                forage.myIBMMQ.jms.broker.url=127.0.0.1
                """;

        File propsFile = new File(tempDir, "application.properties");
        try (FileWriter writer = new FileWriter(propsFile)) {
            writer.write(propertiesContent);
        }

        // Create and execute the command with filter
        ConfigReadCommand command = new ConfigReadCommand(new CamelJBangMain().withPrinter(printer));
        setField(command, "directory", tempDir);
        setField(command, "strategy", "application");
        setField(command, "filter", "jdbc");

        int exitCode = command.doCall();

        assertThat(exitCode).isEqualTo(0);

        String output = printer.getOutput();
        JsonNode result = OBJECT_MAPPER.readTree(output);

        assertThat(result.get("success").asBoolean()).isTrue();

        JsonNode beans = result.get("beans");
        assertThat(beans.isArray()).isTrue();

        // Should only have jdbc beans (filter applied), verify we have H2 but no JMS
        boolean foundH2 = false;
        boolean foundJms = false;
        for (JsonNode bean : beans) {
            String name = bean.get("name").asText();
            if ("myH2".equals(name)) {
                foundH2 = true;
            }
            if ("myIBMMQ".equals(name)) {
                foundJms = true;
            }
        }
        assertThat(foundH2).as("Should find H2 bean").isTrue();
        assertThat(foundJms)
                .as("Should NOT find JMS bean when filtering by jdbc")
                .isFalse();
    }

    @Test
    void testConditionalBeanNameFromConfig() throws Exception {
        String propertiesContent =
                """
                forage.myDB.jdbc.db.kind=postgresql
                forage.myDB.jdbc.url=jdbc:postgresql://localhost:5432/test
                forage.myDB.jdbc.transaction.enabled=true
                forage.myDB.jdbc.aggregation.repository.enabled=true
                forage.myDB.jdbc.aggregation.repository.name=orderAggRepo
                forage.myDB.jdbc.idempotent.repository.enabled=true
                forage.myDB.jdbc.idempotent.repository.table.name=ORDER_IDEMPOTENT
                """;

        JsonNode conditionalBeans = readConditionalBeans(propertiesContent, "myDB");

        assertThat(conditionalBeanName(conditionalBeans, "JdbcAggregationRepository"))
                .isEqualTo("orderAggRepo");
        assertThat(conditionalBeanName(conditionalBeans, "JdbcMessageIdRepository"))
                .isEqualTo("ORDER_IDEMPOTENT");
    }

    @Test
    void testConditionalBeanNameFromConfigUnset() throws Exception {
        String propertiesContent =
                """
                forage.myDB.jdbc.db.kind=postgresql
                forage.myDB.jdbc.url=jdbc:postgresql://localhost:5432/test
                forage.myDB.jdbc.transaction.enabled=true
                forage.myDB.jdbc.aggregation.repository.enabled=true
                forage.myDB.jdbc.idempotent.repository.enabled=true
                """;

        JsonNode conditionalBeans = readConditionalBeans(propertiesContent, "myDB");

        assertThat(conditionalBeanName(conditionalBeans, "JdbcAggregationRepository"))
                .as("Aggregation repository has no default name, so it is not registered")
                .isNull();
        assertThat(conditionalBeanName(conditionalBeans, "JdbcMessageIdRepository"))
                .as("Idempotent repository falls back to the default table name")
                .isEqualTo("CAMEL_MESSAGEPROCESSED");
    }

    private JsonNode readConditionalBeans(String propertiesContent, String beanName) throws Exception {
        File propsFile = new File(tempDir, "application.properties");
        try (FileWriter writer = new FileWriter(propsFile)) {
            writer.write(propertiesContent);
        }

        ConfigReadCommand command = new ConfigReadCommand(new CamelJBangMain().withPrinter(printer));
        setField(command, "directory", tempDir);
        setField(command, "strategy", "application");

        assertThat(command.doCall()).isEqualTo(0);

        JsonNode result = OBJECT_MAPPER.readTree(printer.getOutput());
        for (JsonNode bean : result.get("beans")) {
            if (beanName.equals(bean.get("name").asText())) {
                return bean.get("conditionalBeans");
            }
        }
        throw new AssertionError("Bean '" + beanName + "' not found in output: " + result);
    }

    private static String conditionalBeanName(JsonNode conditionalBeans, String javaTypeSuffix) {
        for (JsonNode condBean : conditionalBeans) {
            if (condBean.get("javaType").asText().endsWith(javaTypeSuffix)) {
                return condBean.get("name").asText();
            }
        }
        return null;
    }

    @Test
    void testPropertyKeysAreParsedLikeTheRuntime() throws Exception {
        String propertiesContent =
                """
                forage.postgresql.jdbc.db.kind=postgresql
                forage.postgresql.jdbc.url=jdbc:postgresql://localhost:5432/orders
                forage.my.ds.jdbc.db.kind=h2
                forage.my.ds.jdbc.url=jdbc:h2:mem:test
                forage.multi.agent.names=first,second
                forage.google.api.key=secret
                forage.memory.message-window.max.messages=10
                forage.keycloak.server.url=http://localhost:8180
                """;

        File propsFile = new File(tempDir, "application.properties");
        try (FileWriter writer = new FileWriter(propsFile)) {
            writer.write(propertiesContent);
        }

        ConfigReadCommand command = new ConfigReadCommand(new CamelJBangMain().withPrinter(printer));
        setField(command, "directory", tempDir);
        setField(command, "strategy", "application");

        assertThat(command.doCall()).isEqualTo(0);

        JsonNode result = OBJECT_MAPPER.readTree(printer.getOutput());
        java.util.Map<String, JsonNode> beansByName = new java.util.HashMap<>();
        java.util.List<String> kinds = new java.util.ArrayList<>();
        java.util.List<String> javaTypes = new java.util.ArrayList<>();
        for (JsonNode bean : result.get("beans")) {
            beansByName.put(bean.get("name").asText(), bean);
            if (bean.has("kind")) {
                kinds.add(bean.get("kind").asText());
            }
            javaTypes.add(bean.get("javaType").asText());
        }

        // An instance named like a provider is an instance, not the provider kind
        JsonNode postgresql = beansByName.get("postgresql");
        assertThat(postgresql).as("DataSource named 'postgresql'").isNotNull();
        assertThat(postgresql.get("javaType").asText()).isEqualTo("javax.sql.DataSource");
        assertThat(postgresql.get("configuration").get("url").asText())
                .isEqualTo("jdbc:postgresql://localhost:5432/orders");

        // Instance names can contain dots
        JsonNode dotted = beansByName.get("my.ds");
        assertThat(dotted).as("DataSource named 'my.ds'").isNotNull();
        assertThat(dotted.get("configuration").get("url").asText()).isEqualTo("jdbc:h2:mem:test");

        // Multi-segment config entries belong to the default instance
        assertThat(beansByName).doesNotContainKeys("multi", "memory");

        // forage.google.* is the Gemini chat model, not the web search factory
        assertThat(kinds).contains("google-gemini", "message-window", "keycloak");
        assertThat(javaTypes).doesNotContain("dev.langchain4j.web.search.WebSearchEngine");
    }

    @Test
    void testDefaultBeanNamesMatchRuntime() throws Exception {
        String propertiesContent =
                """
                forage.tls.keystore.path=/tmp/keystore.p12
                forage.rabbitmq.host=localhost
                forage.influxdb.url=http://localhost:8086
                forage.influxdb2.url=http://localhost:8087
                forage.shiro.ini.resource.path=classpath:shiro.ini
                forage.jdbc.db.kind=h2
                """;

        File propsFile = new File(tempDir, "application.properties");
        try (FileWriter writer = new FileWriter(propsFile)) {
            writer.write(propertiesContent);
        }

        ConfigReadCommand command = new ConfigReadCommand(new CamelJBangMain().withPrinter(printer));
        setField(command, "directory", tempDir);
        setField(command, "strategy", "application");

        assertThat(command.doCall()).isEqualTo(0);

        JsonNode result = OBJECT_MAPPER.readTree(printer.getOutput());
        java.util.Map<String, String> namesByJavaType = new java.util.HashMap<>();
        for (JsonNode bean : result.get("beans")) {
            namesByJavaType.put(bean.get("javaType").asText(), bean.get("name").asText());
        }

        assertThat(namesByJavaType)
                .containsEntry("org.apache.camel.support.jsse.SSLContextParameters", "sslContextParameters")
                .containsEntry(
                        "org.springframework.amqp.rabbit.connection.ConnectionFactory", "rabbitConnectionFactory")
                .containsEntry("org.influxdb.InfluxDB", "influxdb")
                .containsEntry("com.influxdb.client.InfluxDBClient", "influxdb2")
                .containsEntry("javax.sql.DataSource", "dataSource")
                .containsValue("shiroPolicy");
    }

    @Test
    void testReadConfigWithNoPropertiesFiles() throws Exception {
        // Create an empty temp directory (no properties files)
        ConfigReadCommand command = new ConfigReadCommand(new CamelJBangMain().withPrinter(printer));
        setField(command, "directory", tempDir);
        setField(command, "strategy", "application");

        int exitCode = command.doCall();

        assertThat(exitCode).isEqualTo(0);

        String output = printer.getOutput();
        JsonNode result = OBJECT_MAPPER.readTree(output);

        assertThat(result.get("success").asBoolean()).isTrue();
        assertThat(result.get("message").asText()).contains("No Forage properties files found");
        assertThat(result.get("beanCount").asInt()).isEqualTo(0);
    }

    /**
     * Helper method to set private fields on the command object using reflection.
     */
    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
