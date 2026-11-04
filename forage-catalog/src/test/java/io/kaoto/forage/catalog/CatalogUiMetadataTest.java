package io.kaoto.forage.catalog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaId;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SpecVersion;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the UI metadata of the generated {@code forage-catalog.json} (issue #233): every
 * {@code propertiesSchema} is a valid JSON Schema Draft-07 that a form renderer can use, the
 * factory and bean schemas split the config entries without overlap, internal entries are hidden,
 * and every bean reference points to a real Camel catalog option.
 */
class CatalogUiMetadataTest {

    private static final Path CATALOG = Path.of("target", "generated-catalog", "forage-catalog.json");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonSchemaFactory SCHEMA_FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
    private static final JsonSchema DRAFT_07 = SCHEMA_FACTORY.getSchema(SchemaLocation.of(SchemaId.V7));

    private static JsonNode catalog;

    @BeforeAll
    static void loadCatalog() throws IOException {
        assertThat(CATALOG).as("catalog generated in process-classes").exists();
        catalog = MAPPER.readTree(Files.readString(CATALOG));
    }

    static Stream<Arguments> factories() throws IOException {
        loadCatalog();
        List<Arguments> arguments = new ArrayList<>();
        catalog.get("factories")
                .forEach(factory ->
                        arguments.add(Arguments.of(factory.get("name").asText(), factory)));
        return arguments.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("factories")
    void factoryHasModelBlock(String name, JsonNode factory) {
        JsonNode model = factory.get("model");

        assertThat(model.get("kind").asText()).isEqualTo("forage-factory");
        assertThat(model.get("name").asText()).matches("[a-z0-9]+(-[a-z0-9]+)*");
        assertThat(model.get("title").asText()).isEqualTo(name);
        assertThat(model.get("description").asText())
                .isEqualTo(factory.get("description").asText());
    }

    @Test
    void modelNamesAreUnique() {
        Set<String> names = new HashSet<>();
        catalog.get("factories").forEach(factory -> assertThat(
                        names.add(factory.at("/model/name").asText()))
                .as("unique model name %s", factory.at("/model/name").asText())
                .isTrue());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("factories")
    void everySchemaIsValidDraft07(String name, JsonNode factory) {
        for (Map.Entry<String, JsonNode> schema : schemas(factory).entrySet()) {
            assertThat(DRAFT_07.validate(schema.getValue())).as(schema.getKey()).isEmpty();
            assertThat(schema.getValue().get("$schema").asText()).isEqualTo("http://json-schema.org/draft-07/schema#");
            assertThat(schema.getValue().get("type").asText()).isEqualTo("object");

            schema.getValue().path("patternProperties").properties().forEach(property -> assertThat(property.getKey())
                    .as("pattern property key of %s", schema.getKey())
                    .startsWith("^forage\\."));
            List<Map.Entry<String, JsonNode>> allProperties = new ArrayList<>();
            schema.getValue().get("properties").properties().forEach(allProperties::add);
            schema.getValue().path("patternProperties").properties().forEach(allProperties::add);
            allProperties.forEach(property -> {
                assertThat(property.getKey())
                        .as("property key of %s", schema.getKey())
                        .matches("\\^?forage.*");
                assertThat(property.getValue().get("type").asText())
                        .isIn("string", "integer", "number", "boolean", "object");
                JsonNode defaultValue = property.getValue().get("default");
                if (defaultValue != null) {
                    assertThat(SCHEMA_FACTORY.getSchema(property.getValue()).validate(defaultValue))
                            .as("default of %s in %s", property.getKey(), schema.getKey())
                            .isEmpty();
                }
            });
            schema.getValue().path("required").forEach(required -> assertThat(
                            schema.getValue().get("properties").has(required.asText()))
                    .as("required %s is a property of %s", required.asText(), schema.getKey())
                    .isTrue());
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("factories")
    void factoryAndBeanSchemasCoverTheVisibleConfigEntriesOnce(String name, JsonNode factory) {
        // On camel-4.18.x the factory configEntries do not repeat the entries of its beans
        Set<String> visibleEntries = new HashSet<>();
        List<JsonNode> entries = new ArrayList<>();
        factory.path("configEntries").forEach(entries::add);
        beans(factory).forEach(bean -> bean.path("configEntries").forEach(entries::add));
        entries.forEach(entry -> {
            if (!entry.path("hidden").asBoolean(false)) {
                visibleEntries.add(entry.get("name").asText());
            }
        });

        // A templated entry (forage.route.policy.{routeId}.name) is covered by exactly one pattern property
        Map<String, JsonNode> patterns = new LinkedHashMap<>();
        factory.at("/propertiesSchema/patternProperties")
                .properties()
                .forEach(p -> patterns.put(p.getKey(), p.getValue()));
        beans(factory).forEach(bean -> bean.at("/propertiesSchema/patternProperties")
                .properties()
                .forEach(p -> assertThat(patterns.put(p.getKey(), p.getValue()))
                        .as("pattern %s in one schema only", p.getKey())
                        .isNull()));
        Set<String> matchedPatterns = new HashSet<>();
        visibleEntries.removeIf(entry -> {
            if (!entry.contains("{")) {
                return false;
            }
            String sample = entry.replaceAll("\\{[^}]+}", "orders.in");
            List<String> matching = patterns.keySet().stream()
                    .filter(pattern -> sample.matches(pattern.substring(1, pattern.length() - 1)))
                    .toList();
            assertThat(matching).as("pattern properties matching %s", entry).hasSize(1);
            matchedPatterns.add(matching.get(0));
            return true;
        });
        assertThat(matchedPatterns).containsExactlyInAnyOrderElementsOf(patterns.keySet());

        Set<String> factoryKeys = keys(factory.get("propertiesSchema"));
        Set<String> beanKeys = new HashSet<>();
        beans(factory).forEach(bean -> beanKeys.addAll(keys(bean.get("propertiesSchema"))));

        Set<String> overlap = new HashSet<>(factoryKeys);
        overlap.retainAll(beanKeys);
        assertThat(overlap).as("factory keys also in a bean schema").isEmpty();
        Set<String> allKeys = new HashSet<>(factoryKeys);
        allKeys.addAll(beanKeys);
        assertThat(allKeys).containsExactlyInAnyOrderElementsOf(visibleEntries);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("factories")
    void beanNamePropertiesListTheBeansOfTheSelectedFeature(String name, JsonNode factory) {
        Map<String, List<String>> beansByFeature = new LinkedHashMap<>();
        factory.path("beansByFeature").forEach(feature -> {
            List<String> names = new ArrayList<>();
            feature.path("beans").forEach(bean -> names.add(bean.get("name").asText()));
            beansByFeature.put(feature.get("feature").asText(), names);
        });

        factory.path("configEntries").forEach(entry -> {
            JsonNode property = factory.at("/propertiesSchema/properties")
                    .get(entry.get("name").asText());
            if (!"bean-name".equals(entry.get("type").asText()) || property == null) {
                return;
            }
            List<String> expected = beansByFeature.get(entry.get("selectsFrom").asText());
            if (expected == null) {
                assertThat(property.has("enum")).isFalse();
            } else if (entry.path("multiValue").asBoolean(false)) {
                assertThat(property.has("enum")).isFalse();
                assertThat(property.get("pattern").asText()).contains(expected);
            } else {
                assertThat(property.get("enum")).extracting(JsonNode::asText).containsExactlyElementsOf(expected);
            }
        });
    }

    @Test
    void internalAgentEntriesAreHidden() {
        JsonNode agent = factory("Agent");
        List<String> hidden = new ArrayList<>();
        agent.get("configEntries").forEach(entry -> {
            if (entry.path("hidden").asBoolean(false)) {
                hidden.add(entry.get("name").asText());
            }
        });

        assertThat(hidden)
                .containsExactlyInAnyOrder(
                        "forage.multi.agent.names",
                        "forage.multi.agent.id.source",
                        "forage.multi.agent.id.source.header",
                        "forage.multi.agent.id.source.property",
                        "forage.multi.agent.id.source.variable",
                        "forage.provider.model.factory.class",
                        "forage.provider.features",
                        "forage.provider.features.memory.factory.class",
                        "forage.provider.agent.class");
        assertThat(keys(agent.get("propertiesSchema"))).doesNotContainAnyElementsOf(hidden);
        assertThat(keys(agent.get("propertiesSchema"))).contains("forage.agent.model.kind");
    }

    @Test
    void onlyFactoriesWithNamedInstancesHaveAPrefixPattern() {
        List<String> withoutPrefixPattern = new ArrayList<>();
        catalog.get("factories").forEach(factory -> {
            if (factory.has("prefixPattern")) {
                assertThat(factory.get("prefixPattern").asText()).isEqualTo("forage.{name}.{suffix}");
            } else {
                withoutPrefixPattern.add(factory.get("name").asText());
            }
        });

        // camel-4.18.x has no Security Policy factory
        assertThat(withoutPrefixPattern).containsExactlyInAnyOrder("WebSearch", "Route Policy");
    }

    @Test
    void beanReferencesMatchTheExpectedRouteUsage() {
        Map<String, List<String>> references = new LinkedHashMap<>();
        catalog.get("factories").forEach(factory -> {
            List<String> values = new ArrayList<>();
            factory.path("beanReferences")
                    .forEach(
                            reference -> values.add(reference.get("catalogKind").asText() + ":"
                                    + reference.get("name").asText() + ":"
                                    + reference.get("option").asText() + ":"
                                    + reference.get("optionKind").asText() + ":"
                                    + reference.get("valueTemplate").asText()));
            references.put(factory.get("name").asText(), values);
        });

        assertThat(references.get("DataSource"))
                .containsExactly(
                        "component:sql:dataSource:parameter:#{beanRef}",
                        "component:jdbc:dataSourceName:path:{beanRef}",
                        "component:spring-jdbc:dataSourceName:path:{beanRef}");
        assertThat(references.get("JMS Connection"))
                .containsExactly("component:jms:connectionFactory:parameter:#{beanRef}");
        assertThat(references.get("Agent")).containsExactly("component:langchain4j-agent:agent:parameter:#{beanRef}");
        assertThat(references.get("CXF Web Service")).containsExactly("component:cxf:beanId:path:bean:{beanRef}");
        // Camel uses these beans without a route referencing them
        assertThat(references.get("TLS Context")).isEmpty();
        assertThat(references.get("Route Policy")).isEmpty();
        references.forEach((factory, values) -> {
            if (!"TLS Context".equals(factory) && !"Route Policy".equals(factory)) {
                assertThat(values).as("bean references of %s", factory).isNotEmpty();
            }
        });
    }

    @Test
    void beanReferencesPointToCamelCatalogOptions() throws IOException {
        CamelCatalog camelCatalog = new DefaultCamelCatalog();
        for (JsonNode factory : catalog.get("factories")) {
            for (JsonNode reference : factory.path("beanReferences")) {
                String kind = reference.get("catalogKind").asText();
                String entryName = reference.get("name").asText();
                String json = "processor".equals(kind)
                        ? camelCatalog.modelJSonSchema(entryName)
                        : camelCatalog.componentJSonSchema(entryName);
                assertThat(json).as("Camel catalog %s %s", kind, entryName).isNotNull();

                JsonNode option = MAPPER.readTree(json)
                        .path("properties")
                        .path(reference.get("option").asText());
                assertThat(option.isMissingNode()).as("option of %s", reference).isFalse();
                assertThat(option.get("kind").asText())
                        .isEqualTo(reference.get("optionKind").asText());
                assertThat(reference.get("valueTemplate").asText()).contains("{beanRef}");
            }
        }
    }

    private static JsonNode factory(String name) {
        for (JsonNode factory : catalog.get("factories")) {
            if (name.equals(factory.get("name").asText())) {
                return factory;
            }
        }
        throw new AssertionError("No factory " + name);
    }

    private static Map<String, JsonNode> schemas(JsonNode factory) {
        Map<String, JsonNode> schemas = new LinkedHashMap<>();
        String factoryName = factory.get("name").asText();
        schemas.put(factoryName, factory.get("propertiesSchema"));
        factory.path("beansByFeature").forEach(feature -> feature.path("beans")
                .forEach(bean -> schemas.put(
                        factoryName + " / " + feature.get("feature").asText() + " / "
                                + bean.get("name").asText(),
                        bean.get("propertiesSchema"))));
        schemas.forEach((name, schema) ->
                assertThat(schema).as("propertiesSchema of %s", name).isNotNull());
        return schemas;
    }

    private static List<JsonNode> beans(JsonNode factory) {
        List<JsonNode> beans = new ArrayList<>();
        factory.path("beansByFeature").forEach(feature -> feature.path("beans").forEach(beans::add));
        return beans;
    }

    @Test
    void routePolicyNameAcceptsSeveralPoliciesForAnyRoute() {
        JsonNode schema = factory("Route Policy").get("propertiesSchema");
        assertThat(keys(schema)).doesNotContain("forage.route.policy.{routeId}.name");
        assertThat(DRAFT_07.validate(schema)).isEmpty();

        JsonSchema validator = SCHEMA_FACTORY.getSchema(schema);
        ObjectNode valid = JsonNodeFactory.instance.objectNode();
        valid.put("forage.route.policy.orders.in.name", "schedule,flip");
        assertThat(validator.validate(valid)).isEmpty();
        ObjectNode invalid = JsonNodeFactory.instance.objectNode();
        invalid.put("forage.route.policy.orders.name", "schedule,flop");
        assertThat(validator.validate(invalid)).isNotEmpty();
    }

    private static Set<String> keys(JsonNode schema) {
        Set<String> keys = new HashSet<>();
        schema.get("properties").fieldNames().forEachRemaining(keys::add);
        return keys;
    }
}
