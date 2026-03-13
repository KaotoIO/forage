package io.kaoto.forage.plugin;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.Export;
import picocli.CommandLine;

/**
 * Forage export command with property validation.
 *
 * <p>Extends the standard Camel JBang export command to validate Forage properties
 * before exporting the project.
 *
 * @since 1.1
 */
public class ForageExport extends Export {

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

    public ForageExport(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        // Validate properties before exporting
        int validationResult = ForagePropertyValidator.validateAndReport(printer(), skipValidation, strict);
        if (validationResult != 0) {
            return validationResult;
        }

        return super.doCall();
    }
}
