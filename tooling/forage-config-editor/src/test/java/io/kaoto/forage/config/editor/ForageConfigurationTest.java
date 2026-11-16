package io.kaoto.forage.config.editor;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;
import io.kaoto.forage.config.editor.ForageConfiguration.Instance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class ForageConfigurationTest {

    private final ForageCatalogReader catalog = ForageCatalogReader.getInstance();

    @TempDir
    File tempDir;

    @Test
    void ambiguousKeyBelongsToTheKnownInstance() throws Exception {
        // openai.model.name only reads as instance "azure", so openai.api.key goes there too
        ForageConfiguration configuration = read("forage.azure.openai.api.key=k", "forage.azure.openai.model.name=gpt");

        assertThat(instanceOf(configuration))
                .containsOnly(
                        Map.entry("forage.azure.openai.api.key", "azure"),
                        Map.entry("forage.azure.openai.model.name", "azure"));
        assertThat(configuration.instances()).singleElement().satisfies(instance -> assertThat(instance.name())
                .isEqualTo("azure"));
    }

    @Test
    void ambiguousKeyBelongsToTheDefaultInstanceOtherwise() throws Exception {
        // azure.openai.endpoint only reads as the default Azure OpenAI bean: no instance is named "azure"
        ForageConfiguration configuration =
                read("forage.azure.openai.api.key=k", "forage.azure.openai.endpoint=https://example");

        assertThat(configuration.entries()).allSatisfy(entry -> {
            assertThat(entry.instanceName()).isNull();
            assertThat(entry.property().beanType()).isEqualTo("azure-openai");
        });
    }

    @Test
    void knownInstanceNamesResolveAmbiguousKeys() throws Exception {
        ForageConfiguration configuration =
                ForageConfiguration.read(catalog, List.of(write("forage.azure.openai.api.key=k")), Set.of("azure"));

        assertThat(configuration.entries().get(0).instanceName()).isEqualTo("azure");
    }

    @Test
    void overriddenEntriesDoNotMakeNamesKnown() throws Exception {
        // The second list overrides the first, which named "azure"
        ForageConfiguration configuration = read(
                "forage.multi.agent.names=azure", "forage.multi.agent.names=other", "forage.azure.openai.api.key=k");

        assertThat(instanceOf(configuration)).containsEntry("forage.azure.openai.api.key", "<default>");
    }

    @Test
    void instanceListsMakeNamesKnown() throws Exception {
        ForageConfiguration configuration = read("forage.multi.agent.names=azure", "forage.azure.openai.api.key=k");

        assertThat(instanceOf(configuration)).containsEntry("forage.azure.openai.api.key", "azure");
    }

    @Test
    void unmatchedKeysBelongToTheKnownInstanceTheyStartWith() throws Exception {
        // An agent named "ollama" with a typo, next to a default Ollama chat model
        ForageConfiguration configuration = read(
                "forage.ollama.agent.model.kind=ollama",
                "forage.ollama.agent.modle.nmae=llama3",
                "forage.ollama.model.name=llama3",
                "forage.ollama.whatever=x");

        Map<String, String> instances = instanceOf(configuration);
        assertThat(instances.get("forage.ollama.agent.model.kind")).isEqualTo("ollama");
        assertThat(instances.get("forage.ollama.agent.modle.nmae")).isEqualTo("ollama");
        assertThat(instances.get("forage.ollama.model.name")).isEqualTo("<default>");
        // Not recognised at all, but under the instance prefix
        assertThat(configuration.entriesOf("ollama"))
                .extracting(ForageConfiguration.Entry::key)
                .contains("forage.ollama.whatever");
    }

    @Test
    void dottedInstanceNames() throws Exception {
        ForageConfiguration configuration = ForageConfiguration.read(
                catalog,
                List.of(write("forage.my.ds.jdbc.url=jdbc:h2:mem:x", "forage.my.ds.jdbc.urll=typo")),
                Set.of("my"));

        // "my" is known, but both keys belong to "my.ds"
        assertThat(instanceOf(configuration))
                .containsOnly(
                        Map.entry("forage.my.ds.jdbc.url", "my.ds"), Map.entry("forage.my.ds.jdbc.urll", "my.ds"));
        assertThat(configuration.entriesOf("my")).isEmpty();
    }

    @Test
    void instancesGroupEntriesAndBeans() throws Exception {
        ForageConfiguration configuration = read(
                "forage.a1.agent.model.kind=ollama",
                "forage.a1.agent.memory.kind=redis",
                "forage.a1.ollama.model.name=llama3",
                "forage.ds1.jdbc.db.kind=postgresql",
                "camel.main.name=app");

        List<Instance> instances = configuration.instances();
        assertThat(instances)
                .extracting(Instance::factoryType, Instance::name)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("agent", "a1"),
                        org.assertj.core.groups.Tuple.tuple("jdbc", "ds1"));
        assertThat(instances.get(0).beans()).containsExactlyInAnyOrder("ollama", "redis");
        assertThat(instances.get(0).entries()).hasSize(3);
        assertThat(instances.get(1).beans()).containsExactly("postgresql");
        assertThat(configuration.hasInstance("a1")).isTrue();
        assertThat(configuration.hasInstance("a2")).isFalse();
    }

    @Test
    void instanceNamesAreKnownAcrossFiles() throws Exception {
        File first = writeFile("first.properties", "forage.azure.openai.model.name=gpt");
        File second = writeFile("second.properties", "forage.azure.openai.api.key=k");

        ForageConfiguration configuration = ForageConfiguration.read(catalog, List.of(first, second), Set.of());

        assertThat(configuration.entriesOf("azure"))
                .extracting(ForageConfiguration.Entry::file)
                .containsExactly(first, second);
    }

    private ForageConfiguration read(String... lines) throws Exception {
        return ForageConfiguration.read(catalog, List.of(write(lines)), Set.of());
    }

    private static Map<String, String> instanceOf(ForageConfiguration configuration) {
        return configuration.entries().stream()
                .collect(Collectors.toMap(
                        ForageConfiguration.Entry::key,
                        entry -> entry.instanceName() != null ? entry.instanceName() : "<default>",
                        (first, last) -> last));
    }

    private File write(String... lines) throws Exception {
        return writeFile("application.properties", lines);
    }

    private File writeFile(String name, String... lines) throws Exception {
        File file = new File(tempDir, name);
        Files.writeString(file.toPath(), String.join("\n", lines) + "\n");
        return file;
    }
}
