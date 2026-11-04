package io.kaoto.forage.plugin;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PropertyKeysTest {

    @Test
    void plainEntryMatchesWithOrWithoutInstanceName() {
        assertThat(PropertyKeys.matches("db.kind", "db.kind")).isTrue();
        assertThat(PropertyKeys.matches("ds1.db.kind", "db.kind")).isTrue();
        assertThat(PropertyKeys.matches("ollama.agent.rag.max.results", "rag.max.results"))
                .isTrue();
        assertThat(PropertyKeys.matches("xdb.kind", "db.kind")).isFalse();
        assertThat(PropertyKeys.matches("db.kind.extra", "db.kind")).isFalse();
    }

    @Test
    void placeholderMatchesOneOrMoreSegments() {
        String entry = "policy.{routeId}.name";

        assertThat(PropertyKeys.isTemplate(entry)).isTrue();
        assertThat(PropertyKeys.matches("policy.route-a.name", entry)).isTrue();
        assertThat(PropertyKeys.matches("policy.orders.v2.name", entry)).isTrue();
        assertThat(PropertyKeys.matches("policy.name", entry)).isFalse();
        assertThat(PropertyKeys.matches("policy.route-a.flip.paired-route", entry))
                .isFalse();
        assertThat(PropertyKeys.matches("policy.route-a.flip.paired-route", "policy.{routeId}.flip.paired-route"))
                .isTrue();
    }

    @Test
    void exactMatchDoesNotAcceptInstanceName() {
        assertThat(PropertyKeys.matchesExactly("embedding.model.name", "embedding.model.name"))
                .isTrue();
        assertThat(PropertyKeys.matchesExactly("ollama.embedding.model.name", "embedding.model.name"))
                .isFalse();
        assertThat(PropertyKeys.matchesExactly("policy.route-a.name", "policy.{routeId}.name"))
                .isTrue();
        assertThat(PropertyKeys.matchesExactly("x.policy.route-a.name", "policy.{routeId}.name"))
                .isFalse();
    }

    @Test
    void braceInsideSegmentIsNotAPlaceholder() {
        assertThat(PropertyKeys.isTemplate("jdbc.url")).isFalse();
        assertThat(PropertyKeys.isTemplate("policy.{}.name")).isFalse();
    }

    @Test
    void splitValuesTrimsAndDropsBlanks() {
        assertThat(PropertyKeys.splitValues("schedule, flip,,")).containsExactly("schedule", "flip");
        assertThat(PropertyKeys.splitValues("postgresql ")).containsExactly("postgresql");
        assertThat(PropertyKeys.splitValues("")).isEmpty();
        assertThat(PropertyKeys.splitValues(null)).isEmpty();
    }
}
