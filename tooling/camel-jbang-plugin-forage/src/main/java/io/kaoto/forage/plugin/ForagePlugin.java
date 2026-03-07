package io.kaoto.forage.plugin;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.Export;
import org.apache.camel.dsl.jbang.core.common.CamelJBangPlugin;
import org.apache.camel.dsl.jbang.core.common.Plugin;
import org.apache.camel.dsl.jbang.core.common.PluginExporter;
import org.apache.camel.dsl.jbang.core.common.Printer;
import io.kaoto.forage.core.common.ExportCustomizer;
import io.kaoto.forage.core.common.RuntimeType;
import io.kaoto.forage.plugin.config.ConfigCommand;
import io.kaoto.forage.plugin.config.ConfigReadCommand;
import io.kaoto.forage.plugin.config.ConfigWriteCommand;
import picocli.CommandLine;

@CamelJBangPlugin(name = "camel-jbang-plugin-forage", firstVersion = "4.16.0")
public class ForagePlugin implements Plugin {

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
                        .addSubcommand("export", new Export(main))
                        .addSubcommand("run", new ForageRun(main)));
    }

    /**
     * Exporter is used to add runtime dependencies for both `forage run` and `forage export`
     */
    @Override
    public Optional<PluginExporter> getExporter() {
        return Optional.of(new PluginExporter() {
            @Override
            public Set<String> getDependencies(org.apache.camel.dsl.jbang.core.common.RuntimeType runtimeType) {
                // gather dependencies across all (enabled) export customizers for the specific runtime
                return ExportHelper.getAllCustomizers()
                        .filter(ExportCustomizer::isEnabled)
                        .map(exportCustomizer ->
                                exportCustomizer.resolveRuntimeDependencies(RuntimeType.fromValue(runtimeType.name())))
                        .flatMap(Set::stream)
                        .collect(Collectors.toSet());
            }

            @Override
            public void addSourceFiles(Path buildDir, String packageName, Printer printer) {}
        });
    }
}
