package io.kaoto.forage.core.ai;

import dev.langchain4j.model.embedding.EmbeddingModel;
import io.kaoto.forage.core.common.BeanProvider;

/**
 * Provider interface for creating AI embedding models
 */
public interface EmbeddingModelProvider extends BeanProvider<EmbeddingModel> {}
