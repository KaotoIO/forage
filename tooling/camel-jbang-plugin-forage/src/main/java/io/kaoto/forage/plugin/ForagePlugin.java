package io.kaoto.forage.plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CamelJBangPlugin;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.Plugin;
import org.apache.camel.dsl.jbang.core.common.PluginExporter;
import org.apache.camel.dsl.jbang.core.common.PluginRunCustomizer;
import org.apache.camel.dsl.jbang.core.common.Printer;
import org.apache.camel.main.KameletMain;
import org.apache.camel.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.core.common.ExportCustomizer;
import io.kaoto.forage.core.common.RuntimeType;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.plugin.config.ConfigCommand;
import io.kaoto.forage.plugin.config.ConfigReadCommand;
import io.kaoto.forage.plugin.config.ConfigWriteCommand;
import picocli.CommandLine;

@CamelJBangPlugin(name = "camel-jbang-plugin-forage", firstVersion = "4.16.0")
public class ForagePlugin implements Plugin {

    private static final Logger LOG = LoggerFactory.getLogger(ForagePlugin.class);

    @Override
    public void customize(CommandLine commandLine, CamelJBangMain main) {
        commandLine.addSubcommand(
                "forage",
                new CommandLine(new ForageCommand(main))
                        .addSubcommand(
                                "config",
                                new CommandLine(new ConfigCommand(main))
                                        .addSubcommand("read", new CommandLine(new ConfigReadCommand(main)))
                                        .addSubcommand("write", new CommandLine(new ConfigWriteCommand(main))))
                        .addSubcommand("run", new CommandLine(new ForageRun(main)))
                        .addSubcommand("export", new ForageExport(main)));
    }

    @Override
    public Optional<PluginRunCustomizer> getRunCustomizer() {
        return Optional.of(this::beforeRun);
    }

    /**
     * Exporter is used to add runtime dependencies for both `camel run` and `camel export`
     */
    @Override
    public Optional<PluginExporter> getExporter() {
        return Optional.of(new PluginExporter() {

            @Override
            public Set<String> getDependencies(org.apache.camel.dsl.jbang.core.common.RuntimeType runtimeType) {
                if (RuntimeType.quarkus.name().equals(runtimeType.name())) {
                    // mirrors ExportBaseCommand.BUILD_DIR (protected, not accessible from plugins)
                    Path buildDir = Path.of(CommandLineHelper.CAMEL_JBANG_WORK_DIR, "work");
                    translateForageProperties(buildDir);
                }
                // gather dependencies across all (enabled) export customizers for the specific runtime
                return ExportHelper.getAllCustomizers()
                        .filter(ExportCustomizer::isEnabled)
                        .map(exportCustomizer ->
                                exportCustomizer.resolveRuntimeDependencies(RuntimeType.fromValue(runtimeType.name())))
                        .flatMap(Set::stream)
                        .collect(Collectors.toSet());
            }

            @Override
            public boolean contributeRuntimeDependencies() {
                return true;
            }

            @Override
            public void addSourceFiles(Path buildDir, String packageName, Printer printer) {}
        });
    }

    private static void translateForageProperties(Path buildDir) {
        String configDir = System.getProperty("forage.config.dir");
        File workingDir = configDir != null ? new File(configDir) : new File(System.getProperty("user.dir"));

        QuarkusPropertyTranslator.TranslationResult result;
        try {
            result = QuarkusPropertyTranslator.translate(workingDir);
        } catch (IOException e) {
            LOG.warn("Failed to scan forage properties for Quarkus translation: {}", e.getMessage());
            return;
        } finally {
            // translate() populates the global ConfigStore singleton via config.register() — clean up
            // so scanned values don't leak into subsequent operations in the same JVM
            ConfigStore.getInstance().reload();
        }

        if (result.isEmpty()) {
            return;
        }

        Path appPropsPath = buildDir.resolve("src/main/resources/application.properties");
        if (!Files.exists(appPropsPath)) {
            LOG.warn("application.properties not found at {} — skipping property translation", appPropsPath);
            return;
        }

        try {
            rewriteApplicationProperties(appPropsPath, result);
            LOG.info("Translated {} forage properties to Quarkus-native format", result.propertyCount());
        } catch (IOException e) {
            LOG.warn("Failed to rewrite application.properties: {}", e.getMessage());
        }
    }

