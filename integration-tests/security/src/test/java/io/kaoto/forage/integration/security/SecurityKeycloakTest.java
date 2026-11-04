package io.kaoto.forage.integration.security;

import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
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
 * Secures a route with the {@code keycloakPolicy} bean created from {@code forage.keycloak.*} properties, against a
 * Keycloak server. The tokens are obtained by the test and sent to the route.
 */
@CitrusSupport
@ExtendWith({IntegrationTestSetupExtension.class})
public class SecurityKeycloakTest implements ForageIntegrationTest {
    private static final String NAME = "forage-security-keycloak-test";
    private static final String TOKEN_HEADER = "CamelKeycloakAccessToken";

    @Override
    public String runBeforeAll(ForageTestCaseRunner runner, Consumer<AutoCloseable> afterAll) {
        Resource properties = PropertiesTemplateHelper.createFromTemplate(
                classResource("forage-security-keycloak.properties.template"),
                Map.of(
                        "forage\\.keycloak\\.server\\.url=.*",
                        Matcher.quoteReplacement("forage.keycloak.server.url=" + KeycloakContainer.serverUrl())),
                afterAll);
        runner.when(forageRun(NAME)
                .addResource(properties)
                .addResource(classResource("route.camel.yaml"))
                .dumpIntegrationOutput(true));
        return NAME;
    }

    @Test
    @CitrusTest
    void grantsAccessToTokensWithTheRequiredRole(ForageTestCaseRunner runner) {
        runner.when(forageCmdSend(NAME)
                .endpoint("direct:secured")
                .body("request")
                .header("caller", "admin")
                .header(TOKEN_HEADER, KeycloakContainer.accessToken("forage-admin", "admin-secret")));
        runner.then(camel().cli().verify().integration(NAME).waitForLogMessage("Keycloak access granted to admin"));
    }

    @Test
    @CitrusTest
    void deniesAccessToTokensWithoutTheRequiredRole(ForageTestCaseRunner runner) {
        runner.when(forageCmdSend(NAME)
                .endpoint("direct:secured")
                .body("request")
                .header("caller", "guest")
                .header(TOKEN_HEADER, KeycloakContainer.accessToken("forage-guest", "guest-secret")));
        runner.then(camel().cli().verify().integration(NAME).waitForLogMessage("Keycloak access denied to guest"));
    }

    @Test
    @CitrusTest
    void deniesAccessWithoutToken(ForageTestCaseRunner runner) {
        runner.when(
                forageCmdSend(NAME).endpoint("direct:secured").body("request").header("caller", "anonymous"));
        runner.then(camel().cli().verify().integration(NAME).waitForLogMessage("Keycloak access denied to anonymous"));
    }
}
