package io.kaoto.forage.core.util.config;

import java.util.Map;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.support.service.ServiceSupport;

/**
 * Test placeholder function {@code {{forage-test:name}}}, discovered like Camel's vault functions through
 * {@code META-INF/services/org/apache/camel/properties-function/forage-test}. Like the AWS function, it
 * reads the vault configuration of the CamelContext when it starts.
 */
public class TestSecretPropertiesFunction extends ServiceSupport implements PropertiesFunction, CamelContextAware {

    public static final String SECRET = "s3cr3t-Value";

    private static final Map<String, String> SECRETS = Map.of("db-password", SECRET, "api-key", "k3y-Value");

    private CamelContext camelContext;
    private String region;
    private boolean defaultCredentialsProvider;

    @Override
    protected void doStart() {
        region = camelContext.getVaultConfiguration().aws().getRegion();
        defaultCredentialsProvider = camelContext.getVaultConfiguration().aws().isDefaultCredentialsProvider();
    }

    @Override
    public String getName() {
        return "forage-test";
    }

    @Override
    public String apply(String remainder) {
        if ("boom".equals(remainder)) {
            throw new IllegalStateException("secret store unavailable");
        }
        if ("region".equals(remainder)) {
            return region;
        }
        if ("default-credentials-provider".equals(remainder)) {
            return String.valueOf(defaultCredentialsProvider);
        }
        return SECRETS.get(remainder);
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
