package io.kaoto.forage.rag.defaultRag;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.kaoto.forage.core.ai.EmbeddingModelAware;
import io.kaoto.forage.core.ai.EmbeddingStoreAware;
import io.kaoto.forage.core.ai.RetrievalAugmentorProvider;
import io.kaoto.forage.core.annotations.ForageBean;
import io.kaoto.forage.core.util.config.Config;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This provider creates instance of {@link DefaultRetrievalAugmentorProvider}
 *
 * <p>This configuration class manages the settings required to create a default implementation of
 * {@link RetrievalAugmentor}, which is intended to be suitable for the majority of use cases.</p?
 *
 * <p>An <strong>{@link dev.langchain4j.store.embedding.EmbeddingStore}</strong> and
 * {@link EmbeddingModel}</strong> have to be provided for the successful construction of {@link RetrievalAugmentor}
 * as the part of the agent configuration.
 * </p>
 *
 * <p><strong>Configuration Parameters:</strong>
 * <ul>
 *   <li><strong>MAX_RESULTS</strong> - The maximum number of Contents to retrieve.</li>
 *   <li><strong>MIN_SCORE</strong> - The minimum relevance score for the returned Contents. Contents scoring below #minScore are excluded from the results.</li>
 * </ul>
 *
 * <p><strong>Usage:</strong>
 * <pre>{@code
 * // Configuration is automatic through environment variables or defaults
 * DefaultRetrievalAugmentorProvider provider = new DefaultRetrievalAugmentorProvider();
 * provider.withEmbeddingModel(embeddingModel);
 * provider.withEmbeddingStore(embeddingStore);
 * RetrievalAugmentor rag = provider.newModel();
 * }</pre>
 *
 * @see Config
 * @see ConfigStore
 * @see ConfigModule
 * @since 1.0
 */
@ForageBean(
        value = "defaultRag",
        components = {"camel-langchain4j-agent"},
        feature = "RAG",
        description = "Default retrieval augmentor for rag scenario")
public class DefaultRetrievalAugmentorProvider
        implements RetrievalAugmentorProvider, EmbeddingModelAware, EmbeddingStoreAware {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultRetrievalAugmentorProvider.class);

    private EmbeddingStore<TextSegment> embeddingStore;
    private EmbeddingModel embeddingModel;

    @Override
    public void withEmbeddingStore(EmbeddingStore<TextSegment> embeddingStore) {
        this.embeddingStore = embeddingStore;
    }

    @Override
    public void withEmbeddingModel(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * Creates a default retrieval augmentor with the configured parameters.
     *
     * @return a new default retrieval augmentor instance
     */
    @Override
    public RetrievalAugmentor create(String id) {
        final DefaultRetrievalAugmentorConfig config = new DefaultRetrievalAugmentorConfig(id);

        Integer maxResults = config.maxResults();
        Double minScore = config.minScore();

        if (embeddingModel == null) {
            LOG.trace("RAG is not configured, because no embedding model is provided");
            return null;
        }
        if (embeddingStore == null) {
            LOG.trace("RAG is not configured, because no embedding store is provided");
            return null;
        }

        // Create content retriever
        EmbeddingStoreContentRetriever.EmbeddingStoreContentRetrieverBuilder contentRetrieverBuilder =
                EmbeddingStoreContentRetriever.builder()
                        .embeddingStore(embeddingStore)
                        .embeddingModel(embeddingModel);

        if (maxResults != null) {
            contentRetrieverBuilder.maxResults(maxResults);
        }

        if (minScore != null) {
            contentRetrieverBuilder.minScore(minScore);
        }

        LOG.trace(
                "Creating DefaultRetrievalAugmentor model with configuration: maxResults={}, minScore={}",
                maxResults,
                minScore);

        EmbeddingStoreContentRetriever contentRetriever = contentRetrieverBuilder.build();

        // Creates a RetrievalAugmentor that uses only a content retriever : naive rag scenario
        return DefaultRetrievalAugmentor.builder()
                .contentRetriever(contentRetriever)
                .build();
    }
}
