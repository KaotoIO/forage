package io.kaoto.forage.core.ai;

import io.kaoto.forage.core.common.BeanProvider;
import dev.langchain4j.web.search.WebSearchEngine;

/**
 * Provider interface for creating web search engine instances
 */
public interface WebSearchEngineProvider extends BeanProvider<WebSearchEngine> {}
