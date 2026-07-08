package io.kaoto.forage.catalog.reader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ForageCatalogReaderTest {

    private static final String TEST_CATALOG_JSON =
            """
            {
              "version": "1.0-test",
              "generatedBy": "test",
              "timestamp": 1234567890,
              "runtimeVersions": {
                "org.apache.camel": "4.18.2",
                "org.apache.camel.springboot": "4.18.2",
                "org.apache.camel.extensions.quarkus": "3.33.0"
              },
              "factories": [
                {
                  "name": "JDBC DataSource Factory",
                  "factoryType": "javax.sql.DataSource",
                  "description": "Creates JDBC DataSource beans",
                  "components": ["camel-sql"],
                  "autowired": true,
                  "propertiesFile": "forage-jdbc.properties",
                  "variants": {
                    "base": { "className": "io.kaoto.forage.jdbc.JdbcFactory", "gav": "io.kaoto.forage:forage-jdbc:1.0" },
                    "springboot": { "className": "io.kaoto.forage.jdbc.sb.JdbcFactory", "gav": "io.kaoto.forage:forage-jdbc-springboot:1.0" }
                  },
                  "configEntries": [
                    { "name": "forage.jdbc.name", "type": "prefix", "description": "Instance name", "required": false },
                    { "name": "forage.jdbc.url", "type": "string", "description": "JDBC URL", "required": true }
                  ],
                  "beansByFeature": [
                    {
                      "feature": "javax.sql.DataSource",
                      "beans": [
                        {
                          "name": "postgresql",
                          "description": "PostgreSQL DataSource",
                          "className": "io.kaoto.forage.jdbc.PostgresqlProvider",
                          "gav": "io.kaoto.forage:forage-jdbc-postgresql:1.0",
                          "configEntries": [
                            { "name": "forage.postgresql.url", "type": "string", "description": "URL" }
                          ]
                        },
                        {
                          "name": "h2",
                          "description": "H2 DataSource",
                          "className": "io.kaoto.forage.jdbc.H2Provider",
                          "gav": "io.kaoto.forage:forage-jdbc-h2:1.0"
                        }
                      ]
                    }
                  ],
                  "conditionalBeans": [
                    {
                      "id": "transaction-manager",
                      "description": "Transaction manager",
                      "configEntry": "jdbc.transaction.enabled",
                      "beans": [
                        { "name": "txManager", "javaType": "javax.transaction.TransactionManager", "description": "TX manager" }
                      ]
                    }
                  ]
                }
              ]
            }
            """;

    private ForageCatalogReader reader;

    @BeforeEach
    void setUp() throws IOException {
        InputStream is = new ByteArrayInputStream(TEST_CATALOG_JSON.getBytes(StandardCharsets.UTF_8));
        reader = ForageCatalogReader.fromInputStream(is);
    }

    @Test
    void testGetFactoryMetadata() {
        Optional<ForageCatalogReader.FactoryMetadata> metadata = reader.getFactoryMetadata("jdbc");
        assertThat(metadata).isPresent();
        assertThat(metadata.get().factoryName()).isEqualTo("JDBC DataSource Factory");
        assertThat(metadata.get().factoryType()).isEqualTo("javax.sql.DataSource");
        assertThat(metadata.get().propertiesFileName()).isEqualTo("forage-jdbc.properties");
        assertThat(metadata.get().prefixPropertyName()).isEqualTo("forage.jdbc.name");
        assertThat(metadata.get().factoryTypeKey()).isEqualTo("jdbc");
        assertThat(metadata.get().description()).isEqualTo("Creates JDBC DataSource beans");
        assertThat(metadata.get().components()).containsExactly("camel-sql");
        assertThat(metadata.get().autowired()).isTrue();
    }

    @Test
    void testGetFactoryMetadataNotFound() {
        Optional<ForageCatalogReader.FactoryMetadata> metadata = reader.getFactoryMetadata("nonexistent");
        assertThat(metadata).isEmpty();
    }

    @Test
    void testGetAllFactories() {
        Collection<ForageCatalogReader.FactoryMetadata> factories = reader.getAllFactories();
        assertThat(factories).hasSize(1);
    }

    @Test
    void testGetBeanGavs() {
        Collection<String> gavs = reader.getBeanGavs("postgresql");
        assertThat(gavs).containsExactly("io.kaoto.forage:forage-jdbc-postgresql:1.0");
    }

    @Test
    void testGetBeanGavsNotFound() {
        Collection<String> gavs = reader.getBeanGavs("unknown");
        assertThat(gavs).isEmpty();
    }

    @Test
    void testFindFactoryTypeKeyForBeanName() {
        Optional<String> key = reader.findFactoryTypeKeyForBeanName("postgresql");
        assertThat(key).isPresent().hasValue("jdbc");

        key = reader.findFactoryTypeKeyForBeanName("h2");
        assertThat(key).isPresent().hasValue("jdbc");
    }

    @Test
    void testFindFactoryTypeKeyForBeanNameNotFound() {
        Optional<String> key = reader.findFactoryTypeKeyForBeanName("unknown");
        assertThat(key).isEmpty();
    }

    @Test
    void testGetBeanFeature() {
        Optional<String> feature = reader.getBeanFeature("postgresql");
        assertThat(feature).isPresent().hasValue("javax.sql.DataSource");
    }

    @Test
    void testGetConditionalBeans() {
        var conditionalBeans = reader.getConditionalBeans("jdbc");
        assertThat(conditionalBeans).hasSize(1);
        assertThat(conditionalBeans.get(0).getId()).isEqualTo("transaction-manager");
    }

    @Test
    void testGetConditionalBeansEmpty() {
        var conditionalBeans = reader.getConditionalBeans("nonexistent");
        assertThat(conditionalBeans).isEmpty();
    }

    @Test
    void testGetFactoryVariantGav() {
        Optional<String> gav = reader.getFactoryVariantGav("jdbc", "base");
        assertThat(gav).isPresent().hasValue("io.kaoto.forage:forage-jdbc:1.0");

        gav = reader.getFactoryVariantGav("jdbc", "springboot");
        assertThat(gav).isPresent().hasValue("io.kaoto.forage:forage-jdbc-springboot:1.0");

        gav = reader.getFactoryVariantGav("jdbc", "quarkus");
        assertThat(gav).isEmpty();
    }

    @Test
    void testGetPrefixPropertyName() {
        Optional<String> prefix = reader.getPrefixPropertyName("jdbc");
        assertThat(prefix).isPresent().hasValue("forage.jdbc.name");
    }

    @Test
    void testGetPropertiesFileName() {
        Optional<String> fileName = reader.getPropertiesFileName("jdbc");
        assertThat(fileName).isPresent().hasValue("forage-jdbc.properties");
    }

    @Test
    void testFindFactoryTypeKeyForInputProperty() {
        Optional<String> key = reader.findFactoryTypeKeyForInputProperty("forage.jdbc.url");
        assertThat(key).isPresent().hasValue("jdbc");

        key = reader.findFactoryTypeKeyForInputProperty("jdbc.url");
        assertThat(key).isPresent().hasValue("jdbc");
    }

    @Test
    void testNormalizePropertyKey() {
        assertThat(reader.normalizePropertyKey("forage.jdbc.url")).isEqualTo("jdbc.url");
        assertThat(reader.normalizePropertyKey("jdbc.url")).isEqualTo("jdbc.url");
    }

    @Test
    void testShortPrefixPropertyKey() {
        Optional<ForageCatalogReader.FactoryMetadata> metadata = reader.getFactoryMetadata("jdbc");
        assertThat(metadata).isPresent();
        Optional<String> shortKey = metadata.get().getShortPrefixPropertyKey();
        assertThat(shortKey).isPresent().hasValue("jdbc.name");
    }

    @Test
    void testGetRuntimeVersions() {
        Map<String, String> versions = reader.getRuntimeVersions();
        assertThat(versions).hasSize(3);
        assertThat(versions).containsEntry("org.apache.camel", "4.18.2");
        assertThat(versions).containsEntry("org.apache.camel.springboot", "4.18.2");
        assertThat(versions).containsEntry("org.apache.camel.extensions.quarkus", "3.33.0");
    }

    @Test
    void testGetRuntimeVersion() {
        Optional<String> version = reader.getRuntimeVersion("org.apache.camel");
        assertThat(version).isPresent().hasValue("4.18.2");

        version = reader.getRuntimeVersion("org.apache.camel.extensions.quarkus");
        assertThat(version).isPresent().hasValue("3.33.0");
    }

    @Test
    void testGetRuntimeVersionNotFound() {
        Optional<String> version = reader.getRuntimeVersion("org.unknown");
        assertThat(version).isEmpty();
    }

    @Test
    void testGetRuntimeVersionNull() {
        Optional<String> version = reader.getRuntimeVersion(null);
        assertThat(version).isEmpty();
    }
}
