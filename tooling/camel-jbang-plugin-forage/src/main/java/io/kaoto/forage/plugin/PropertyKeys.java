package io.kaoto.forage.plugin;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Matches the property keys found by {@link ForagePropertyScanner} against catalog config entry names.
 *
 * <p>Scanned keys are relative to their factory and may start with an instance name, for example
 * {@code ds1.jdbc.url} or {@code ollama.agent.rag.max.results}. A catalog entry name can contain
 * placeholder segments, such as {@code {routeId}} in {@code forage.route.policy.{routeId}.name}, for the
 * parts of a property name that the user chooses; a placeholder matches one or more segments, since a
 * route ID may contain dots.
 */
final class PropertyKeys {

    private static final String ANY_SEGMENTS = "[^.]+(?:\\.[^.]+)*";

    private PropertyKeys() {}

    /**
     * Whether the key is the entry, optionally preceded by an instance name.
     *
     * @param key a scanned property key (e.g., "ds1.jdbc.url", "policy.route-a.name")
     * @param entry a catalog entry name relative to the same level (e.g., "jdbc.url", "policy.{routeId}.name")
     */
    static boolean matches(String key, String entry) {
        if (isTemplate(entry)) {
            return Pattern.matches("(?:.+\\.)?" + toRegex(entry), key);
        }
        return key.equals(entry) || key.endsWith("." + entry);
    }

    /**
     * Whether the key is exactly the entry, with any placeholder resolved, and no instance name.
     */
    static boolean matchesExactly(String key, String entry) {
        if (isTemplate(entry)) {
            return Pattern.matches(toRegex(entry), key);
        }
        return key.equals(entry);
    }

    /**
     * Whether the entry name has a placeholder segment, such as {@code {routeId}}.
     */
    static boolean isTemplate(String entry) {
        return Arrays.stream(entry.split("\\.")).anyMatch(PropertyKeys::isPlaceholder);
    }

    /**
     * Splits a bean-name value into the bean names it lists. Selectors such as
     * {@code forage.route.policy.<routeId>.name} accept a comma-separated list (e.g., "schedule,flip").
     */
    static List<String> splitValues(String value) {
        if (value == null) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .toList();
    }

    private static boolean isPlaceholder(String segment) {
        return segment.length() > 2 && segment.startsWith("{") && segment.endsWith("}");
    }

    private static String toRegex(String entry) {
        return Arrays.stream(entry.split("\\."))
                .map(segment -> isPlaceholder(segment) ? ANY_SEGMENTS : Pattern.quote(segment))
                .collect(Collectors.joining("\\."));
    }
}
