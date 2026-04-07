package io.kaoto.forage.core.util.config;

import java.io.InputStream;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ClassPathPropertyFileSourceTest {

    @Test
    void priorityIs100() {
        assertThat(new ClassPathPropertyFileSource().priority()).isEqualTo(100);
    }

    @Test
    void returnsNullForNonexistentResource() {
        ClassPathPropertyFileSource source = new ClassPathPropertyFileSource();
        InputStream is = source.locate("nonexistent-resource-" + System.nanoTime());
        assertThat(is).isNull();
    }

    @Test
    void findsClasspathResource() throws Exception {
        ClassPathPropertyFileSource source = new ClassPathPropertyFileSource();
        // The test-only service file is on the test classpath
        InputStream is =
                source.locate("META-INF/services/io.kaoto.forage.core.util.config.PluggablePropertyFileSource");
        assertThat(is).isNotNull();
        is.close();
    }
}
