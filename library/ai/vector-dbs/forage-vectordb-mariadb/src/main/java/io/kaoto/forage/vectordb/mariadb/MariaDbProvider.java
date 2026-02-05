package io.kaoto.forage.vectordb.mariadb;

import dev.langchain4j.store.embedding.mariadb.MariaDbEmbeddingStore;
import io.kaoto.forage.core.ai.EmbeddingStoreProvider;
import io.kaoto.forage.core.annotations.ForageBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ForageBean(
        value = "mariadb",
        components = {"camel-langchain4j-embeddings"},
        description = "MariaDB with vector support")
public class MariaDbProvider implements EmbeddingStoreProvider {
    private static final Logger LOG = LoggerFactory.getLogger(MariaDbProvider.class);

    @Override
    public MariaDbEmbeddingStore create(String id) {
        LOG.trace("Creating MariaDB embedding store");

        final MariaDbConfig config = new MariaDbConfig(id);

        MariaDbEmbeddingStore.Builder builder = MariaDbEmbeddingStore.builder()
                .url(config.url())
                .user(config.user())
                .password(config.password())
                .table(config.table())
                .distanceType(config.distanceType())
                .idFieldName(config.idFieldName())
                .embeddingFieldName(config.embeddingFieldName())
                .contentFieldName(config.contentFieldName())
                .createTable(config.createTable())
                .dropTableFirst(config.dropTableFirst());

        if (config.dimension() != null) {
            builder.dimension(config.dimension());
        }

        return builder.build();
    }
}
