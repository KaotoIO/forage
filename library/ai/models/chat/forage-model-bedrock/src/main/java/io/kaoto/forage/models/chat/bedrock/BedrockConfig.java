package io.kaoto.forage.models.chat.bedrock;

import io.kaoto.forage.core.util.config.AbstractConfig;

import static io.kaoto.forage.models.chat.bedrock.BedrockConfigEntries.ACCESS_KEY_ID;
import static io.kaoto.forage.models.chat.bedrock.BedrockConfigEntries.MAX_TOKENS;
import static io.kaoto.forage.models.chat.bedrock.BedrockConfigEntries.MODEL_ID;
import static io.kaoto.forage.models.chat.bedrock.BedrockConfigEntries.REGION;
import static io.kaoto.forage.models.chat.bedrock.BedrockConfigEntries.SECRET_ACCESS_KEY;
import static io.kaoto.forage.models.chat.bedrock.BedrockConfigEntries.SESSION_TOKEN;
import static io.kaoto.forage.models.chat.bedrock.BedrockConfigEntries.TEMPERATURE;
import static io.kaoto.forage.models.chat.bedrock.BedrockConfigEntries.TOP_P;

/**
 * Configuration class for Amazon Bedrock model integration in the Forage framework.
 *
 * <p>This configuration class manages the settings required to connect to and use Amazon Bedrock
 * foundation models. It handles AWS authentication, region configuration, model selection, and various
 * parameters through environment variables and system properties.
 *
 * <p><strong>Required Configuration:</strong>
 * <ul>
 *   <li><strong>BEDROCK_MODEL_ID</strong> - The Bedrock model identifier to use</li>
 * </ul>
 *
 * <p><strong>Optional Configuration:</strong>
 * <ul>
 *   <li><strong>BEDROCK_REGION</strong> - AWS region (default: "us-east-1")</li>
 *   <li><strong>BEDROCK_ACCESS_KEY_ID</strong> - AWS access key (uses default credential chain if not provided)</li>
 *   <li><strong>BEDROCK_SECRET_ACCESS_KEY</strong> - AWS secret key (uses default credential chain if not provided)</li>
 *   <li><strong>BEDROCK_TEMPERATURE</strong> - Temperature for response randomness, 0.0-1.0 (no default)</li>
 *   <li><strong>BEDROCK_MAX_TOKENS</strong> - Maximum tokens to generate (no default)</li>
 *   <li><strong>BEDROCK_TOP_P</strong> - Top-P (nucleus) sampling parameter, 0.0-1.0 (no default)</li>
 * </ul>
 *
 * <p><strong>Configuration Sources:</strong>
 * Configuration values are resolved in the following order of precedence:
 * <ol>
 *   <li>Environment variables (BEDROCK_REGION, BEDROCK_MODEL_ID, etc.)</li>
 *   <li>System properties (bedrock.region, bedrock.model.id, etc.)</li>
 *   <li>forage-model-bedrock.properties file in classpath</li>
 *   <li>Default values (only for region)</li>
 * </ol>
 *
 * <p><strong>AWS Authentication:</strong>
 * Bedrock supports multiple authentication methods (in order of precedence):
 * <ol>
 *   <li>Explicit credentials via ACCESS_KEY_ID and SECRET_ACCESS_KEY configuration</li>
 *   <li>AWS environment variables (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)</li>
 *   <li>AWS shared credentials file (~/.aws/credentials)</li>
 *   <li>IAM role (for EC2, ECS, Lambda, etc.)</li>
 * </ol>
 *
 * <p><strong>Security Considerations:</strong>
 * AWS credentials are sensitive information and should be properly secured. Never commit credentials
 * to version control. Use environment variables, AWS credential files, or IAM roles in production.
 *
 * @see AbstractConfig
 * @see io.kaoto.forage.core.util.config.ConfigStore
 * @see io.kaoto.forage.core.util.config.ConfigModule
 * @since 1.0
 */
public class BedrockConfig extends AbstractConfig {

    /**
     * Constructs a new BedrockConfig with no prefix.
     */
    public BedrockConfig() {
        this(null);
    }

