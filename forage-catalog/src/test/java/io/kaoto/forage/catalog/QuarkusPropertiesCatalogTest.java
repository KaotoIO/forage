package io.kaoto.forage.catalog;

import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import io.kaoto.forage.core.common.ForageModuleDescriptor;
import io.kaoto.forage.jdbc.common.JdbcModuleDescriptor;
import io.kaoto.forage.jms.common.JmsModuleDescriptor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The catalog's {@code quarkusProperty} / {@code quarkusUnit} are exactly the module descriptors'
 * {@code quarkusProperties()} lists, in both catalogs, and nothing else.
 */
class QuarkusPropertiesCatalogTest {

    private static final Path DIR = Path.of("target", "generated-catalog");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void catalogHasTheDescriptorLists() throws Exception {
        Map<String, String> expected = new TreeMap<>();
        new JdbcModuleDescriptor()
                .quarkusProperties()
                .forEach(p -> expected.put(
                        "forage.jdbc." + p.forageKey().replaceAll("\\.$", ""), p.quarkusKey() + "|" + p.unit()));
        new JmsModuleDescriptor()
                .quarkusProperties()
                .forEach(p -> expected.put("forage.jms." + p.forageKey(), p.quarkusKey() + "|" + p.unit()));

        assertThat(mapped("forage-catalog.json")).isEqualTo(expected);
        assertThat(mapped("forage-configuration-catalog.json")).isEqualTo(expected);
    }

    /** {@code quarkusIgnored} is exactly the descriptors' {@code quarkusIgnoredProperties()}, without a Quarkus key. */
    @Test
    void catalogHasTheIgnoredLists() throws Exception {
        Set<String> expected = new TreeSet<>();
        new JmsModuleDescriptor().quarkusIgnoredProperties().forEach(key -> expected.add("forage.jms." + key));
        assertThat(expected).contains("forage.jms.pool.connection.timeout.millis");

        assertThat(ignored("forage-catalog.json")).isEqualTo(expected);
        assertThat(ignored("forage-configuration-catalog.json")).isEqualTo(expected);
    }

    /** {@code quarkusHandled} is exactly the descriptors' {@code quarkusHandledProperties()}. */
    @Test
    void catalogHasTheHandledLists() throws Exception {
        Set<String> expected = new TreeSet<>();
        new JmsModuleDescriptor().quarkusHandledProperties().forEach(key -> expected.add("forage.jms." + key));
        assertThat(expected).contains("forage.jms.pool.enabled");

        for (String file : List.of("forage-catalog.json", "forage-configuration-catalog.json")) {
            Set<String> handled = new TreeSet<>();
            read(file)
                    .findParents("quarkusHandled")
                    .forEach(node -> handled.add(node.get("name").asText()));
            assertThat(handled).isEqualTo(expected);
        }
    }

    /** {@code quarkusSelector} is exactly the descriptors' {@code quarkusSelector()}. */
    @Test
    void catalogHasTheSelectors() throws Exception {
        var jdbc = new JdbcModuleDescriptor().quarkusSelector();
        var jms = new JmsModuleDescriptor().quarkusSelector();
        Map<String, Object> expected = new TreeMap<>();
        expected.put("forage.jdbc." + jdbc.forageKey(), selector(jdbc.kinds(), jdbc.defaultOnly()));
        expected.put("forage.jms." + jms.forageKey(), selector(jms.kinds(), jms.defaultOnly()));
        assertThat(expected).containsKeys("forage.jdbc.db.kind", "forage.jms.kind");

        for (String file : List.of("forage-catalog.json", "forage-configuration-catalog.json")) {
            Map<String, Object> selectors = new TreeMap<>();
            read(file).findParents("quarkusSelector").forEach(node -> {
                Object previous = selectors.put(
                        node.get("name").asText(), MAPPER.convertValue(node.get("quarkusSelector"), Map.class));
                assertThat(previous).isIn(null, selectors.get(node.get("name").asText()));
            });
            assertThat(selectors).isEqualTo(expected);
        }
    }

