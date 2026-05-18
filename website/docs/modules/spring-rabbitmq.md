# Spring RabbitMQ

Forage creates Spring RabbitMQ `CachingConnectionFactory` beans for AMQP messaging with the `camel-spring-rabbitmq` component.

## Quick Start

```properties
forage.spring.rabbitmq.host=localhost
forage.spring.rabbitmq.port=5672
forage.spring.rabbitmq.username=guest
forage.spring.rabbitmq.password=guest
```

```yaml
- to:
    uri: spring-rabbitmq:orders
    parameters:
      connectionFactory: "#rabbitConnectionFactory"
```

## Properties

{{ forage_properties("Spring RabbitMQ Connection") }}

## Multiple Brokers

Use prefixed configuration to create multiple named connection factories:

```properties
forage.primary.spring.rabbitmq.host=broker1.example.com
forage.primary.spring.rabbitmq.port=5672
forage.primary.spring.rabbitmq.username=admin
forage.primary.spring.rabbitmq.password=secret

forage.backup.spring.rabbitmq.host=broker2.example.com
forage.backup.spring.rabbitmq.port=5672
forage.backup.spring.rabbitmq.username=admin
forage.backup.spring.rabbitmq.password=secret
```

```yaml
- to:
    uri: spring-rabbitmq:orders
    parameters:
      connectionFactory: "#primary"
- to:
    uri: spring-rabbitmq:notifications
    parameters:
      connectionFactory: "#backup"
```

## Cluster Failover

Use the `addresses` property to connect to a RabbitMQ cluster. When set, it takes precedence for connection routing:

```properties
forage.spring.rabbitmq.addresses=broker1:5672,broker2:5672,broker3:5672
forage.spring.rabbitmq.automatic.recovery.enabled=true
forage.spring.rabbitmq.network.recovery.interval=5000
```

## Cache Modes

The `CachingConnectionFactory` supports two cache modes:

- **CHANNEL** (default) — caches channels within a single connection. Suitable for most use cases.
- **CONNECTION** — caches connections and channels. Use when you need multiple connections to the broker.

```properties
forage.spring.rabbitmq.cache.mode=CONNECTION
forage.spring.rabbitmq.connection.cache.size=5
forage.spring.rabbitmq.channel.cache.size=25
```
