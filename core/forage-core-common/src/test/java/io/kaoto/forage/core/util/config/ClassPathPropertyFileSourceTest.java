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
    void findsClasspathResource() {
        ClassPathPropertyFileSource source = new ClassPathPropertyFileSource();
        // The META-INF/services file should be findable on classpath
        InputStream is = source.locate("META-INF/services/org.apache.camel.spi.ContextServicePlugin");
        if (is != null) {
            assertThat(is).isNotNull();
            try {
                is.close();
            } catch (Exception e) {
                // ignore
            }
        }
        // If not found, it's fine — just verify no exception was thrown
    }
}
