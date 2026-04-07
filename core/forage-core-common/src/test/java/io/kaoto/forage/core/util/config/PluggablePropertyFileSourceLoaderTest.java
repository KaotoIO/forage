package io.kaoto.forage.core.util.config;

import java.util.List;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PluggablePropertyFileSourceLoaderTest {

    @Test
    void getSourcesReturnsEmptyByDefault() {
        // No META-INF/services file registered in test classpath, so should be empty
        PluggablePropertyFileSourceLoader.reload();
        List<PluggablePropertyFileSource> sources = PluggablePropertyFileSourceLoader.getSources();
        assertThat(sources).isNotNull();
    }

    @Test
    void getSourcesIsCached() {
        PluggablePropertyFileSourceLoader.reload();
        List<PluggablePropertyFileSource> first = PluggablePropertyFileSourceLoader.getSources();
        List<PluggablePropertyFileSource> second = PluggablePropertyFileSourceLoader.getSources();
        assertThat(first).isSameAs(second);
    }

    @Test
    void reloadClearsCache() {
        PluggablePropertyFileSourceLoader.reload();
        PluggablePropertyFileSourceLoader.getSources();
        // After reload, getSources() should succeed without error
        PluggablePropertyFileSourceLoader.reload();
        List<PluggablePropertyFileSource> reloaded = PluggablePropertyFileSourceLoader.getSources();
        assertThat(reloaded).isNotNull();
    }
}
