# Distributed XA

[:material-github: Source](https://github.com/KaotoIO/forage-examples/tree/main/transactions){ .md-button .md-button--primary }

A single XA transaction spanning both JMS and JDBC, demonstrating two-phase commit with all-or-nothing guarantees across ActiveMQ Artemis and PostgreSQL.

## What You'll Learn

- How to configure distributed XA transactions across JMS and JDBC resources
- How Forage coordinates a shared Narayana transaction manager for both providers
- Two-phase commit behavior: either all operations succeed or all roll back
- How rollback messages trigger automatic redelivery and eventually reach the dead letter queue

## Prerequisites

- Java 17 or later
- [Camel JBang](https://camel.apache.org/manual/camel-jbang.html) with the Forage plugin installed

Start ActiveMQ Artemis:

```bash
camel infra run artemis
```

Start PostgreSQL with prepared transactions enabled:

```bash
docker run -d --name camel-postgres -p 5432:5432 \
  -e POSTGRES_USER=test -e POSTGRES_PASSWORD=test -e POSTGRES_DB=postgres \
  postgres:16 -c max_prepared_transactions=100
```

!!! warning "PostgreSQL must allow prepared transactions"
    A transaction spanning two resources uses two-phase commit: Narayana issues
    `PREPARE TRANSACTION` against PostgreSQL, which fails with
    `ERROR: prepared transactions are disabled` when `max_prepared_transactions` is `0` —
    and `0` is both the PostgreSQL default and what `camel infra run postgres` starts with.
    Start PostgreSQL with `-c max_prepared_transactions=100` as shown above, or on an
    existing server run `ALTER SYSTEM SET max_prepared_transactions = 100;` and restart it.
    The single-resource JDBC examples don't need this: with only one participant, Narayana
    uses the one-phase-commit optimization and never issues `PREPARE TRANSACTION`.

Create the database schema:

```bash
docker exec -i camel-postgres psql -U test -d postgres -c \
  "CREATE TABLE IF NOT EXISTS test (id INTEGER PRIMARY KEY, action VARCHAR(255));"
```

## Configuration

Create `application.properties`:

```properties
# ── JMS (ActiveMQ Artemis) ──────────────────────────────────

forage.myBroker.jms.kind=artemis
forage.myBroker.jms.broker.url=tcp://localhost:61616
forage.myBroker.jms.username=artemis
forage.myBroker.jms.password=artemis

# Connection pool
forage.myBroker.jms.pool.enabled=true
forage.myBroker.jms.pool.max.connections=10
forage.myBroker.jms.pool.max.sessions.per.connection=500
forage.myBroker.jms.pool.idle.timeout.millis=30000
forage.myBroker.jms.pool.connection.timeout.millis=30000
forage.myBroker.jms.pool.block.if.full=true

# XA transactions — shared node ID links JMS and JDBC
# into the same Narayana transaction manager instance
forage.myBroker.jms.transaction.enabled=true
forage.myBroker.jms.transaction.timeout.seconds=30
forage.myBroker.jms.transaction.node.id=xa-node1
forage.myBroker.jms.transaction.enable.recovery=true
forage.myBroker.jms.transaction.object.store.directory=tx-object-store
forage.myBroker.jms.transaction.object.store.type=file-system

# ── JDBC (PostgreSQL) ──────────────────────────────────────

forage.myDatabase.jdbc.db.kind=postgresql
forage.myDatabase.jdbc.url=jdbc:postgresql://localhost:5432/postgres
forage.myDatabase.jdbc.username=test
forage.myDatabase.jdbc.password=test

# Connection pool
forage.myDatabase.jdbc.pool.initial.size=5
forage.myDatabase.jdbc.pool.min.size=2
forage.myDatabase.jdbc.pool.max.size=20
forage.myDatabase.jdbc.pool.acquisition.timeout.seconds=5
forage.myDatabase.jdbc.pool.validation.timeout.seconds=3
forage.myDatabase.jdbc.pool.leak.timeout.minutes=10
forage.myDatabase.jdbc.pool.idle.validation.timeout.minutes=3

# XA transactions — same node ID as JMS
forage.myDatabase.jdbc.transaction.enabled=true
forage.myDatabase.jdbc.transaction.timeout.seconds=30
forage.myDatabase.jdbc.transaction.node.id=xa-node1
forage.myDatabase.jdbc.transaction.enable.recovery=true
forage.myDatabase.jdbc.transaction.object.store.directory=tx-object-store
forage.myDatabase.jdbc.transaction.object.store.type=file-system
```

The critical detail is the shared `transaction.node.id=xa-node1` across both JMS and JDBC configurations. This tells Forage to use the same Narayana transaction manager instance for both resources, enabling distributed two-phase commit.

## Route

```yaml
# Transaction processing route — consumes from JMS, inserts into DB,
# produces to JMS, all within a single XA transaction
- route:
    id: xa-processing-route
    from:
      uri: jms
      parameters:
        destinationName: in
        # transacted stays false with XA: the JTA transaction manager wired into the
        # JMS component drives the transaction (a local JMS transaction on an XA
        # connection is rejected by brokers such as IBM MQ)
        cacheLevelName: CACHE_NONE
      steps:
        - transacted:
            ref: PROPAGATION_REQUIRED
        - log:
            message: >-
              Start transaction with message ${body}
              and event id ${headers.eventId}
        - toD:
            uri: sql
            parameters:
              dataSource: "#myDatabase"
              query: >-
                INSERT INTO test (id, action)
                VALUES (${headers.eventId}, 'test')
        - log:
            message: Query executed successfully
        - to:
            uri: jms
            parameters:
              destinationName: out
        - choice:
            when:
              - simple:
                  expression: ${body} == "ROLLBACK"
                steps:
                  - throwException:
                      exceptionType: java.lang.RuntimeException
                      message: Rollback transaction
            otherwise:
              steps:
                - log:
                    message: End transaction with message ${body}

# Message generator — produces test messages every 5 seconds,
# ~40% are ROLLBACK messages to demonstrate rollback behavior.
# The transacted step is required: with XA enabled, sends outside a JTA
# transaction are never enlisted and brokers such as IBM MQ silently
# discard them (Artemis auto-commits them, masking the problem)
- route:
    id: message-generator-route
    from:
      uri: timer:template
      parameters:
        period: "5000"
      steps:
        - transacted:
            ref: PROPAGATION_REQUIRED
        - setHeader:
            name: eventId
            simple:
              expression: ${random(0,10000)}
        - choice:
            when:
              - simple:
                  expression: ${random(0,10)} > 5
                steps:
                  - log:
                      message: Sending rollback message
                  - setBody:
                      simple:
                        expression: ROLLBACK
                  - to:
                      uri: jms
                      parameters:
                        destinationName: in
            otherwise:
              steps:
                - log:
                    message: Sending OK message
                - setBody:
                    simple:
                      expression: OK
                - to:
                    uri: jms
                    parameters:
                      destinationName: in
```

The example has two routes:

**Transaction processing route** -- consumes a message from the `in` queue inside an XA transaction, then performs three operations within the same transaction boundary:

1. Inserts a row into the `test` table using the `eventId` header as the primary key
2. Sends the message to the `out` queue
3. Checks the body -- if it is `ROLLBACK`, throws an exception to trigger a full rollback

When the exception is thrown, all three operations are undone atomically: the database insert is rolled back, the message to `out` is cancelled, and the original message returns to the `in` queue for redelivery.

**Message generator route** -- a timer fires every 5 seconds, generates a random `eventId` header, and sends either an `OK` or `ROLLBACK` message to the `in` queue (roughly 40% rollbacks).

## Running

```bash
camel run *
```

Watch the logs to observe the two flows:

**Successful flow** (body = `OK`):

```
Start transaction with message OK and event id 4821
Query executed successfully
End transaction with message OK
```

The database row persists and the message arrives in the `out` queue.

**Rollback flow** (body = `ROLLBACK`):

```
Start transaction with message ROLLBACK and event id 7392
Query executed successfully
[Exception: Rollback transaction]
```

Despite the insert having executed, the database row is removed by the XA rollback. The message returns to `in` for redelivery. After the broker's maximum redelivery attempts, the message moves to the DLQ.

You can verify atomicity by checking the database -- no rows will exist for `ROLLBACK` messages, even though the `INSERT` was executed before the rollback:

```bash
docker exec -i camel-postgres psql -U postgres -c "SELECT * FROM test;"
```

Monitor queue depths in the Artemis web console at `http://localhost:8161/console` (credentials: `artemis` / `artemis`).

## Key Takeaways

- **Shared transaction manager** -- using the same `transaction.node.id` across JMS and JDBC configurations links both resources into a single Narayana XA coordinator.
- **True atomicity** -- the two-phase commit protocol guarantees that either all operations (JMS consume, DB insert, JMS produce) succeed together or all roll back together.
- **No partial state** -- without XA, a successful database insert followed by a failed JMS send would leave orphaned data. With XA, this cannot happen.
- **Recovery after crashes** -- with `transaction.enable.recovery=true` Forage starts Narayana's periodic recovery manager and registers a recovery helper per broker and datasource; the file-system object store (`tx-object-store/`) persists the transaction logs it replays to resolve in-doubt transactions after a restart. Use an absolute object store path and a stable `transaction.node.id` so the restarted instance finds its own logs (see the [JMS](../../modules/jms.md#crash-recovery) and [JDBC](../../modules/jdbc.md#crash-recovery) module pages). To run without a persistent volume (for example on Kubernetes/OpenShift), set `transaction.object.store.type=jdbc` to keep the logs in a database table instead (see [JDBC object store](../../modules/jdbc.md#jdbc-object-store)).
- **Zero coordination code** -- Forage handles all XA wiring (enlisting resources, two-phase commit, recovery) through properties alone.
