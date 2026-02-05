package io.kaoto.forage.vectordb.inmemory;

import static io.kaoto.forage.vectordb.inmemory.InMemoryStoreConfigEntries.*;

import dev.langchain4j.store.embedding.EmbeddingStore;
import io.kaoto.forage.core.util.config.Config;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigStore;
import java.util.Optional;

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
 * <p>An {@link dev.langchain4j.model.embedding.EmbeddingModel}</strong> have to be provided for the successful construction of {@link RetrievalAugmentor}
 * as the part of the agent configuration.
 * </p>
 *
 * <p><strong>Configuration Parameters:</strong>
 * <ul>
 *   <li><strong>FILE_SOURCE</strong> - Path to a file to be loaded into store via {@link dev.langchain4j.data.document.Document#from}.</li>
 *   <li><strong>MAX_SIZE</strong> - The maximum size of the segment, defined in characters.</li>
 *   <li><strong>OVERLAP_SIZE</strong> - The maximum size of the overlap, defined in characters. Only full sentences are considered for the overlap..</li>
 * </ul>
 *
 * @see Config
 * @see ConfigStore
 * @see ConfigModule
 * @since 1.0
 */
public class InMemoryStoreConfig implements Config {

    private final String prefix;

    /**
     * Creates a new InMemory configuration using default (non-prefixed) properties.
     */
    public InMemoryStoreConfig() {
        this(null);
    }

    public InMemoryStoreConfig(String prefix) {
        this.prefix = prefix;

        // First register new configuration modules. This happens only if a prefix is provided
        InMemoryStoreConfigEntries.register(prefix);

        // Then, loads the configurations from the properties file associated with this Config module
        ConfigStore.getInstance().load(InMemoryStoreConfig.class, this, this::register);

        // Lastly, load the overrides defined in system properties and environment variables
        InMemoryStoreConfigEntries.loadOverrides(prefix);
    }

    @Override
    public String name() {
        return "forage-vectordb-in-memory";
    }

    /**
     * Returns the file-source parameter.
     *
     * <p>Path to a file to be loaded into store via {@link dev.langchain4j.data.document.Document#from}.</p>
     */
    public String fileSource() {
        return ConfigStore.getInstance().get(FILE_SOURCE.asNamed(prefix)).orElse(null);
    }

    /**
     * Returns the max-size parameter.
     *
     * <p>The maximum size of the segment, defined in characters.</p>
     */
    public Integer maxSize() {
        return ConfigStore.getInstance()
                .get(MAX_SIZE.asNamed(prefix))
                .map(Integer::parseInt)
                .orElse(null);
    }

    /**
     * Returns the overlap-size parameter.
     *
     * <p>The maximum size of the overlap, defined in characters. Only full sentences are considered for the overlap.</p>
     */
    public Integer overlapSize() {
        return ConfigStore.getInstance()
                .get(OVERLAP_SIZE.asNamed(prefix))
                .map(Integer::parseInt)
                .orElse(null);
    }

    @Override
    public void register(String name, String value) {
        Optional<ConfigModule> config = InMemoryStoreConfigEntries.find(prefix, name);

        config.ifPresent(module -> ConfigStore.getInstance().set(module, value));
    }
}
