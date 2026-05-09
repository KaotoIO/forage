package io.kaoto.forage.websearch.google;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.core.ai.WebSearchEngineProvider;
import io.kaoto.forage.core.annotations.ForageBean;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.google.customsearch.GoogleCustomWebSearchEngine;

@ForageBean(
        value = "google-custom-search",
        components = {"camel-langchain4j-web-search"},
        feature = "Web Search Engine",
        description = "Google Custom Search engine via Programmable Search Engine")
public class GoogleCustomSearchProvider implements WebSearchEngineProvider {
    private static final Logger LOG = LoggerFactory.getLogger(GoogleCustomSearchProvider.class);

    @Override
    public WebSearchEngine create(String id) {
        GoogleCustomSearchConfig config = new GoogleCustomSearchConfig(id);

        LOG.trace(
                "Creating Google Custom Search engine with configuration: siteRestrict={}, includeImages={}, maxRetries={}, logRequests={}, logResponses={}",
                config.siteRestrict(),
                config.includeImages(),
                config.maxRetries(),
                config.logRequests(),
                config.logResponses());

        GoogleCustomWebSearchEngine.GoogleCustomWebSearchEngineBuilder builder =
                GoogleCustomWebSearchEngine.builder().apiKey(config.apiKey()).csi(config.csi());

        if (config.siteRestrict() != null) {
            builder.siteRestrict(config.siteRestrict());
        }

        if (config.includeImages() != null) {
            builder.includeImages(config.includeImages());
        }

        if (config.timeout() != null) {
            builder.timeout(config.timeout());
        }

        if (config.maxRetries() != null) {
            builder.maxRetries(config.maxRetries());
        }

        if (config.logRequests() != null) {
            builder.logRequests(config.logRequests());
        }

        if (config.logResponses() != null) {
            builder.logResponses(config.logResponses());
        }

        return builder.build();
    }
}
