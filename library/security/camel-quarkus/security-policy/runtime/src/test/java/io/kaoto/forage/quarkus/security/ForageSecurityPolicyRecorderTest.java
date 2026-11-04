package io.kaoto.forage.quarkus.security;

import java.util.ArrayList;
import java.util.List;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.spi.AuthorizationPolicy;
import io.kaoto.forage.core.security.SecurityPolicyProvider;
import io.quarkus.runtime.ShutdownContext;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ForageSecurityPolicyRecorderTest {

    @Test
    void closesTheCreatedPolicyOnShutdown() {
        List<Runnable> tasks = new ArrayList<>();
        ShutdownContext shutdown = mock(ShutdownContext.class);
        doAnswer(invocation -> tasks.add(invocation.getArgument(0)))
                .when(shutdown)
                .addShutdownTask(any(Runnable.class));

        AuthorizationPolicy policy = new ForageSecurityPolicyRecorder()
                .createPolicy(CloseableProvider.class.getName(), shutdown)
                .getValue();

        assertThat(policy).isInstanceOf(CloseablePolicy.class);
        assertThat(((CloseablePolicy) policy).closed).isFalse();
        assertThat(tasks).hasSize(1);
        tasks.get(0).run();
        assertThat(((CloseablePolicy) policy).closed).isTrue();
    }

    @Test
    void registersNoShutdownTaskForPoliciesWithoutResources() {
        ShutdownContext shutdown = mock(ShutdownContext.class);

        AuthorizationPolicy policy = new ForageSecurityPolicyRecorder()
                .createPolicy(PlainProvider.class.getName(), shutdown)
                .getValue();

        assertThat(policy).isInstanceOf(PlainPolicy.class);
        verifyNoInteractions(shutdown);
    }

    @Test
    void failsForAnUnknownProvider() {
        ForageSecurityPolicyRecorder recorder = new ForageSecurityPolicyRecorder();
        ShutdownContext shutdown = mock(ShutdownContext.class);

        assertThatThrownBy(() -> recorder.createPolicy("com.example.Missing", shutdown))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("com.example.Missing");
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

    public static class PlainProvider implements SecurityPolicyProvider {
        @Override
        public String name() {
            return "plain";
        }

        @Override
        public AuthorizationPolicy create(String id) {
            return new PlainPolicy();
        }
    }

    static class PlainPolicy implements AuthorizationPolicy {
        @Override
        public void beforeWrap(Route route, NamedNode definition) {
            // nothing to prepare
        }

        @Override
        public Processor wrap(Route route, Processor processor) {
            return processor;
        }
    }

    static class CloseablePolicy extends PlainPolicy implements AutoCloseable {
        volatile boolean closed;

        @Override
        public void close() {
            closed = true;
        }
    }
}
