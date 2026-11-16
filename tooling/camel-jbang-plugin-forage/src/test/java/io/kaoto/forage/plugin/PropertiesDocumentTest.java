package io.kaoto.forage.plugin;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import io.kaoto.forage.plugin.PropertiesDocument.Entry;

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
    void readAndWrite() throws Exception {
        File file = new File(tempDir, "app.properties");
        assertThat(PropertiesDocument.read(file).entries()).isEmpty();

        PropertiesDocument.write(file, List.of("# header", "a=1"));

        PropertiesDocument document = PropertiesDocument.read(file);
        assertThat(document.lines()).containsExactly("# header", "a=1");
        assertThat(document.entries()).containsExactly(new Entry("a", "1", 1, 1));
    }
}
