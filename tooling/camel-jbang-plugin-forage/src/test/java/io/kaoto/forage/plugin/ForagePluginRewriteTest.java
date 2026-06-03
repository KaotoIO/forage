package io.kaoto.forage.plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class ForagePluginRewriteTest {

    @TempDir
    Path tempDir;

    @Test
    void forageKeysReplacedWithQuarkusProperties() throws IOException {
        Path appProps = writeProps(
                "# App config",
                "server.port=8080",
                "forage.jdbc.db.kind=postgresql",
                "forage.jdbc.url=jdbc:postgresql://localhost:5432/mydb",
                "forage.jdbc.username=admin",
                "forage.jdbc.password=secret",
                "camel.rest.port=9090");

        ForagePlugin.rewriteApplicationProperties(
                appProps,
                result(
                        group(
                                "jdbc",
                                null,
                                Map.of(
                                        "forage.jdbc.db.kind",
                                        "postgresql",
                                        "forage.jdbc.url",
                                        "jdbc:postgresql://localhost:5432/mydb",
                                        "forage.jdbc.username",
                                        "admin",
                                        "forage.jdbc.password",
                                        "secret"),
                                Map.of(
                                        "quarkus.datasource.\"dataSource\".db-kind",
                                        "postgresql",
                                        "quarkus.datasource.\"dataSource\".jdbc.url",
                                        "jdbc:postgresql://localhost:5432/mydb",
                                        "quarkus.datasource.\"dataSource\".username",
                                        "admin",
                                        "quarkus.datasource.\"dataSource\".password",
                                        "secret")),
                        Set.of(
                                "forage.jdbc.db.kind",
                                "forage.jdbc.url",
                                "forage.jdbc.username",
                                "forage.jdbc.password")));

        List<String> lines = Files.readAllLines(appProps);

        assertThat(lines).contains("# App config", "server.port=8080", "camel.rest.port=9090");
        assertThat(lines).noneMatch(l -> l.startsWith("forage."));
        assertThat(lines).anyMatch(l -> l.contains("quarkus.datasource.\"dataSource\".db-kind=postgresql"));
        assertThat(lines).anyMatch(l -> l.contains("quarkus.datasource.\"dataSource\".username=admin"));
    }

    @Test
    void namedPrefixesProduceGroupComments() throws IOException {
        Path appProps = writeProps("forage.ds1.jdbc.db.kind=postgresql", "forage.ds2.jdbc.db.kind=mysql");

        ForagePlugin.rewriteApplicationProperties(
                appProps,
                result(
                        List.of(
                                group(
                                        "jdbc",
                                        "ds1",
                                        Map.of("forage.ds1.jdbc.db.kind", "postgresql"),
                                        Map.of("quarkus.datasource.\"ds1\".db-kind", "postgresql")),
                                group(
                                        "jdbc",
                                        "ds2",
                                        Map.of("forage.ds2.jdbc.db.kind", "mysql"),
                                        Map.of("quarkus.datasource.\"ds2\".db-kind", "mysql"))),
                        Set.of("forage.ds1.jdbc.db.kind", "forage.ds2.jdbc.db.kind")));

        List<String> lines = Files.readAllLines(appProps);

        assertThat(lines).contains("# Properties for jdbc with prefix 'ds1'");
        assertThat(lines).contains("# Properties for jdbc with prefix 'ds2'");
        assertThat(lines).noneMatch(l -> l.startsWith("forage."));
    }

    @Test
    void defaultPrefixCommentOmitsPrefixLabel() throws IOException {
        Path appProps = writeProps("forage.jdbc.db.kind=postgresql");

        ForagePlugin.rewriteApplicationProperties(
                appProps,
                result(
                        group(
                                "jdbc",
                                null,
                                Map.of("forage.jdbc.db.kind", "postgresql"),
                                Map.of("quarkus.datasource.\"dataSource\".db-kind", "postgresql")),
                        Set.of("forage.jdbc.db.kind")));

        List<String> lines = Files.readAllLines(appProps);

        assertThat(lines).contains("# Properties for jdbc");
        assertThat(lines).noneMatch(l -> l.contains("with prefix"));
    }

    @Test
    void sourcePropertiesShownAsComments() throws IOException {
        Path appProps = writeProps("forage.jdbc.db.kind=postgresql", "forage.jdbc.username=admin");

        ForagePlugin.rewriteApplicationProperties(
                appProps,
                result(
                        group(
                                "jdbc",
                                null,
                                Map.of("forage.jdbc.db.kind", "postgresql", "forage.jdbc.username", "admin"),
                                Map.of(
                                        "quarkus.datasource.\"dataSource\".db-kind",
                                        "postgresql",
                                        "quarkus.datasource.\"dataSource\".username",
                                        "admin")),
                        Set.of("forage.jdbc.db.kind", "forage.jdbc.username")));

        List<String> lines = Files.readAllLines(appProps);

        assertThat(lines).contains("# Translated from:");
        assertThat(lines).anyMatch(l -> l.equals("#   forage.jdbc.db.kind=postgresql"));
        assertThat(lines).anyMatch(l -> l.equals("#   forage.jdbc.username=admin"));
    }

    @Test
    void commentsAndBlankLinesPreserved() throws IOException {
        Path appProps = writeProps(
                "# My application", "", "! legacy comment", "forage.jdbc.db.kind=postgresql", "", "other.prop=value");

        ForagePlugin.rewriteApplicationProperties(
                appProps,
                result(
                        group(
                                "jdbc",
                                null,
                                Map.of("forage.jdbc.db.kind", "postgresql"),
                                Map.of("quarkus.datasource.\"dataSource\".db-kind", "postgresql")),
                        Set.of("forage.jdbc.db.kind")));

        List<String> lines = Files.readAllLines(appProps);

        assertThat(lines).contains("# My application", "", "! legacy comment", "other.prop=value");
        assertThat(lines).noneMatch(l -> l.equals("forage.jdbc.db.kind=postgresql"));
    }

    @Test
    void nonTranslatedForageKeysKept() throws IOException {
        Path appProps = writeProps("forage.jdbc.db.kind=postgresql", "forage.cxf.kind=soap");

        ForagePlugin.rewriteApplicationProperties(
                appProps,
                result(
                        group(
                                "jdbc",
                                null,
                                Map.of("forage.jdbc.db.kind", "postgresql"),
                                Map.of("quarkus.datasource.\"dataSource\".db-kind", "postgresql")),
                        Set.of("forage.jdbc.db.kind")));

        List<String> lines = Files.readAllLines(appProps);

        assertThat(lines).anyMatch(l -> l.equals("forage.cxf.kind=soap"));
        assertThat(lines).noneMatch(l -> l.equals("forage.jdbc.db.kind=postgresql"));
    }

    private Path writeProps(String... lines) throws IOException {
        Path path = tempDir.resolve("application.properties");
        Files.write(path, List.of(lines));
        return path;
    }

    private static QuarkusPropertyTranslator.TranslationGroup group(
            String moduleType, String prefix, Map<String, String> source, Map<String, String> translated) {
        return new QuarkusPropertyTranslator.TranslationGroup(
                moduleType, prefix, new LinkedHashMap<>(source), new LinkedHashMap<>(translated));
    }

    private static QuarkusPropertyTranslator.TranslationResult result(
            QuarkusPropertyTranslator.TranslationGroup group, Set<String> translatedKeys) {
        return new QuarkusPropertyTranslator.TranslationResult(List.of(group), new LinkedHashSet<>(translatedKeys));
    }

    private static QuarkusPropertyTranslator.TranslationResult result(
            List<QuarkusPropertyTranslator.TranslationGroup> groups, Set<String> translatedKeys) {
        return new QuarkusPropertyTranslator.TranslationResult(groups, new LinkedHashSet<>(translatedKeys));
    }
}
