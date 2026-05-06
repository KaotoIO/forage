package io.kaoto.forage.cxf;

import java.util.Collections;
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
        var builder = forageRun(INTEGRATION_NAME, "forage-cxf.properties", "route-server.camel.yaml")
                .dumpIntegrationOutput(true);

        String runtime = System.getProperty(IntegrationTestSetupExtension.RUNTIME_PROPERTY);
        if ("quarkus".equals(runtime)) {
            builder.withEnvs(Collections.singletonMap("FORAGE_CXF_ADDRESS", "/hello"));
        }

        runner.when(builder);

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
