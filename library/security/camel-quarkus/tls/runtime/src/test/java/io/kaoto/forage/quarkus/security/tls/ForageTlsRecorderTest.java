package io.kaoto.forage.quarkus.security.tls;

import org.apache.camel.support.jsse.SSLContextParameters;
import io.kaoto.forage.core.util.config.ConfigStore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.parallel.Resources.SYSTEM_PROPERTIES;

@ResourceLock(SYSTEM_PROPERTIES)
class ForageTlsRecorderTest {

    @AfterEach
    void reset() {
        System.clearProperty("forage.partner.tls.truststore.path");
        ConfigStore.getInstance().reload();
    }

    @Test
    void createsTheParametersFromTheRuntimeConfiguration() {
        System.setProperty("forage.partner.tls.truststore.path", "classpath:partner.p12");

        SSLContextParameters parameters =
                new ForageTlsRecorder().createSslContextParameters("partner").getValue();

        assertThat(parameters.getTrustManagers().getKeyStore().getResource()).isEqualTo("classpath:partner.p12");
    }

    @Test
    void failsWhenTheProfileLostItsStoresAtRuntime() {
        ForageTlsRecorder recorder = new ForageTlsRecorder();

        assertThatThrownBy(() -> recorder.createSslContextParameters("partner"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("forage.partner.tls.keystore.path");
    }
}
