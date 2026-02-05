package io.kaoto.forage.vectordb.inmemory;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import io.kaoto.forage.core.ai.EmbeddingModelAware;
import io.kaoto.forage.core.ai.EmbeddingStoreProvider;
import io.kaoto.forage.core.annotations.ForageBean;
import io.kaoto.forage.core.util.config.Config;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigStore;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration class for the {@link dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore}.
 *
 * An {@link EmbeddingStore} that stores embeddings in memory.
 * <p>
 * Uses a brute force approach by iterating over all embeddings to find the best matches.
 * <p>
 * This store can be persisted using the {@link dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore#serializeToJson()} and {@link dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore#serializeToFile(Path)} methods.
 * <p>
 * It can also be recreated from JSON or a file using the {@link dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore#fromJson(String)} and {@link dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore#fromFile(Path)} methods.
 *
 * <p><strong>Configuration Parameters:</strong>
 * <ul>
 *   <li><strong>FILE_SOURCE</strong> - Path to a file to be loaded into store via {@link dev.langchain4j.data.document.Document#from}.</li>
 *   <li><strong>MAX_SIZE</strong> - The maximum size of the segment, defined in characters.</li>
 *   <li><strong>OVERLAP_SIZE</strong> - The maximum size of the overlap, defined in characters. Only full sentences are considered for the overlap..</li>
 * </ul>
 *
 * <p>An {@link dev.langchain4j.model.embedding.EmbeddingModel}</strong> have to be provided for the successful construction of {@link RetrievalAugmentor}
 * as the part of the agent configuration.
 * </p>
 *
 * <p><strong>Usage:</strong>
 * <pre>{@code
 * // Configuration is automatic through environment variables or defaults
 * DefaultRetrievalAugmentorProvider provider = new DefaultRetrievalAugmentorProvider();
 * provider.withEmbeddingModel(embeddingModel);
 * RetrievalAugmentor rag = provider.newModel();
 * }</pre>
 *
 * @see Config
 * @see ConfigStore
 * @see ConfigModule
 * @since 1.0
 */
@ForageBean(
        value = "inMemoryStore",
        components = {"camel-langchain4j-embeddings"},
        description = "MariaDB with vector support")
public class InMemoryStoreProvider implements EmbeddingStoreProvider, EmbeddingModelAware {
    private static final Logger LOG = LoggerFactory.getLogger(InMemoryStoreProvider.class);

    private EmbeddingModel embeddingModel;

    @Override
    public void withEmbeddingModel(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public EmbeddingStore<TextSegment> create(String id) {
        final InMemoryStoreConfig config = new InMemoryStoreConfig(id);

        if (embeddingModel == null) {
            LOG.trace("embeddingModel is mandatory for InMemoryStore creation");
            return null;
        }

        String fileSource = config.fileSource();
        Integer maxSize = config.maxSize();
        Integer overlapSize = config.overlapSize();

        LOG.trace(
                "Creating InMemory embedding store from {} with configuration: maxSize={}, overlapSize={}",
                fileSource,
                maxSize,
                overlapSize);

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(fileSource)) {
            if (stream == null) {
                LOG.trace("InMemory embedding store is not created. The source file is not provided.");
                return null;
            }

            Document document = Document.from(new String(stream.readAllBytes(), StandardCharsets.UTF_8));

            List<TextSegment> segments =
                    DocumentSplitters.recursive(maxSize, overlapSize).split(document);

            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

            // Store in embedding store
            EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
            embeddingStore.addAll(embeddings, segments);

            return embeddingStore;
        } catch (IOException e) {
            throw new RuntimeException("Non accessible source file '%s'".formatted(fileSource), e);
        }
    }
}
