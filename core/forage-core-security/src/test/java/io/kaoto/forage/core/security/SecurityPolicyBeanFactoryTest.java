package io.kaoto.forage.core.security;

import java.util.concurrent.atomic.AtomicInteger;
import org.apache.camel.CamelContext;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.AuthorizationPolicy;
import org.apache.camel.spi.ContextServiceLoaderPluginResolver;
import io.kaoto.forage.core.util.config.Config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the factory through Camel's context service plugin lifecycle, as on plain Camel.
 */
class SecurityPolicyBeanFactoryTest {

    @BeforeEach
    void reset() {
        UnconfiguredProvider.CREATED.set(0);
    }

    @Test
    void registersPoliciesOfConfiguredProvidersOnly() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            assertThat(context.getRegistry().lookupByNameAndType("closeablePolicy", AuthorizationPolicy.class))
                    .isInstanceOf(CloseablePolicy.class);
            assertThat(context.getRegistry().lookupByName("unconfiguredPolicy")).isNull();
            assertThat(UnconfiguredProvider.CREATED).hasValue(0);
        }
    }

    @Test
    void closesPoliciesWhenTheContextStops() throws Exception {
        CloseablePolicy policy;
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            policy = context.getRegistry().lookupByNameAndType("closeablePolicy", CloseablePolicy.class);
            assertThat(policy.closed).isFalse();
        }
        assertThat(policy.closed).isTrue();
    }

    @Test
    void closesReplacedPoliciesOnceNoRouteCanUseThem() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:secured")
                            .routeId("secured")
                            .policy("closeablePolicy")
                            .to("mock:result");
                }
            });
            context.start();
            CloseablePolicy original =
                    context.getRegistry().lookupByNameAndType("closeablePolicy", CloseablePolicy.class);

            reload(context);

            CloseablePolicy replacement =
                    context.getRegistry().lookupByNameAndType("closeablePolicy", CloseablePolicy.class);
            assertThat(replacement).isNotSameAs(original);
            assertThat(original.closed)
                    .as("the running route still uses the original policy")
                    .isFalse();

            context.getRouteController().stopRoute("secured");
            context.removeRoute("secured");

            assertThat(original.closed).isTrue();
            assertThat(replacement.closed).isFalse();
        }
    }

    @Test
    void closesReplacedPoliciesImmediatelyWithoutRoutes() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            CloseablePolicy original =
                    context.getRegistry().lookupByNameAndType("closeablePolicy", CloseablePolicy.class);

            reload(context);

            assertThat(original.closed).isTrue();
            assertThat(context.getRegistry().lookupByNameAndType("closeablePolicy", CloseablePolicy.class))
                    .isNotSameAs(original);
        }
    }

    private static void reload(CamelContext context) {
        context.getCamelContextExtension()
                .getContextPlugin(ContextServiceLoaderPluginResolver.class)
                .onReload();
    }

    public static class CloseableProvider implements SecurityPolicyProvider {
        @Override
        public String name() {
            return "closeable";
        }

        @Override
        public AuthorizationPolicy create(String id) {
            return new CloseablePolicy();
        }
    }

    public static class UnconfiguredProvider implements SecurityPolicyProvider {
        static final AtomicInteger CREATED = new AtomicInteger();

        @Override
        public String name() {
            return "unconfigured";
        }

        @Override
        public SecurityPolicyModuleDescriptor<?> descriptor() {
            return new SecurityPolicyModuleDescriptor<Config>() {
                @Override
                public boolean isConfigured() {
                    return false;
                }

                @Override
                public String modulePrefix() {
                    return "unconfigured";
                }

                @Override
                public Config createConfig(String prefix) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public String resolveProviderClassName(Config config) {
                    return UnconfiguredProvider.class.getName();
                }

                @Override
                public String defaultBeanName() {
                    return "unconfiguredPolicy";
                }
            };
        }

        @Override
        public AuthorizationPolicy create(String id) {
            CREATED.incrementAndGet();
            return new CloseablePolicy();
        }
    }

    static class CloseablePolicy implements AuthorizationPolicy, AutoCloseable {
        volatile boolean closed;

        @Override
        public void beforeWrap(Route route, NamedNode definition) {
            // nothing to prepare
        }

        @Override
        public Processor wrap(Route route, Processor processor) {
            return processor;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
