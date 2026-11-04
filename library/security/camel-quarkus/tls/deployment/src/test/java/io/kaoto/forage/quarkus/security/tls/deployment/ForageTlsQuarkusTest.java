package io.kaoto.forage.quarkus.security.tls.deployment;

import jakarta.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.camel.support.jsse.SSLContextParameters;
import io.kaoto.forage.security.tls.TlsBeanFactory;
import io.quarkus.test.QuarkusUnitTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots a Camel Quarkus application (JVM mode) with two TLS profiles configured through application properties.
 */
class ForageTlsQuarkusTest {

    @RegisterExtension
    static final QuarkusUnitTest TEST = new QuarkusUnitTest()
            .withEmptyApplication()
            .overrideConfigKey("forage.partner.tls.truststore.path", "classpath:forage-test-truststore.p12")
            .overrideConfigKey("forage.partner.tls.truststore.password", "changeit")
            .overrideConfigKey("forage.mtls.tls.keystore.path", "file:/certs/mtls.p12")
            .overrideConfigKey("forage.mtls.tls.client.authentication", "REQUIRE")
            .overrideConfigKey("forage.mtls.tls.secure.socket.protocol", "TLSv1.2")
            // The plain factory would log the profiles it registers during static initialization
            .setLogRecordPredicate(record -> TlsBeanFactory.class.getName().equals(record.getLoggerName()))
            .assertLogRecords(records -> assertThat(records)
                    .as("the plain TlsBeanFactory must not run on Quarkus")
                    .isEmpty());

    @Inject
    CamelContext context;

    @Test
    void registersEveryProfile() {
        SSLContextParameters partner = context.getRegistry().lookupByNameAndType("partner", SSLContextParameters.class);
        assertThat(partner.getTrustManagers().getKeyStore().getResource())
                .isEqualTo("classpath:forage-test-truststore.p12");
        assertThat(partner.getTrustManagers().getKeyStore().getPassword()).isEqualTo("changeit");

        SSLContextParameters mtls = context.getRegistry().lookupByNameAndType("mtls", SSLContextParameters.class);
        assertThat(mtls.getServerParameters().getClientAuthentication()).isEqualTo("REQUIRE");
        assertThat(mtls.getSecureSocketProtocol()).isEqualTo("TLSv1.2");

        assertThat(context.getRegistry().lookupByName("sslContextParameters")).isNull();
    }
}
