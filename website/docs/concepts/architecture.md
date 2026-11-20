# Architecture

Forage is organized in layers: core interfaces define the contracts, library modules provide implementations, and runtime adapters integrate with Camel JBang, Spring Boot, and Quarkus.

## Project Structure

```
forage/
├── core/                        # Interfaces and utilities
│   ├── forage-core-common/      # BeanProvider, Config, ServiceLoader helpers
│   ├── forage-core-ai/          # ModelProvider, ChatMemoryBeanProvider, EmbeddingStoreProvider
│   ├── forage-core-jdbc/        # DataSourceProvider
│   ├── forage-core-jms/         # JMS interfaces
│   └── forage-core-jta/         # Transaction interfaces
│
├── library/                     # Implementations
│   ├── ai/
│   │   ├── agents/              # Agent framework and factories
│   │   ├── chat-memory/         # Memory: message-window, infinispan, redis
│   │   ├── models/chat/         # Models: openai, ollama, gemini, anthropic, ...
│   │   └── vector-dbs/          # Vector DBs: qdrant, milvus, pgvector, ...
│   ├── jdbc/                    # JDBC: postgresql, mysql, h2, ...
│   ├── jms/                     # JMS: artemis, ...
│   └── common/                  # Shared Spring Boot and Quarkus adapters
│
├── tooling/
│   └── camel-jbang-plugin-forage/  # Camel JBang plugin
│
└── integration-tests/           # Citrus-based integration tests
```

## How the Layers Connect

### Core Layer

The core defines provider interfaces that all implementations must follow:

- **`BeanProvider<T>`** — Base interface with a `create(String id)` method
- **`ModelProvider`** — Creates AI chat models (extends `BeanProvider<ChatModel>`)
- **`DataSourceProvider`** — Creates pooled datasources (extends `BeanProvider<DataSource>`)
- **`ChatMemoryBeanProvider`** — Creates conversation memory stores
- **`EmbeddingStoreProvider`** — Creates vector embedding stores

The core also provides the configuration framework (`ConfigEntries`, `AbstractConfig`, `ConfigStore`) that all modules share.

### Library Layer

Each library module implements a core interface for a specific technology. For example:

- `forage-jdbc-postgresql` implements `DataSourceProvider` for PostgreSQL
- `forage-model-ollama` implements `ModelProvider` for Ollama
- `forage-memory-redis` implements `ChatMemoryBeanProvider` for Redis

Modules register themselves via Java `ServiceLoader` — a `META-INF/services` file maps the interface to the implementation class. When you add a module to your classpath, it becomes available automatically.

### Runtime Layer

Runtime adapters bridge Forage with each Camel runtime:

- **Camel JBang** — The `camel-jbang-plugin-forage` plugin handles dependency resolution and property validation
- **Spring Boot** — Auto-configuration classes discover Forage properties and register beans as Spring beans
- **Quarkus** — A config source translates Forage properties to Quarkus configuration (at build time and again at application start); build-time processors register the beans

The same properties files work across all three runtimes — only the dependencies change.

## Module Anatomy

Every Forage module follows the same pattern:

```
forage-<category>-<technology>/
├── src/main/java/
│   ├── <Technology>Provider.java       # Implements BeanProvider
│   ├── <Technology>Config.java         # Reads properties
│   └── <Technology>ConfigEntries.java  # Declares available properties
├── src/main/resources/
│   ├── META-INF/services/              # ServiceLoader registration
│   └── forage-<module>.properties      # Default values
└── pom.xml
```

For example, the PostgreSQL JDBC module:

- **`PostgresqlJdbc`** implements `DataSourceProvider` — knows how to create a PostgreSQL datasource with Agroal connection pooling and optional XA transaction support
- **`DataSourceFactoryConfig`** reads properties like `forage.<name>.jdbc.url`, `forage.<name>.jdbc.username`, pool sizes, and transaction settings
- **`DataSourceFactoryConfigEntries`** declares all available properties with descriptions, defaults, and types
- **`META-INF/services/io.kaoto.forage.core.jdbc.DataSourceProvider`** lists `PostgresqlJdbc` so ServiceLoader can discover it

## Adding Dependencies

You don't need to understand the internal architecture to use Forage. Just add the modules you need:

- Need PostgreSQL? Add `forage-jdbc-postgresql`
- Need Ollama? Add `forage-model-ollama`
- Need Redis memory? Add `forage-memory-redis`

When using `camel run`, dependencies are resolved automatically based on your configuration. When exporting with `camel export`, the correct runtime-specific dependencies are included.
