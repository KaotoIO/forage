package io.kaoto.forage.core.jta.store;

import java.util.Objects;

/**
 * The object store settings of one Forage JDBC or JMS configuration with transactions enabled.
 *
 * @param type the raw {@code object.store.type} value (parsed with {@link ObjectStoreType#fromValue})
 * @param directory the file store root directory ({@code file-system} store only)
 * @param dataSource the name of the Forage datasource holding the log table ({@code jdbc} store
 *     only); blank values are normalized to {@code null}
 * @param createTable whether the JDBC store creates its table at startup if missing
 * @param dropTable whether the JDBC store drops its table at startup (destroys pending logs)
 * @param tablePrefix prefix of the JDBC store table name
 * @param typeProperty the fully qualified property name {@code type} was read from, for messages
 * @param dataSourceProperty the fully qualified property name {@code dataSource} was read from
 */
public record ObjectStoreSettings(
        String type,
        String directory,
        String dataSource,
        boolean createTable,
        boolean dropTable,
        String tablePrefix,
        String typeProperty,
        String dataSourceProperty) {

    public ObjectStoreSettings {
        dataSource = dataSource == null || dataSource.isBlank() ? null : dataSource.trim();
    }

    /** The parsed store type; fails for unsupported values. */
    public ObjectStoreType storeType() {
        return ObjectStoreType.fromValue(type, typeProperty);
    }

    /** Whether both settings describe the same JDBC store (datasource, table and table flags). */
    boolean sameJdbcStoreAs(ObjectStoreSettings other) {
        return Objects.equals(dataSource, other.dataSource)
                && Objects.equals(tablePrefix, other.tablePrefix)
                && createTable == other.createTable
                && dropTable == other.dropTable;
    }

    String describeJdbcStore() {
        return "datasource '" + dataSource + "', table " + NarayanaObjectStore.tableName(tablePrefix)
                + ", create.table=" + createTable + ", drop.table=" + dropTable;
    }
}
