package io.kaoto.forage.springboot.jdbc;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.support.service.ServiceSupport;

/**
 * Test placeholder function {@code {{forage-sb-test:name}}}, discovered like Camel's vault functions
 * through {@code META-INF/services/org/apache/camel/properties-function/forage-sb-test}. Like the AWS
 * function, {@code {{forage-sb-test:region}}} returns {@code camel.vault.aws.region}.
 */
public class TestSecretPropertiesFunction extends ServiceSupport implements PropertiesFunction, CamelContextAware {

    public static final String SECRET = "sb-s3cr3t";

    private CamelContext camelContext;

    @Override
    public String getName() {
        return "forage-sb-test";
    }

    @Override
    public String apply(String remainder) {
        return switch (remainder) {
            case "db-password" -> SECRET;
            case "region" -> camelContext.getVaultConfiguration().aws().getRegion();
            default -> null;
        };
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }
}
