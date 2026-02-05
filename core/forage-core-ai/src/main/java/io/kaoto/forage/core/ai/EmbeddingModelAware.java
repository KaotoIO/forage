package io.kaoto.forage.core.ai;

import dev.langchain4j.model.embedding.EmbeddingModel;

/**
 * If provider requires access to an EmbeddingModel, use this interface.
 * See example in {@link io.kaoto.forage.vectordb.inmemory.InMemoryStoreProvider}.
 */
public interface EmbeddingModelAware {

    void withEmbeddingModel(EmbeddingModel embeddingModel);
}
