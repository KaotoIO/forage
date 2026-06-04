# Spring RabbitMQ

Forage creates Spring RabbitMQ `CachingConnectionFactory` beans for AMQP messaging with the `camel-spring-rabbitmq` component.

## Quick Start

```properties
forage.rabbitmq.host=localhost
forage.rabbitmq.port=5672
forage.rabbitmq.username=guest
forage.rabbitmq.password=guest
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
forage.primary.rabbitmq.host=broker1.example.com
forage.primary.rabbitmq.port=5672
forage.primary.rabbitmq.username=admin
forage.primary.rabbitmq.password=secret

forage.backup.rabbitmq.host=broker2.example.com
forage.backup.rabbitmq.port=5672
forage.backup.rabbitmq.username=admin
forage.backup.rabbitmq.password=secret
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
forage.rabbitmq.addresses=broker1:5672,broker2:5672,broker3:5672
forage.rabbitmq.automatic.recovery.enabled=true
forage.rabbitmq.network.recovery.interval=5000
```

## Health and Metrics

Forage automatically enables Spring Boot Actuator health indicators and Micrometer metrics for RabbitMQ when the corresponding dependencies are present.

### Health Indicators

When `spring-boot-starter-actuator` is on the classpath, Forage registers health indicators for all RabbitMQ connection factories:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

The health indicator checks broker connectivity and reports server version:

```bash
curl http://localhost:8080/actuator/health
```

```json
{
  "status": "UP",
  "components": {
    "rabbit": {
      "status": "UP",
      "details": {
        "version": "3.13.0"
      }
    }
  }
}
```

### Metrics

When `micrometer-core` and actuator are on the classpath, Forage automatically configures RabbitMQ client metrics for all connection factories:

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-core</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

Available metrics include:
- `rabbitmq.connections` - Number of open connections
- `rabbitmq.channels` - Number of open channels
- `rabbitmq.consumed` - Messages consumed
- `rabbitmq.published` - Messages published
- `rabbitmq.acknowledged` - Messages acknowledged
- `rabbitmq.rejected` - Messages rejected

Each metric is tagged with the connection factory name:

```bash
curl http://localhost:8080/actuator/metrics/rabbitmq.published
```

```json
{
  "name": "rabbitmq.published",
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 42.0
    }
  ],
  "availableTags": [
    {
      "tag": "name",
      "values": ["rabbit"]
    }
  ]
}
```

## Cache Modes

The `CachingConnectionFactory` supports two cache modes:

- **CHANNEL** (default) — caches channels within a single connection. Suitable for most use cases.
- **CONNECTION** — caches connections and channels. Use when you need multiple connections to the broker.

```properties
forage.rabbitmq.cache.mode=CONNECTION
forage.rabbitmq.connection.cache.size=5
forage.rabbitmq.channel.cache.size=25
```
