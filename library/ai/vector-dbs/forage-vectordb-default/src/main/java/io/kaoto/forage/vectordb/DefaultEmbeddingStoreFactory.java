package io.kaoto.forage.vectordb;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.kaoto.forage.core.ai.EmbeddingStoreProvider;
import java.util.ServiceLoader;
import org.apache.camel.CamelContext;
import org.apache.camel.component.langchain4j.embeddingstore.EmbeddingStoreFactory;

public class DefaultEmbeddingStoreFactory implements EmbeddingStoreFactory {
    private CamelContext camelContext;

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public EmbeddingStore<TextSegment> createEmbeddingStore() {
        ServiceLoader<EmbeddingStoreProvider> providers = ServiceLoader.load(EmbeddingStoreProvider.class);
        EmbeddingStoreProvider provider =
                providers.findFirst().orElseThrow(() -> new IllegalStateException("No EmbeddingStoreProvider found"));
        return provider.create();
    }
}
