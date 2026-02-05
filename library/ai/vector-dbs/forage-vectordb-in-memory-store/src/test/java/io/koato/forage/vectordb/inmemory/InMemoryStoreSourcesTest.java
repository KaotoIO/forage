package io.kaoto.forage.vectordb.inmemory;

import static org.assertj.core.api.Assertions.assertThat;

import io.kaoto.forage.core.util.config.ConfigStore;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for faultRetrievalAugmentor focusing on configuration source precedence.
 *
 * <p>This test class verifies the precedence order:
 * <ol>
 *   <li>Environment variables (highest precedence)</li>
 *   <li>System properties</li>
 *   <li>Configuration files</li>
 *   <li>Default values (lowest precedence)</li>
 * </ol>
 */
@DisplayName("DefaultRetrievalAugmentor Source Precedence Tests")
class InMemoryStoreSourcesTest {

    @BeforeEach
    void setUp() {
        ConfigStore.getInstance().setClassLoader(getClass().getClassLoader());
    }

    @Test
    @DisplayName("Should load from configuration file only")
    void shouldLoadFromConfigurationFileOnly() throws IOException {
        InMemoryStoreConfig config = new InMemoryStoreConfig();

        assertThat(config.fileSource()).isEqualTo("file.txt");
        assertThat(config.maxSize()).isEqualTo(10);
        assertThat(config.overlapSize()).isEqualTo(1);
    }
}
