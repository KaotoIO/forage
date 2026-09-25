package io.kaoto.forage.vectordb.infinispan;

import io.kaoto.forage.core.util.config.ConfigEntries;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigTag;

public final class InfinispanConfigEntries extends ConfigEntries {
    public static final ConfigModule CACHE_NAME = ConfigModule.of(
            InfinispanConfig.class,
            "forage.infinispan.cache.name",
            "Name of the Infinispan cache",
            "Cache Name",
            null,
            ConfigModule.TYPE_STRING,
            true,
            ConfigTag.COMMON);
    public static final ConfigModule DIMENSION = ConfigModule.of(
            InfinispanConfig.class,
            "forage.infinispan.dimension",
            "Vector dimension for embeddings",
            "Dimension",
            null,
            ConfigModule.TYPE_INTEGER,
            true,
            ConfigTag.COMMON);
    public static final ConfigModule DISTANCE = ConfigModule.of(
            InfinispanConfig.class,
            "forage.infinispan.distance",
            "Distance metric for similarity (3 for cosine)",
            "Distance",
            "3",
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule SIMILARITY = ConfigModule.of(
            InfinispanConfig.class,
            "forage.infinispan.similarity",
            "Similarity algorithm",
            "Similarity",
            "COSINE",
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule CACHE_CONFIG = ConfigModule.of(
            InfinispanConfig.class,
            "forage.infinispan.cache.config",
            "Cache configuration settings",
            "Cache Config",
            null,
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule PACKAGE_NAME = ConfigModule.of(
            InfinispanConfig.class,
            "forage.infinispan.package.name",
            "Package name for generated classes",
            "Package Name",
            "io.kaoto.forage.vectordb.infinispan.schema",
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule FILE_NAME = ConfigModule.of(
            InfinispanConfig.class,
            "forage.infinispan.file.name",
            "Schema file name",
            "File Name",
            "langchain-item.proto",
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule LANGCHAIN_ITEM_NAME = ConfigModule.of(
            InfinispanConfig.class,
            "forage.infinispan.langchain.item.name",
            "LangChain item class name",
            "LangChain Item Name",
            "LangChainItem",
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule METADATA_ITEM_NAME = ConfigModule.of(
            InfinispanConfig.class,
            "forage.infinispan.metadata.item.name",
            "Metadata item class name",
            "Metadata Item Name",
            "MetadataItem",
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule REGISTER_SCHEMA = ConfigModule.of(
            InfinispanConfig.class,
            "forage.infinispan.register.schema",
            "Whether to register schema automatically",
            "Register Schema",
            "true",
            "boolean",
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule CREATE_CACHE = ConfigModule.of(
            InfinispanConfig.class,
            "forage.infinispan.create.cache",
            "Whether to create cache if it doesn't exist",
            "Create Cache",
            "true",
            "boolean",
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule HOST = ConfigModule.of(
            InfinispanConfig.class,
            "forage.infinispan.host",
            "Infinispan server host address",
            "Host",
            "localhost",
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.COMMON);
    public static final ConfigModule PORT = ConfigModule.of(
            InfinispanConfig.class,
            "forage.infinispan.port",
            "Infinispan server port number",
            "Port",
            "11222",
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.COMMON);
    public static final ConfigModule USERNAME = ConfigModule.of(
            InfinispanConfig.class,
            "forage.infinispan.username",
            "Username for authentication",
            "Username",
            null,
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.SECURITY);
    public static final ConfigModule PASSWORD = ConfigModule.of(
            InfinispanConfig.class,
            "forage.infinispan.password",
            "Password for authentication",
            "Password",
            null,
            ConfigModule.TYPE_PASSWORD,
            false,
            ConfigTag.SECURITY);

    static {
        initModules(
                InfinispanConfigEntries.class,
                CACHE_NAME,
                DIMENSION,
                DISTANCE,
                SIMILARITY,
                CACHE_CONFIG,
                PACKAGE_NAME,
                FILE_NAME,
                LANGCHAIN_ITEM_NAME,
                METADATA_ITEM_NAME,
                REGISTER_SCHEMA,
                CREATE_CACHE,
                HOST,
                PORT,
                USERNAME,
                PASSWORD);
    }
}
