package io.kaoto.forage.integration.tests.suites;

import org.slf4j.Logger;
import io.kaoto.forage.core.common.RuntimeType;
import io.kaoto.forage.integration.tests.IntegrationTestSetupExtension;
import io.kaoto.forage.plugin.ExportHelper;

/**
 * Helper class for Suites.
 */
class TestSuiteHelper {

    public static void afterSuite() {
        System.clearProperty(IntegrationTestSetupExtension.RUNTIME_PROPERTY);
        System.clearProperty("citrus.camel.jbang.version");
    }

    public static void beforeSuite(RuntimeType runtime, Logger log) {
        if (runtime != RuntimeType.main) {
            System.setProperty(IntegrationTestSetupExtension.RUNTIME_PROPERTY, runtime.runtime());
        } else {
            System.clearProperty(IntegrationTestSetupExtension.RUNTIME_PROPERTY);
        }
        System.setProperty("citrus.camel.jbang.version", ExportHelper.getCamelVersion());
    }
}
