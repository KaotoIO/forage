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

## XA Transactions

Setting `forage.jdbc.transaction.enabled=true` switches the module to XA mode: the Agroal pool
enlists connections in the Narayana transaction manager (initialized from the
`forage.jdbc.transaction.*` properties), and JTA transaction policies (`PROPAGATION_REQUIRED`,
`REQUIRES_NEW`, ...) are registered for use with the `transacted` EIP.

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

- **The object store must be persistent and stable.** With the default `file-system` store,
  `forage.jdbc.transaction.object.store.directory` defaults to `ObjectStore`, resolved against the
  process working directory — in production point it to an absolute path on durable storage, and
  make sure the restarted instance uses the same directory. Alternatively keep the transaction log
  in a database with the [JDBC object store](#jdbc-object-store).
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

## Transaction Log Storage

Before committing the branches of a two-phase commit, Narayana records the commit decision in its
*object store*; crash recovery replays it. `forage.jdbc.transaction.object.store.type` selects
where it lives:

| Type | Transaction log | Use it for |
|------|-----------------|------------|
| `file-system` (default) | Files under `object.store.directory` | A single node with a durable, node-exclusive disk |
| `jdbc` | A table in a database | Containers and Kubernetes/OpenShift: no persistent volume needed |
| `volatile` | Memory only | Tests. Nothing survives a crash, so recovery is impossible |

Any other value fails at startup with the list of supported types.

### JDBC Object Store

```properties
# The XA datasource used by the routes
forage.orders.jdbc.db.kind=postgresql
forage.orders.jdbc.url=jdbc:postgresql://db:5432/orders
forage.orders.jdbc.username=app
forage.orders.jdbc.password=secret
forage.orders.jdbc.transaction.enabled=true
forage.orders.jdbc.transaction.enable.recovery=true
forage.orders.jdbc.transaction.node.id=orders-0
forage.orders.jdbc.transaction.object.store.type=jdbc
forage.orders.jdbc.transaction.object.store.datasource=txlog
forage.orders.jdbc.transaction.object.store.create.table=true

# A plain (non-XA) datasource for the transaction log; it may point at the same database
forage.txlog.jdbc.db.kind=postgresql
forage.txlog.jdbc.url=jdbc:postgresql://db:5432/orders
forage.txlog.jdbc.username=app
forage.txlog.jdbc.password=secret
forage.txlog.jdbc.pool.max.size=5
```

- **`object.store.datasource` names another Forage datasource** (the `<name>` of its
  `forage.<name>.jdbc.*` properties). Forage builds a dedicated connection pool for Narayana from
  that configuration, sized by its `pool.*` properties. The `<name>` datasource itself is still
  registered as a regular bean; Narayana does not share its pool.
- **The referenced datasource must not be XA.** Narayana writes the log in its own local
  transactions, outside the XA transaction being recorded. Leave `transaction.enabled` unset on the
  referenced configuration. Forage rejects a reference to a datasource with
  `transaction.enabled=true` at startup. The log can live in the same database as the business
  data; only the pool has to be separate.
- **Misconfiguration fails at startup.** If the type is `jdbc` but `object.store.datasource` is not
  set, names a datasource that is not configured, or names an XA datasource, the application does
  not start. It never falls back to the file store.
- **The table** is `<table.prefix>JBossTSTxTable`, and the default prefix is `forage_`. Narayana's
  three stores (the transaction log, the state store and the communication store) share it.
  `create.table=true` creates the table at startup if it is missing. The default is `false`: the
  table must then exist already, for example created by your DBA. For PostgreSQL:

    ```sql
    CREATE TABLE forage_JBossTSTxTable (
        StateType INTEGER NOT NULL, Hidden INTEGER NOT NULL,
        TypeName VARCHAR(255) NOT NULL, UidString VARCHAR(255) NOT NULL,
        ObjectState BYTEA,
        PRIMARY KEY (UidString, TypeName, StateType));
    ```

- `drop.table=true` drops the table **at startup** and loses every pending transaction log, which
  makes crash recovery impossible. Use it in tests only.

{% raw %}
!!! tip "Kubernetes and OpenShift"
    The file store needs a durable disk that only one node uses. On Kubernetes that means a
    StatefulSet with a PersistentVolumeClaim per replica. With the JDBC store the pods keep no
    transaction state on disk, so you don't need PVCs or `volumeClaimTemplates`. Pods can be
    rescheduled to any node, and the transaction log is as durable and available as the database.

    The store doesn't change what Narayana needs from each replica:

    - **A stable, unique node id that the replica reuses after a restart.** A restarted replica
      only resolves branches created under its own node id. Random Deployment pod names don't
      work. A StatefulSet without volumes provides a stable identity, for example the pod index
      exposed through the Downward API as `POD_INDEX`:
      `transaction.node.id=orders-{{env:POD_INDEX}}`.
    - **A table for each replica.** Narayana assumes that no other live process shares its object
      store. Recovery replays every log record in the table and checks whether the owning process
      is alive through an address recorded in the store. Another pod can't reach that address, so
      a shared table lets one replica replay another replica's in-flight commits. Give each replica
      its own prefix (letters, digits and `_` only), for example
      `transaction.object.store.table.prefix=orders{{env:POD_INDEX}}_`.
{% endraw %}

!!! warning "One object store per JVM"
    Narayana's object store is JVM-global, and every Forage JDBC and JMS configuration with
    transactions enabled shares it. Configure the same `transaction.object.store.*` properties on
    all of them. The first `jdbc` store configured wins, and a configuration with different store
    settings is ignored with a warning. Switching the store type after the recovery manager has
    started fails at startup.

!!! note "Quarkus"
    Forage translates the settings to `quarkus.transaction-manager.object-store.*`. It also sets
    `quarkus.datasource."<name>".jdbc.transactions=disabled` on the referenced datasource, because
    `quarkus-narayana-jta` requires that for the store datasource.
