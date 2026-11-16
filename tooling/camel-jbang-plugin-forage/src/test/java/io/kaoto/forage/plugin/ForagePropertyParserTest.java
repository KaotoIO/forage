package io.kaoto.forage.plugin;

import java.util.List;
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
    void selectedBeans() {
        // Catalog bean-name entries, whatever their name
        assertThat(selectedBeans("forage.ds1.jdbc.db.kind", "postgresql")).containsExactly("postgresql");
        assertThat(selectedBeans("forage.a1.agent.model.kind", "ollama")).containsExactly("ollama");
        assertThat(selectedBeans("forage.agent.memory.kind", "redis")).containsExactly("redis");
        assertThat(selectedBeans("forage.a1.agent.embedding.store.kind", "in-memory"))
                .containsExactly("in-memory");
        // A multi-value bean-name entry lists several beans
        assertThat(selectedBeans("forage.route.policy.route-a.name", "schedule, flip"))
                .containsExactly("schedule", "flip");
        // Other *.kind properties
        assertThat(selectedBeans("forage.cxf.kind", "soap")).containsExactly("soap");
        // Everything else selects nothing
        assertThat(selectedBeans("forage.ds1.jdbc.url", "jdbc:h2:mem:x")).isEmpty();
        assertThat(selectedBeans("forage.ds1.jdbc.db.kind", " ")).isEmpty();
    }

    @Test
    void configEntries() {
        assertThat(parser.isConfigEntry("forage.jdbc.url")).isTrue();
        assertThat(parser.isConfigEntry("forage.openai.api.key")).isTrue();
        assertThat(parser.isConfigEntry("forage.ds1.jdbc.url")).isFalse();
    }

    @Test
    void unknownKeysFallBackToSegments() {
        // Typo in the property name: the segment heuristic still finds the factory
        assertThat(parse("forage.ds1.jdbc.urll"))
                .isEqualTo(new ParsedProperty("jdbc", "ds1", null, "urll", "jdbc.urll"));
        assertThat(parser.parse("forage.nothing.known.here")).isEmpty();
        assertThat(parser.parse("camel.jbang.dependencies")).isEmpty();
    }

    private static List<String> selectedBeans(String key, String value) {
        return parser.selectedBeans(parse(key), value);
    }

    private static ParsedProperty parse(String key) {
        return parser.parse(key).orElseThrow(() -> new AssertionError("Not parsed: " + key));
    }
}
