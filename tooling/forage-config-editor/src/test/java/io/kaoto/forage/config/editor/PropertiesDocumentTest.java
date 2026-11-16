package io.kaoto.forage.config.editor;

import java.io.File;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import io.kaoto.forage.config.editor.PropertiesDocument.Entry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class PropertiesDocumentTest {

    @TempDir
    File tempDir;

    @Test
    void entriesFollowThePropertiesFormat() {
        PropertiesDocument document = PropertiesDocument.of(List.of(
                "# comment",
                "! another comment",
                "",
                "a=1",
                "  b : 2",
                "c 3",
                "d = long\\",
                "    value",
                "e\\:f=4",
                "g"));

        assertThat(document.entries())
                .containsExactly(
                        new Entry("a", "1", 3, 3),
                        new Entry("b", "2", 4, 4),
                        new Entry("c", "3", 5, 5),
                        new Entry("d", "longvalue", 6, 7),
                        new Entry("e:f", "4", 8, 8),
                        new Entry("g", "", 9, 9));
    }

    @Test
    void escapedBackslashDoesNotContinueTheLine() {
        PropertiesDocument document = PropertiesDocument.of(List.of("path=C:\\\\", "next=1"));

        assertThat(document.entries()).extracting(Entry::key).containsExactly("path", "next");
        assertThat(document.entries().get(0).value()).isEqualTo("C:\\");
    }

    @Test
    void malformedUnicodeEscapeFallsBackToTheFirstSeparator() {
        // Properties.load rejects \\u followed by non-hex digits; the entry is still found
        PropertiesDocument document = PropertiesDocument.of(List.of("url=jdbc:h2:file:C:\\users\\data", "next=1"));

        assertThat(document.entries())
                .containsExactly(new Entry("url", "jdbc:h2:file:C:\\users\\data", 0, 0), new Entry("next", "1", 1, 1));
    }

    @Test
    void withoutRemovesEntriesAndTheirContinuationLines() {
        PropertiesDocument document = PropertiesDocument.of(List.of("# keep", "a=1", "b=long\\", "  value", "c=3"));

        List<String> lines = document.without(List.of(document.entries().get(1)));

        assertThat(lines).containsExactly("# keep", "a=1", "c=3");
    }

    @Test
    void withValuesUpdatesInPlaceAndAppendsNewKeys() {
        PropertiesDocument document =
                PropertiesDocument.of(List.of("# header", "a: 1", "b=long\\", "  value", "c=3", "a=duplicate"));
        Map<String, String> values = new LinkedHashMap<>();
        values.put("b", "2");
        values.put("a", "10");
        values.put("c", null);
        values.put("d", "4");
        values.put("e", null);

        assertThat(document.withValues(values)).containsExactly("# header", "a=10", "b=2", "d=4");
    }

    @Test
    void withValuesEscapesKeysAndValues() throws Exception {
        PropertiesDocument document = PropertiesDocument.of(List.of("e\\:f=4"));
        Map<String, String> values = new LinkedHashMap<>();
        values.put("e:f", "5");
        values.put("path", "C:\\data");
        values.put("multi", "a\nb");
        values.put("lead", " x");
        values.put("key with spaces#!", "a=b:c#d!e");

        Properties reloaded = load(document.withValues(values));

        assertThat(reloaded).hasSize(values.size());
        values.forEach(
                (key, value) -> assertThat(reloaded.getProperty(key)).as(key).isEqualTo(value));
    }

    @Test
    void appendingEndsADanglingContinuation() throws Exception {
        PropertiesDocument document = PropertiesDocument.of(List.of("a=one\\"));

        Properties reloaded = load(document.withValues(Map.of("b", "2")));

        assertThat(reloaded.getProperty("a")).isEqualTo("one");
        assertThat(reloaded.getProperty("b")).isEqualTo("2");
    }

    private static Properties load(List<String> lines) throws Exception {
        Properties properties = new Properties();
        properties.load(new StringReader(String.join("\n", lines) + "\n"));
        return properties;
    }

    @Test
    void editsApplyToTheOriginalLines() {
        PropertiesDocument document = PropertiesDocument.of(List.of("# header", "a=1", "b=long\\", "  value", "c=3"));
        Map<Entry, String> newValues = new LinkedHashMap<>();
        newValues.put(document.entries().get(2), null);
        newValues.put(document.entries().get(1), "two");
        newValues.put(document.entries().get(0), "one");

        List<PropertiesDocument.TextEdit> edits = document.edits(newValues, Map.of("d", "4"));

        assertThat(edits)
                .containsExactly(
                        new PropertiesDocument.TextEdit(1, 2, List.of("a=one")),
                        new PropertiesDocument.TextEdit(2, 4, List.of("b=two")),
                        new PropertiesDocument.TextEdit(4, 5, List.of()),
                        new PropertiesDocument.TextEdit(5, 5, List.of("d=4")));
        assertThat(document.apply(edits)).containsExactly("# header", "a=one", "b=two", "d=4");
    }

    @Test
    void readAndWrite() throws Exception {
        File file = new File(tempDir, "app.properties");
        assertThat(PropertiesDocument.read(file).entries()).isEmpty();

        PropertiesDocument.write(file, List.of("# header", "a=1"));

        PropertiesDocument document = PropertiesDocument.read(file);
        assertThat(document.lines()).containsExactly("# header", "a=1");
        assertThat(document.entries()).containsExactly(new Entry("a", "1", 1, 1));
    }
}
