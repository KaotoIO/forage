package io.kaoto.forage.config.editor;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
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
     * A change to the lines of a document: lines {@code startLine} (inclusive) to {@code endLine} (exclusive) are
     * replaced by {@code lines}. An insertion has {@code startLine == endLine}; a removal has no lines. Line numbers
     * refer to the document the edit was computed for, so a list of edits applies to that document at once, the way
     * an editor applies a set of text edits.
     *
     * @param startLine the first replaced line
     * @param endLine the line after the last replaced one
     * @param lines the new lines
     */
    public record TextEdit(int startLine, int endLine, List<String> lines) {

        public TextEdit {
            lines = List.copyOf(lines);
        }
    }

    /**
     * Returns the edits that replace the given entries: each by a single {@code key=value} line with its new value,
     * or by nothing when the new value is {@code null}, continuation lines included. Keys and values are escaped as
     * {@link Properties} expects.
     *
     * @param newValues the entries to replace, with their new values
     * @param appended entries to append at the end of the document, in order
     * @return the edits, in line order
     */
    public List<TextEdit> edits(Map<Entry, String> newValues, Map<String, String> appended) {
        List<TextEdit> edits = new ArrayList<>();
        newValues.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparingInt(Entry::firstLine)))
                .forEach(change -> edits.add(new TextEdit(
                        change.getKey().firstLine(),
                        change.getKey().lastLine() + 1,
                        change.getValue() != null
                                ? List.of(format(change.getKey().key(), change.getValue()))
                                : List.of())));
        List<String> appendedLines = new ArrayList<>();
        appended.forEach((key, value) -> appendedLines.add(format(key, value)));
        if (!appendedLines.isEmpty()) {
            // A last line ending with a continuation would swallow the appended entries: end it with a blank line
            if (!lines.isEmpty() && isContinued(lines.get(lines.size() - 1))) {
                appendedLines.add(0, "");
            }
            edits.add(new TextEdit(lines.size(), lines.size(), appendedLines));
        }
        return edits;
    }

    /**
     * Applies edits computed for this document.
     *
     * @param edits non-overlapping edits, as returned by {@link #edits(Map, Map)}
     * @return the edited lines
     */
    public List<String> apply(List<TextEdit> edits) {
        List<TextEdit> sorted = edits.stream()
                .sorted(Comparator.comparingInt(TextEdit::startLine))
                .toList();
        List<String> result = new ArrayList<>();
        int next = 0;
        for (TextEdit edit : sorted) {
            result.addAll(lines.subList(next, edit.startLine()));
            result.addAll(edit.lines());
            next = edit.endLine();
        }
        result.addAll(lines.subList(next, lines.size()));
        return result;
    }

    /**
     * Returns the lines without the given entries, including their continuation lines.
     */
    public List<String> without(Collection<Entry> removed) {
        Map<Entry, String> newValues = new HashMap<>();
        removed.forEach(entry -> newValues.put(entry, null));
        return apply(edits(newValues, Map.of()));
    }

    /**
     * Returns the edits that set the given values: the first entry of a key is replaced, in place, by a single
     * {@code key=value} line, further entries of the same key are removed, and keys not in the document are
     * appended in the given order. A {@code null} value removes every entry of its key.
     */
    public List<TextEdit> editsSettingValues(Map<String, String> values) {
        Map<Entry, String> newValues = new HashMap<>();
        Set<String> found = new HashSet<>();
        for (Entry entry : entries) {
            if (values.containsKey(entry.key())) {
                newValues.put(entry, found.add(entry.key()) ? values.get(entry.key()) : null);
            }
        }
        Map<String, String> appended = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (value != null && !found.contains(key)) {
                appended.put(key, value);
            }
        });
        return edits(newValues, appended);
    }

    /**
     * Returns the lines with the given values set, see {@link #editsSettingValues(Map)}. Keys and values are escaped
     * as {@link Properties} expects, so they read back unchanged.
     */
    public List<String> withValues(Map<String, String> values) {
        return apply(editsSettingValues(values));
    }

    /**
     * Formats an entry as a {@code key=value} line, escaping the key and the value as {@link Properties} expects.
     * Characters outside ASCII are kept as they are, since the files are written in UTF-8.
     */
    static String format(String key, String value) {
        return escape(key, true) + "=" + escape(value, false);
    }

    private static String escape(String text, boolean isKey) {
        StringBuilder escaped = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\' -> escaped.append("\\\\");
                case '\t' -> escaped.append("\\t");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\f' -> escaped.append("\\f");
                case ' ' -> escaped.append(isKey || i == 0 ? "\\ " : " ");
                case '=', ':', '#', '!' -> escaped.append(isKey ? "\\" + c : String.valueOf(c));
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
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
        while (separator < line.length() && !isSeparator(line.charAt(separator))) {
            // An escaped character, such as \=, is part of the key
            separator += line.charAt(separator) == '\\' ? 2 : 1;
        }
        String key = line.substring(0, Math.min(separator, line.length()));
        String rest = separator < line.length() ? line.substring(separator + 1).strip() : "";
        if (rest.startsWith("=") || rest.startsWith(":")) {
            rest = rest.substring(1).strip();
        }
        return Map.entry(key, rest);
    }

    private static boolean isSeparator(char c) {
        return c == '=' || c == ':' || Character.isWhitespace(c);
    }
}