    /**
     * Every module descriptor (all Forage modules are on this classpath) has Quarkus keys or a reason in the catalog,
     * not both: a new module cannot silently miss its Quarkus export.
     */
    @Test
    void everyModuleDescriptorDeclaresItsQuarkusExport() throws Exception {
        Set<String> mapped = mapped("forage-catalog.json").keySet();
        JsonNode reasons = read("forage-catalog.json").path("quarkusUnsupported");
        Set<String> modules = new TreeSet<>();
        for (Class<?> type : moduleDescriptors()) {
            String module =
                    ((ForageModuleDescriptor<?, ?>) type.getConstructor().newInstance()).modulePrefix();
            modules.add(module);
            boolean keys = mapped.stream().anyMatch(name -> name.startsWith("forage." + module + "."));
            String reason = reasons.path(module).asText("");
            assertThat(keys || !reason.isBlank())
                    .as(type.getName() + " declares neither quarkusProperties() nor quarkusUnsupportedReason()")
                    .isTrue();
            assertThat(keys && !reason.isBlank())
                    .as(type.getName() + " declares both")
                    .isFalse();
        }
        assertThat(modules)
                .contains(
                        "jdbc",
                        "jms",
                        "agent",
                        "cxf",
                        "influxdb",
                        "influxdb2",
                        "rabbitmq",
                        "keycloak",
                        "shiro",
                        "spring.security",
                        "tls");
        assertThat(modules).containsAll(() -> reasons.fieldNames());
    }

    /** The concrete ForageModuleDescriptor classes under io.kaoto.forage on the test classpath. */
    private static List<Class<?>> moduleDescriptors() throws Exception {
        String root = "io/kaoto/forage";
        Set<String> names = new TreeSet<>();
        for (URL url : Collections.list(
                QuarkusPropertiesCatalogTest.class.getClassLoader().getResources(root))) {
            if (url.getProtocol().equals("jar")) {
                try (FileSystem jar = FileSystems.newFileSystem(url.toURI(), Map.of())) {
                    names.addAll(descriptorNames(jar.getPath("/" + root), root));
                }
            } else {
                names.addAll(descriptorNames(Path.of(url.toURI()), root));
            }
        }
        List<Class<?>> descriptors = new ArrayList<>();
        for (String name : names) {
            Class<?> type = Class.forName(name);
            if (ForageModuleDescriptor.class.isAssignableFrom(type)
                    && !type.isInterface()
                    && !Modifier.isAbstract(type.getModifiers())) {
                descriptors.add(type);
            }
        }
        return descriptors;
    }

    private static List<String> descriptorNames(Path dir, String root) throws Exception {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.map(file -> dir.relativize(file)
                            .toString()
                            .replace(dir.getFileSystem().getSeparator(), "."))
                    .filter(name -> name.endsWith("ModuleDescriptor.class") && !name.contains("$"))
                    .map(name -> root.replace('/', '.') + "." + name.substring(0, name.length() - ".class".length()))
                    .toList();
        }
    }

    private static Map<String, Object> selector(Map<String, Map<String, String>> kinds, boolean defaultOnly) {
        Map<String, Object> selector = new TreeMap<>();
        if (kinds != null) {
            selector.put("kinds", kinds);
        }
        if (defaultOnly) {
            selector.put("defaultOnly", true);
        }
        return selector;
    }

    private static JsonNode read(String file) throws Exception {
        return MAPPER.readTree(Files.readString(DIR.resolve(file)));
    }

    /** Every config entry with quarkusIgnored=true (and no quarkusProperty). */
    private static Set<String> ignored(String file) throws Exception {
        Set<String> ignored = new TreeSet<>();
        new ObjectMapper()
                .readTree(Files.readString(DIR.resolve(file)))
                .findParents("quarkusIgnored")
                .forEach(node -> {
                    assertThat(node.get("quarkusIgnored").asBoolean()).isTrue();
                    assertThat(node.has("quarkusProperty")).isFalse();
                    ignored.add(node.get("name").asText());
                });
        return ignored;
    }

    /** Every config entry with a quarkusProperty: name -> quarkusProperty|quarkusUnit. */
    private static Map<String, String> mapped(String file) throws Exception {
        Map<String, String> mapped = new TreeMap<>();
        collect(new ObjectMapper().readTree(Files.readString(DIR.resolve(file))), mapped);
        return mapped;
    }

    private static void collect(JsonNode node, Map<String, String> mapped) {
        if (node.has("quarkusProperty")) {
            String value = node.get("quarkusProperty").asText() + "|"
                    + (node.has("quarkusUnit") ? node.get("quarkusUnit").asText() : "");
            String previous = mapped.put(node.get("name").asText(), value);
            assertThat(previous).isIn(null, value);
        }
        node.forEach(child -> collect(child, mapped));
    }
}
