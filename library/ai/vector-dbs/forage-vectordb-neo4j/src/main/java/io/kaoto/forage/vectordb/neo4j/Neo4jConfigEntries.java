package io.kaoto.forage.vectordb.neo4j;

import io.kaoto.forage.core.util.config.ConfigEntries;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigTag;

public final class Neo4jConfigEntries extends ConfigEntries {
    public static final ConfigModule INDEX_NAME = ConfigModule.of(
            Neo4jConfig.class,
            "forage.neo4j.index.name",
            "Vector index name",
            "Index Name",
            "vector-index",
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.COMMON);
    public static final ConfigModule METADATA_PREFIX = ConfigModule.of(
            Neo4jConfig.class,
            "forage.neo4j.metadata.prefix",
            "Metadata prefix",
            "Metadata Prefix",
            "metadata_",
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule EMBEDDING_PROPERTY = ConfigModule.of(
            Neo4jConfig.class,
            "forage.neo4j.embedding.property",
            "Embedding property name",
            "Embedding Property",
            "embedding",
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule ID_PROPERTY = ConfigModule.of(
            Neo4jConfig.class,
            "forage.neo4j.id.property",
            "ID property name",
            "ID Property",
            "id",
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule LABEL = ConfigModule.of(
            Neo4jConfig.class,
            "neo4j.label",
            "Node label",
            "Label",
            "Document",
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.COMMON);
    public static final ConfigModule TEXT_PROPERTY = ConfigModule.of(
            Neo4jConfig.class,
            "forage.neo4j.text.property",
            "Text property name",
            "Text Property",
            "text",
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule DATABASE_NAME = ConfigModule.of(
            Neo4jConfig.class,
            "forage.neo4j.database.name",
            "Database name",
            "Database Name",
            "neo4j",
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.COMMON);
    public static final ConfigModule RETRIEVAL_QUERY = ConfigModule.of(
            Neo4jConfig.class,
            "forage.neo4j.retrieval.query",
            "Custom retrieval query",
            "Retrieval Query",
            null,
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule DIMENSION = ConfigModule.of(
            Neo4jConfig.class,
            "forage.neo4j.dimension",
            "Vector dimension",
            "Dimension",
            null,
            ConfigModule.TYPE_INTEGER,
            true,
            ConfigTag.COMMON);
    public static final ConfigModule AWAIT_INDEX_TIMEOUT = ConfigModule.of(
            Neo4jConfig.class,
            "forage.neo4j.await.index.timeout",
            "Index creation timeout in seconds",
            "Await Index Timeout",
            "60",
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule FULL_TEXT_INDEX_NAME = ConfigModule.of(
            Neo4jConfig.class,
            "forage.neo4j.full.text.index.name",
            "Full text index name",
            "Full Text Index Name",
            null,
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule FULL_TEXT_QUERY = ConfigModule.of(
            Neo4jConfig.class,
            "forage.neo4j.full.text.query",
            "Full text query",
            "Full Text Query",
            null,
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule FULL_TEXT_RETRIEVAL_QUERY = ConfigModule.of(
            Neo4jConfig.class,
            "forage.neo4j.full.text.retrieval.query",
            "Full text retrieval query",
            "Full Text Retrieval Query",
            null,
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule AUTO_CREATE_FULL_TEXT = ConfigModule.of(
            Neo4jConfig.class,
            "forage.neo4j.auto.create.full.text",
            "Auto create full text index",
            "Auto Create Full Text",
            "false",
            ConfigModule.TYPE_FALSE,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule ENTITY_CREATION_QUERY = ConfigModule.of(
            Neo4jConfig.class,
            "forage.neo4j.entity.creation.query",
            "Custom entity creation query",
            "Entity Creation Query",
            null,
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule URI = ConfigModule.of(
            Neo4jConfig.class,
            "forage.neo4j.uri",
            "Neo4j server URI",
            "URI",
            "bolt://localhost:7687",
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.COMMON);
    public static final ConfigModule USER = ConfigModule.of(
            Neo4jConfig.class,
            "forage.neo4j.user",
            "Username",
            "User",
            "neo4j",
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.SECURITY);
    public static final ConfigModule PASSWORD = ConfigModule.of(
            Neo4jConfig.class,
            "forage.neo4j.password",
            "Password",
            "Password",
            null,
            ConfigModule.TYPE_PASSWORD,
            true,
            ConfigTag.SECURITY);
    public static final ConfigModule WITH_ENCRYPTION = ConfigModule.of(
            Neo4jConfig.class,
            "forage.neo4j.with.encryption",
            "Enable SSL encryption",
            "With Encryption",
            "false",
            ConfigModule.TYPE_FALSE,
            false,
            ConfigTag.SECURITY);
    public static final ConfigModule CONNECTION_TIMEOUT = ConfigModule.of(
            Neo4jConfig.class,
            "forage.neo4j.connection.timeout",
            "Connection timeout in seconds",
            "Connection Timeout",
            "30",
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule MAX_CONNECTION_LIFETIME = ConfigModule.of(
            Neo4jConfig.class,
            "forage.neo4j.max.connection.lifetime",
            "Max connection lifetime in minutes",
            "Max Connection Lifetime",
            "60",
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule MAX_CONNECTION_POOL_SIZE = ConfigModule.of(
            Neo4jConfig.class,
            "forage.neo4j.max.connection.pool.size",
            "Max connection pool size",
            "Max Connection Pool Size",
            "100",
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule CONNECTION_ACQUISITION_TIMEOUT = ConfigModule.of(
            Neo4jConfig.class,
            "forage.neo4j.connection.acquisition.timeout",
            "Connection acquisition timeout in seconds",
            "Connection Acquisition Timeout",
            "60",
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.ADVANCED);

    static {
        initModules(
                Neo4jConfigEntries.class,
                INDEX_NAME,
                METADATA_PREFIX,
                EMBEDDING_PROPERTY,
                ID_PROPERTY,
                LABEL,
                TEXT_PROPERTY,
                DATABASE_NAME,
                RETRIEVAL_QUERY,
                DIMENSION,
                AWAIT_INDEX_TIMEOUT,
                FULL_TEXT_INDEX_NAME,
                FULL_TEXT_QUERY,
                FULL_TEXT_RETRIEVAL_QUERY,
                AUTO_CREATE_FULL_TEXT,
                ENTITY_CREATION_QUERY,
                URI,
                USER,
                PASSWORD,
                WITH_ENCRYPTION,
                CONNECTION_TIMEOUT,
                MAX_CONNECTION_LIFETIME,
                MAX_CONNECTION_POOL_SIZE,
                CONNECTION_ACQUISITION_TIMEOUT);
    }
}
