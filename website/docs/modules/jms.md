# JMS

Forage creates pooled JMS connection factories with optional XA transaction support.

## Quick Start

```properties
forage.myBroker.jms.kind=artemis
forage.myBroker.jms.url=tcp://localhost:61616
forage.myBroker.jms.username=admin
forage.myBroker.jms.password=secret
```

```yaml
- to:
    uri: jms:queue:orders
    parameters:
      connectionFactory: "#myBroker"
```

{% raw %}
!!! tip "Keep the password out of the file"
    Use a placeholder instead of a literal password: `{{env:BROKER_PASSWORD}}` on every runtime, or a
    Kubernetes Secret (`{{secret:my-broker/password}}`) or vault placeholder on Camel JBang and Spring Boot.
    See [Secrets](../concepts/configuration.md#secrets).
{% endraw %}

## Supported Brokers

{{ forage_beans_table("JMS Connection", "jakarta.jms.ConnectionFactory") }}

## Properties

{{ forage_properties("JMS Connection") }}

## Multiple Brokers

```properties
forage.primaryBroker.jms.kind=artemis
forage.primaryBroker.jms.url=tcp://broker1:61616

forage.backupBroker.jms.kind=artemis
forage.backupBroker.jms.url=tcp://broker2:61617
```

### Per-Broker Components

Each named broker prefix registers its own `JmsComponent` in the Camel context, so routes can
reference the prefix name directly as the component:

```yaml
# Uses the primaryBroker component (and its connection factory)
- from:
    uri: primaryBroker:queue:orders

# Uses the backupBroker component
- to:
    uri: backupBroker:queue:audit
```

This replaces the older pattern of using `jms:` with a `connectionFactory` parameter.
The default (unprefixed) configuration continues to use the `jms` component for backwards
compatibility.

## XA Transactions

Setting `forage.jms.transaction.enabled=true` switches the module to XA mode:

- The connection factory becomes an XA-aware pool (`JmsPoolXAConnectionFactory`) that enlists
  sessions in the Narayana transaction manager.
- The Narayana transaction manager is initialized from the `forage.jms.transaction.*` properties.
- JTA transaction policies (`PROPAGATION_REQUIRED`, `REQUIRES_NEW`, ...) are registered in the
  Camel registry for use with the `transacted` EIP.
- The Camel JMS component is configured with a JTA transaction manager, so consumers receive
  each message inside a JTA transaction and a rollback returns the message to the broker.

### Mixed XA and Non-XA Brokers

Transaction wiring is scoped per broker. In a mixed setup — one XA broker for transactional
work, one plain broker for fire-and-forget — each broker's semantics are self-contained:

```properties
# XA broker — full two-phase commit
forage.xaBroker.jms.kind=ibm-mq
forage.xaBroker.jms.broker.url=localhost(1414)
forage.xaBroker.jms.transaction.enabled=true

# Plain broker — no transaction manager
forage.plainBroker.jms.kind=artemis
forage.plainBroker.jms.broker.url=tcp://localhost:61616
```

Routes reference each broker by prefix:

```yaml
# Transactional consumption from the XA broker
- from:
    uri: xaBroker:queue:orders
    steps:
      - transacted: {}
      - to: sql:insert into orders values(:#id, :#name)?dataSource=#myDb

# Non-transactional send to the plain broker
- to:
    uri: plainBroker:queue:audit
```

The `xaBroker` component gets a `JtaTransactionManager`; the `plainBroker` component does not.
This avoids the overhead and confusion of wrapping non-XA sessions in JTA transactions.

!!! warning "Endpoint contract"
    Leave `transacted` at its default (`false`) on `jms:` endpoints. The JTA transaction manager
    wired into the component drives the transaction; enabling the endpoint's *local* JMS
    transaction on an XA connection is rejected by brokers such as IBM MQ
    (`MQRC_SYNCPOINT_NOT_AVAILABLE`, reason code 2072). Use `cacheLevelName: CACHE_NONE` on
    transactional consumers.

!!! warning "Producers need a transaction too"
    Any route that *sends* to a `jms:` endpoint must also run inside a JTA transaction (add a
    `transacted` step before the send). The XA pool always hands out XA sessions, and a send
    outside a JTA transaction is never enlisted: on IBM MQ it lands in a local syncpoint unit
    of work that is never committed, so the message is **silently discarded**. ActiveMQ Artemis
    auto-commits such sends, which can mask the problem until you switch brokers. Consumers are
    covered automatically — the JTA transaction manager on the component starts a transaction
    around each delivery.

## Crash Recovery

If the application crashes between the prepare and commit phase of an XA transaction, the
transaction branch is left *in doubt* on the broker (IBM MQ keeps an orphaned unit of work
holding locks; Artemis shows it in the `artemis transaction` tooling) until someone resolves it.
Setting `forage.jms.transaction.enable.recovery=true` makes Forage run Narayana's periodic
recovery in-process:

- A recovery manager is started once per JVM (shared with the JDBC module) as soon as the first
  XA-enabled connection factory is created, and stopped when the Camel context stops (or the
  Spring application context closes).
- For every configured broker — including named/prefixed ones — a recovery helper is registered
  that opens fresh XA connections with the broker credentials from the Forage configuration, so
  Narayana can list and resolve in-doubt branches after a restart.
- Every `forage.jms.transaction.recovery.period.seconds` (default 120) a recovery scan replays
  the transaction log: branches whose commit decision was recorded are committed, unresolved
  prepared branches are rolled back once the orphan filters approve.

For recovery to work across restarts:

- **The object store must be persistent and stable.** With the default `file-system` store,
  `forage.jms.transaction.object.store.directory` defaults to `ObjectStore`, resolved against the
  process working directory — in production point it to an absolute path on durable storage, and
  make sure the restarted instance uses the same directory. Alternatively keep the transaction log
  in a database with the [JDBC object store](#transaction-log-storage).
- **`forage.jms.transaction.node.id` must be stable and unique per node.** Narayana tags every
  transaction branch with it; a restarted instance only recovers branches created under the same
  node id, and two nodes sharing an id would steal each other's transactions.

With `forage.jms.transaction.enable.recovery=false` (the default) no recovery thread is started.

!!! note "Quarkus"
    On Quarkus, recovery is owned by the `quarkus-narayana-jta` extension: Forage translates
    `enable.recovery`, the object store settings, and the node id to the corresponding
    `quarkus.transaction-manager.*` properties (for IBM MQ, the recovery helper created by the
    Forage extension is registered with the Quarkus recovery service). The recovery scan
    interval is not configurable through Quarkus properties — use the Narayana system property
    `-DRecoveryEnvironmentBean.periodicRecoveryPeriod=<seconds>` if you need to change it.

## Transaction Log Storage

`forage.jms.transaction.object.store.type` selects where Narayana keeps the transaction log:
`file-system` (default), `jdbc` (a database table, with no persistent volume needed on
Kubernetes/OpenShift) or `volatile` (memory only, tests only). The JDBC store works with JMS-only
applications too. It needs a plain, non-XA Forage datasource for the log table, so add the Forage
JDBC module and a driver module (for example `forage-jdbc-postgresql`). With Camel JBang, defining
the `forage.<name>.jdbc.*` datasource is enough:

```properties
forage.myBroker.jms.transaction.enabled=true
forage.myBroker.jms.transaction.enable.recovery=true
forage.myBroker.jms.transaction.node.id=broker-node-0
forage.myBroker.jms.transaction.object.store.type=jdbc
forage.myBroker.jms.transaction.object.store.datasource=txlog
forage.myBroker.jms.transaction.object.store.create.table=true

# non-XA datasource holding the transaction log (no transaction.enabled)
forage.txlog.jdbc.db.kind=postgresql
forage.txlog.jdbc.url=jdbc:postgresql://db:5432/txlog
forage.txlog.jdbc.username=app
forage.txlog.jdbc.password=secret
```

`forage.jms.transaction.object.store.{datasource,create.table,drop.table,table.prefix}` work the
same as their `forage.jdbc.*` counterparts. See
[JDBC object store](jdbc.md#jdbc-object-store) for the datasource rules, the table, and the
Kubernetes/OpenShift guidance. The object store is shared by the whole JVM: in an application that
also uses Forage JDBC transactions, configure the same object store on both.
