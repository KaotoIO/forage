package io.kaoto.forage.websearch.tavily;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import io.kaoto.forage.core.util.config.AbstractConfig;

import static io.kaoto.forage.websearch.tavily.TavilyConfigEntries.API_KEY;
import static io.kaoto.forage.websearch.tavily.TavilyConfigEntries.BASE_URL;
import static io.kaoto.forage.websearch.tavily.TavilyConfigEntries.EXCLUDE_DOMAINS;
import static io.kaoto.forage.websearch.tavily.TavilyConfigEntries.INCLUDE_ANSWER;
import static io.kaoto.forage.websearch.tavily.TavilyConfigEntries.INCLUDE_DOMAINS;
import static io.kaoto.forage.websearch.tavily.TavilyConfigEntries.INCLUDE_RAW_CONTENT;
import static io.kaoto.forage.websearch.tavily.TavilyConfigEntries.SEARCH_DEPTH;
import static io.kaoto.forage.websearch.tavily.TavilyConfigEntries.TIMEOUT;

public class TavilyConfig extends AbstractConfig {

    public TavilyConfig() {
        this(null);
    }

    public TavilyConfig(String prefix) {
        super(prefix, TavilyConfigEntries.class);
    }

    @Override
    public String name() {
        return "forage-web-search-tavily";
    }

    public String apiKey() {
        return getRequired(API_KEY, "Missing Tavily API key");
    }

    public String baseUrl() {
        return get(BASE_URL).orElse(BASE_URL.defaultValue());
    }

    public Duration timeout() {
        return get(TIMEOUT).map(Duration::parse).orElse(null);
    }

    public String searchDepth() {
        return get(SEARCH_DEPTH).orElse(null);
    }

    public Boolean includeAnswer() {
        return get(INCLUDE_ANSWER).map(Boolean::parseBoolean).orElse(null);
    }

    public Boolean includeRawContent() {
        return get(INCLUDE_RAW_CONTENT).map(Boolean::parseBoolean).orElse(null);
    }

    public List<String> includeDomains() {
        return get(INCLUDE_DOMAINS)
                .map(s -> Arrays.asList(s.split(",")))
                .map(list -> list.stream().map(String::trim).toList())
                .orElse(null);
    }

    public List<String> excludeDomains() {
        return get(EXCLUDE_DOMAINS)
                .map(s -> Arrays.asList(s.split(",")))
                .map(list -> list.stream().map(String::trim).toList())
                .orElse(null);
    }
}
