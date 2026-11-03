# JDBC

Forage creates pooled datasources with connection management, optional XA transactions, and auxiliary repositories.

## Quick Start

```properties
forage.myDb.jdbc.db.kind=postgresql
forage.myDb.jdbc.url=jdbc:postgresql://localhost:5432/mydb
forage.myDb.jdbc.username=admin
forage.myDb.jdbc.password=secret
```

```yaml
- to:
    uri: sql
    parameters:
      query: select * from orders
      dataSource: "#myDb"
```

## Supported Databases

{{ forage_beans_table("DataSource", "javax.sql.DataSource") }}

## Properties

{{ forage_properties("DataSource") }}

!!! info "Unknown db.kind"
    Setting an unrecognized `db.kind` value now fails at startup with an error listing the
    available providers, instead of producing a `NullPointerException` later.

## Multiple Datasources

Use different names to configure multiple databases:

```properties
forage.ordersDb.jdbc.db.kind=postgresql
forage.ordersDb.jdbc.url=jdbc:postgresql://db1:5432/orders

forage.analyticsDb.jdbc.db.kind=mysql
forage.analyticsDb.jdbc.url=jdbc:mysql://db2:3306/analytics
```

{% raw %}
## Driver Properties

Driver-specific settings, such as SSL, failover or timeouts, go under
`forage.[<name>.]jdbc.properties.<property>`, and Forage passes each one to the JDBC driver:

```properties
forage.ordersDb.jdbc.db.kind=db2
forage.ordersDb.jdbc.url=jdbc:db2://db2-primary:50001/ORDERS
forage.ordersDb.jdbc.properties.sslConnection=true
forage.ordersDb.jdbc.properties.sslTrustStoreLocation=/certs/truststore.jks
forage.ordersDb.jdbc.properties.sslTrustStorePassword={{env:TRUSTSTORE_PASSWORD}}
forage.ordersDb.jdbc.properties.clientRerouteAlternateServerName=db2-standby
forage.ordersDb.jdbc.properties.clientRerouteAlternatePortNumber=50001
forage.ordersDb.jdbc.properties.enableSeamlessFailover=1
```

Property names are case-sensitive and must match the driver's documentation. Names may contain
dots (for example `oracle.jdbc.ReadTimeout`).

Without XA transactions, the properties go to the driver as connection properties. With
`transaction.enabled=true`, Forage sets each one on the driver's XA datasource class, through the
setter of the same name:

- PostgreSQL and Oracle accept any property name.
- DB2, MySQL and SQL Server accept the properties their XA datasource has a setter for, which covers
  the documented connection properties.
- The MariaDB and H2 XA datasources have almost no setters. Put their options in the JDBC URL
  instead.

Forage logs a warning for each property the XA datasource has no setter for.

- Set them in properties files, `application.properties` or system properties. Environment
  variables are not read for these keys, because environment variable names cannot keep the case
  the driver expects. Use an `{{env:NAME}}` placeholder in the value instead, as with the password above.
- On Quarkus, they become `quarkus.datasource."<name>".jdbc.additional-jdbc-properties.*`.
{% endraw %}

## XA Transactions

Setting `forage.jdbc.transaction.enabled=true` switches the module to XA mode: the Agroal pool
enlists connections in the Narayana transaction manager (initialized from the
`forage.jdbc.transaction.*` properties), and JTA transaction policies (`PROPAGATION_REQUIRED`,
`REQUIRES_NEW`, ...) are registered for use with the `transacted` EIP.

!!! note "DB2"
    The DB2 XA datasource (`DB2XADataSource`) cannot take a JDBC URL, so in XA mode Forage reads
    the server, port, database and any `:property=value;` settings from `forage.jdbc.url` and
    passes them to the driver one by one. Use a type 4 URL (`jdbc:db2://server:port/database`).

## Crash Recovery

If the application crashes between the prepare and commit phase of an XA transaction, the
transaction branch is left *in doubt* on the database until someone resolves it. Setting
`forage.jdbc.transaction.enable.recovery=true` makes Forage run Narayana's periodic recovery
in-process:

- A recovery manager is started once per JVM (shared with the JMS module) as soon as the first
  XA-enabled datasource is created, and stopped when the Camel context stops (or the Spring
  application context closes).
- Every XA-enabled datasource — including named/prefixed ones — is registered with the recovery
  manager through Agroal's Narayana integration, so Narayana can obtain fresh connections and
  resolve in-doubt branches after a restart.
- Every `forage.jdbc.transaction.recovery.period.seconds` (default 120) a recovery scan replays
  the transaction log: branches whose commit decision was recorded are committed, unresolved
  prepared branches are rolled back once the orphan filters approve.

For recovery to work across restarts:

- **The object store must be persistent and stable.** `forage.jdbc.transaction.object.store.directory`
  defaults to `ObjectStore`, resolved against the process working directory — in production point
  it to an absolute path on durable storage, and make sure the restarted instance uses the same
  directory.
- **`forage.jdbc.transaction.node.id` must be stable and unique per node.** Narayana tags every
  transaction branch with it; a restarted instance only recovers branches created under the same
  node id, and two nodes sharing an id would steal each other's transactions.

With `forage.jdbc.transaction.enable.recovery=false` (the default) no recovery thread is started.

!!! note "Quarkus"
    On Quarkus, recovery is owned by the `quarkus-narayana-jta` extension: Forage translates
    `enable.recovery`, the object store settings, and the node id to the corresponding
    `quarkus.transaction-manager.*` properties. The recovery scan interval is not configurable
    through Quarkus properties — use the Narayana system property
    `-DRecoveryEnvironmentBean.periodicRecoveryPeriod=<seconds>` if you need to change it.
