package io.kaoto.forage.maven.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import io.kaoto.forage.catalog.model.ConfigEntry;
import io.kaoto.forage.catalog.model.PropertiesSchema;
import io.kaoto.forage.catalog.model.PropertySchema;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

class PropertiesSchemaBuilderTest {

    private static final Function<String, List<String>> NO_BEANS = feature -> List.of();

    @ParameterizedTest
    @CsvSource(
            nullValues = "null",
            value = {
                "string, string, null",
                "String, string, null",
                "integer, integer, null",
                "int, integer, null",
                "long, integer, null",
                "double, number, null",
                "boolean, boolean, null",
                "password, string, password",
                "Duration, string, duration",
                "bean-name, string, null",
                "unknown, string, null",
                "null, string, null"
            })
    void mapsConfigEntryTypes(String entryType, String jsonType, String format) {
        PropertySchema property = PropertiesSchemaBuilder.property(entry("forage.x.y", entryType), NO_BEANS);

        assertThat(property.getType()).isEqualTo(jsonType);
        assertThat(property.getFormat()).isEqualTo(format);
    }

    @Test
    void mapsFreeFormKeysToStringValuedObject() {
        PropertySchema property = PropertiesSchemaBuilder.property(entry("forage.jdbc.properties", "map"), NO_BEANS);

        assertThat(property.getType()).isEqualTo("object");
        assertThat(property.getAdditionalProperties().getType()).isEqualTo("string");
    }

    @Test
    void mapsBeanNameToEnumOfTheSelectedFeature() {
        ConfigEntry kind = entry("forage.jdbc.db.kind", "bean-name");
        kind.setSelectsFrom("javax.sql.DataSource");

        PropertySchema property = PropertiesSchemaBuilder.property(
                kind, feature -> "javax.sql.DataSource".equals(feature) ? List.of("postgresql", "mysql") : List.of());

        assertThat(property.getEnumValues()).containsExactly("postgresql", "mysql");
    }

    @Test
    void leavesBeanNameWithoutEnumWhenTheFeatureHasNoBeans() {
        ConfigEntry kind = entry("forage.agent.embedding.store.kind", "bean-name");
        kind.setSelectsFrom("Embedding Store");

        assertThat(PropertiesSchemaBuilder.property(kind, NO_BEANS).getEnumValues())
                .isNull();
    }

    @Test
    void mapsLabelDescriptionAndConfigTag() {
        ConfigEntry url = entry("forage.jms.broker.url", "string");
        url.setLabel("Broker URL");
        url.setDescription("The JMS broker URL");
        url.setConfigTag("SECURITY");

        PropertySchema property = PropertiesSchemaBuilder.property(url, NO_BEANS);

        assertThat(property.getTitle()).isEqualTo("Broker URL");
        assertThat(property.getDescription()).isEqualTo("The JMS broker URL");
        assertThat(property.getComment()).isEqualTo("group:security");
    }

    @Test
    void typesDefaultValues() {
        assertThat(PropertiesSchemaBuilder.typedDefault("integer", "30000")).isEqualTo(30000L);
        assertThat(PropertiesSchemaBuilder.typedDefault("integer", "-1")).isEqualTo(-1L);
        assertThat(PropertiesSchemaBuilder.typedDefault("number", "0.7")).isEqualTo(new BigDecimal("0.7"));
        assertThat(PropertiesSchemaBuilder.typedDefault("boolean", "TRUE")).isEqualTo(true);
        assertThat(PropertiesSchemaBuilder.typedDefault("string", "JKS")).isEqualTo("JKS");
        // Values that do not parse are kept as written
        assertThat(PropertiesSchemaBuilder.typedDefault("integer", "${port}")).isEqualTo("${port}");
        assertThat(PropertiesSchemaBuilder.typedDefault("boolean", "yes")).isEqualTo("yes");
    }

    @Test
    void collectsRequiredAndSkipsHiddenEntries() {
        ConfigEntry kind = entry("forage.jms.kind", "string");
        kind.setRequired(true);
        ConfigEntry internal = entry("forage.provider.agent.class", "string");
        internal.setHidden(true);
        internal.setRequired(true);
        ConfigEntry optional = entry("forage.jms.client.id", "string");

        PropertiesSchema schema = PropertiesSchemaBuilder.build(List.of(kind, internal, optional, kind), NO_BEANS);

        assertThat(schema.getProperties()).containsOnlyKeys("forage.jms.kind", "forage.jms.client.id");
        assertThat(schema.getRequired()).containsExactly("forage.jms.kind");
    }

    @Test
    void buildsAnEmptyObjectSchemaWithoutEntries() throws Exception {
        PropertiesSchema schema = PropertiesSchemaBuilder.build(null, NO_BEANS);

        JsonNode json = new ObjectMapper().valueToTree(schema);
        assertThat(json.get("$schema").asText()).isEqualTo("http://json-schema.org/draft-07/schema#");
        assertThat(json.get("type").asText()).isEqualTo("object");
        assertThat(json.get("properties").isEmpty()).isTrue();
        assertThat(json.has("required")).isFalse();
    }

    @Test
    void producesValidDraft07Schemas() {
        ConfigEntry kind = entry("forage.jdbc.db.kind", "bean-name");
        kind.setSelectsFrom("javax.sql.DataSource");
        kind.setRequired(true);
        kind.setConfigTag("COMMON");
        ConfigEntry password = entry("forage.jdbc.password", "password");
        ConfigEntry properties = entry("forage.jdbc.properties", "map");
        ConfigEntry poolSize = entry("forage.jdbc.pool.max.size", "integer");
        poolSize.setDefaultValue("20");
        ConfigEntry temperature = entry("forage.agent.temperature", "double");
        temperature.setDefaultValue("0.7");
        ConfigEntry enabled = entry("forage.jdbc.transaction.enabled", "boolean");
        enabled.setDefaultValue("false");
        ConfigEntry timeout = entry("forage.agent.timeout", "Duration");
        timeout.setDefaultValue("PT60S");

        PropertiesSchema schema = PropertiesSchemaBuilder.build(
                List.of(kind, password, properties, poolSize, temperature, enabled, timeout),
                feature -> List.of("postgresql", "mysql"));

        assertThat(Draft07.metaSchemaErrors(schema)).isEmpty();
        // The meta-schema check does reject invalid schemas
        assertThat(Draft07.metaSchemaErrors(Map.of("type", "object", "required", "forage.jdbc.url")))
                .isNotEmpty();
        assertThat(Draft07.errors(
                        schema,
                        Map.of(
                                "forage.jdbc.db.kind",
                                "postgresql",
                                "forage.jdbc.properties",
                                Map.of("sslConnection", "true"),
                                "forage.jdbc.pool.max.size",
                                20,
                                "forage.agent.temperature",
                                0.7,
                                "forage.jdbc.transaction.enabled",
                                false)))
                .isEmpty();
        assertThat(Draft07.errors(schema, Map.of("forage.jdbc.db.kind", "oracle")))
                .isNotEmpty();
        assertThat(Draft07.errors(schema, Map.of())).isNotEmpty();
    }

    private static ConfigEntry entry(String name, String type) {
        ConfigEntry entry = new ConfigEntry(name, type, null, false);
        entry.setType(type);
        return entry;
    }
}
