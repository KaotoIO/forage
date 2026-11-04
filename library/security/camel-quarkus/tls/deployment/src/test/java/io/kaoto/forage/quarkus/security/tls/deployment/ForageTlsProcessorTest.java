package io.kaoto.forage.quarkus.security.tls.deployment;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.Resources.SYSTEM_PROPERTIES;

@ResourceLock(SYSTEM_PROPERTIES)
class ForageTlsProcessorTest {

    private static final List<String> PROPERTIES = List.of(
            "forage.tls.truststore.path",
            "forage.tls.keystore.path",
            "forage.tls.cipher.suites",
            "forage.internal.tls.keystore.path",
            "forage.external.tls.truststore.path",
            "forage.incomplete.tls.cipher.suites");

    @AfterEach
    void reset() {
        PROPERTIES.forEach(System::clearProperty);
        ConfigStore.getInstance().reload();
    }

    @Test
    void registersTheDefaultProfileAsSslContextParameters() {
        System.setProperty("forage.tls.truststore.path", "classpath:forage-test-truststore.p12");

        assertThat(ForageTlsProcessor.profiles()).containsOnlyKeys("sslContextParameters");
        assertThat(ForageTlsProcessor.profiles().get("sslContextParameters")).isNull();
    }

    @Test
    void registersNamedProfilesDefiningAStoreOnly() {
        System.setProperty("forage.internal.tls.keystore.path", "file:/certs/internal.p12");
        System.setProperty("forage.external.tls.truststore.path", "classpath:forage-test-truststore.p12");
        System.setProperty("forage.incomplete.tls.cipher.suites", "TLS_AES_256_GCM_SHA384");

        Map<String, String> profiles = ForageTlsProcessor.profiles();

        assertThat(profiles).containsExactly(Map.entry("external", "external"), Map.entry("internal", "internal"));
    }

    @Test
    void registersNothingWithoutAStore() {
        System.setProperty("forage.tls.cipher.suites", "TLS_AES_256_GCM_SHA384");

        assertThat(ForageTlsProcessor.profiles()).isEmpty();
    }

    @Test
    void includesTheClasspathStoresInTheNativeImage() {
        System.setProperty("forage.tls.truststore.path", "classpath:forage-test-truststore.p12");
        System.setProperty("forage.tls.keystore.path", "file:/certs/server.p12");
        List<NativeImageResourceBuildItem> resources = new ArrayList<>();

        new ForageTlsProcessor().registerNativeResources(resources::add);

        assertThat(resources).singleElement().satisfies(item -> assertThat(item.getResources())
                .containsExactly("forage-test-truststore.p12"));
    }

    @Test
    void resolvesLocationsWithoutSchemeFromTheClasspath() {
        ClassLoader classLoader = getClass().getClassLoader();
        Set<String> paths = new LinkedHashSet<>();

        ForageTlsProcessor.addClasspathResource("forage-test-truststore.p12", classLoader, paths);
        ForageTlsProcessor.addClasspathResource("/forage-test-truststore.p12", classLoader, paths);
        ForageTlsProcessor.addClasspathResource("missing.p12", classLoader, paths);
        ForageTlsProcessor.addClasspathResource("https://example.com/store.p12", classLoader, paths);

        assertThat(paths).containsExactly("forage-test-truststore.p12");
    }
}
