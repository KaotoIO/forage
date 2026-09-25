package io.kaoto.forage.models.chat.bedrock;

import dev.langchain4j.model.chat.ChatModel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for BedrockProvider.
 */
@DisplayName("BedrockProvider Integration Tests")
class BedrockProviderTest {

    @AfterEach
    void cleanup() {
        System.clearProperty("forage.bedrock.model.id");
        System.clearProperty("forage.bedrock.region");
        System.clearProperty("forage.bedrock.access.key.id");
        System.clearProperty("forage.bedrock.secret.access.key");
        System.clearProperty("forage.bedrock.session.token");
        System.clearProperty("forage.bedrock.temperature");
        System.clearProperty("forage.bedrock.max.tokens");
        System.clearProperty("forage.bedrock.top.p");
        System.clearProperty("forage.agent1.bedrock.model.id");
        System.clearProperty("forage.agent2.bedrock.model.id");
        System.clearProperty("forage.test1.bedrock.model.id");
        System.clearProperty("forage.test2.bedrock.model.id");
        System.clearProperty("forage.test3.bedrock.model.id");
        System.clearProperty("forage.test4.bedrock.model.id");
        System.clearProperty("forage.test5.bedrock.model.id");
    }

    @Nested
    @DisplayName("Model Creation Tests")
    class ModelCreationTests {

        @Test
        @DisplayName("Should create Anthropic Claude model")
        void shouldCreateAnthropicClaudeModel() {
            System.setProperty("forage.bedrock.model.id", "anthropic.claude-3-haiku-20240307-v1:0");
            System.setProperty("forage.bedrock.region", "us-east-1");

            BedrockProvider provider = new BedrockProvider();
            ChatModel model = provider.create();

            assertThat(model).isNotNull();
        }

        @Test
        @DisplayName("Should create Meta Llama model")
        void shouldCreateMetaLlamaModel() {
            System.setProperty("forage.bedrock.model.id", "meta.llama3-1-8b-instruct-v1:0");
            System.setProperty("forage.bedrock.region", "us-east-1");

            BedrockProvider provider = new BedrockProvider();
            ChatModel model = provider.create();

            assertThat(model).isNotNull();
        }

        @Test
        @DisplayName("Should create Amazon Titan model")
        void shouldCreateAmazonTitanModel() {
            System.setProperty("forage.bedrock.model.id", "amazon.titan-text-express-v1");
            System.setProperty("forage.bedrock.region", "us-east-1");

            BedrockProvider provider = new BedrockProvider();
            ChatModel model = provider.create();

            assertThat(model).isNotNull();
        }

        @Test
        @DisplayName("Should create Cohere Command model")
        void shouldCreateCohereCommandModel() {
            System.setProperty("forage.bedrock.model.id", "cohere.command-text-v14");
            System.setProperty("forage.bedrock.region", "us-east-1");

            BedrockProvider provider = new BedrockProvider();
            ChatModel model = provider.create();

            assertThat(model).isNotNull();
        }

        @Test
        @DisplayName("Should create Mistral AI model")
        void shouldCreateMistralAiModel() {
            System.setProperty("forage.bedrock.model.id", "mistral.mistral-7b-instruct-v0:2");
            System.setProperty("forage.bedrock.region", "us-east-1");

            BedrockProvider provider = new BedrockProvider();
            ChatModel model = provider.create();

            assertThat(model).isNotNull();
        }
    }

    @Nested
    @DisplayName("Named Configuration Tests")
    class NamedConfigurationTests {

        @Test
        @DisplayName("Should create models with different named configurations")
        void shouldCreateModelsWithDifferentNamedConfigurations() {
            System.setProperty("forage.bedrock.model.id", "anthropic.claude-3-haiku-20240307-v1:0");
            System.setProperty("forage.agent1.bedrock.model.id", "anthropic.claude-3-5-sonnet-20240620-v1:0");
            System.setProperty("forage.agent2.bedrock.model.id", "meta.llama3-1-8b-instruct-v1:0");

            BedrockProvider provider = new BedrockProvider();

            ChatModel defaultModel = provider.create();
            ChatModel agent1Model = provider.create("agent1");
            ChatModel agent2Model = provider.create("agent2");

            assertThat(defaultModel).isNotNull();
            assertThat(agent1Model).isNotNull();
            assertThat(agent2Model).isNotNull();

            assertThat(defaultModel).isNotSameAs(agent1Model);
            assertThat(defaultModel).isNotSameAs(agent2Model);
            assertThat(agent1Model).isNotSameAs(agent2Model);
        }
    }

