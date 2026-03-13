package io.kaoto.forage.plugin;

import org.apache.camel.dsl.jbang.core.common.Printer;
import picocli.CommandLine;

/**
 * PicoCli mixin for property validation options.
 *
 * <p>This mixin provides common command-line options for property validation
 * and a helper method to validate properties using the ForagePropertyValidator.
 *
 * @since 1.1
 */
public class PropertyValidationMixin {

    @CommandLine.Option(
            names = {"--strict"},
            description = "Fail on property validation warnings",
            defaultValue = "false")
    private boolean strict;

    @CommandLine.Option(
            names = {"--skip-validation"},
            description = "Skip property validation",
            defaultValue = "false")
    private boolean skipValidation;

    /**
     * Validates Forage properties and prints warnings.
     *
     * @param printer the printer to use for output
     * @return 0 if validation passed or was skipped, 1 if strict mode failed
     */
    public int validateAndReport(Printer printer) {
        return ForagePropertyValidator.validateAndReport(printer, skipValidation, strict);
    }
}
