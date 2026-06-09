# Getting Started

Get up and running with Forage in minutes.

## Prerequisites

- Java 17 or later
- [Apache Camel {{ camel_version }}+](https://camel.apache.org/) with [Camel JBang](https://camel.apache.org/manual/camel-jbang.html) installed

## Install the Forage Plugin

Forage extends Camel JBang with the `forage` plugin. Install it once:

```bash
camel plugin add forage --gav io.kaoto.forage:camel-jbang-plugin-forage:{{ forage_version }}
```

Verify the installation:

```bash
camel forage --help
```

Once installed, the standard `camel run` and `camel export` commands automatically discover Forage dependencies from your properties files — no `--dep` flags needed. The plugin also adds `camel forage config` and `camel forage datasource` subcommands for configuration management.

## Your First Route

Let's build a simple route that queries a PostgreSQL database.

### 1. Start a database

```bash
camel infra run postgres
```

Create a test table:

```bash
docker exec -i camel-postgres psql -U postgres -c \
  "CREATE TABLE users (id SERIAL PRIMARY KEY, name TEXT); \
   INSERT INTO users (name) VALUES ('Alice'), ('Bob');"
```

### 2. Create the route

Create a file called `route.camel.yaml`:

```yaml
- route:
    from:
      uri: timer:query
      parameters:
        period: "5000"
      steps:
        - to:
            uri: sql
            parameters:
              query: select * from users
              dataSource: "#myDatabase"
        - log:
            message: "Users: ${body}"
```

### 3. Configure the datasource

Create a file called `application.properties`:

```properties
forage.myDatabase.jdbc.db.kind=postgresql
forage.myDatabase.jdbc.url=jdbc:postgresql://localhost:5432/test
forage.myDatabase.jdbc.username=test
forage.myDatabase.jdbc.password=test
```

The `myDatabase` prefix becomes the bean name — Forage registers a pooled datasource as `#myDatabase` in the Camel registry. No Java code, no bean definitions.

### 4. Run it

```bash
camel run *
```

You should see the query results logged every 5 seconds:

```text
Users: [{id=1, name=Alice}, {id=2, name=Bob}]
```

### 5. Export to production

When you're ready to deploy, export to Spring Boot or Quarkus:

=== "Spring Boot"

    ```bash
    camel export --runtime=spring-boot --directory=./my-app
    ```

=== "Quarkus"

    ```bash
    camel export --runtime=quarkus --directory=./my-app
    ```

## What's Next?

- Explore the [Core Concepts](../concepts/index.md) to understand how Forage works
- Browse working [Examples](../examples/index.md) covering JDBC, JMS, transactions, and AI agents
- See all available [Modules](../modules/index.md) for a full list of supported technologies
- Try the latest development features with [Snapshot Builds](snapshot.md)
