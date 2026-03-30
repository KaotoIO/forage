package io.kaoto.forage.integration.tests;

import org.citrusframework.DefaultTestCaseRunner;
import org.citrusframework.GherkinTestActionRunner;
import org.citrusframework.TestAction;
import org.citrusframework.TestActionBuilder;
import org.citrusframework.TestCase;
import org.citrusframework.actions.camel.CamelIntegrationRunCustomizedActionBuilder;
import org.citrusframework.context.TestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.plugin.ExportHelper;

/**
 * Custom test case runner takes care of the quarkus version used by citrus framework
 */
public class ForageTestCaseRunner extends DefaultTestCaseRunner {
    private static final Logger LOG = LoggerFactory.getLogger(ForageTestCaseRunner.class);

    public ForageTestCaseRunner(TestContext context) {
        super(context);
    }

    public ForageTestCaseRunner(TestCase testCase, TestContext context) {
        super(testCase, context);
    }

    @Override
    public <T extends TestAction> GherkinTestActionRunner given(TestActionBuilder<T> builder) {
        if (builder instanceof CamelIntegrationRunCustomizedActionBuilder<?, ?> camelBuilder) {
            initializeTestAction(camelBuilder);
        }
        return super.given(builder);
    }

    @Override
    public <T extends TestAction> GherkinTestActionRunner when(TestActionBuilder<T> builder) {
        if (builder instanceof CamelIntegrationRunCustomizedActionBuilder<?, ?> camelBuilder) {
            initializeTestAction(camelBuilder);
        }
        return super.when(builder);
    }

    private <T extends TestAction> void initializeTestAction(CamelIntegrationRunCustomizedActionBuilder<?, ?> builder) {
        builder.withSystemProperty("camel.jbang.quarkusVersion", ExportHelper.getQuarkusVersion());
        builder.withArg("--java-version=" + System.getProperty("java.specification.version"));
        String runtime = System.getProperty(
                IntegrationTestSetupExtension.RUNTIME_PROPERTY,
                System.getenv(IntegrationTestSetupExtension.RUNTIME_PROPERTY));
        if (runtime != null) {
            builder.withArg("--runtime=" + runtime);
        }
        logTextInBox(runtime);
    }

    private void logTextInBox(String text) {

        int totalLength = 80;

        var _text = "runtime: " + (text == null || text.isBlank() ? "camel-main" : text);

        String paddedText = " " + _text + " ";
        int textLength = paddedText.length();

        int dashLength = totalLength - textLength;
        int leftDashes = dashLength / 2;
        int rightDashes = dashLength - leftDashes;

        String line = "-".repeat(leftDashes) + paddedText + "-".repeat(rightDashes);

        LOG.info("-".repeat(totalLength));
        LOG.info(line);
        LOG.info("-".repeat(totalLength));
    }
}
