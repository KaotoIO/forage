package io.kaoto.forage.core;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import io.kaoto.forage.core.common.BeanFactory;
import io.kaoto.forage.core.common.RuntimeManagedBeanFactories;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ForageContextServicePluginTest {

    private CamelContext context;

    @BeforeEach
    void setUp() {
        PlainFactory.CONFIGURED.set(0);
        RuntimeManagedFactory.CONFIGURED.set(0);
        // Not built: building would make Camel itself load the plugin
        context = new DefaultCamelContext(false);
        context.setApplicationContextClassLoader(getClass().getClassLoader());
    }

    @AfterEach
    void tearDown() throws Exception {
        context.close();
    }

    @Test
    void configuresFactoriesThatNoRuntimeManages() {
        new ForageContextServicePlugin().load(context);

        assertThat(PlainFactory.CONFIGURED).hasValue(1);
    }

    @Test
    void skipsFactoriesManagedByTheRuntime() {
        new ForageContextServicePlugin().load(context);

        assertThat(RuntimeManagedFactory.CONFIGURED).hasValue(0);
    }

    public static class PlainFactory extends ContextAwareFactory {
        static final AtomicInteger CONFIGURED = new AtomicInteger();

        @Override
        public void configure() {
            CONFIGURED.incrementAndGet();
        }
    }

    public static class RuntimeManagedFactory extends ContextAwareFactory {
        static final AtomicInteger CONFIGURED = new AtomicInteger();

        @Override
        public void configure() {
            CONFIGURED.incrementAndGet();
        }
    }

    public static class TestRuntimeManagedBeanFactories implements RuntimeManagedBeanFactories {
        @Override
        public Set<String> beanFactoryClassNames() {
            return Set.of(RuntimeManagedFactory.class.getName());
        }
    }

    abstract static class ContextAwareFactory implements BeanFactory {
        private CamelContext camelContext;

        @Override
        public void setCamelContext(CamelContext camelContext) {
            this.camelContext = camelContext;
        }

        @Override
        public CamelContext getCamelContext() {
            return camelContext;
        }
    }
}
