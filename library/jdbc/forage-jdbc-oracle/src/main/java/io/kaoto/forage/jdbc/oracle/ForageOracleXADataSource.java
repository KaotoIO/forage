package io.kaoto.forage.jdbc.oracle;

import java.sql.SQLException;

/**
 * Oracle XA datasource that Agroal can configure with any Oracle connection property.
 *
 * <p>Agroal sets each JDBC property through the matching setter. Most Oracle properties, such as
 * {@code oracle.jdbc.ReadTimeout} or {@code oracle.net.CONNECT_TIMEOUT}, have no setter, and
 * Agroal then falls back to a two-argument {@code setProperty} method. This class provides that
 * method and passes the property on with {@link #setConnectionProperty(String, String)}. Typed
 * setters still take precedence, because Agroal tries them first.
 */
public class ForageOracleXADataSource extends oracle.jdbc.xa.client.OracleXADataSource {

    private static final long serialVersionUID = 1L;

    public ForageOracleXADataSource() throws SQLException {
        super();
    }

    /**
     * Sets a connection property that has no typed setter.
     *
     * <p>Agroal also calls this for the JDBC URL as {@code url}, because Oracle names its setter
     * {@code setURL}, so the URL is routed there.
     *
     * @param name the property name
     * @param value the property value
     * @throws SQLException if Oracle rejects the property
     */
    public void setProperty(String name, String value) throws SQLException {
        if ("url".equalsIgnoreCase(name)) {
            setURL(value);
        } else {
            setConnectionProperty(name, value);
        }
    }
}
