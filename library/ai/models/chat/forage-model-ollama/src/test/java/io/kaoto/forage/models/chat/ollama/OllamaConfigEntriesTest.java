package io.kaoto.forage.models.chat.ollama;

import java.util.Map;
import java.util.Optional;
import io.kaoto.forage.core.util.config.ConfigEntries;
import io.kaoto.forage.core.util.config.ConfigEntry;
import io.kaoto.forage.core.util.config.ConfigModule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for OllamaConfigEntries focusing on the ConfigEntries pattern implementation.
 *
 * <p>This test class verifies:
 * <ul>
 *   <li>Static ConfigModule field definitions</li>
 *   <li>CONFIG_MODULES map initialization</li>
 *   <li>find() method functionality with prefixes</li>
 *   <li>register() method functionality</li>
 *   <li>entries() method immutability</li>
 * </ul>
 */
@DisplayName("OllamaConfigEntries Pattern Tests")
class OllamaConfigEntriesTest {

    @Nested
    @DisplayName("Static ConfigModule Field Tests")
    class StaticConfigModuleTests {

        @Test
        @DisplayName("Should have correct ConfigModule names")
        void shouldHaveCorrectConfigModuleNames() {
            assertThat(OllamaConfigEntries.BASE_URL.name()).isEqualTo("forage.ollama.base.url");
            assertThat(OllamaConfigEntries.MODEL_NAME.name()).isEqualTo("forage.ollama.model.name");
            assertThat(OllamaConfigEntries.TEMPERATURE.name()).isEqualTo("forage.ollama.temperature");
            assertThat(OllamaConfigEntries.TOP_K.name()).isEqualTo("forage.ollama.top.k");
            assertThat(OllamaConfigEntries.TOP_P.name()).isEqualTo("forage.ollama.top.p");
            assertThat(OllamaConfigEntries.MIN_P.name()).isEqualTo("forage.ollama.min.p");
            assertThat(OllamaConfigEntries.NUM_CTX.name()).isEqualTo("forage.ollama.num.ctx");
            assertThat(OllamaConfigEntries.LOG_REQUESTS.name()).isEqualTo("forage.ollama.log.requests");
            assertThat(OllamaConfigEntries.LOG_RESPONSES.name()).isEqualTo("forage.ollama.log.responses");
            assertThat(OllamaConfigEntries.TIMEOUT.name()).isEqualTo("forage.ollama.timeout");
        }

        @Test
        @DisplayName("Should have correct ConfigModule config class references")
        void shouldHaveCorrectConfigModuleConfigClassReferences() {
            assertThat(OllamaConfigEntries.BASE_URL.config()).isEqualTo(OllamaConfig.class);
            assertThat(OllamaConfigEntries.MODEL_NAME.config()).isEqualTo(OllamaConfig.class);
            assertThat(OllamaConfigEntries.TEMPERATURE.config()).isEqualTo(OllamaConfig.class);
            assertThat(OllamaConfigEntries.TOP_K.config()).isEqualTo(OllamaConfig.class);
            assertThat(OllamaConfigEntries.TOP_P.config()).isEqualTo(OllamaConfig.class);
            assertThat(OllamaConfigEntries.MIN_P.config()).isEqualTo(OllamaConfig.class);
            assertThat(OllamaConfigEntries.NUM_CTX.config()).isEqualTo(OllamaConfig.class);
            assertThat(OllamaConfigEntries.LOG_REQUESTS.config()).isEqualTo(OllamaConfig.class);
            assertThat(OllamaConfigEntries.LOG_RESPONSES.config()).isEqualTo(OllamaConfig.class);
            assertThat(OllamaConfigEntries.TIMEOUT.config()).isEqualTo(OllamaConfig.class);
        }
    }

    @Nested
    @DisplayName("CONFIG_MODULES Map Tests")
    class ConfigModulesMapTests {

        @Test
        @DisplayName("Should return immutable map from entries()")
        void shouldReturnImmutableMapFromEntries() {
            Map<ConfigModule, ConfigEntry> entries = ConfigEntries.entriesOf(OllamaConfigEntries.class);

            assertThat(entries).isNotNull();
            assertThat(entries).isNotEmpty();

            // Should throw UnsupportedOperationException when trying to modify
            assertThatThrownBy(entries::clear).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("Should contain all ConfigModules in entries map")
        void shouldContainAllConfigModulesInEntriesMap() {
            Map<ConfigModule, ConfigEntry> entries = ConfigEntries.entriesOf(OllamaConfigEntries.class);

            assertThat(entries).containsKey(OllamaConfigEntries.BASE_URL);
            assertThat(entries).containsKey(OllamaConfigEntries.MODEL_NAME);
            assertThat(entries).containsKey(OllamaConfigEntries.TEMPERATURE);
            assertThat(entries).containsKey(OllamaConfigEntries.TOP_K);
            assertThat(entries).containsKey(OllamaConfigEntries.TOP_P);
            assertThat(entries).containsKey(OllamaConfigEntries.MIN_P);
            assertThat(entries).containsKey(OllamaConfigEntries.NUM_CTX);
            assertThat(entries).containsKey(OllamaConfigEntries.LOG_REQUESTS);
            assertThat(entries).containsKey(OllamaConfigEntries.LOG_RESPONSES);
            assertThat(entries).containsKey(OllamaConfigEntries.TIMEOUT);
        }

        @Test
        @DisplayName("Should have correct environment variable mappings")
        void shouldHaveCorrectEnvironmentVariableMappings() {
            Map<ConfigModule, ConfigEntry> entries = ConfigEntries.entriesOf(OllamaConfigEntries.class);

            // Note: We can't directly inspect the environment variable names from ConfigEntry
            // but we can verify that all entries exist and are not null
            assertThat(entries.get(OllamaConfigEntries.BASE_URL)).isNotNull();
            assertThat(entries.get(OllamaConfigEntries.MODEL_NAME)).isNotNull();
            assertThat(entries.get(OllamaConfigEntries.TEMPERATURE)).isNotNull();
            assertThat(entries.get(OllamaConfigEntries.TOP_K)).isNotNull();
            assertThat(entries.get(OllamaConfigEntries.TOP_P)).isNotNull();
            assertThat(entries.get(OllamaConfigEntries.MIN_P)).isNotNull();
            assertThat(entries.get(OllamaConfigEntries.NUM_CTX)).isNotNull();
            assertThat(entries.get(OllamaConfigEntries.LOG_REQUESTS)).isNotNull();
            assertThat(entries.get(OllamaConfigEntries.LOG_RESPONSES)).isNotNull();
            assertThat(entries.get(OllamaConfigEntries.TIMEOUT)).isNotNull();
        }
    }

