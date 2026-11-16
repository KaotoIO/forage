package io.kaoto.forage.plugin;

import java.util.Map;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;
import io.kaoto.forage.plugin.ForagePropertyParser.ParsedProperty;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ForagePropertyParserTest {

    private static ForageCatalogReader catalog;
    private static ForagePropertyParser parser;

    @BeforeAll
    static void setUp() {
        catalog = ForageCatalogReader.getInstance();
        parser = new ForagePropertyParser(catalog);
    }

    @Test
    void defaultAndNamedFactoryInstances() {
        assertThat(parse("forage.jdbc.url")).isEqualTo(new ParsedProperty("jdbc", null, null, "url", "jdbc.url"));
        assertThat(parse("forage.ds1.jdbc.db.kind"))
                .isEqualTo(new ParsedProperty("jdbc", "ds1", null, "db.kind", "jdbc.db.kind"));
        assertThat(parse("forage.rabbitmq.host").factoryType()).isEqualTo("rabbitmq");
        assertThat(parse("forage.rabbitmq.host").instanceName()).isNull();
    }

    @Test
    void dottedInstanceNames() {
        assertThat(parse("forage.my.ds.jdbc.url"))
                .isEqualTo(new ParsedProperty("jdbc", "my.ds", null, "url", "jdbc.url"));
    }

    @Test
    void beanProperties() {
        assertThat(parse("forage.ollama.model.name"))
                .isEqualTo(new ParsedProperty("agent", null, "ollama", "model.name", "ollama.model.name"));
        assertThat(parse("forage.myOllama.ollama.model.name"))
                .isEqualTo(new ParsedProperty("agent", "myOllama", "ollama", "model.name", "ollama.model.name"));
        // Bean prefix that differs from the bean name
        ParsedProperty gemini = parse("forage.myG.google.api.key");
        assertThat(gemini.factoryType()).isEqualTo("agent");
        assertThat(gemini.instanceName()).isEqualTo("myG");
        assertThat(gemini.beanType()).isEqualTo("google-gemini");
        assertThat(gemini.propertyName()).isEqualTo("api.key");
    }

    @Test
    void instanceNamedLikeABean() {
        assertThat(parse("forage.ollama.jdbc.db.kind"))
                .isEqualTo(new ParsedProperty("jdbc", "ollama", null, "db.kind", "jdbc.db.kind"));
    }

    @Test
    void kindProperties() {
        assertThat(parse("forage.ds1.jdbc.db.kind").isKindProperty()).isTrue();
        assertThat(parse("forage.ds1.jdbc.url").isKindProperty()).isFalse();
    }

    @Test
    void unknownKeysFallBackToSegments() {
        // Typo in the property name: the segment heuristic still finds the factory
        assertThat(parse("forage.ds1.jdbc.urll"))
                .isEqualTo(new ParsedProperty("jdbc", "ds1", null, "urll", "jdbc.urll"));
        assertThat(parser.parse("forage.nothing.known.here")).isEmpty();
        assertThat(parser.parse("camel.jbang.dependencies")).isEmpty();
    }

    @Test
    void resolverCollectsFactoryAndBeanDependencies() {
        ForageDependencyResolver resolver = new ForageDependencyResolver(catalog, parser);

        ForageDependencyResolver.Dependencies jdbc =
                resolver.resolve(Map.of("forage.myPG.jdbc.db.kind", "postgresql", "camel.main.name", "x"));
        assertThat(jdbc.base()).anyMatch(gav -> gav.contains("forage-jdbc-postgresql:"));
        assertThat(jdbc.main()).anyMatch(gav -> gav.contains("forage-jdbc:"));
        assertThat(jdbc.springBoot()).anyMatch(gav -> gav.contains("forage-jdbc-starter:"));
        assertThat(jdbc.quarkus()).anyMatch(gav -> gav.contains("forage-quarkus-jdbc-deployment:"));

        ForageDependencyResolver.Dependencies ollama = resolver.resolve(Map.of("forage.ollama.model.name", "llama3"));
        assertThat(ollama.base()).anyMatch(gav -> gav.contains("forage-model-ollama:"));
        assertThat(ollama.main()).anyMatch(gav -> gav.contains("forage-agent:"));

        assertThat(resolver.resolve(Map.of("camel.main.name", "x")).isEmpty()).isTrue();
    }

    private static ParsedProperty parse(String key) {
        return parser.parse(key).orElseThrow(() -> new AssertionError("Not parsed: " + key));
    }
}
