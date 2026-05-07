package io.kaoto.forage.cxf;

import java.util.function.Consumer;
import org.citrusframework.annotations.CitrusTest;
import org.citrusframework.junit.jupiter.CitrusSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.integration.tests.ForageIntegrationTest;
import io.kaoto.forage.integration.tests.ForageTestCaseRunner;
import io.kaoto.forage.integration.tests.IntegrationTestSetupExtension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@CitrusSupport
@ExtendWith(IntegrationTestSetupExtension.class)
public class CxfSoapServerTest implements ForageIntegrationTest {
    private static final Logger LOG = LoggerFactory.getLogger(CxfSoapServerTest.class);

    public static final String INTEGRATION_NAME = "cxf-soap-server";

    @Override
    public String runBeforeAll(ForageTestCaseRunner runner, Consumer<AutoCloseable> afterAll) {
        runner.when(forageRun(INTEGRATION_NAME, "forage-cxf.properties", "cxf-soap-server.camel.yaml")
                .dumpIntegrationOutput(true));

        return INTEGRATION_NAME;
    }

    @Test
    @CitrusTest()
    public void soapServerCall(ForageTestCaseRunner runner) {
        runner.then(camel().jbang()
                .verify()
                .integration(INTEGRATION_NAME)
                .maxAttempts(8)
                .delayBetweenAttempts(5000)
                .waitForLogMessage("Server received request"));

        runner.then(camel().jbang()
                .verify()
                .integration(INTEGRATION_NAME)
                .maxAttempts(8)
                .delayBetweenAttempts(5000)
                .waitForLogMessage("Hello from CXF server"));
    }
}
