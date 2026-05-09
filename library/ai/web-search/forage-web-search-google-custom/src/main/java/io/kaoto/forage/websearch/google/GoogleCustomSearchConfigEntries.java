package io.kaoto.forage.websearch.google;

import io.kaoto.forage.core.util.config.ConfigEntries;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigTag;

public final class GoogleCustomSearchConfigEntries extends ConfigEntries {
    public static final ConfigModule API_KEY = ConfigModule.of(
            GoogleCustomSearchConfig.class,
            "forage.google.custom.search.api.key",
            "Google API key for Custom Search API",
            "API Key",
            null,
            "password",
            true,
            ConfigTag.SECURITY);
    public static final ConfigModule CSI = ConfigModule.of(
            GoogleCustomSearchConfig.class,
            "forage.google.custom.search.csi",
            "Custom Search Engine ID (Programmable Search Engine)",
            "Custom Search ID",
            null,
            "string",
            true,
            ConfigTag.COMMON);
    public static final ConfigModule SITE_RESTRICT = ConfigModule.of(
            GoogleCustomSearchConfig.class,
            "forage.google.custom.search.site.restrict",
            "Restrict search to sites specified in the Custom Search Engine",
            "Site Restrict",
            null,
            "boolean",
            false,
            ConfigTag.COMMON);
    public static final ConfigModule INCLUDE_IMAGES = ConfigModule.of(
            GoogleCustomSearchConfig.class,
            "forage.google.custom.search.include.images",
            "Include public images in search results",
            "Include Images",
            null,
            "boolean",
            false,
            ConfigTag.COMMON);
    public static final ConfigModule TIMEOUT = ConfigModule.of(
            GoogleCustomSearchConfig.class,
            "forage.google.custom.search.timeout",
            "Request timeout duration (ISO-8601, e.g. PT60S)",
            "Timeout",
            null,
            "string",
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule MAX_RETRIES = ConfigModule.of(
            GoogleCustomSearchConfig.class,
            "forage.google.custom.search.max.retries",
            "Maximum number of retry attempts for failed requests",
            "Max Retries",
            null,
            "integer",
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule LOG_REQUESTS = ConfigModule.of(
            GoogleCustomSearchConfig.class,
            "forage.google.custom.search.log.requests",
            "Enable request logging",
            "Log Requests",
            null,
            "boolean",
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule LOG_RESPONSES = ConfigModule.of(
            GoogleCustomSearchConfig.class,
            "forage.google.custom.search.log.responses",
            "Enable response logging",
            "Log Responses",
            null,
            "boolean",
            false,
            ConfigTag.ADVANCED);

    static {
        initModules(
                GoogleCustomSearchConfigEntries.class,
                API_KEY,
                CSI,
                SITE_RESTRICT,
                INCLUDE_IMAGES,
                TIMEOUT,
                MAX_RETRIES,
                LOG_REQUESTS,
                LOG_RESPONSES);
    }
}
