package io.kaoto.forage.security.tls;

import org.apache.camel.CamelContext;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import io.kaoto.forage.core.util.config.ConfigStore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.Resources.SYSTEM_PROPERTIES;

/**
 * On Spring Boot, the factory runs without a Spring Boot adapter: Camel Spring Boot builds the CamelContext, which
 * loads Forage's context service plugin and with it every {@code BeanFactory} on the classpath.
 */
@ResourceLock(SYSTEM_PROPERTIES)
class TlsBeanFactorySpringBootTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(CamelAutoConfiguration.class));

    @AfterEach
    void reset() {
        ConfigStore.getInstance().reload();
    }

    @Test
    void registersTheDefaultProfileInTheCamelRegistry() {
        runner.withSystemProperties(
                        "forage.tls.truststore.path=classpath:truststore.p12",
                        "forage.tls.truststore.password=changeit",
                        "forage.tls.secure.socket.protocol=TLSv1.2")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SSLContextParameters parameters = context.getBean(CamelContext.class)
                            .getRegistry()
                            .lookupByNameAndType("sslContextParameters", SSLContextParameters.class);
                    assertThat(parameters).isNotNull();
                    assertThat(parameters.getTrustManagers().getKeyStore().getResource())
                            .isEqualTo("classpath:truststore.p12");
                    assertThat(parameters.getSecureSocketProtocol()).isEqualTo("TLSv1.2");
                });
    }

    @Test
    void registersNamedProfilesInTheCamelRegistry() {
        runner.withSystemProperties(
                        "forage.internal.tls.keystore.path=classpath:internal.p12",
                        "forage.internal.tls.client.authentication=REQUIRE",
                        "forage.external.tls.truststore.path=classpath:external.p12")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    CamelContext camel = context.getBean(CamelContext.class);
                    assertThat(camel.getRegistry()
                                    .lookupByNameAndType("internal", SSLContextParameters.class)
                                    .getServerParameters()
                                    .getClientAuthentication())
                            .isEqualTo("REQUIRE");
                    assertThat(camel.getRegistry().lookupByNameAndType("external", SSLContextParameters.class))
                            .isNotNull();
                    assertThat(camel.getRegistry().lookupByName("sslContextParameters"))
                            .isNull();
                });
    }
}
