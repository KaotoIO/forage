package io.kaoto.forage.plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
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
import io.kaoto.forage.plugin.config.ConfigCommand;
import io.kaoto.forage.plugin.config.ConfigReadCommand;
import io.kaoto.forage.plugin.config.ConfigWriteCommand;
import picocli.CommandLine;

@CamelJBangPlugin(name = "camel-jbang-plugin-forage", firstVersion = "4.16.0")
public class ForagePlugin implements Plugin {

    private static final Logger LOG = LoggerFactory.getLogger(ForagePlugin.class);

    /** Set by a run, taken by its own getDependencies call. */
    private static final AtomicBoolean RUN_STARTED = new AtomicBoolean();

    /**
     * Set in this JVM by a run to the modules it translated (comma separated): an export without it reuses earlier run
     * data, an export translates only these modules.
     */
    static final String FRESH_RUN = "forage.quarkus.translation.fresh";
    /** The package of the exported app; Camel JBang adds the source files before it asks for the dependencies. */
    static final String EXPORT_PACKAGE = "forage.quarkus.translation.package";

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
                return dependencies(
                        RuntimeType.fromValue(runtimeType.name()),
                        CommandLineHelper.getWorkDir().resolve("work/src/main/resources"));
            }

            @Override
            public boolean contributeRuntimeDependencies() {
                return true;
            }

            @Override
            public void addSourceFiles(Path buildDir, String packageName, Printer printer) {
                // the startup warning of a Quarkus export is written with its translation (getDependencies)
                if (packageName != null) {
                    System.setProperty(EXPORT_PACKAGE, packageName);
                }
            }
        });
    }

    static Set<String> dependencies(RuntimeType runtime, Path exportedResources) {
        boolean run = RUN_STARTED.getAndSet(false);
        try {
            return dependencies(runtime, exportedResources, run);
        } finally {
            if (!run || runtime != RuntimeType.QUARKUS) {
                // only the silent run of a Quarkus export passes it on, to that export (camel shell runs many)
                System.clearProperty(FRESH_RUN);
            }
            if (!run) {
                System.clearProperty(EXPORT_PACKAGE);
            }
        }
    }

    private static Set<String> dependencies(RuntimeType runtime, Path exportedResources, boolean run) {
        Set<String> quarkusModules = Set.of();
        Path scanDir = null;
        if (runtime == RuntimeType.QUARKUS) {
            if (!run && System.getProperty(FRESH_RUN) == null) {
                // the export reuses earlier run data, which may lack the Forage artifacts of the modules an earlier
                // export translated, and has no project directory to scan: add them from the exported resources
                if (!booleanOverride("forage.quarkus.translation.skip", "FORAGE_QUARKUS_TRANSLATION_SKIP")) {
                    new Printer.SystemOutPrinter()
                            .println("Forage configuration stays as forage.* configuration: the export reuses earlier"
                                    + " run data.");
                }
                scanDir = exportedResources;
            } else {
                // only the silent run of an export has the Quarkus runtime; Camel JBang writes its dependencies into
                // the run data the export reads back, so it leaves out the Forage artifacts of the translated modules
                quarkusModules = run ? runQuarkusModules() : exportQuarkusConfig(exportedResources);
            }
        }
        // gather dependencies across all (enabled) export customizers for the specific runtime
        return ExportHelper.getAllCustomizers(quarkusModules, scanDir)
                .filter(ExportCustomizer::isEnabled)
                .map(exportCustomizer -> exportCustomizer.resolveRuntimeDependencies(runtime))
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }

    /** Called when a Camel JBang run starts. */
    static void runStarted() {
        RUN_STARTED.set(true);
        System.setProperty(FRESH_RUN, "");
    }

    /**
     * The modules the export will translate, from the project files (the export work directory may be left over from
     * an earlier export); writes nothing.
     */
    private static Set<String> runQuarkusModules() {
        if (booleanOverride("forage.quarkus.translation.skip", "FORAGE_QUARKUS_TRANSLATION_SKIP")) {
            return Set.of();
        }
        try {
            String configDir = System.getProperty("forage.config.dir");
            Set<String> modules = QuarkusExport.modules(Path.of(configDir == null ? "" : configDir));
            System.setProperty(FRESH_RUN, String.join(",", modules));
            return modules;
        } catch (Exception e) {
            // the export then translates nothing
            return Set.of();
        }
    }

    /**
     * Writes the Forage JDBC / default Artemis JMS configuration as quarkus.* keys into the exported resources, and
     * returns the translated modules. Camel JBang calls getDependencies after it wrote them.
     */
    private static Set<String> exportQuarkusConfig(Path resources) {
        if (booleanOverride("forage.quarkus.translation.skip", "FORAGE_QUARKUS_TRANSLATION_SKIP")) {
            return Set.of();
        }
        Printer printer = new Printer.SystemOutPrinter();
        try {
            QuarkusExport.Result result = QuarkusExport.export(
                    resources, Set.of(System.getProperty(FRESH_RUN).split(",")));
            result.notes().forEach(printer::println);
            if (!result.modules().isEmpty()) {
                try {
                    QuarkusExport.writeWarning(
                            resources.resolveSibling("java"),
                            System.getProperty(EXPORT_PACKAGE, "io.kaoto.forage.export"),
                            result.files());
                } catch (IOException e) {
                    printer.println("Forage startup warning about unread module files not added: " + e.getMessage());
                }
            }
            return result.modules();
        } catch (Exception e) {
            // never fail where the export without translation works; the message holds no values
            printer.println("Forage configuration stays as forage.* configuration: "
                    + e.getClass().getSimpleName());
            return Set.of();
        }
    }

    private void beforeRun(KameletMain main, List<String> files) {
        runStarted();
        resolveConfigDir(files);
        propagateProfile(main);
        addCatalogDrivenRepos(main);
        validateProperties();
    }

    /**
     * Validates Forage properties when running via plain {@code camel run}.
     *
     * <p>Skipped when a Forage command ({@code camel forage run}/{@code camel forage export}) has
     * already validated with the user's {@code --skip-validation}/{@code --strict} flags, to avoid
     * printing warnings twice.
     *
     * <p>For plain {@code camel run} (where the Forage command options are not available),
     * validation is controlled via the {@code forage.validation.skip} system property (or
     * {@code FORAGE_VALIDATION_SKIP} environment variable) to skip validation, and
     * {@code forage.validation.strict} (or {@code FORAGE_VALIDATION_STRICT}) to fail the run on
     * validation warnings.
     *
     * <p>{@link PluginRunCustomizer#beforeRun} returns {@code void} and Camel JBang's {@code Run}
     * invokes it outside any try/catch, so in strict mode the only way to abort the run is to
     * throw. The exception propagates to picocli's execution exception handler, which prints it
     * and exits with a non-zero code.
     */
    private static void validateProperties() {
        if (ForagePropertyValidator.consumeValidationHandled()) {
            return;
        }

        boolean skip = booleanOverride("forage.validation.skip", "FORAGE_VALIDATION_SKIP");
        boolean strict = booleanOverride("forage.validation.strict", "FORAGE_VALIDATION_STRICT");

        int result = ForagePropertyValidator.validateAndReport(new Printer.SystemOutPrinter(), skip, strict);
        if (result != 0) {
            throw new RuntimeException("Forage property validation failed in strict mode. "
                    + "Fix the reported warnings, or unset forage.validation.strict / FORAGE_VALIDATION_STRICT.");
        }
    }

    private static boolean booleanOverride(String systemProperty, String envVar) {
        String value = System.getProperty(systemProperty);
        if (value == null) {
            value = System.getenv(envVar);
        }
        return Boolean.parseBoolean(value);
    }

    // JVM-global side effect: sets camel.main.profile so ForagePropertyScanner can resolve the
    // active profile. Safe because camel run/export each run in their own JVM process — no cleanup
    // needed. Consistent with how forage.config.dir is handled in resolveConfigDir.
    private static void propagateProfile(KameletMain main) {
        if (System.getProperty("camel.main.profile") != null) {
            return;
        }
        String profile = main.getProfile();
        if (profile != null) {
            LOG.debug("Propagating camel.main.profile={} from KameletMain", profile);
            System.setProperty("camel.main.profile", profile);
        }
    }

    private static void addCatalogDrivenRepos(KameletMain main) {
        try {
            CatalogDrivenExportCustomizer customizer = new CatalogDrivenExportCustomizer();
            Set<String> repos = customizer.resolveRepositories();
            for (String repoUrl : repos) {
                addRepository(main, repoUrl);
            }
        } catch (Exception e) {
            LOG.warn("Failed to resolve catalog-driven repositories: {}", e.getMessage());
        }
    }

    private static void addRepository(KameletMain main, String repoUrl) {
        String existing = main.getRepositories();
        if (existing != null && existing.contains(repoUrl)) {
            return;
        }
        String repos = existing == null || existing.isBlank() ? repoUrl : existing + "," + repoUrl;
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
