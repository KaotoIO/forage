package io.kaoto.forage.vectordb.milvus;

import io.kaoto.forage.core.util.config.ConfigEntries;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigTag;

public final class MilvusConfigEntries extends ConfigEntries {
    public static final ConfigModule HOST = ConfigModule.of(
            MilvusConfig.class,
            "forage.milvus.host",
            "Milvus server host address",
            "Host",
            null,
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.COMMON);
    public static final ConfigModule PORT = ConfigModule.of(
            MilvusConfig.class,
            "forage.milvus.port",
            "Milvus server port number",
            "Port",
            null,
            "integer",
            false,
            ConfigTag.COMMON);
    public static final ConfigModule COLLECTION_NAME = ConfigModule.of(
            MilvusConfig.class,
            "forage.milvus.collection.name",
            "Name of the Milvus collection",
            "Collection Name",
            "default",
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.COMMON);
    public static final ConfigModule DIMENSION = ConfigModule.of(
            MilvusConfig.class,
            "forage.milvus.dimension",
            "Vector dimension size",
            "Dimension",
            null,
            "integer",
            true,
            ConfigTag.COMMON);
    public static final ConfigModule INDEX_TYPE = ConfigModule.of(
            MilvusConfig.class,
            "forage.milvus.index.type",
            "Index type for vector search (e.g., IVF_FLAT, HNSW)",
            "Index Type",
            "FLAT",
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule METRIC_TYPE = ConfigModule.of(
            MilvusConfig.class,
            "forage.milvus.metric.type",
            "Distance metric type (e.g., COSINE, L2, IP)",
            "Metric Type",
            "COSINE",
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule URI = ConfigModule.of(
            MilvusConfig.class,
            "forage.milvus.uri",
            "Milvus server URI (alternative to host/port)",
            "URI",
            null,
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.COMMON);
    public static final ConfigModule TOKEN = ConfigModule.of(
            MilvusConfig.class,
            "forage.milvus.token",
            "Authentication token",
            "Token",
            null,
            "password",
            false,
            ConfigTag.SECURITY);
    public static final ConfigModule USERNAME = ConfigModule.of(
            MilvusConfig.class,
            "forage.milvus.username",
            "Username for authentication",
            "Username",
            null,
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.SECURITY);
    public static final ConfigModule PASSWORD = ConfigModule.of(
            MilvusConfig.class,
            "forage.milvus.password",
            "Password for authentication",
            "Password",
            null,
            "password",
            false,
            ConfigTag.SECURITY);
    public static final ConfigModule CONSISTENCY_LEVEL = ConfigModule.of(
            MilvusConfig.class,
            "forage.milvus.consistency.level",
            "Consistency level for queries (e.g., STRONG, EVENTUALLY)",
            "Consistency Level",
            "EVENTUALLY",
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule RETRIEVE_EMBEDDINGS_ON_SEARCH = ConfigModule.of(
            MilvusConfig.class,
            "forage.milvus.retrieve.embeddings.on.search",
            "Whether to retrieve embeddings in search results",
            "Retrieve Embeddings",
            "false",
            "boolean",
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule AUTO_FLUSH_ON_INSERT = ConfigModule.of(
            MilvusConfig.class,
            "forage.milvus.auto.flush.on.insert",
            "Automatically flush data after insert operations",
            "Auto Flush",
            "false",
            "boolean",
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule DATABASE_NAME = ConfigModule.of(
            MilvusConfig.class,
            "forage.milvus.database.name",
            "Name of the Milvus database",
            "Database Name",
            null,
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.COMMON);
    public static final ConfigModule ID_FIELD_NAME = ConfigModule.of(
            MilvusConfig.class,
            "forage.milvus.id.field.name",
            "Name of the ID field",
            "ID Field Name",
            "id",
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule TEXT_FIELD_NAME = ConfigModule.of(
            MilvusConfig.class,
            "forage.milvus.text.field.name",
            "Name of the text field",
            "Text Field Name",
            "text",
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule METADATA_FIELD_NAME = ConfigModule.of(
            MilvusConfig.class,
            "forage.milvus.metadata.field.name",
            "Name of the metadata field",
            "Metadata Field Name",
            "metadata",
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule VECTOR_FIELD_NAME = ConfigModule.of(
            MilvusConfig.class,
            "forage.milvus.vector.field.name",
            "Name of the vector field",
            "Vector Field Name",
            "vector",
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.ADVANCED);

    static {
        initModules(
                MilvusConfigEntries.class,
                HOST,
                PORT,
                COLLECTION_NAME,
                DIMENSION,
                INDEX_TYPE,
                METRIC_TYPE,
                URI,
                TOKEN,
                USERNAME,
                PASSWORD,
                CONSISTENCY_LEVEL,
                RETRIEVE_EMBEDDINGS_ON_SEARCH,
                AUTO_FLUSH_ON_INSERT,
                DATABASE_NAME,
                ID_FIELD_NAME,
                TEXT_FIELD_NAME,
                METADATA_FIELD_NAME,
                VECTOR_FIELD_NAME);
    }
}
