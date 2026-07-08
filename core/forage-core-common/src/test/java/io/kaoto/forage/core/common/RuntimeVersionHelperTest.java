package io.kaoto.forage.core.common;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RuntimeVersionHelperTest {

    @Test
    void testVersionsAreLoaded() {
        assertThat(RuntimeVersionHelper.VERSIONS).isNotNull();
        assertThat(RuntimeVersionHelper.VERSIONS).isNotEmpty();
    }

    @Test
    void testCamelVersionIsPresent() {
        assertThat(RuntimeVersionHelper.VERSIONS.getProperty("org.apache.camel"))
                .isNotNull()
                .isNotEmpty();
    }

    @Test
    void testCamelSpringBootVersionIsPresent() {
        assertThat(RuntimeVersionHelper.VERSIONS.getProperty("org.apache.camel.springboot"))
                .isNotNull()
                .isNotEmpty();
    }

    @Test
    void testCamelQuarkusVersionIsPresent() {
        assertThat(RuntimeVersionHelper.VERSIONS.getProperty("org.apache.camel.extensions.quarkus"))
                .isNotNull()
                .isNotEmpty();
    }
}
