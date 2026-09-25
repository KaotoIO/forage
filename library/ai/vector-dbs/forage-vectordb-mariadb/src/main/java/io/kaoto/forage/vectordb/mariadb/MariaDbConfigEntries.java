package io.kaoto.forage.vectordb.mariadb;

import io.kaoto.forage.core.util.config.ConfigEntries;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigTag;

public final class MariaDbConfigEntries extends ConfigEntries {
    public static final ConfigModule URL = ConfigModule.of(
            MariaDbConfig.class,
            "forage.mariadb.url",
            "MariaDB JDBC URL (e.g., jdbc:mariadb://localhost:3306/vectordb)",
            "URL",
            null,
            ConfigModule.TYPE_STRING,
            true,
            ConfigTag.COMMON);
    public static final ConfigModule USER = ConfigModule.of(
            MariaDbConfig.class,
            "forage.mariadb.user",
            "Database username for authentication",
            "User",
            null,
            ConfigModule.TYPE_STRING,
            true,
            ConfigTag.SECURITY);
    public static final ConfigModule PASSWORD = ConfigModule.of(
            MariaDbConfig.class,
            "forage.mariadb.password",
            "Database password for authentication",
            "Password",
            null,
            ConfigModule.TYPE_PASSWORD,
            false,
            ConfigTag.SECURITY);
    public static final ConfigModule TABLE = ConfigModule.of(
            MariaDbConfig.class,
            "forage.mariadb.table",
            "Name of the table to store embeddings",
            "Table",
            "embeddings",
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.COMMON);
    public static final ConfigModule ID_FIELD_NAME = ConfigModule.of(
            MariaDbConfig.class,
            "forage.mariadb.id.field.name",
            "Name of the ID field in the embeddings table",
            "ID Field Name",
            "id",
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule EMBEDDING_FIELD_NAME = ConfigModule.of(
            MariaDbConfig.class,
            "forage.mariadb.embedding.field.name",
            "Name of the embedding vector field",
            "Embedding Field Name",
            "embedding",
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule CONTENT_FIELD_NAME = ConfigModule.of(
            MariaDbConfig.class,
            "forage.mariadb.content.field.name",
            "Name of the content text field",
            "Content Field Name",
            "content",
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule DISTANCE_TYPE = ConfigModule.of(
            MariaDbConfig.class,
            "forage.mariadb.distance.type",
            "Distance calculation method: COSINE or EUCLIDEAN",
            "Distance Type",
            "COSINE",
            ConfigModule.TYPE_STRING,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule DIMENSION = ConfigModule.of(
            MariaDbConfig.class,
            "forage.mariadb.dimension",
            "Vector dimension size",
            "Dimension",
            "384",
            ConfigModule.TYPE_INTEGER,
            false,
            ConfigTag.COMMON);
    public static final ConfigModule CREATE_TABLE = ConfigModule.of(
            MariaDbConfig.class,
            "forage.mariadb.create.table",
            "Whether to create the table if it doesn't exist",
            "Create Table",
            "true",
            ConfigModule.TYPE_BOOLEAN,
            false,
            ConfigTag.ADVANCED);
    public static final ConfigModule DROP_TABLE_FIRST = ConfigModule.of(
            MariaDbConfig.class,
            "forage.mariadb.drop.table.first",
            "Whether to drop the table before creating it",
            "Drop Table First",
            "false",
            ConfigModule.TYPE_BOOLEAN,
            false,
            ConfigTag.ADVANCED);

    static {
        initModules(
                MariaDbConfigEntries.class,
                URL,
                USER,
                PASSWORD,
                TABLE,
                DISTANCE_TYPE,
                ID_FIELD_NAME,
                EMBEDDING_FIELD_NAME,
                CONTENT_FIELD_NAME,
                CREATE_TABLE,
                DROP_TABLE_FIRST,
                DIMENSION);
    }
}
