package io.kaoto.forage.integration.security;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.microprofile.config.ConfigProvider;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Keycloak server with the {@code forage} realm: the {@code forage-camel} client used by the policy, and two
 * service-account clients whose tokens carry the {@code admin} and {@code guest} realm roles.
 */
final class KeycloakContainer {
    static final String REALM = "forage";

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Pattern ACCESS_TOKEN = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");
    private static final GenericContainer<?> KEYCLOAK = new GenericContainer<>(DockerImageName.parse(
                    ConfigProvider.getConfig().getValue("keycloak.container.image", String.class)))
            .withExposedPorts(8080)
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource(
                            "io/kaoto/forage/integration/security/SecurityKeycloakTest/forage-realm.json"),
                    "/opt/keycloak/data/import/forage-realm.json")
            .withCommand("start-dev", "--import-realm")
            .waitingFor(Wait.forHttp("/realms/" + REALM + "/.well-known/openid-configuration")
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(3)));

    private KeycloakContainer() {}

    static synchronized String serverUrl() {
        if (!KEYCLOAK.isRunning()) {
            KEYCLOAK.start();
        }
        return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080);
    }

    /**
     * Obtains an access token with the client credentials grant.
     */
    static String accessToken(String clientId, String clientSecret) {
        String form = "grant_type=client_credentials&client_id=" + encode(clientId) + "&client_secret="
                + encode(clientSecret);
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(serverUrl() + "/realms/" + REALM + "/protocol/openid-connect/token"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            Matcher matcher = ACCESS_TOKEN.matcher(response.body());
            if (response.statusCode() != 200 || !matcher.find()) {
                throw new IllegalStateException(
                        "Cannot obtain a token for " + clientId + ": " + response.statusCode() + " " + response.body());
            }
            return matcher.group(1);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
