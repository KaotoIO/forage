# Forage

<p align="center">
  <img src="assets/forage-logo.png" alt="Forage Logo" width="400">
</p>

**Opinionated bean factories for Apache Camel.**

Forage eliminates manual Java bean instantiation by providing factory classes configurable through properties files, environment variables, or system properties. Write your Camel routes — Forage handles the wiring.

---

## Why Forage?

Apache Camel components often require manually creating and registering Java beans — data sources, connection factories, AI models, transaction managers. Forage replaces that boilerplate with a configuration-driven approach:

```properties
# "ordersDb" becomes a named bean — use it in routes as #ordersDb
forage.ordersDb.jdbc.db.kind=postgresql
forage.ordersDb.jdbc.url=jdbc:postgresql://localhost:5432/orders
forage.ordersDb.jdbc.username=admin
forage.ordersDb.jdbc.password=secret
```

No Java code. No Spring `@Bean` methods. No Quarkus producers. Just properties. The name you choose (e.g. `ordersDb`) registers a bean in the Camel registry, ready to reference in your routes as `#ordersDb`.

## Features

<div class="grid cards" markdown>

- :material-database: **JDBC** — Auto-configured datasources with connection pooling
- :material-message: **JMS** — Connection factories for ActiveMQ Artemis and more
- :material-robot: **AI Agents** — LangChain4j integration with tool use and memory
- :material-brain: **Chat Models** — OpenAI, Ollama, Gemini, Anthropic, and more
- :material-vector-point: **Vector DBs** — Qdrant, Milvus, PGVector, and more
- :material-swap-horizontal: **Transactions** — XA distributed transactions with Narayana

</div>

## Runtime Support

Forage works with all major Camel runtimes:

| Runtime | Support |
|---|---|
| **Camel JBang** | Run directly with `camel run` (Forage plugin auto-discovers dependencies) |
| **Camel Spring Boot** | Auto-configuration via starters |
| **Camel Quarkus** | Native compilation ready |

## Quick Example

A complete AI agent with tool use in just two files:

=== "Route (YAML)"

    ```yaml
    - route:
        from:
          uri: timer:agent
          parameters:
            repeatCount: 1
          steps:
            - setBody:
                constant: "give details of user 123"
            - to:
                uri: langchain4j-agent:myGraniteAgent
                parameters:
                  agent: "#myGraniteAgent"
            - log: "${body}"
    ```

=== "Configuration"

    ```properties
    # "myGraniteAgent" — pick any name, then reference it in routes as #myGraniteAgent
    forage.myGraniteAgent.agent.model.kind=ollama
    forage.myGraniteAgent.agent.model.name=granite4:3b
    forage.myGraniteAgent.agent.base.url=http://localhost:11434
    ```

## Versions

Forage publishes release streams so you can match your Apache Camel version:

| Stream | Forage | Apache Camel | Description |
|--------|--------|-------------|-------------|
| **LTS** | {{ forage_version }} | {{ camel_lts_version }} | Tracks the current Camel LTS line — recommended for production |
| **Previous LTS** | {{ forage_previous_lts_version }} | {{ camel_previous_lts_version }} | Maintenance releases for the previous Camel LTS line |
| **Latest** | {{ forage_latest_version }} | {{ camel_latest_version }} | Tracks the newest (non-LTS) Camel release — dormant between Camel "latest" releases |

Pick the stream that matches the Camel version you use, then follow the [Getting Started](getting-started/index.md) guide.

## Getting Started

Ready to dive in? Head to the [Getting Started](getting-started/index.md) guide.

Looking for working examples? Check out the [Examples](examples/index.md) gallery.