    /**
     * Constructs a new BedrockConfig with an optional prefix for named configurations.
     *
     * @param prefix the optional prefix for this configuration instance
     */
    public BedrockConfig(String prefix) {
        super(prefix, BedrockConfigEntries.class);
    }

    /**
     * Returns the unique identifier for this Bedrock configuration module.
     *
     * @return the module name "forage-model-bedrock"
     */
    @Override
    public String name() {
        return "forage-model-bedrock";
    }

    /**
     * Returns the AWS region where Bedrock is available.
     *
     * <p><strong>Configuration Sources (in order of precedence):</strong>
     * <ol>
     *   <li>BEDROCK_REGION environment variable</li>
     *   <li>bedrock.region system property</li>
     *   <li>region property in forage-model-bedrock.properties</li>
     *   <li>Default value: "us-east-1"</li>
     * </ol>
     *
     * @return the AWS region, never null
     */
    public String region() {
        return get(REGION).orElse(REGION.defaultValue());
    }

    /**
     * Returns the Bedrock model identifier.
     *
     * <p><strong>Common Model IDs:</strong>
     * <ul>
     *   <li>anthropic.claude-3-5-sonnet-20240620-v1:0 - Claude 3.5 Sonnet</li>
     *   <li>anthropic.claude-3-opus-20240229-v1:0 - Claude 3 Opus</li>
     *   <li>meta.llama3-1-70b-instruct-v1:0 - Llama 3.1 70B</li>
     *   <li>amazon.titan-text-express-v1 - Titan Text Express</li>
     * </ul>
     *
     * <p><strong>Configuration Sources (in order of precedence):</strong>
     * <ol>
     *   <li>BEDROCK_MODEL_ID environment variable</li>
     *   <li>bedrock.model.id system property</li>
     *   <li>model-id property in forage-model-bedrock.properties</li>
     * </ol>
     *
     * @return the Bedrock model identifier
     * @throws io.kaoto.forage.core.util.config.MissingConfigException if no model ID is configured
     */
    public String modelId() {
        return getRequired(MODEL_ID, "Missing Bedrock model ID");
    }

    /**
     * Returns the AWS access key ID for authentication.
     *
     * <p>If not provided, Bedrock will use the default AWS credential chain.
     *
     * @return the AWS access key ID, or null if not configured
     */
    public String accessKeyId() {
        return get(ACCESS_KEY_ID).orElse(null);
    }

    /**
     * Returns the AWS secret access key for authentication.
     *
     * <p>If not provided, Bedrock will use the default AWS credential chain.
     *
     * @return the AWS secret access key, or null if not configured
     */
    public String secretAccessKey() {
        return get(SECRET_ACCESS_KEY).orElse(null);
    }

    /**
     * Returns the AWS session token for temporary STS credentials.
     *
     * <p>Used together with {@link #accessKeyId()} and {@link #secretAccessKey()} when
     * authenticating with temporary credentials from an assumed IAM role.
     *
     * @return the AWS session token, or null if not configured
     */
    public String sessionToken() {
        return get(SESSION_TOKEN).orElse(null);
    }

    /**
     * Returns the temperature setting for response randomness.
     *
     * <p>Temperature controls the randomness of the model's responses. Lower values make
     * the output more focused and deterministic, while higher values increase creativity.
     *
     * <p><strong>Valid Range:</strong> 0.0 to 1.0
     *
     * @return the temperature value, or null if not configured
     */
    public Double temperature() {
        return get(TEMPERATURE).map(Double::parseDouble).orElse(null);
    }

    /**
     * Returns the maximum number of tokens to generate.
     *
     * <p>This parameter controls the maximum length of the generated response.
     *
     * @return the maximum tokens value, or null if not configured
     */
    public Integer maxTokens() {
        return get(MAX_TOKENS).map(Integer::parseInt).orElse(null);
    }

    /**
     * Returns the top-P (nucleus) sampling parameter.
     *
     * <p>Top-P sampling selects from the smallest set of tokens whose cumulative
     * probability exceeds P.
     *
     * <p><strong>Valid Range:</strong> 0.0 to 1.0
     *
     * @return the top-P value, or null if not configured
     */
    public Double topP() {
        return get(TOP_P).map(Double::parseDouble).orElse(null);
    }
}
