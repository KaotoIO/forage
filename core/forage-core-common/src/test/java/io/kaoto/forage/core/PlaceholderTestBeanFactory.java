package io.kaoto.forage.core;

import org.apache.camel.CamelContext;
import io.kaoto.forage.core.common.BeanFactory;
import io.kaoto.forage.core.util.config.PlaceholderTestConfig;

/**
 * Test bean factory: binds the configured {@code forage.placeholdertest.password}, if any, as bean
 * {@value #BEAN_NAME}, the way module factories pass a password to a DataSource. No-op otherwise.
 */
public class PlaceholderTestBeanFactory implements BeanFactory {

    public static final String BEAN_NAME = "placeholderTestPassword";

    private CamelContext camelContext;

    @Override
    public void configure() {
        new PlaceholderTestConfig()
                .password()
                .ifPresent(password -> camelContext.getRegistry().bind(BEAN_NAME, password));
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }
}
