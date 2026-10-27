package io.kaoto.forage.guardrails.output;

import io.kaoto.forage.core.util.config.ConfigEntries;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigTag;

/**
 * Configuration entries for sensitive data output guardrail.
 */
public final class SensitiveDataGuardrailConfigEntries extends ConfigEntries {

    public static final ConfigModule DETECT_TYPES = ConfigModule.of(
            SensitiveDataGuardrailConfig.class,
            "forage.guardrail.sensitive.data.detect.types",
            "Comma-separated list of sensitive data types to detect: API_KEY, AWS_KEY, SECRET, PRIVATE_KEY, CREDIT_CARD, SSN, JWT, CONNECTION_STRING, GITHUB_TOKEN",
            "Detect Types",
            "API_KEY,AWS_KEY,SECRET,PRIVATE_KEY,CREDIT_CARD,SSN,JWT,CONNECTION_STRING,GITHUB_TOKEN",
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.COMMON);

    public static final ConfigModule ACTION = ConfigModule.of(
            SensitiveDataGuardrailConfig.class,
            "forage.guardrail.sensitive.data.action",
            "Action to take when sensitive data is detected: BLOCK, REDACT, WARN",
            "Action",
            "BLOCK",
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.COMMON);

    public static final ConfigModule REDACTION_TEXT = ConfigModule.of(
            SensitiveDataGuardrailConfig.class,
            "forage.guardrail.sensitive.data.redaction.text",
            "Text to use for redaction when action is REDACT",
            "Redaction Text",
            "[REDACTED]",
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.COMMON);

    static {
        initModules(SensitiveDataGuardrailConfigEntries.class, DETECT_TYPES, ACTION, REDACTION_TEXT);
    }
}
