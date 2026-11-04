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

Property values accept Camel property placeholders, with the same syntax as in routes: environment
variables, system properties, and secrets read from Kubernetes or a vault.

```properties
forage.myDb.jdbc.url=jdbc:postgresql://{{env:DB_HOST:localhost}}:5432/orders
forage.myDb.jdbc.username={{env:DB_USER}}
forage.myDb.jdbc.password={{secret:orders-db/password}}
```

### Environment Variables and System Properties

| Placeholder | Resolves to |
|---|---|
| `{{env:KEY}}` | Environment variable `KEY` |
| `{{env:KEY:default}}` | Environment variable `KEY`, or `default` if not set |
| `{{sys:KEY}}` | System property `KEY` |
| `{{sys:KEY:default}}` | System property `KEY`, or `default` if not set |

Forage resolves these itself, on every runtime, when it loads the properties. If a variable is not set
and the placeholder has no default, the placeholder is left unchanged.

### Secrets

Any other placeholder function is applied by Camel when Forage creates the bean that uses the value,
so the secret never has to be written in a properties file. Add the Camel component that provides the
function and configure it as you would for routes:

| Placeholder | Reads from | Camel component |
|---|---|---|
| `{{secret:name/key}}` | Kubernetes Secret | `camel-kubernetes` |
| `{{configmap:name/key}}` | Kubernetes ConfigMap | `camel-kubernetes` |
| `{{aws:name}}`, `{{aws:name#field}}` | AWS Secrets Manager | `camel-aws-secrets-manager` |
| `{{azure:name}}`, `{{azure:name#field}}` | Azure Key Vault | `camel-azure-key-vault` |
| `{{gcp:name}}`, `{{gcp:name#field}}` | Google Secret Manager | `camel-google-secret-manager` |
| `{{hashicorp:engine:path#field}}` | HashiCorp Vault | `camel-hashicorp-vault` |

CyberArk Conjur (`{{cyberark:...}}`), IBM Secrets Manager (`{{ibm:...}}`), AWS Parameter Store
(`{{aws-parameterstore:...}}`) and any other Camel properties function work the same way. See the
[Camel documentation](https://camel.apache.org/manual/using-propertyplaceholder.html) for the syntax of
each function, including default values.

In a Kubernetes pod, `{{secret:...}}` needs no configuration: Camel uses the pod service account, which
must be allowed to `get` the secret, or reads the secrets mounted in the directory set by
`camel.kubernetes-config.mount-path-secrets`. Vaults are configured with the `camel.vault.*` properties
or their environment variables, as for routes:

```properties
camel.vault.aws.region=eu-west-1
camel.vault.aws.defaultCredentialsProvider=true

forage.ordersDb.jdbc.password={{aws:orders-db#password}}
```

Camel JBang downloads the component of the common functions automatically, as it does for routes. On
Spring Boot, add the component starter (for example `camel-kubernetes-starter`).

### Unresolvable Placeholders

When a function cannot resolve a placeholder (the secret does not exist, the vault cannot be reached,
or the component is not on the classpath), the application fails at startup. The error names the
property, never its value:

```text
UnresolvedPlaceholderException: Cannot resolve the {{secret:...}} placeholder in property
'forage.ordersDb.jdbc.password': the placeholder function returned no value
```

When Camel ignores missing properties (`camel.component.properties.ignore-missing-property=true`, which
Camel JBang sets when exporting a project), the placeholder is left unchanged instead.

Placeholders without a function, such as `{{name}}` or `{{name:default}}`, are always left unchanged:
values such as AI agent prompts may contain them on purpose.

### Runtime Support

| Placeholder | Camel JBang / Camel Main | Spring Boot | Quarkus |
|---|---|---|---|
| `{{env:...}}`, `{{sys:...}}` | Yes | Yes | Yes |
| `{{secret:...}}`, `{{aws:...}}` and other functions | Yes | Yes | No, fails at build and startup |
| `${KEY}` | No | Yes, Spring Environment | Yes, SmallRye Config |

On Spring Boot, the functions are applied through the `CamelContext` bean, so the `camel.vault.*`
properties can come from `application.properties`, YAML or any Spring property source.

On Quarkus, Forage translates its properties into Quarkus configuration before any `CamelContext`
exists, and that configuration is consumed by Quarkus extensions (Agroal for JDBC, Artemis for JMS,
LangChain4j for AI models), not by Camel. Camel cannot resolve the value there, so Forage fails with an
explanation instead of passing the placeholder through. Use a mechanism Quarkus understands:

- Expose the secret as an environment variable (on Kubernetes, `env.valueFrom.secretKeyRef`) and use
  `{{env:NAME}}`.
- Use a configuration expression (`${...}`) backed by a Quarkus configuration source, such as the
  [Kubernetes Config extension](https://quarkus.io/guides/kubernetes-config), which reads Secrets and
  ConfigMaps. Forage passes `${...}` through unchanged.

### Placeholders vs Overrides

Property placeholders and [environment variable overrides](#property-precedence) are two different mechanisms:

- **Overrides** replace the entire property: setting `FORAGE_MYDB_JDBC_URL` overrides the value from files entirely
- **Placeholders** are embedded inside values: `{{env:DB_HOST}}` resolves to the variable's value within a larger string

When both are present, overrides take priority — they are checked before file-based values.

```properties
# Placeholders let you compose values from multiple variables
forage.myDb.jdbc.url=jdbc:postgresql://{{env:DB_HOST:localhost}}:{{env:DB_PORT:5432}}/{{env:DB_NAME:orders}}
```

See the [Runtime Support](runtimes.md) page for `${KEY}` placeholders on Spring Boot and Quarkus.
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