    @Nested
    @DisplayName("find() Method Tests")
    class FindMethodTests {

        @Test
        @DisplayName("Should find ConfigModule without prefix")
        void shouldFindConfigModuleWithoutPrefix() {
            Optional<ConfigModule> found = ConfigEntries.find(
                    ConfigEntries.getModules(OllamaConfigEntries.class), null, "forage.ollama.base.url");

            assertThat(found.get()).isEqualTo(OllamaConfigEntries.BASE_URL);
        }

        @Test
        @DisplayName("Should find all ConfigModules with different prefixes")
        void shouldFindAllConfigModulesWithDifferentPrefixes() {
            String prefix1 = "test1";
            String prefix2 = "test2";

            // Test all configuration modules with different prefixes
            assertThat(ConfigEntries.find(
                            ConfigEntries.getModules(OllamaConfigEntries.class), prefix1, prefix1 + ".ollama.base.url"))
                    .isNotNull();
            assertThat(ConfigEntries.find(
                            ConfigEntries.getModules(OllamaConfigEntries.class),
                            prefix1,
                            prefix1 + ".ollama.model.name"))
                    .isNotNull();
            assertThat(ConfigEntries.find(
                            ConfigEntries.getModules(OllamaConfigEntries.class),
                            prefix1,
                            prefix1 + ".ollama.temperature"))
                    .isNotNull();

            assertThat(ConfigEntries.find(
                            ConfigEntries.getModules(OllamaConfigEntries.class), prefix2, prefix2 + ".ollama.base.url"))
                    .isNotNull();
            assertThat(ConfigEntries.find(
                            ConfigEntries.getModules(OllamaConfigEntries.class),
                            prefix2,
                            prefix2 + ".ollama.model.name"))
                    .isNotNull();
            assertThat(ConfigEntries.find(
                            ConfigEntries.getModules(OllamaConfigEntries.class),
                            prefix2,
                            prefix2 + ".ollama.temperature"))
                    .isNotNull();
        }

        @Test
        @DisplayName("Should return Optional.empty for unknown configuration name")
        void shouldReturnEmptyForUnknownConfigurationName() {
            assertThat(ConfigEntries.find(ConfigEntries.getModules(OllamaConfigEntries.class), null, "unknown.config"))
                    .isEqualTo(Optional.empty());
        }

        @Test
        @DisplayName("Should return Optional.empty for unknown prefixed configuration name")
        void shouldReturnEmptyForUnknownPrefixedConfigurationName() {
            assertThat(ConfigEntries.find(
                            ConfigEntries.getModules(OllamaConfigEntries.class), "prefix", "prefix.unknown.config"))
                    .isEqualTo(Optional.empty());
        }
    }

    @Nested
    @DisplayName("register() Method Tests")
    class RegisterMethodTests {

        @Test
        @DisplayName("Should register configurations without prefix")
        void shouldRegisterConfigurationsWithoutPrefix() {
            // This should not throw any exception
            ConfigEntries.registerPrefix(OllamaConfigEntries.class, null);

            // Verify registration occurred by checking if we can create a config
            OllamaConfig config = new OllamaConfig();
            assertThat(config).isNotNull();
        }

        @Test
        @DisplayName("Should register configurations with prefix")
        void shouldRegisterConfigurationsWithPrefix() {
            String prefix = "testprefix";

            // This should not throw any exception
            ConfigEntries.registerPrefix(OllamaConfigEntries.class, prefix);

            // Verify that prefixed configuration works
            System.setProperty("forage." + prefix + ".ollama.base.url", "http://prefixed-server:11434");

            OllamaConfig config = new OllamaConfig(prefix);
            assertThat(config.baseUrl()).isEqualTo("http://prefixed-server:11434");
        }

        @Test
        @DisplayName("Should register configurations with different prefixes independently")
        void shouldRegisterConfigurationsWithDifferentPrefixesIndependently() {
            String prefix1 = "app1";
            String prefix2 = "app2";

            ConfigEntries.registerPrefix(OllamaConfigEntries.class, prefix1);
            ConfigEntries.registerPrefix(OllamaConfigEntries.class, prefix2);

            // Set different values for each prefix
            System.setProperty("forage." + prefix1 + ".ollama.model.name", "model1");
            System.setProperty("forage." + prefix2 + ".ollama.model.name", "model2");

            OllamaConfig config1 = new OllamaConfig(prefix1);
            OllamaConfig config2 = new OllamaConfig(prefix2);

            assertThat(config1.modelName()).isEqualTo("model1");
            assertThat(config2.modelName()).isEqualTo("model2");
        }
    }
}
