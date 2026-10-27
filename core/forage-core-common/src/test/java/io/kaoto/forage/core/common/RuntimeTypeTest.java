package io.kaoto.forage.core.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeTypeTest {

    @ParameterizedTest
    @CsvSource({
        // org.apache.camel.dsl.jbang.core.common.RuntimeType names, as passed by the Forage plugin exporter
        "jbang, MAIN",
        "main, MAIN",
        "springBoot, SPRING_BOOT",
        "quarkus, QUARKUS",
        // aliases
        "camel-jbang, MAIN",
        "camel-main, MAIN",
        "spring-boot, SPRING_BOOT",
        "camel-spring-boot, SPRING_BOOT",
        "camel-quarkus, QUARKUS"
    })
    void fromValueMapsCamelRuntimes(String value, RuntimeType expected) {
        assertThat(RuntimeType.fromValue(value)).isEqualTo(expected);
    }

    @Test
    void fromValueRejectsUnknownRuntime() {
        assertThatThrownBy(() -> RuntimeType.fromValue("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported runtime unknown");
    }
}
