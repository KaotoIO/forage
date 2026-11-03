# Configuration

Forage modules are configured through properties. You can set them in properties files, environment variables, or system properties — and they can be mixed and overridden freely.

## Property Format

All Forage properties follow the pattern:

```text
forage.<beanName>.<module>.<parameter>=<value>
```

For example:

```properties
forage.myDb.jdbc.url=jdbc:postgresql://localhost:5432/orders
forage.myAgent.agent.model.kind=ollama
forage.myBroker.jms.kind=artemis
```

The `<beanName>` is the name you choose — it becomes the bean name in the Camel registry.

## Property Precedence

When the same property is set in multiple places, the highest-priority source wins:

| Priority | Source | Format | Example |
|---|---|---|---|
| **1 (highest)** | Environment variables | `FORAGE_MYDB_JDBC_URL` | Uppercase, underscores |
| **2** | System properties | `forage.myDb.jdbc.url` | Dot notation, via `-D` flag |
| **3 (lowest)** | Properties files | `forage.myDb.jdbc.url` | Dot notation, in `.properties` files |

This makes it easy to override configuration per environment without changing files:

```bash
# Override the database URL for production
export FORAGE_MYDB_JDBC_URL=jdbc:postgresql://prod-db:5432/orders

# Everything else comes from the properties file
camel run *
```

### Environment Variable Naming

Forage converts property names to environment variable names by:

1. Replacing dots (`.`) with underscores (`_`)
2. Converting to uppercase

| Property | Environment Variable |
|---|---|
| `forage.myDb.jdbc.url` | `FORAGE_MYDB_JDBC_URL` |
| `forage.myAgent.agent.base.url` | `FORAGE_MYAGENT_AGENT_BASE_URL` |
| `forage.myBroker.jms.port` | `FORAGE_MYBROKER_JMS_PORT` |

!!! note "JDBC driver properties"
    [Driver properties](../modules/jdbc.md#driver-properties) (`forage.[<name>.]jdbc.properties.*`)
    are the exception: they are not read from environment variables, because the conversion above
    loses the case that driver property names need. Use a [placeholder](#property-placeholders) in
    the value instead.

{% raw %}
## Property Placeholders

You can reference environment variables and system properties directly in property values using Camel-style placeholders:

```properties
forage.myDb.jdbc.url={{env:DATABASE_URL}}
forage.myDb.jdbc.username={{env:DB_USER}}
forage.myDb.jdbc.password={{env:DB_PASSWORD}}
```

### Syntax

| Placeholder | Resolves to |
|---|---|
| `{{env:KEY}}` | Environment variable `KEY` |
| `{{env:KEY:default}}` | Environment variable `KEY`, or `default` if not set |
| `{{sys:KEY}}` | System property `KEY` |
| `{{sys:KEY:default}}` | System property `KEY`, or `default` if not set |

Placeholders are resolved at property-loading time, before values are used by Forage modules. If a variable is not set and no default is provided, the placeholder is left unchanged and a warning is logged.

### Placeholders vs Overrides

Property placeholders and [environment variable overrides](#property-precedence) are two different mechanisms:

- **Overrides** replace the entire property: setting `FORAGE_MYDB_JDBC_URL` overrides the value from files entirely
- **Placeholders** are embedded inside values: `{{env:DB_HOST}}` resolves to the variable's value within a larger string

When both are present, overrides take priority — they are checked before file-based values.

```properties
# Placeholders let you compose values from multiple variables
forage.myDb.jdbc.url=jdbc:postgresql://{{env:DB_HOST:localhost}}:{{env:DB_PORT:5432}}/{{env:DB_NAME:orders}}
```

### Runtime-Specific Alternatives

Spring Boot and Quarkus have their own placeholder syntax (`${KEY}`). Both syntaxes work in Forage property files — see the [Runtime Support](runtimes.md) page for details.
{% endraw %}

!!! note "Bean-name properties"
    Properties that select a provider (e.g., `db.kind`, `jms.kind`) should use literal values, not placeholders. The Camel JBang plugin reads these at scan time to auto-resolve dependencies, and placeholders cannot be resolved at that stage.

## Properties Files

Forage loads properties from files matching the module name. For example, the JDBC module looks for:

- `forage-datasource-factory.properties`
- `application.properties`

Files are searched in this order:

1. Directory specified by `FORAGE_CONFIG_DIR` environment variable
2. Current working directory
3. Classpath

### Organizing Properties

You can put all configuration in a single `application.properties` file:

```properties
# Database
forage.ordersDb.jdbc.db.kind=postgresql
forage.ordersDb.jdbc.url=jdbc:postgresql://localhost:5432/orders
forage.ordersDb.jdbc.username=admin
forage.ordersDb.jdbc.password=secret

# Message broker
forage.myBroker.jms.kind=artemis
forage.myBroker.jms.url=tcp://localhost:61616

# AI agent
forage.myAgent.agent.model.kind=ollama
forage.myAgent.agent.model.name=granite4:3b
forage.myAgent.agent.base.url=http://localhost:11434
```

Or split them into separate files for clarity:

```text
forage-datasource-factory.properties   # JDBC configuration
forage-connectionfactory.properties    # JMS configuration
forage-agent-factory.properties        # AI agent configuration
forage-security-tls.properties         # TLS/SSL configuration
```

## Required vs Optional Properties

Each module has required properties (the connection must know _where_ to connect) and optional properties (with sensible defaults).

Required properties throw a clear error at startup if missing:

```text
MissingConfigException: Missing required configuration: forage.myDb.jdbc.url
```

Optional properties fall back to defaults. For example, the Ollama model provider defaults to `http://localhost:11434` if no base URL is specified.

## Multiple Instances

Create multiple beans of the same type by using different names:

```properties
# Two PostgreSQL databases
forage.ordersDb.jdbc.db.kind=postgresql
forage.ordersDb.jdbc.url=jdbc:postgresql://db1:5432/orders

forage.analyticsDb.jdbc.db.kind=postgresql
forage.analyticsDb.jdbc.url=jdbc:postgresql://db2:5432/analytics

# Two Artemis brokers
forage.primaryBroker.jms.kind=artemis
forage.primaryBroker.jms.url=tcp://broker1:61616

forage.backupBroker.jms.kind=artemis
forage.backupBroker.jms.url=tcp://broker2:61617
```

Each name registers a separate bean. Use them in routes by name:

```yaml
- to:
    uri: sql
    parameters:
      query: select * from orders
      dataSource: "#ordersDb"
- to:
    uri: primaryBroker:queue:events
```

The same pattern works for TLS profiles:

```properties
# Internal mTLS
forage.internal.tls.keystore.path=/certs/internal.p12
forage.internal.tls.keystore.password=changeit
forage.internal.tls.keystore.type=PKCS12

# External trust-only
forage.external.tls.truststore.path=/certs/external-ca.jks
forage.external.tls.truststore.password=trustme
```
