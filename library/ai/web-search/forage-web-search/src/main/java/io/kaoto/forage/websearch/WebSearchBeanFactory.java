package io.kaoto.forage.websearch;

import java.util.List;
import java.util.ServiceLoader;
import org.apache.camel.CamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.core.ai.WebSearchEngineProvider;
import io.kaoto.forage.core.annotations.FactoryType;
import io.kaoto.forage.core.annotations.ForageFactory;
import io.kaoto.forage.core.common.BeanFactory;
import io.kaoto.forage.core.util.config.ConfigStore;
import dev.langchain4j.web.search.WebSearchEngine;

@ForageFactory(
        value = "WebSearch",
        components = {"camel-langchain4j-web-search"},
        description = "Creates web search engine beans for LangChain4j integration",
        type = FactoryType.WEB_SEARCH_ENGINE,
        autowired = true)
public class WebSearchBeanFactory implements BeanFactory {
    private static final Logger LOG = LoggerFactory.getLogger(WebSearchBeanFactory.class);
    static final String DEFAULT_BEAN_NAME = "web-search-engine";

    private CamelContext camelContext;

    @Override
    public void cleanup() {
        camelContext.getRegistry().unbind(DEFAULT_BEAN_NAME);
    }

    @Override
    public void configure() {
        if (camelContext.getRegistry().lookupByNameAndType(DEFAULT_BEAN_NAME, WebSearchEngine.class) != null) {
            LOG.debug("WebSearchEngine bean already registered, skipping");
            return;
        }

        List<ServiceLoader.Provider<WebSearchEngineProvider>> providers = findProviders(WebSearchEngineProvider.class);
        if (providers.isEmpty()) {
            LOG.debug("No WebSearchEngineProvider found, skipping web search engine registration");
            return;
        }

        ServiceLoader.Provider<WebSearchEngineProvider> provider = providers.get(0);
        if (providers.size() > 1) {
            LOG.info(
                    "Multiple WebSearchEngineProvider implementations found, using: {}",
                    provider.type().getName());
        }

        try {
            WebSearchEngine engine = provider.get().create();
            camelContext.getRegistry().bind(DEFAULT_BEAN_NAME, engine);
            LOG.info("Registered WebSearchEngine bean with name: {}", DEFAULT_BEAN_NAME);
        } catch (Exception e) {
            LOG.warn("Failed to create web search engine: {}", e.getMessage());
            LOG.debug("Web search engine creation exception details", e);
        }
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
        ConfigStore.getInstance().setClassLoader(camelContext.getApplicationContextClassLoader());
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }
}
