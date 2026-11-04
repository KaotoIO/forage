# Forage Catalog

The Forage catalog is a machine-readable metadata file that describes every factory, bean, and configuration property available in Forage. It is the foundation for tooling integration: UI designers like [Kaoto](https://kaoto.io) consume the catalog to generate wizard-like configuration pages, enabling low-code/no-code scenarios that would otherwise require manual Java bean instantiation.

## Why the Catalog Matters

Configuring a Camel integration with Forage beans requires knowing which providers exist, what properties each one accepts, which are required, and how they relate to each other. The catalog captures all of this metadata in a structured format so that tools can:

- **Generate configuration wizards** -- present factories as selectable cards, render form fields from config entries, and populate dropdowns from available beans.
- **Validate properties** -- catch typos, unknown keys, and invalid bean references before runtime (see [Property Validation](camel-jbang.md#property-validation)).
- **Resolve dependencies** -- determine which Maven artifacts to include based on the user's selections.

## Catalog Files

The build produces these files under `forage-catalog/target/generated-catalog/`:

| File | Purpose |
|------|---------|
| `forage-catalog.json` | Factories with their beans, config entries, variants, and conditional beans |
| `forage-configuration-catalog.json` | Flat list of all configuration entries grouped by module |
| `forage-catalog.schema.json` | JSON Schema (2020-12) for the catalog |
| `forage-configuration-catalog.schema.json` | JSON Schema for the configuration catalog |

YAML versions (`forage-catalog.yaml`, `forage-configuration-catalog.yaml`) are also generated.

## Generating the Catalog

```bash
mvn clean compile -f forage-catalog
```

The `forage-maven-catalog-plugin` runs during the `process-classes` phase. It scans all Forage module dependencies for `@ForageFactory` and `@ForageBean` annotations, extracts configuration entries from `ConfigEntries` subclasses, and writes the catalog files.

!!! note
    Adding a new module with the proper annotations extends the catalog -- no manual catalog editing is required -- as long as the module is a dependency of `forage-catalog/pom.xml` and each of its beans shares a component with a factory (see [Completeness Checks](#completeness-checks)).

### Completeness Checks

A bean or factory missing from the catalog is invisible to Kaoto, and `camel forage run/export` never resolves its Maven dependency. The build therefore fails instead of dropping it silently:

- **The plugin** fails when a `@ForageBean` is not listed under any `@ForageFactory`. A bean is listed under every factory that declares one of its `components`. It also fails when two factory classes claim the same variant of a factory type, or when two bean classes have the same name in the same feature of a factory.
- **`CatalogCompletenessTest`** (in `forage-catalog`) scans the `src/main/java` directories of the whole repository and fails when a `@ForageBean` or `@ForageFactory` class is not in `forage-catalog.json`, which covers modules that were never added as dependencies of `forage-catalog`. It also fails when a factory declares components but lists no bean. A factory that creates its bean itself, such as TLS Context, declares no components.

A bean that is deliberately kept out of the catalog, for example because its factory is still being written, is listed with a reason in the plugin configuration in `forage-catalog/pom.xml`:

```xml
<excludedBeans>
    <excludedBean>
        <className>io.kaoto.forage.vectordb.qdrant.QdrantProvider</className>
        <reason>Pending #538: no factory declares camel-langchain4j-embeddings yet</reason>
    </excludedBean>
</excludedBeans>
```

The reason is mandatory. An entry that no longer matches a scanned bean, or whose bean is now listed under a factory, also fails the build, so the list only holds the beans that are actually missing from the catalog.

## Catalog Structure

### Top-Level Object

```json
{
  "version": "2.0",
  "generatedBy": "forage-maven-catalog-plugin",
  "timestamp": 1773133890023,
  "factories": [ ... ]
}
```

The `factories` array is the core of the catalog. Each entry describes one Forage factory and everything needed to configure and use it.

### Factory

A factory represents a high-level capability (e.g., AI Agent, DataSource, JMS Connection). Each factory maps to one or more Camel components.

```json
{
  "name": "Agent",
  "factoryType": "org.apache.camel.component.langchain4j.agent.api.Agent",
  "description": "Creates AI agents with configurable chat models ...",
  "components": ["camel-langchain4j-agent"],
  "autowired": true,
  "propertiesFile": "forage-agent-factory.properties",
  "variants": { ... },
  "configEntries": [ ... ],
  "beansByFeature": [ ... ],
  "conditionalBeans": [ ... ],
  "model": { ... },
  "prefixPattern": "forage.{name}.{suffix}",
  "beanReferences": [ ... ],
  "propertiesSchema": { ... }
}
```

| Field | Description |
|-------|-------------|
| `name` | Display name for the factory |
| `factoryType` | Fully qualified Java type this factory produces |
| `description` | Human-readable summary |
| `components` | Camel component IDs this factory supports |
| `autowired` | If `true`, the factory can self-configure without an explicit bean name |
| `propertiesFile` | Default properties file name |
| `variants` | Platform-specific implementations |
| `configEntries` | Configuration properties for the factory |
| `beansByFeature` | Selectable bean providers grouped by feature |
| `conditionalBeans` | Beans automatically registered when a config flag is enabled |
| `model` | Display metadata shaped like a camel-catalog `model` block -- see [UI Metadata](#ui-metadata) |
| `prefixPattern` | How the property keys of a named instance are built; absent when the factory only creates its default bean |
| `beanReferences` | Where a Camel route references the beans of this factory |
| `propertiesSchema` | JSON Schema (Draft-07) of the factory's own properties, ready for a JSON Schema form renderer |

### Variants

Each factory provides up to three platform variants so that tooling can resolve the correct Maven dependency for the user's runtime:

```json
"variants": {
  "base": {
    "className": "io.kaoto.forage.agent.AgentBeanFactory",
    "gav": "io.kaoto.forage:forage-agent:{{ forage_version }}"
  },
  "springboot": {
    "className": "io.kaoto.forage.springboot.agent.ForageAgentAutoConfiguration",
    "gav": "io.kaoto.forage:forage-agent-starter:{{ forage_version }}"
  },
  "quarkus": {
    "className": "io.kaoto.forage.quarkus.agent.deployment.ForageAgentProcessor",
    "gav": "io.kaoto.forage:forage-quarkus-agent-deployment:{{ forage_version }}"
  }
}
```

A variant may also include `additionalDependencies` -- extra GAVs required by that platform.

### Config Entries

Config entries define the properties a user can set. Each entry carries enough metadata for a UI to render the appropriate form control:

```json
{
  "name": "forage.agent.model.kind",
  "type": "bean-name",
  "description": "The model provider kind (e.g., ollama, openai)",
  "required": true,
  "label": "Model Kind",
  "configTag": "COMMON",
  "selectsFrom": "Chat Model"
}
```

| Field | Description |
|-------|-------------|
| `name` | Property key (e.g., `forage.agent.api.key`) |
| `type` | Data type -- see table below |
| `description` | Help text for the field |
| `required` | Whether the property must be set |
| `defaultValue` | Pre-populated value (optional) |
| `label` | Human-readable label for the form field |
| `configTag` | Grouping hint: `COMMON`, `ADVANCED`, or `SECURITY` |
| `selectsFrom` | For `bean-name` type -- the feature name whose beans populate the dropdown |
| `hidden` | `true` for internal entries (infrastructure overrides) that a UI should not render; omitted otherwise |

**Supported types:**

| `type` | UI Control | Notes |
|--------|-----------|-------|
| `string` | Text input | General-purpose text field |
| `integer` | Number input | Whole numbers |
| `long` | Number input | Whole numbers |
| `double` | Number input | Decimal numbers |
| `boolean` | Toggle / checkbox | True/false flags |
| `password` | Password input | Masked text field for secrets |
| `Duration` | Text input | A duration such as `PT30S` |
| `bean-name` | Dropdown | Options populated from `beansByFeature` matching `selectsFrom` |
| `map` | Key/value list | `name` is a prefix: each pair is written as `name.key=value`, for example `forage.jdbc.properties.sslConnection=true` |

**Config tags** allow a UI to organize fields into expandable sections. Typically `COMMON` fields are shown expanded by default, while `ADVANCED` and `SECURITY` sections are collapsed.

### Beans by Feature

Beans represent the concrete implementations a user can choose from. They are grouped by feature so that a UI can present them as selectable options -- for example, a "Memory" dropdown offering `message-window`, `redis`, and `infinispan`.

```json
"beansByFeature": [
  {
    "feature": "Memory",
    "beans": [
      {
        "name": "infinispan",
        "description": "Distributed storage using Infinispan",
        "className": "io.kaoto.forage.memory.chat.infinispan.InfinispanMemoryBeanProvider",
        "gav": "io.kaoto.forage:forage-memory-infinispan:{{ forage_version }}",
        "propertiesFile": "forage-memory-infinispan.properties",
        "configEntries": [ ... ]
      },
      {
        "name": "redis",
        "description": "Persistent storage using Redis",
        "configEntries": [ ... ]
      },
      {
        "name": "message-window",
        "description": "Simple in-memory message window",
        "configEntries": [ ... ]
      }
    ]
  }
]
```

The link between a factory's `configEntries` and its `beansByFeature` is the **`selectsFrom`** field. When a config entry has `"type": "bean-name"` and `"selectsFrom": "Memory"`, the UI populates its dropdown from the beans in the `"Memory"` feature group. When the user selects a bean (e.g., `redis`), that bean's own `configEntries` are rendered as additional form fields.

Each bean also carries a `gav` field so that tooling can add the correct Maven dependency to the project, and a `propertiesSchema` with the JSON Schema of its own properties (see [UI Metadata](#ui-metadata)).

### Conditional Beans

Conditional beans are automatically registered in the Camel registry when a boolean config entry is enabled. Unlike `beansByFeature` (which are user-selectable alternatives), conditional beans are activated as a group.

```json
"conditionalBeans": [
  {
    "id": "jta-transaction-policies",
    "description": "JTA Transaction Policy beans for Camel transacted routes",
    "configEntry": "forage.jdbc.transaction.enabled",
    "beans": [
      {
        "name": "PROPAGATION_REQUIRED",
        "javaType": "org.apache.camel.spi.TransactedPolicy",
        "description": "Starts a new transaction if none exists"
      },
      { "name": "MANDATORY", "..." : "..." },
      { "name": "REQUIRES_NEW", "..." : "..." }
    ],
    "runtimeDependencies": {
      "quarkus": ["mvn:io.quarkus:quarkus-narayana-jta"]
    }
  }
]
```

A conditional bean can have either a fixed `name` or a `nameFromConfig` field that references another config entry whose value becomes the bean name at runtime:

```json
{
  "nameFromConfig": "forage.jdbc.aggregation.repository.name",
  "javaType": "org.apache.camel.processor.aggregate.jdbc.JdbcAggregationRepository",
  "description": "Transactional JDBC aggregation repository"
}
```

## UI Metadata

The catalog carries pre-computed UI metadata so that a UI renders Forage forms the same way it renders [camel-catalog](https://github.com/KaotoIO/camel-catalog) entries, without a Forage specific adapter. All of it is additive: `configEntries` and the rest of the structure are unchanged.

### Properties Schema

Every factory and every bean has a `propertiesSchema`: a JSON Schema (Draft-07) object that a JSON Schema form renderer, such as Kaoto's `KaotoForm`, renders directly. It follows the camel-catalog conventions:

```json
"propertiesSchema": {
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["forage.jms.kind"],
  "properties": {
    "forage.jms.kind": {
      "title": "JMS Kind",
      "description": "The JMS broker kind/type",
      "type": "string",
      "enum": ["artemis", "ibmmq"],
      "$comment": "group:common"
    },
    "forage.jms.password": {
      "title": "Password",
      "description": "The JMS broker password",
      "type": "string",
      "format": "password",
      "$comment": "group:security"
    },
    "forage.jms.pool.max.connections": {
      "title": "Max Connections",
      "description": "Maximum number of connections in the pool",
      "type": "integer",
      "default": 10,
      "$comment": "group:advanced"
    }
  }
}
```

It is generated from the config entries:

| Config entry | JSON Schema |
|--------------|-------------|
| `type: string` | `"type": "string"` |
| `type: integer`, `int`, `long` | `"type": "integer"` |
| `type: double` | `"type": "number"` |
| `type: boolean` | `"type": "boolean"` |
| `type: password` | `"type": "string", "format": "password"` |
| `type: Duration` | `"type": "string", "format": "duration"` |
| `type: map` | `"type": "object", "additionalProperties": {"type": "string"}` |
| `type: bean-name` + `selectsFrom` | `"type": "string", "enum": [...]` listing the beans of the `selectsFrom` feature |
| `configTag` | `"$comment": "group:common"`, `group:advanced` or `group:security` (lower case, as in camel-catalog) |
| `required: true` | The key is listed in the schema level `required` array |
| `defaultValue` | `"default"`, as a number or boolean for numeric and boolean properties |
| `label` / `description` | `"title"` / `"description"` |
| `hidden: true` | Left out of the schema |

**Property keys are the full keys of the default instance** (`forage.jms.broker.url`): the same names as in `configEntries` and in the documentation. A UI writes the form values of the default instance as they are, and applies the `prefixPattern` for a named instance (see below).

A `map` property holds free-form keys: the form value `{"sslConnection": "true"}` of `forage.jdbc.properties` is written as `forage.jdbc.properties.sslConnection=true`.

**Factory and bean schemas do not overlap.** The factory `propertiesSchema` holds the factory's own properties; the properties a bean brings are only in that bean's `propertiesSchema`. A UI renders the factory form, then the form of each bean the user selects (for example the `forage.jms.artemis.*` properties once `artemis` is chosen as `forage.jms.kind`). Together they cover every non-hidden config entry of the factory exactly once.

### Hidden Entries

Some config entries are infrastructure overrides that a configuration wizard should not show, for example the fully qualified class names `forage.provider.*` and the `forage.multi.agent.*` settings of the `MultiAgentFactory`. They are marked with `@InternalConfig` in their `ConfigEntries` class, flagged `"hidden": true` in `configEntries` and left out of `propertiesSchema`. They remain valid, documented properties.

### Named Instances

Factories that create a bean per named configuration prefix carry a `prefixPattern`:

```json
"prefixPattern": "forage.{name}.{suffix}"
```

`{suffix}` is a `propertiesSchema` key without its leading `forage.`, and `{name}` is the instance name, which is also the bean name. For the instance `orders`, `forage.jdbc.url` becomes `forage.orders.jdbc.url`. The name may contain dots, but none of its segments may be the module segment of the key (`jdbc` in `forage.jdbc.url`). The matching environment variable is the key in upper case with dots replaced by underscores (`FORAGE_ORDERS_JDBC_URL`).

Factories that only register their default bean, declared with `@ForageFactory(namedInstances = false)`, have no `prefixPattern`: a UI should not offer to add instances for them.

### Bean References

`beanReferences` tells a UI where a Camel route uses a bean of the factory, so that it can generate an example route without per-factory templates. Each reference names an option of a Camel catalog entry and how its value is written, `{beanRef}` standing for the bean name:

```json
"beanReferences": [
  {"catalogKind": "component", "name": "sql", "option": "dataSource", "optionKind": "parameter", "valueTemplate": "#{beanRef}"},
  {"catalogKind": "component", "name": "jdbc", "option": "dataSourceName", "optionKind": "path", "valueTemplate": "{beanRef}"}
]
```

| Field | Description |
|-------|-------------|
| `catalogKind` | Camel catalog kind: `component`, or `processor` for an EIP such as `policy` |
| `name` | Component scheme or EIP name |
| `option` | Option of that Camel catalog entry holding the reference |
| `optionKind` | Option kind as in the Camel catalog: `parameter` or `path` for endpoints, `attribute` for EIPs |
| `valueTemplate` | Option value, `{beanRef}` being replaced by the bean name |

For the bean `orders`, the references above give:

```yaml
- route:
    from:
      uri: direct:orders
      steps:
        - to:
            uri: sql
            parameters:
              query: select * from orders
              dataSource: "#orders"
        - to:
            uri: jdbc
            parameters:
              dataSourceName: orders
```

The references are derived from the Camel catalog of the Camel version Forage is built with: for every component in the factory `components`, the endpoint parameters whose Java type is the factory bean type (for example `dataSource` on `sql`, `connectionFactory` on `jms`, `agent` on `langchain4j-agent`). Where the bean name goes in the endpoint path (`jdbc`, `influxdb`, `cxf`) or the bean is used by an EIP (`policy` for security policies), the factory declares the reference with `@ForageFactory(beanReferences = @BeanReference(...))`, which replaces the derived one for that component.

### Model

The `model` block holds display metadata in the shape of a camel-catalog `model` block, for catalog tiles:

```json
"model": {
  "kind": "forage-factory",
  "name": "data-source",
  "title": "DataSource",
  "description": "Creates JDBC DataSource connections for database operations with transaction and repository support",
  "label": "database"
}
```

`name` is a stable identifier derived from the factory type (it does not change with the display name), `title` is the factory `name`, and `label` collects the Camel catalog labels of the factory components.

## How the Catalog Powers UI Wizards

The catalog is designed to provide everything a UI tool needs to generate configuration wizards without Forage-specific code. Here is the flow as implemented in [Kaoto](https://kaoto.io):

```mermaid
flowchart LR
    A[Catalog JSON] --> B[Factory Gallery]
    B --> C[Configuration Form]
    C --> D[Bean Selection]
    D --> E[Properties Output]
    D --> F[Example Route]
    D --> G[Maven Dependencies]
```

### Step 1: Factory Selection

The UI reads the `factories` array and renders each factory as a selectable card showing its `name`, `description`, and supported `components`. The user picks the capability they want to configure.

### Step 2: Configuration Form

Once a factory is selected, the UI renders its `propertiesSchema` with a JSON Schema form renderer:

- `type` and `format` determine the control (text input, number, password, dropdown, toggle).
- Fields are grouped into expandable sections by their `$comment` group (`group:common`, `group:advanced`, `group:security`).
- `required` properties are validated before submission.
- `default` pre-populates fields.
- `title` and `description` provide field labels and help text.

`bean-name` properties are dropdowns whose `enum` lists the beans of the `selectsFrom` feature. When the user selects a bean, its own `propertiesSchema` is rendered as an additional form.

### Step 3: Multi-Instance Support

The wizard supports creating multiple instances of the same factory (e.g., two DataSources for different databases) when the factory has a `prefixPattern`. Each instance gets a name, and all property keys are prefixed accordingly:

```properties
# Instance "orders"
forage.orders.jdbc.url=jdbc:postgresql://localhost/orders
forage.orders.jdbc.username=admin

# Instance "inventory"
forage.inventory.jdbc.url=jdbc:postgresql://localhost/inventory
forage.inventory.jdbc.username=admin
```

### Step 4: Output Generation

The wizard produces:

- A **properties file** with all configured values.
- An **example Camel route** (YAML) that uses the configured factory, built from `beanReferences`.
- The correct **Maven dependencies** resolved from variants and bean `gav` coordinates.

## What Drives the Catalog

The catalog is entirely derived from annotations and configuration classes in the source code:

| Source | Catalog Output |
|--------|---------------|
| `@ForageFactory` on a class | Factory entry with `name`, `description`, `components`, `factoryType` |
| `@ForageFactory(variant = ...)` | Populates the `variants` map (base, springboot, quarkus) |
| `@ForageFactory(conditionalBeans = ...)` | Populates the `conditionalBeans` array |
| `@ForageFactory(configClass = ...)` | Links the factory to its config entries |
| `@ForageBean` on a class | Bean entry under the `beansByFeature` of every factory that shares one of its `components` |
| `@ForageBean(feature = "Memory")` | Groups the bean under the `"Memory"` feature |
| `ConfigModule.of(...)` | Config entry with `name`, `type`, `label`, `description`, `defaultValue`, `configTag` |
| `ConfigModule.ofBeanName(...)` | Config entry with `type: "bean-name"` and `selectsFrom` linking to a feature |
| `@InternalConfig` on a `ConfigModule` field | Config entry with `hidden: true`, left out of `propertiesSchema` |
| `@ForageFactory(namedInstances = false)` | No `prefixPattern` on the factory |
| `@ForageFactory(beanReferences = ...)` | Declared `beanReferences`, replacing the derived ones for the same component |
| The Camel catalog | Derived `beanReferences` and the `model` `label` |

## Configuration Catalog

The build also produces `forage-configuration-catalog.json`, a flat list of all config entries grouped by module:

```json
{
  "version": "1.0",
  "generatedBy": "forage-maven-catalog-plugin",
  "timestamp": 1773133890023,
  "modules": [
    {
      "artifactId": "forage-model-open-ai-common",
      "groupId": "io.kaoto.forage",
      "propertiesFile": "forage-model-openai.properties",
      "configEntries": [ ... ]
    }
  ]
}
```

This flat view is used by the [property validator](camel-jbang.md#property-validation) and by tooling that needs a complete list of all known properties without the factory/bean hierarchy.

## Programmatic Access

The catalog model classes are available as a Maven dependency:

```xml
<dependency>
    <groupId>io.kaoto.forage</groupId>
    <artifactId>forage-catalog-model</artifactId>
    <version>{{ forage_version }}</version>
</dependency>
```

Key classes in `io.kaoto.forage.catalog.model`:

| Class | Description |
|-------|-------------|
| `ForageCatalog` | Root object with `factories` list |
| `ForageFactory` | Factory with variants, config entries, beans, conditional beans |
| `ForageBean` | Bean provider with its own config entries and GAV |
| `ConfigEntry` | Single configuration property with type, label, tag |
| `PropertiesSchema` / `PropertySchema` | JSON Schema (Draft-07) of the properties of a factory or bean |
| `FactoryModel` | Display metadata of a factory |
| `BeanReference` | Where a Camel route references a factory bean |
| `FeatureBeans` | Groups beans under a named feature |
| `FactoryVariants` / `FactoryVariant` | Platform-specific implementation info |
| `ConditionalBeanGroup` / `ConditionalBeanInfo` | Beans conditionally registered via a config flag |
| `ForageConfigurationCatalog` / `ConfigurationModule` | Flat configuration catalog model |

These classes can be deserialized from the catalog JSON using Jackson.
