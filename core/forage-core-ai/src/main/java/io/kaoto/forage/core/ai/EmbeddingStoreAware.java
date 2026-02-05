package io.kaoto.forage.core.ai;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;

/**
 * If provider requires access to an EmbeddingStore, use this interface.
 * See example in {@link io.kaoto.forage.vectordb.inmemory.InMemoryStoreProvider}.
 */
public interface EmbeddingStoreAware {

    void withEmbeddingStore(EmbeddingStore<TextSegment> embeddingStore);
}
