package io.kaoto.forage.security.keycloak;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import io.kaoto.forage.core.util.config.ConfigStore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.parallel.Resources.SYSTEM_PROPERTIES;

/**
 * Runs a plain Camel route secured by the {@code keycloakPolicy} bean, registered by the Forage bean factory, against
 * a Keycloak server.
 */
@Testcontainers(disabledWithoutDocker = true)
@ResourceLock(SYSTEM_PROPERTIES)
class KeycloakSecurityPolicyContainerTest {

    private static final String TOKEN_HEADER = "CamelKeycloakAccessToken";
    private static final Pattern ACCESS_TOKEN = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final List<String> PROPERTIES = List.of(
            "forage.keycloak.server.url",
            "forage.keycloak.realm",
            "forage.keycloak.client.id",
            "forage.keycloak.client.secret",
            "forage.keycloak.required.roles",
            "forage.keycloak.use.token.introspection");

    // Aligned with Camel Quarkus 3.40
    @Container
    static final GenericContainer<?> KEYCLOAK = new GenericContainer<>(
                    DockerImageName.parse("quay.io/keycloak/keycloak:26.7.4"))
            .withExposedPorts(8080)
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("forage-realm.json"),
                    "/opt/keycloak/data/import/forage-realm.json")
            .withCommand("start-dev", "--import-realm")
            .waitingFor(Wait.forHttp("/realms/forage/.well-known/openid-configuration")
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(3)));

    @AfterEach
    void reset() {
        PROPERTIES.forEach(System::clearProperty);
        ConfigStore.getInstance().reload();
    }

    @Test
    void securesTheRouteAndClosesTheClientsWhenCamelStops() throws Exception {
        configure(false);
        ForageKeycloakSecurityPolicy policy;
        try (CamelContext camel = securedCamel()) {
            policy = camel.getRegistry().lookupByNameAndType("keycloakPolicy", ForageKeycloakSecurityPolicy.class);
            ProducerTemplate template = camel.createProducerTemplate();

            assertThat(template.requestBodyAndHeader(
                            "direct:secured", "", TOKEN_HEADER, accessToken("forage-admin", "admin-secret")))
                    .isEqualTo("granted");
            assertThatThrownBy(() -> template.requestBodyAndHeader(
                            "direct:secured", "", TOKEN_HEADER, accessToken("forage-guest", "guest-secret")))
                    .hasCauseInstanceOf(CamelAuthorizationException.class);
            assertThat(policy.getKeycloakClient().isClosed()).isFalse();
        }
        assertThat(policy.isClosed()).isTrue();
        assertThat(policy.getKeycloakClient().isClosed()).isTrue();
    }

    @Test
    void closesTheIntrospectionClientWhenCamelStops() throws Exception {
        configure(true);
        ForageKeycloakSecurityPolicy policy;
        try (CamelContext camel = securedCamel()) {
            policy = camel.getRegistry().lookupByNameAndType("keycloakPolicy", ForageKeycloakSecurityPolicy.class);

            assertThat(camel.createProducerTemplate()
                            .requestBodyAndHeader(
                                    "direct:secured", "", TOKEN_HEADER, accessToken("forage-admin", "admin-secret")))
                    .isEqualTo("granted");
        }
        String token = accessToken("forage-admin", "admin-secret");
        assertThatThrownBy(() -> policy.getTokenIntrospector().introspect(token))
                .isInstanceOf(IOException.class)
                .rootCause()
                .hasMessageContaining("shut down");
    }

    private static void configure(boolean introspection) {
        System.setProperty("forage.keycloak.server.url", serverUrl());
        System.setProperty("forage.keycloak.realm", "forage");
        System.setProperty("forage.keycloak.client.id", "forage-camel");
        System.setProperty("forage.keycloak.client.secret", "camel-secret");
        System.setProperty("forage.keycloak.required.roles", "admin");
        System.setProperty("forage.keycloak.use.token.introspection", String.valueOf(introspection));
    }

    private static CamelContext securedCamel() throws Exception {
        CamelContext camel = new DefaultCamelContext();
        camel.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:secured").policy("keycloakPolicy").setBody(constant("granted"));
            }
        });
        camel.start();
        return camel;
    }

    private static String serverUrl() {
        return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080);
    }

    private static String accessToken(String clientId, String clientSecret) throws Exception {
        String form = "grant_type=client_credentials&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(serverUrl() + "/realms/forage/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        Matcher matcher = ACCESS_TOKEN.matcher(response.body());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
