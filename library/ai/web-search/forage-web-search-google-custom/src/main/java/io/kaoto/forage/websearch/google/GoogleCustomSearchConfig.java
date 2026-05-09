package io.kaoto.forage.websearch.google;

import java.time.Duration;
import io.kaoto.forage.core.util.config.AbstractConfig;

import static io.kaoto.forage.websearch.google.GoogleCustomSearchConfigEntries.API_KEY;
import static io.kaoto.forage.websearch.google.GoogleCustomSearchConfigEntries.CSI;
import static io.kaoto.forage.websearch.google.GoogleCustomSearchConfigEntries.INCLUDE_IMAGES;
import static io.kaoto.forage.websearch.google.GoogleCustomSearchConfigEntries.LOG_REQUESTS;
import static io.kaoto.forage.websearch.google.GoogleCustomSearchConfigEntries.LOG_RESPONSES;
import static io.kaoto.forage.websearch.google.GoogleCustomSearchConfigEntries.MAX_RETRIES;
import static io.kaoto.forage.websearch.google.GoogleCustomSearchConfigEntries.SITE_RESTRICT;
import static io.kaoto.forage.websearch.google.GoogleCustomSearchConfigEntries.TIMEOUT;

public class GoogleCustomSearchConfig extends AbstractConfig {

    public GoogleCustomSearchConfig() {
        this(null);
    }

    public GoogleCustomSearchConfig(String prefix) {
        super(prefix, GoogleCustomSearchConfigEntries.class);
    }

    @Override
    public String name() {
        return "forage-web-search-google-custom";
    }

    public String apiKey() {
        return getRequired(API_KEY, "Missing Google Custom Search API key");
    }

    public String csi() {
        return getRequired(CSI, "Missing Google Custom Search Engine ID (CSI)");
    }

    public Boolean siteRestrict() {
        return get(SITE_RESTRICT).map(Boolean::parseBoolean).orElse(null);
    }

    public Boolean includeImages() {
        return get(INCLUDE_IMAGES).map(Boolean::parseBoolean).orElse(null);
    }

    public Duration timeout() {
        return get(TIMEOUT).map(Duration::parse).orElse(null);
    }

    public Integer maxRetries() {
        return get(MAX_RETRIES).map(Integer::parseInt).orElse(null);
    }

    public Boolean logRequests() {
        return get(LOG_REQUESTS).map(Boolean::parseBoolean).orElse(null);
    }

    public Boolean logResponses() {
        return get(LOG_RESPONSES).map(Boolean::parseBoolean).orElse(null);
    }
}
