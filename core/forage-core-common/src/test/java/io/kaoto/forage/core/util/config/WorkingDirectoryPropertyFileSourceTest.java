package io.kaoto.forage.core.util.config;

import java.io.InputStream;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class WorkingDirectoryPropertyFileSourceTest {

    @Test
    void priorityIs300() {
        assertThat(new WorkingDirectoryPropertyFileSource().priority()).isEqualTo(300);
    }

    @Test
    void returnsNullForNonexistentFile() {
        WorkingDirectoryPropertyFileSource source = new WorkingDirectoryPropertyFileSource();
        InputStream is = source.locate("nonexistent-file-" + System.nanoTime() + ".properties");
        assertThat(is).isNull();
    }

    @Test
    void nameReturnsClassName() {
        assertThat(new WorkingDirectoryPropertyFileSource().name()).isEqualTo("WorkingDirectoryPropertyFileSource");
    }
}
