package io.kaoto.forage.plugin;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.camel.catalog.DefaultCamelCatalog;

/**
 * Checks the Camel CLI running the plugin against the Camel versions this Forage release supports.
 *
 * <p>The supported range is a Maven version range (e.g. {@code [4.23.0,4.24.0)}) declared by the
 * {@code camel.jbang.plugin.camel-versions} property of the plugin POM and filtered into {@code versions.properties}.
 * Versions are compared on their numeric major.minor.micro parts only, so {@code 4.23.0-SNAPSHOT} counts as
 * {@code 4.23.0}.
 *
 * <p>Only APIs available since Camel 4.18 are used, so the check can be backported to every maintained line.
 */
public final class CamelCompatibility {

    static final String DOCS_URL = "https://kaotoio.github.io/forage/#versions";

    private static final AtomicBoolean WARNED = new AtomicBoolean();

    private CamelCompatibility() {}

    /**
     * Prints a warning to stderr, at most once per JVM, when the running Camel CLI is outside the supported range.
     * Never fails: an unknown CLI version or a missing range skips the check.
     */
    // java:S106: a logger would hide the warning (the Camel CLI turns logging off) and stdout carries command output
    // such as the JSON of `camel forage config read`, so the warning goes to stderr
    @SuppressWarnings("java:S106")
    public static void warnIfIncompatible() {
        if (WARNED.get()) {
            return;
        }
        String range = ExportHelper.getSupportedCamelVersions();
        String cliVersion;
        try {
            cliVersion = new DefaultCamelCatalog().getCatalogVersion();
        } catch (RuntimeException | LinkageError e) {
            return;
        }
        warning(ExportHelper.getProjectVersion(), range, cliVersion).ifPresent(message -> {
            if (WARNED.compareAndSet(false, true)) {
                System.err.println(message);
            }
        });
    }

    /**
     * @return the warning to show, or empty when {@code cliVersion} is inside {@code range} or either is unknown
     */
    static Optional<String> warning(String forageVersion, String range, String cliVersion) {
        if (range == null || cliVersion == null || cliVersion.isBlank()) {
            return Optional.empty();
        }
        if (VersionRange.parse(range).contains(cliVersion)) {
            return Optional.empty();
        }
        return Optional.of(String.format(
                "WARNING: Forage %s supports Camel %s, but this Camel CLI is %s.%n"
                        + "         Install the Forage release matching your Camel version with:%n"
                        + "           camel plugin add forage --version <forage-version>%n"
                        + "         See %s for the Forage version of each Camel release.",
                forageVersion, range, cliVersion, DOCS_URL));
    }

    /**
     * Minimal Maven version range with a single interval, e.g. {@code [4.23.0,4.24.0)}, {@code [4.18.0,)} or
     * {@code [4.22.2]}.
     */
    static final class VersionRange {

        private final int[] lower;
        private final boolean lowerInclusive;
        private final int[] upper;
        private final boolean upperInclusive;

        private VersionRange(int[] lower, boolean lowerInclusive, int[] upper, boolean upperInclusive) {
            this.lower = lower;
            this.lowerInclusive = lowerInclusive;
            this.upper = upper;
            this.upperInclusive = upperInclusive;
        }

        static VersionRange parse(String spec) {
            String s = spec.trim();
            if (s.length() < 3 || "[(".indexOf(s.charAt(0)) < 0 || "])".indexOf(s.charAt(s.length() - 1)) < 0) {
                throw new IllegalArgumentException("Invalid version range: " + spec);
            }
            boolean lowerInclusive = s.charAt(0) == '[';
            boolean upperInclusive = s.charAt(s.length() - 1) == ']';
            String body = s.substring(1, s.length() - 1);
            int comma = body.indexOf(',');
            if (comma < 0) {
                // [x] pins a single version
                if (!lowerInclusive || !upperInclusive) {
                    throw new IllegalArgumentException("Invalid version range: " + spec);
                }
                int[] v = numeric(body);
                return new VersionRange(v, true, v, true);
            }
            String low = body.substring(0, comma).trim();
            String high = body.substring(comma + 1).trim();
            return new VersionRange(
                    low.isEmpty() ? null : numeric(low),
                    lowerInclusive,
                    high.isEmpty() ? null : numeric(high),
                    upperInclusive);
        }

        boolean contains(String version) {
            int[] v = numeric(version);
            if (lower != null) {
                int c = compare(v, lower);
                if (c < 0 || (c == 0 && !lowerInclusive)) {
                    return false;
                }
            }
            if (upper != null) {
                int c = compare(v, upper);
                return c < 0 || (c == 0 && upperInclusive);
            }
            return true;
        }

        /** major.minor.micro of a version, ignoring any qualifier: 4.23.0-SNAPSHOT -> [4, 23, 0] */
        static int[] numeric(String version) {
            int[] parts = new int[3];
            String[] tokens = version.trim().split("[.-]");
            for (int i = 0; i < parts.length && i < tokens.length; i++) {
                if (tokens[i].isEmpty() || !tokens[i].chars().allMatch(Character::isDigit)) {
                    break;
                }
                parts[i] = Integer.parseInt(tokens[i]);
            }
            return parts;
        }

        private static int compare(int[] a, int[] b) {
            for (int i = 0; i < a.length; i++) {
                int c = Integer.compare(a[i], b[i]);
                if (c != 0) {
                    return c;
                }
            }
            return 0;
        }
    }
}
