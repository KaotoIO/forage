package io.kaoto.forage.core.util.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

class ConfigStoreTest {
    @TempDir
    Path directory;

    @Test
    void discoversNamesFromSystemPropertiesWithoutInstantiatingNamedConfigs() {
        String key = "forage.metrics.storetest.url";
        try {
            System.setProperty(key, "http://localhost:8086");
            assertThat(ConfigStore.getInstance()
                            .readPrefixes(new TestConfig(), ConfigHelper.getNamedPropertyRegexp("storetest")))
                    .containsExactly("metrics");
        } finally {
            System.clearProperty(key);
        }
    }

    @Test
    void discoversNamesFromEnvironmentWithoutInstantiatingNamedConfigs() throws Exception {
        ProcessBuilder builder = new ProcessBuilder(
                java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java")
                        .toString(),
                "-cp",
                System.getProperty("java.class.path"),
                EnvironmentPrefixProbe.class.getName());
        builder.environment().put("FORAGE_METRICS_STORETEST_URL", "http://localhost:8086");
        Process process = builder.start();
        try {
            assertThat(process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS))
                    .isTrue();
            assertThat(process.exitValue()).isZero();
            assertThat(new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                    .contains("metrics");
        } finally {
            process.destroyForcibly();
        }
    }

    @ParameterizedTest
    @CsvSource({"file,myDb", "file,my_db", "system,myDb", "system,my_db", "resolver,myDb", "resolver,my_db"})
    void environmentOverridesPreserveDeclaredNames(String source, String prefix) throws Exception {
        Files.writeString(
                directory.resolve("test-config-store.properties"),
                "forage." + prefix + ".storetest.url=http://localhost:8086\n");
        ProcessBuilder builder = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp",
                System.getProperty("java.class.path"),
                EnvironmentPrefixProbe.class.getName(),
                source,
                prefix,
                directory.toString());
        builder.environment()
                .put("FORAGE_" + prefix.toUpperCase(java.util.Locale.ROOT) + "_STORETEST_PASSWORD", "secret");
        builder.environment().put("FORAGE_METRICS_STORETEST_URL", "http://localhost:8087");
        builder.redirectErrorStream(true);
        Process process = builder.start();
        try {
            assertThat(process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS))
                    .isTrue();
            String output =
                    new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertThat(process.exitValue()).as(output).isZero();
            assertThat(output).contains("PREFIXES=[metrics, " + prefix + "]");
        } finally {
            process.destroyForcibly();
        }
    }

    @Test
    void discoversSystemPropertyChangesWithoutReload() {
        ConfigStore store = ConfigStore.getInstance();
        String key = "forage.live.storetest.url";
        String regexp = ConfigHelper.getNamedPropertyRegexp("storetest");
        assertThat(store.readPrefixes(new TestConfig(), regexp)).doesNotContain("live");
        try {
            System.setProperty(key, "http://localhost:8086");
            assertThat(store.readPrefixes(new TestConfig(), regexp)).contains("live");
        } finally {
            System.clearProperty(key);
        }
        assertThat(store.readPrefixes(new TestConfig(), regexp)).doesNotContain("live");
    }

    public static class EnvironmentPrefixProbe {
        public static void main(String[] args) {
            if (args.length > 0) {
                String prefix = args[1];
                switch (args[0]) {
                    case "file" -> System.setProperty("forage.config.dir", args[2]);
                    case "system" -> System.setProperty("forage." + prefix + ".storetest.url", "http://localhost:8086");
                    case "resolver" ->
                        ConfigStore.getInstance().registerResolver(new StubResolver(Collections.emptyMap()) {
                            @Override
                            public Set<String> discoverPrefixes(String regexp) {
                                return Set.of(prefix);
                            }
                        });
                    default -> throw new IllegalArgumentException(args[0]);
                }
            }
            System.out.println("PREFIXES="
                    + new java.util.TreeSet<>(ConfigStore.getInstance()
                            .readPrefixes(new TestConfig(), ConfigHelper.getNamedPropertyRegexp("storetest"))));
        }
    }

    private static class TestConfig implements Config {
        @Override
        public String name() {
            return "test-config-store";
        }

        @Override
        public void register(String name, String value) {
            // NO-OP
        }
    }

    private static class StubResolver implements ConfigResolver {
        private final Map<String, String> values;

        StubResolver(Map<String, String> values) {
            this.values = values;
        }

        @Override
        public Optional<String> resolve(String propertyName) {
            return Optional.ofNullable(values.get(propertyName));
        }

        @Override
        public Set<String> discoverPrefixes(String regexp) {
            return Collections.emptySet();
        }

        @Override
        public int priority() {
            return 100;
        }
    }

    @AfterEach
    void cleanup() {
        ConfigStore.getInstance().unregisterResolver(StubResolver.class);
    }

    @Test
    void setNullRemovesValue() {
        ConfigModule module = ConfigModule.of(TestConfig.class, "forage.storetest.remove.key");
        ConfigStore store = ConfigStore.getInstance();

        store.set(module, "value");
        assertThat(store.get(module)).contains("value");
        assertThat(store.getByPropertyName("forage.storetest.remove.key")).contains("value");

        store.set(module, null);
        assertThat(store.get(module)).isEmpty();
        assertThat(store.getByPropertyName("forage.storetest.remove.key")).isEmpty();
    }

    @Test
    void setDirectNullRemovesValue() {
        ConfigStore store = ConfigStore.getInstance();

        store.setDirect("forage.storetest.direct.key", "value");
        assertThat(store.getDirect("forage.storetest.direct.key")).contains("value");

        store.setDirect("forage.storetest.direct.key", null);
        assertThat(store.getDirect("forage.storetest.direct.key")).isEmpty();
    }

    @Test
    void entriesReturnsDefensiveSnapshot() {
        ConfigModule module = ConfigModule.of(TestConfig.class, "forage.storetest.snapshot.key");
        ConfigStore store = ConfigStore.getInstance();
        store.set(module, "before");

        Set<Map.Entry<Object, Object>> snapshot = store.entries();
        store.set(module, "after");

        assertThat(snapshot.stream()
                        .filter(e -> module.equals(e.getKey()))
                        .map(Map.Entry::getValue)
                        .findFirst())
                .contains("before");

        store.set(module, null);
    }

    @Test
    void registerResolverReplacesSameClass() {
        ConfigStore store = ConfigStore.getInstance();
        int before = store.getResolvers().size();

        store.registerResolver(new StubResolver(Map.of("forage.storetest.resolver.key", "first")));
        store.registerResolver(new StubResolver(Map.of("forage.storetest.resolver.key", "second")));

        assertThat(store.getResolvers()).hasSize(before + 1);

        ConfigModule module = ConfigModule.of(TestConfig.class, "forage.storetest.resolver.key");
        store.load(module);
        assertThat(store.get(module)).contains("second");
        store.set(module, null);
    }

    @Test
    void unregisterResolverRemovesIt() {
        ConfigStore store = ConfigStore.getInstance();
        int before = store.getResolvers().size();

        store.registerResolver(new StubResolver(Map.of()));
        assertThat(store.getResolvers()).hasSize(before + 1);

        assertThat(store.unregisterResolver(StubResolver.class)).isTrue();
        assertThat(store.getResolvers()).hasSize(before);
        assertThat(store.unregisterResolver(StubResolver.class)).isFalse();
    }

    @Test
    void systemPropertyBeatsResolverChain() {
        ConfigStore store = ConfigStore.getInstance();
        ConfigModule module = ConfigModule.of(TestConfig.class, "forage.storetest.precedence.key");
        store.registerResolver(new StubResolver(Map.of("forage.storetest.precedence.key", "from-resolver")));

        try {
            System.setProperty("forage.storetest.precedence.key", "from-sysprop");
            store.load(module);
            assertThat(store.get(module)).contains("from-sysprop");
        } finally {
            System.clearProperty("forage.storetest.precedence.key");
            store.set(module, null);
        }
    }

    @Test
    void resolverProvidesValueWhenNoEnvOrSysprop() {
        ConfigStore store = ConfigStore.getInstance();
        ConfigModule module = ConfigModule.of(TestConfig.class, "forage.storetest.fallback.key");
        store.registerResolver(new StubResolver(Map.of("forage.storetest.fallback.key", "from-resolver")));

        store.load(module);
        assertThat(store.get(module)).contains("from-resolver");
        store.set(module, null);
    }

    @Test
    void propertyNamesIncludesIndexedKeys() {
        ConfigStore store = ConfigStore.getInstance();
        ConfigModule module = ConfigModule.of(TestConfig.class, "forage.storetest.names.key");
        store.set(module, "value");

        assertThat(store.propertyNames()).contains("forage.storetest.names.key");
        store.set(module, null);
        assertThat(store.propertyNames()).doesNotContain("forage.storetest.names.key");
    }
}
