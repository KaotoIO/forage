package io.kaoto.forage.rag.defaultRag;

import static io.kaoto.forage.rag.defaultRag.DefaultRetrievalAugmentorConfigEntries.*;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.RetrievalAugmentor;
import io.kaoto.forage.core.util.config.Config;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigStore;
import java.util.Optional;

/**
 * Configuration class for the {@link DefaultRetrievalAugmentorProvider} (default implementation of {@link RetrievalAugmentor})
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
 * @see Config
 * @see ConfigStore
 * @see ConfigModule
 * @since 1.0
 */
public class DefaultRetrievalAugmentorConfig implements Config {

    private final String prefix;

    /**
     * Constructs a new DefaultRetrievalAugmentorConfig and registers configuration parameters with the ConfigStore.
     */
    public DefaultRetrievalAugmentorConfig() {
        this(null);
    }

    public DefaultRetrievalAugmentorConfig(String prefix) {
        this.prefix = prefix;

        // First register new configuration modules. This happens only if a prefix is provided
        DefaultRetrievalAugmentorConfigEntries.register(prefix);

        // Then, loads the configurations from the properties file associated with this Config module
        ConfigStore.getInstance().load(DefaultRetrievalAugmentorConfig.class, this, this::register);

        // Lastly, load the overrides defined in system properties and environment variables
        DefaultRetrievalAugmentorConfigEntries.loadOverrides(prefix);
    }

    @Override
    public void register(String name, String value) {
        Optional<ConfigModule> config = DefaultRetrievalAugmentorConfigEntries.find(prefix, name);

        config.ifPresent(module -> ConfigStore.getInstance().set(module, value));
    }

    /**
     *  Returns the unique identifier for this Ollama configuration module.
     */
    @Override
    public String name() {
        return "forage-rag-default";
    }

    /**
     * Returns the max-result parameter.
     *
     * <p>The maximum number of Contents to retrieve.</p>
     */
    public Integer maxResults() {
        return ConfigStore.getInstance()
                .get(MAX_RESULTS.asNamed(prefix))
                .map(Integer::parseInt)
                .orElse(null);
    }

    /**
     * Returns the min-score parameter.
     *
     * <p>The minimum relevance score for the returned Contents. Contents scoring below #minScore are excluded
     * from the results.</p>
     */
    public Double minScore() {
        return ConfigStore.getInstance()
                .get(MIN_SCORE.asNamed(prefix))
                .map(Double::parseDouble)
                .orElse(null);
    }
}