    static void rewriteApplicationProperties(Path appPropsPath, QuarkusPropertyTranslator.TranslationResult result)
            throws IOException {
        List<String> originalLines = Files.readAllLines(appPropsPath);
        Set<String> keysToRemove = result.translatedForageKeys();

        // Keep every line except those whose key was translated to Quarkus format
        Stream<String> filtered = originalLines.stream().filter(line -> {
            String key = extractPropertyKey(line);
            return key == null || !keysToRemove.contains(key);
        });

        // Append each translation group with a header showing the original forage properties
        Stream<String> translated = result.groups().stream().flatMap(ForagePlugin::translationGroupToLines);

        Files.write(appPropsPath, Stream.concat(filtered, translated).toList());
    }

    /**
     * Extracts the property key from a .properties file line.
     * Returns {@code null} for blank lines, comments, and lines without '='.
     */
    private static String extractPropertyKey(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
            return null;
        }
        int eqIdx = trimmed.indexOf('=');
        return eqIdx < 0 ? null : trimmed.substring(0, eqIdx).trim();
    }

    private static Stream<String> translationGroupToLines(QuarkusPropertyTranslator.TranslationGroup group) {
        Stream.Builder<String> lines = Stream.builder();
        lines.add("");
        if (group.prefix() != null) {
            lines.add("# Properties for " + group.moduleType() + " with prefix '" + group.prefix() + "'");
        } else {
            lines.add("# Properties for " + group.moduleType());
        }
        lines.add("# Translated from:");
        group.sourceProperties().forEach((k, v) -> lines.add("#   " + k + "=" + v));
        group.properties().forEach((k, v) -> {
            if (v != null) {
                lines.add(k + "=" + v);
            }
        });
        return lines.build();
    }

    private static final String SHIBBOLETH_REPO = "https://build.shibboleth.net/maven/releases/";

    private void beforeRun(KameletMain main, List<String> files) {
        resolveConfigDir(files);
        addShibbolethRepo(main);
    }

    private static void addShibbolethRepo(KameletMain main) {
        String existing = main.getRepositories();
        if (existing != null && existing.contains(SHIBBOLETH_REPO)) {
            return;
        }
        String repos = existing == null || existing.isBlank() ? SHIBBOLETH_REPO : existing + "," + SHIBBOLETH_REPO;
        main.setRepositories(repos);
    }

    /**
     * Derives the configuration directory from the file arguments, mirroring
     * how Camel's {@code Run} resolves its base directory.
     *
     * <ul>
     *   <li>Single directory argument (e.g., {@code camel run agent}) — use that directory</li>
     *   <li>Multiple files sharing a common parent different from CWD
     *       (e.g., shell-expanded {@code agent/*}) — use that common parent</li>
     *   <li>Otherwise — leave unset (CWD is fine)</li>
     * </ul>
     *
     * <p>Sets {@code forage.config.dir} system property so that {@code ConfigStore},
     * {@code ConfigHelper}, and {@code ForagePropertyValidator}
     * all resolve properties from the correct location.
     *
     * <p>Called from both the {@link PluginRunCustomizer} SPI ({@code camel run})
     * and {@link ForageRun} ({@code camel forage run}).
     */
    static void resolveConfigDir(List<String> files) {
        if (files == null || files.isEmpty()) {
            return;
        }

        Path configDir = null;

        // Single directory argument
        if (files.size() == 1) {
            String name = FileUtil.stripTrailingSeparator(files.get(0));
            Path first = Path.of(name);
            if (Files.isDirectory(first)) {
                configDir = first;
            }
        }

        // Multiple files (e.g., shell-expanded glob) — find common parent
        if (configDir == null && files.size() > 1) {
            configDir = commonParent(files);
        }

        if (configDir != null) {
            Path cwd = Path.of("").toAbsolutePath();
            Path resolved = configDir.toAbsolutePath().normalize();
            if (!resolved.equals(cwd)) {
                LOG.debug("Setting forage.config.dir={} (derived from file arguments)", resolved);
                System.setProperty("forage.config.dir", resolved.toString());
            }
        }
    }

    /**
     * Returns the common parent directory of the given file paths,
     * or {@code null} if there is no single common parent.
     */
    static Path commonParent(List<String> filePaths) {
        Path common = null;
        for (String filePath : filePaths) {
            Path parent = Path.of(filePath).toAbsolutePath().normalize().getParent();
            if (parent == null) {
                return null;
            }
            if (common == null) {
                common = parent;
            } else if (!common.equals(parent)) {
                return null;
            }
        }
        return common;
    }
}
