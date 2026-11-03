# Transactional JMS

[:material-github: Source](https://github.com/KaotoIO/forage-examples/tree/main/jms/transactional){ .md-button .md-button--primary }

XA transactions with Narayana transaction manager, demonstrating automatic rollback, redelivery, and dead letter queue handling.

## What You'll Learn

- How Forage configures XA transactions and the Narayana transaction manager from properties
- Using `PROPAGATION_REQUIRED` and other transaction policies in routes
- How failed messages are automatically rolled back and redelivered by the broker
- Dead letter queue (DLQ) handling after maximum redelivery attempts

## Prerequisites

- Java 17 or later
- [Camel JBang](https://camel.apache.org/manual/camel-jbang.html) with the Forage plugin installed

Start ActiveMQ Artemis:

```bash
camel infra run artemis
```

This starts Artemis on `tcp://localhost:61616` with the web console at `http://localhost:8161/console` (credentials: `artemis` / `artemis`).

## Configuration

Create `application.properties`:

```properties
# JMS provider
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

# XA Transaction settings — this is what enables transactional mode
forage.myBroker.jms.transaction.enabled=true
forage.myBroker.jms.transaction.timeout.seconds=30
forage.myBroker.jms.transaction.node.id=node1
forage.myBroker.jms.transaction.enable.recovery=true
forage.myBroker.jms.transaction.object.store.directory=tx-object-store
forage.myBroker.jms.transaction.object.store.type=file-system
```

Setting `transaction.enabled=true` changes Forage's behavior: it creates an `XAConnectionFactory` instead of a regular `ConnectionFactory` (pooled through an XA-aware pool that enlists sessions in the transaction), initializes the Narayana transaction manager, registers JTA transaction policies (`PROPAGATION_REQUIRED`, `PROPAGATION_REQUIRES_NEW`, etc.) in the Camel registry, and configures the Camel JMS component with a JTA transaction manager so every consumer receives its messages inside a JTA transaction.

!!! warning "Do not set `transacted=true` on JMS endpoints when XA is enabled"
    With `transaction.enabled=true` the JTA transaction manager wired into the JMS component drives the transaction. Setting `transacted: "true"` on an endpoint would additionally start a *local* JMS transaction on the XA connection, which brokers such as IBM MQ reject (`MQRC_SYNCPOINT_NOT_AVAILABLE`, reason code 2072). Leave `transacted` at its default (`false`).

!!! warning "Producers must send inside a JTA transaction"
    With XA enabled the pool always hands out XA sessions. A send that runs outside a JTA
    transaction is never enlisted: on IBM MQ it lands in a local syncpoint unit of work that is
    never committed, so the message is **silently discarded** (ActiveMQ Artemis auto-commits
    such sends, masking the problem). Start every producer route with a `transacted` step, as
    the producer route below does.

## Route

=== "YAML"

    ```yaml
    # Producer — sends a message to input.queue every 5 seconds,
    # inside a JTA transaction so the XA send is committed
    - route:
        id: producer-route
        from:
          uri: timer:producer
          parameters:
            period: "5000"
          steps:
            - transacted:
                ref: PROPAGATION_REQUIRED
            - setBody:
                simple:
                  expression: Transactional message
            - to:
                uri: jms
                parameters:
                  destinationName: input.queue
                  destinationType: queue
            - log:
                message: Sent message to input queue

    # Transactional consumer — processes within an XA transaction
    - route:
        id: transactional-consumer-route
        from:
          uri: jms
          parameters:
            destinationName: input.queue
            destinationType: queue
            cacheLevelName: CACHE_NONE
          steps:
            - transacted:
                ref: PROPAGATION_REQUIRED
            - log:
                message: "Processing message: ${body}"
            - choice:
                when:
                  - simple: ${random(0,10)} > 7
                    steps:
                      - log:
                          message: Simulating error - message will be rolled back
                      - throwException:
                          exceptionType: java.lang.RuntimeException
                          message: Simulated processing error
                otherwise:
                  steps:
                    - log:
                        message: Processing successful - committing transaction
                    - to:
                        uri: jms
                        parameters:
                          destinationName: output.queue
                          destinationType: queue
                    - log:
                        message: Message forwarded to output queue

    # Consumer for successfully processed messages
    - route:
        id: output-consumer-route
        from:
          uri: jms
          parameters:
            destinationName: output.queue
            destinationType: queue
          steps:
            - log:
                message: "Successfully processed message: ${body}"

    # Dead letter queue consumer
    - route:
        id: dlq-consumer-route
        from:
          uri: jms
          parameters:
            destinationName: DLQ
            destinationType: queue
          steps:
            - log:
                message: "Message sent to DLQ after max redeliveries: ${body}"
    ```

=== "Java"

    ```java
    public class Route extends RouteBuilder {

        @Override
        public void configure() throws Exception {

            // Producer - sends messages to input queue inside a JTA
            // transaction so the XA send is committed
            from("timer:producer?period=10000")
                    .transacted("PROPAGATION_REQUIRED")
                    .setBody(constant("Transactional message"))
                    .to("jms:queue:input.queue")
                    .log("Sent message to input queue");

            // Transactional consumer - processes messages within XA transaction
            from("jms:queue:input.queue?cacheLevelName=CACHE_NONE")
                    .transacted("PROPAGATION_REQUIRED")
                    .log("Processing message: ${body}")
                    .choice()
                        .when(simple("${random(0,10)} > 7"))
                            .log("Simulating error - message will be rolled back")
                            .throwException(new RuntimeException("Simulated processing error"))
                        .otherwise()
                            .log("Processing successful - committing transaction")
                            .to("jms:queue:output.queue")
                            .log("Message forwarded to output queue")
                    .end();

            // Consumer for successfully processed messages
            from("jms:queue:output.queue")
                    .log("Successfully processed message: ${body}");

            // Dead Letter Queue consumer
            from("jms:queue:DLQ")
                    .log("Message sent to DLQ after max redeliveries: ${body}");
        }
    }
    ```

The example has four routes:

1. **Producer** -- a timer sends messages to `input.queue` at a fixed interval, inside its own JTA transaction so the XA send commits.
2. **Transactional consumer** -- consumes from `input.queue` within an XA transaction. Roughly 30% of messages trigger a simulated error, causing the transaction to roll back. Successful messages are forwarded to `output.queue`.
3. **Output consumer** -- logs messages that completed the transaction successfully.
4. **DLQ consumer** -- logs messages that exhausted the broker's maximum redelivery attempts.

Key points in the transactional consumer:

- The JMS endpoint does **not** set `transacted=true` -- the JTA transaction manager that Forage wires into the JMS component starts a JTA transaction around each receive, and the XA session enlists in it. A local JMS transaction would conflict with XA (IBM MQ rejects it with reason code 2072).
- `cacheLevelName: CACHE_NONE` is required for XA transactions to prevent stale session caching.
- `transacted` with `ref: PROPAGATION_REQUIRED` joins the transaction started by the consumer (or creates a new one when there is none).

## Running

```bash
camel run *
```

Watch the logs -- you will see successful commits, simulated rollbacks, and eventually messages arriving in the DLQ after repeated failures.

You can also monitor queue depths in the Artemis web console at `http://localhost:8161/console`.

## Key Takeaways

- **One property enables XA** -- setting `transaction.enabled=true` switches from plain `ConnectionFactory` to a pooled `XAConnectionFactory`, wires up Narayana, and configures the Camel JMS component with a JTA transaction manager automatically.
- **Transaction policies are auto-registered** -- `PROPAGATION_REQUIRED`, `PROPAGATION_REQUIRES_NEW`, and others are available in the registry without any Java configuration.
- **Rollback is automatic** -- any exception within a transacted route causes a full rollback; the message returns to the queue for redelivery.
- **DLQ safety net** -- after the broker's maximum redelivery attempts, messages move to the dead letter queue rather than being lost.
- **Recovery support** -- with `transaction.enable.recovery=true` Forage runs Narayana's periodic recovery manager with a recovery helper per broker; the file-system object store (`tx-object-store/`) persists the transaction logs it replays to commit or roll back in-doubt transactions after a crash. Use an absolute object store path and a stable `transaction.node.id` across restarts (see [Crash Recovery](../../modules/jms.md#crash-recovery)). To run without a persistent volume, keep the logs in a database instead (see [Transaction Log Storage](../../modules/jms.md#transaction-log-storage)).
