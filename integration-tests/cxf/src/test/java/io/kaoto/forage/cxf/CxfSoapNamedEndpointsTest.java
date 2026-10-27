package io.kaoto.forage.cxf;

import java.util.function.Consumer;
import org.citrusframework.annotations.CitrusTest;
import org.citrusframework.junit.jupiter.CitrusSupport;
import io.kaoto.forage.integration.tests.ForageIntegrationTest;
import io.kaoto.forage.integration.tests.ForageTestCaseRunner;
import io.kaoto.forage.integration.tests.IntegrationTestSetupExtension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Tests CXF SOAP server with named endpoints (helloServer + helloClient) instead of the
 * default unnamed cxfEndpoint bean. This validates that all named CXF beans get correct
 * servlet path adaptation on Spring Boot and Quarkus, preventing HttpDestinationFactory
 * errors when exporting to runtimes with embedded servlet containers.
 */
@CitrusSupport
@ExtendWith(IntegrationTestSetupExtension.class)
public class CxfSoapNamedEndpointsTest implements ForageIntegrationTest {

    public static final String INTEGRATION_NAME = "cxf-soap-named-endpoints";

    @Override
    public String runBeforeAll(ForageTestCaseRunner runner, Consumer<AutoCloseable> afterAll) {
        runner.when(forageRun(INTEGRATION_NAME, "forage-cxf.properties", "cxf-soap-named-endpoints.camel.yaml")
                .dumpIntegrationOutput(true));

        return INTEGRATION_NAME;
    }

    @Test
    @CitrusTest()
    public void namedEndpointsServerAndClient(ForageTestCaseRunner runner) {
        runner.then(camel().cli()
                .verify()
                .integration(INTEGRATION_NAME)
                .maxAttempts(8)
                .delayBetweenAttempts(5000)
                .waitForLogMessage("Server received SOAP request"));

        runner.then(camel().cli()
                .verify()
                .integration(INTEGRATION_NAME)
                .maxAttempts(8)
                .delayBetweenAttempts(5000)
                .waitForLogMessage("Client received response"));
    }
}
