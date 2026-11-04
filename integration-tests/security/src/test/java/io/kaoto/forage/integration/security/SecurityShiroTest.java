package io.kaoto.forage.integration.security;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Consumer;
import org.citrusframework.annotations.CitrusTest;
import org.citrusframework.junit.jupiter.CitrusSupport;
import org.citrusframework.spi.Resource;
import io.kaoto.forage.integration.tests.ForageIntegrationTest;
import io.kaoto.forage.integration.tests.ForageTestCaseRunner;
import io.kaoto.forage.integration.tests.IntegrationTestSetupExtension;
import io.kaoto.forage.integration.tests.PropertiesTemplateHelper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Secures a route with the {@code shiroPolicy} bean created from {@code forage.shiro.*} properties. The INI file is
 * referenced by absolute path, since exported runtimes run from a sub-folder.
 */
@CitrusSupport
@ExtendWith({IntegrationTestSetupExtension.class})
public class SecurityShiroTest implements ForageIntegrationTest {
    private static final String NAME = "forage-security-shiro-test";

    @Override
    public String runBeforeAll(ForageTestCaseRunner runner, Consumer<AutoCloseable> afterAll) {
        String iniFile;
        try {
            iniFile = classResource("shiro.ini").file().getCanonicalPath();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Resource properties = PropertiesTemplateHelper.createFromTemplate(
                classResource("forage-security-shiro.properties.template"),
                "forage\\.shiro\\.ini\\.resource\\.path=.*",
                "forage.shiro.ini.resource.path=file:" + iniFile,
                afterAll);
        runner.when(forageRun(NAME)
                .addResource(properties)
                .addResource(classResource("route.camel.yaml"))
                .dumpIntegrationOutput(true));
        return NAME;
    }

    // One test method: Citrus stops the integration at the end of the first test that verifies it
    @Test
    @CitrusTest
    void authorizesUsersByRole(ForageTestCaseRunner runner) {
        runner.then(camel().cli().verify().integration(NAME).waitForLogMessage("Shiro access granted to alice"));
        runner.then(camel().cli().verify().integration(NAME).waitForLogMessage("Shiro access denied to bob"));
    }
}
