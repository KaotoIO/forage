package io.kaoto.forage.core.util.config;

import java.io.InputStream;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PluggablePropertyFileSourceLoaderTest {

    @BeforeEach
    void resetLoader() {
        PluggablePropertyFileSourceLoader.reload();
    }

    @Test
    void discoversStubSource() {
        List<PluggablePropertyFileSource> sources = PluggablePropertyFileSourceLoader.getSources();
        assertThat(sources).isNotEmpty();
        assertThat(sources).anyMatch(s -> s instanceof StubPluggablePropertyFileSource);
    }

    @Test
    void stubSourceHasExpectedPriority() {
        List<PluggablePropertyFileSource> sources = PluggablePropertyFileSourceLoader.getSources();
        PluggablePropertyFileSource stub = sources.stream()
                .filter(s -> s instanceof StubPluggablePropertyFileSource)
                .findFirst()
                .orElseThrow();
        assertThat(stub.priority()).isEqualTo(StubPluggablePropertyFileSource.STUB_PRIORITY);
    }

    @Test
    void stubSourceLocatesStubFile() throws Exception {
        List<PluggablePropertyFileSource> sources = PluggablePropertyFileSourceLoader.getSources();
        PluggablePropertyFileSource stub = sources.stream()
                .filter(s -> s instanceof StubPluggablePropertyFileSource)
                .findFirst()
                .orElseThrow();

        InputStream is = stub.locate(StubPluggablePropertyFileSource.STUB_FILE_NAME);
        assertThat(is).isNotNull();

        Properties props = PropertyFileLocator.readProperties(is);
        assertThat(props.getProperty("stub.key")).isEqualTo("stub-value");
    }

    @Test
    void getSourcesIsCached() {
        List<PluggablePropertyFileSource> first = PluggablePropertyFileSourceLoader.getSources();
        List<PluggablePropertyFileSource> second = PluggablePropertyFileSourceLoader.getSources();
        assertThat(first).isSameAs(second);
    }

    @Test
    void reloadForcesRediscovery() {
        List<PluggablePropertyFileSource> first = PluggablePropertyFileSourceLoader.getSources();
        PluggablePropertyFileSourceLoader.reload();
        List<PluggablePropertyFileSource> second = PluggablePropertyFileSourceLoader.getSources();
        // After reload a fresh list is built — not the same object
        assertThat(first).isNotSameAs(second);
        // But still contains the same discovered source
        assertThat(second).anyMatch(s -> s instanceof StubPluggablePropertyFileSource);
    }
}
