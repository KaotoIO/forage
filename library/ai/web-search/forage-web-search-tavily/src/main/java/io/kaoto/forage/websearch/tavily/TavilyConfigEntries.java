package io.kaoto.forage.websearch.tavily;

import io.kaoto.forage.core.util.config.ConfigEntries;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigTag;

public final class TavilyConfigEntries extends ConfigEntries {
    public static final ConfigModule API_KEY = ConfigModule.of(
            TavilyConfig.class,
            "forage.tavily.api.key",
            "Tavily API key for authentication",
            "API Key",
            null,
            "password",
            true,
            ConfigTag.SECURITY);
    public static final ConfigModule BASE_URL = ConfigModule.of(
            TavilyConfig.class,
            "forage.tavily.base.url",
            "Custom base URL for Tavily API",
            "Base URL",
            "https://api.tavily.com/",
            "string",
            false,
            ConfigTag.COMMON);
    public static final ConfigModule TIMEOUT = ConfigModule.of(
            TavilyConfig.class,
            "forage.tavily.timeout",
            "Request timeout duration (ISO-8601, e.g. PT10S)",
            "Timeout",
            null,
            "string",
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule SEARCH_DEPTH = ConfigModule.of(
            TavilyConfig.class,
            "forage.tavily.search.depth",
            "Search depth (basic or advanced)",
            "Search Depth",
            null,
            "string",
            false,
            ConfigTag.COMMON);
    public static final ConfigModule INCLUDE_ANSWER = ConfigModule.of(
            TavilyConfig.class,
            "forage.tavily.include.answer",
            "Include a generated answer in search results",
            "Include Answer",
            null,
            "boolean",
            false,
            ConfigTag.COMMON);
    public static final ConfigModule INCLUDE_RAW_CONTENT = ConfigModule.of(
            TavilyConfig.class,
            "forage.tavily.include.raw.content",
            "Include raw HTML content in search results",
            "Include Raw Content",
            null,
            "boolean",
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule INCLUDE_DOMAINS = ConfigModule.of(
            TavilyConfig.class,
            "forage.tavily.include.domains",
            "Comma-separated list of domains to include in search",
            "Include Domains",
            null,
            "string",
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule EXCLUDE_DOMAINS = ConfigModule.of(
            TavilyConfig.class,
            "forage.tavily.exclude.domains",
            "Comma-separated list of domains to exclude from search",
            "Exclude Domains",
            null,
            "string",
            false,
            ConfigTag.ADVANCED);

    static {
        initModules(
                TavilyConfigEntries.class,
                API_KEY,
                BASE_URL,
                TIMEOUT,
                SEARCH_DEPTH,
                INCLUDE_ANSWER,
                INCLUDE_RAW_CONTENT,
                INCLUDE_DOMAINS,
                EXCLUDE_DOMAINS);
    }
}
