package io.kaoto.forage.plugin;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * A properties file kept as its lines, so entries can be updated or removed while comments, blank lines and the
 * order of the other entries are preserved.
 *
 * <p>Entries are found with the rules of {@link Properties#load(java.io.Reader)}: the key ends at the first
 * unescaped {@code =}, {@code :} or whitespace, {@code #} and {@code !} start comments, and a line ending with an
 * odd number of backslashes continues on the next line. So every entry {@code Properties} reads, and therefore
 * every entry the scanner and {@code config read} list, can be found and edited here.
 *
 * <p>This class has no CLI dependencies, so it can be reused outside the {@code config} commands.
 */
public final class PropertiesDocument {

    /**
     * An entry of the document.
     *
     * @param key the unescaped key
     * @param value the unescaped value
     * @param firstLine the index of the line the entry starts on
     * @param lastLine the index of the line the entry ends on (after any continuation lines)
     */
    public record Entry(String key, String value, int firstLine, int lastLine) {}

    private final List<String> lines;
    private final List<Entry> entries;

    private PropertiesDocument(List<String> lines) {
        this.lines = List.copyOf(lines);
        this.entries = parseEntries(this.lines);
    }

    /**
     * Reads a properties file, or returns an empty document if it does not exist.
     */
    public static PropertiesDocument read(File file) throws IOException {
        if (!file.isFile()) {
            return new PropertiesDocument(List.of());
        }
        return new PropertiesDocument(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8));
    }

    /**
     * Creates a document from the lines of a properties file.
     */
    public static PropertiesDocument of(List<String> lines) {
        return new PropertiesDocument(lines);
    }

    /**
     * @return the lines of the document
     */
    public List<String> lines() {
        return lines;
    }

    /**
     * @return the entries, in file order; a key written more than once has one entry per occurrence
     */
    public List<Entry> entries() {
        return entries;
    }

    /**
     * Returns the lines without the given entries, including their continuation lines.
     */
    public List<String> without(Collection<Entry> removed) {
        Set<Integer> removedLines = new HashSet<>();
        for (Entry entry : removed) {
            for (int i = entry.firstLine(); i <= entry.lastLine(); i++) {
                removedLines.add(i);
            }
        }
        List<String> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (!removedLines.contains(i)) {
                result.add(lines.get(i));
            }
        }
        return result;
    }

    /**
     * Returns the lines with the given values set: the first entry of a key is replaced, in place, by a single
     * {@code key=value} line, further entries of the same key are removed, and keys not in the document are
     * appended in the given order. A {@code null} value removes every entry of its key.
     */
    public List<String> withValues(Map<String, String> values) {
        Map<Integer, Entry> entriesByFirstLine = new LinkedHashMap<>();
        for (Entry entry : entries) {
            if (values.containsKey(entry.key())) {
                entriesByFirstLine.put(entry.firstLine(), entry);
            }
        }
        Set<String> written = new HashSet<>();
        List<String> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Entry entry = entriesByFirstLine.get(i);
            if (entry == null) {
                result.add(lines.get(i));
                continue;
            }
            String value = values.get(entry.key());
            if (value != null && written.add(entry.key())) {
                result.add(entry.key() + "=" + value);
            }
            i = entry.lastLine();
        }
        values.forEach((key, value) -> {
            if (value != null && !written.contains(key)) {
                result.add(key + "=" + value);
            }
        });
        return result;
    }

    /**
     * Writes lines to a file, each followed by a line feed.
     */
    public static void write(File file, List<String> lines) throws IOException {
        StringBuilder content = new StringBuilder();
        for (String line : lines) {
            content.append(line).append('\n');
        }
        Files.writeString(file.toPath(), content.toString(), StandardCharsets.UTF_8);
    }

    private static List<Entry> parseEntries(List<String> lines) {
        List<Entry> result = new ArrayList<>();
        int i = 0;
        while (i < lines.size()) {
            String stripped = lines.get(i).stripLeading();
            if (stripped.isEmpty() || stripped.startsWith("#") || stripped.startsWith("!")) {
                i++;
                continue;
            }
            int first = i;
            StringBuilder logicalLine = new StringBuilder(lines.get(i));
            while (isContinued(lines.get(i)) && i + 1 < lines.size()) {
                i++;
                logicalLine.append('\n').append(lines.get(i));
            }
            Map.Entry<String, String> keyValue = parseLogicalLine(logicalLine.toString());
            result.add(new Entry(keyValue.getKey(), keyValue.getValue(), first, i));
            i++;
        }
        return result;
    }

    private static boolean isContinued(String line) {
        int backslashes = 0;
        for (int i = line.length() - 1; i >= 0 && line.charAt(i) == '\\'; i--) {
            backslashes++;
        }
        return backslashes % 2 == 1;
    }

    private static Map.Entry<String, String> parseLogicalLine(String logicalLine) {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(logicalLine));
        } catch (IOException | IllegalArgumentException e) {
            // e.g. a malformed \\uxxxx escape: fall back to splitting on the first separator
            return splitOnSeparator(logicalLine);
        }
        if (properties.isEmpty()) {
            return splitOnSeparator(logicalLine);
        }
        String key = properties.stringPropertyNames().iterator().next();
        return Map.entry(key, properties.getProperty(key));
    }

    private static Map.Entry<String, String> splitOnSeparator(String logicalLine) {
        String line = logicalLine.stripLeading();
        int separator = 0;
        while (separator < line.length()) {
            char c = line.charAt(separator);
            if (c == '\\') {
                separator += 2;
                continue;
            }
            if (c == '=' || c == ':' || Character.isWhitespace(c)) {
                break;
            }
            separator++;
        }
        String key = line.substring(0, Math.min(separator, line.length()));
        String rest = separator < line.length() ? line.substring(separator + 1).strip() : "";
        if (rest.startsWith("=") || rest.startsWith(":")) {
            rest = rest.substring(1).strip();
        }
        return Map.entry(key, rest);
    }
}
