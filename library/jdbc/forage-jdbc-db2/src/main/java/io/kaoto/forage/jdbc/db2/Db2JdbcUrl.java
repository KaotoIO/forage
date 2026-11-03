package io.kaoto.forage.jdbc.db2;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns a DB2 JDBC URL into the equivalent {@code com.ibm.db2.jcc.DB2BaseDataSource} properties.
 *
 * <p>{@code DB2XADataSource} has no URL setter, so a pool cannot hand it the JDBC URL. It needs
 * {@code serverName}, {@code portNumber}, {@code databaseName} and {@code driverType} set
 * individually. Without them the driver falls back to type 2 (native) connectivity and fails to
 * load the {@code db2jcct2} library.
 *
 * <p>Supported formats:
 * <ul>
 *   <li>Type 4: {@code jdbc:db2://server[:port]/database[:property=value;...]}</li>
 *   <li>Type 2: {@code jdbc:db2:database[:property=value;...]}</li>
 * </ul>
 * Driver properties after the database name are returned as well, so they keep working in XA mode.
 */
final class Db2JdbcUrl {

    private static final String TYPE_4_PREFIX = "jdbc:db2://";
    private static final String TYPE_2_PREFIX = "jdbc:db2:";

    private Db2JdbcUrl() {}

    /**
     * Returns the datasource properties described by {@code url}.
     *
     * @param url the DB2 JDBC URL
     * @return the datasource properties, in URL order
     * @throws IllegalArgumentException if {@code url} is not a DB2 JDBC URL. The message does not
     *     include the URL, because driver properties in it can hold credentials.
     */
    static Map<String, String> toDataSourceProperties(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("A DB2 XA datasource needs forage.jdbc.url to be set");
        }
        String trimmed = url.trim();
        Map<String, String> properties = new LinkedHashMap<>();
        if (trimmed.startsWith(TYPE_4_PREFIX)) {
            parseType4(trimmed.substring(TYPE_4_PREFIX.length()), properties);
        } else if (trimmed.startsWith(TYPE_2_PREFIX)) {
            properties.put("driverType", "2");
            parseDatabaseAndProperties(trimmed.substring(TYPE_2_PREFIX.length()), properties);
        } else {
            throw new IllegalArgumentException(
                    "A DB2 XA datasource needs a URL in the form jdbc:db2://server[:port]/database");
        }
        return properties;
    }

    private static void parseType4(String rest, Map<String, String> properties) {
        int hostEnd;
        String serverName;
        if (rest.startsWith("[")) {
            // IPv6 literal, e.g. [2001:db8::1]:50000
            hostEnd = rest.indexOf(']');
            if (hostEnd < 0) {
                throw new IllegalArgumentException("Invalid DB2 URL: unterminated IPv6 address");
            }
            serverName = rest.substring(1, hostEnd);
            hostEnd++;
        } else {
            hostEnd = indexOfAny(rest, ':', '/');
            if (hostEnd < 0) {
                throw new IllegalArgumentException("Invalid DB2 URL: missing /database");
            }
            serverName = rest.substring(0, hostEnd);
        }
        if (serverName.isEmpty()) {
            throw new IllegalArgumentException("Invalid DB2 URL: missing server name");
        }

        int slash = rest.indexOf('/', hostEnd);
        if (slash < 0) {
            throw new IllegalArgumentException("Invalid DB2 URL: missing /database");
        }
        properties.put("serverName", serverName);
        if (hostEnd < slash && rest.charAt(hostEnd) == ':') {
            String port = rest.substring(hostEnd + 1, slash);
            if (!port.chars().allMatch(Character::isDigit) || port.isEmpty()) {
                throw new IllegalArgumentException("Invalid DB2 URL: the port must be a number");
            }
            properties.put("portNumber", port);
        }
        properties.put("driverType", "4");
        parseDatabaseAndProperties(rest.substring(slash + 1), properties);
    }

    private static void parseDatabaseAndProperties(String rest, Map<String, String> properties) {
        int colon = rest.indexOf(':');
        String databaseName = colon < 0 ? rest : rest.substring(0, colon);
        if (databaseName.isEmpty()) {
            throw new IllegalArgumentException("Invalid DB2 URL: missing database name");
        }
        properties.put("databaseName", databaseName);
        if (colon < 0) {
            return;
        }
        for (String property : rest.substring(colon + 1).split(";")) {
            String entry = property.trim();
            if (entry.isEmpty()) {
                continue;
            }
            int equals = entry.indexOf('=');
            if (equals <= 0) {
                throw new IllegalArgumentException("Invalid DB2 URL: driver properties must be name=value;");
            }
            properties.put(
                    entry.substring(0, equals).trim(),
                    entry.substring(equals + 1).trim());
        }
    }

    private static int indexOfAny(String value, char first, char second) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == first || c == second) {
                return i;
            }
        }
        return -1;
    }
}