    @Nested
    @DisplayName("Configuration Parameter Tests")
    class ConfigurationParameterTests {

        @Test
        @DisplayName("Should apply temperature configuration")
        void shouldApplyTemperatureConfiguration() {
            System.setProperty("forage.bedrock.model.id", "anthropic.claude-3-haiku-20240307-v1:0");
            System.setProperty("forage.bedrock.temperature", "0.7");

            BedrockProvider provider = new BedrockProvider();
            ChatModel model = provider.create();

            assertThat(model).isNotNull();
        }

        @Test
        @DisplayName("Should apply max tokens configuration")
        void shouldApplyMaxTokensConfiguration() {
            System.setProperty("forage.bedrock.model.id", "anthropic.claude-3-haiku-20240307-v1:0");
            System.setProperty("forage.bedrock.max.tokens", "2048");

            BedrockProvider provider = new BedrockProvider();
            ChatModel model = provider.create();

            assertThat(model).isNotNull();
        }

        @Test
        @DisplayName("Should apply top-P configuration")
        void shouldApplyTopPConfiguration() {
            System.setProperty("forage.bedrock.model.id", "anthropic.claude-3-haiku-20240307-v1:0");
            System.setProperty("forage.bedrock.top.p", "0.9");

            BedrockProvider provider = new BedrockProvider();
            ChatModel model = provider.create();

            assertThat(model).isNotNull();
        }

        @Test
        @DisplayName("Should apply STS session token credentials")
        void shouldApplySessionTokenCredentials() {
            System.setProperty("forage.bedrock.model.id", "anthropic.claude-3-haiku-20240307-v1:0");
            System.setProperty("forage.bedrock.access.key.id", "test-access-key");
            System.setProperty("forage.bedrock.secret.access.key", "test-secret-key");
            System.setProperty("forage.bedrock.session.token", "test-session-token");

            BedrockProvider provider = new BedrockProvider();
            ChatModel model = provider.create();

            assertThat(model).isNotNull();
        }

        @Test
        @DisplayName("Should apply all optional parameters")
        void shouldApplyAllOptionalParameters() {
            System.setProperty("forage.bedrock.model.id", "anthropic.claude-3-haiku-20240307-v1:0");
            System.setProperty("forage.bedrock.temperature", "0.7");
            System.setProperty("forage.bedrock.max.tokens", "2048");
            System.setProperty("forage.bedrock.top.p", "0.9");
            System.setProperty("forage.bedrock.region", "us-west-2");

            BedrockProvider provider = new BedrockProvider();
            ChatModel model = provider.create();

            assertThat(model).isNotNull();
        }
    }

    @Nested
    @DisplayName("Model ID Tests")
    class ModelIdTests {

        @Test
        @DisplayName("Should support different model families")
        void shouldSupportDifferentModelFamilies() {
            BedrockProvider provider = new BedrockProvider();

            System.setProperty("forage.test1.bedrock.model.id", "anthropic.claude-3-haiku-20240307-v1:0");
            assertThat(provider.create("test1")).isNotNull();

            System.setProperty("forage.test2.bedrock.model.id", "meta.llama3-1-8b-instruct-v1:0");
            assertThat(provider.create("test2")).isNotNull();

            System.setProperty("forage.test3.bedrock.model.id", "amazon.titan-text-lite-v1");
            assertThat(provider.create("test3")).isNotNull();

            System.setProperty("forage.test4.bedrock.model.id", "cohere.command-light-text-v14");
            assertThat(provider.create("test4")).isNotNull();

            System.setProperty("forage.test5.bedrock.model.id", "mistral.mistral-7b-instruct-v0:2");
            assertThat(provider.create("test5")).isNotNull();
        }
    }
}
