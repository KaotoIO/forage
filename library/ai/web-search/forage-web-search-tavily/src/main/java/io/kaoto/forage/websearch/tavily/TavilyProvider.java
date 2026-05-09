package io.kaoto.forage.websearch.tavily;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.core.ai.WebSearchEngineProvider;
import io.kaoto.forage.core.annotations.ForageBean;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;

@ForageBean(
        value = "tavily",
        components = {"camel-langchain4j-web-search"},
        feature = "Web Search Engine",
        description = "Tavily AI-powered web search engine")
public class TavilyProvider implements WebSearchEngineProvider {
    private static final Logger LOG = LoggerFactory.getLogger(TavilyProvider.class);

    @Override
    public WebSearchEngine create(String id) {
        TavilyConfig config = new TavilyConfig(id);

        LOG.trace(
                "Creating Tavily web search engine with configuration: baseUrl={}, searchDepth={}, includeAnswer={}, includeRawContent={}",
                config.baseUrl(),
                config.searchDepth(),
                config.includeAnswer(),
                config.includeRawContent());

        TavilyWebSearchEngine.TavilyWebSearchEngineBuilder builder =
                TavilyWebSearchEngine.builder().apiKey(config.apiKey());

        if (config.baseUrl() != null) {
            builder.baseUrl(config.baseUrl());
        }

        if (config.timeout() != null) {
            builder.timeout(config.timeout());
        }

        if (config.searchDepth() != null) {
            builder.searchDepth(config.searchDepth());
        }

        if (config.includeAnswer() != null) {
            builder.includeAnswer(config.includeAnswer());
        }

        if (config.includeRawContent() != null) {
            builder.includeRawContent(config.includeRawContent());
        }

        if (config.includeDomains() != null) {
            builder.includeDomains(config.includeDomains());
        }

        if (config.excludeDomains() != null) {
            builder.excludeDomains(config.excludeDomains());
        }

        return builder.build();
    }
}
