# Troubleshooting

This guide helps you diagnose and resolve common issues when using Forage.

## Quick Diagnostics

Before diving into specific issues, try these diagnostic steps:

```bash
# Validate properties without running
camel run * --strict

# Enable debug logging
camel run * --logging-level=DEBUG

# Check Forage plugin installation
camel plugin list | grep forage
```

---

## Common Configuration Issues

### Missing Required Properties

**Error:**
```
MissingConfigException: Missing required configuration: forage.myDb.jdbc.url
```

**Causes:**
- Property not set in any configuration source
- Typo in property name
- Wrong file location

**Solutions:**

1. **Check property naming:**
   ```properties
   # Wrong
   forage.myDb.jdbc.ur=jdbc:postgresql://localhost:5432/db
   
   # Correct
   forage.myDb.jdbc.url=jdbc:postgresql://localhost:5432/db
   ```

2. **Verify file location:**
   - Properties files must be in the working directory
   - Or set `FORAGE_CONFIG_DIR` environment variable
   - Or include in classpath

3. **Check environment variable format:**
   ```bash
   # Wrong
   export forage.myDb.jdbc.url=jdbc:postgresql://localhost:5432/db
   
   # Correct
   export FORAGE_MYDB_JDBC_URL=jdbc:postgresql://localhost:5432/db
   ```

### Property Typos and Unknown Properties

**Error:**
```
[UNKNOWN_PROPERTY] Unknown property 'usernam' for factory 'jdbc'. Did you mean 'username'?
```

**Solution:**

Use property validation to catch typos before runtime:

```bash
# Validate and show suggestions
camel run * --strict
```

The validator uses Levenshtein distance to suggest corrections. See the [Property Validation](../guides/camel-jbang.md#property-validation) guide for details.

### Unknown Provider Kind

**Error:**
```
IllegalArgumentException: Unknown JMS kind 'activemq'. Valid options: [artemis, ibm-mq]
```

or:

```
IllegalStateException: No DataSourceProvider found for kind 'postgres'. Available providers: [postgresql, mysql, ...]
```

**Causes:**

- Typo in `jms.kind` or `db.kind` value
- Missing provider dependency on the classpath

**Solutions:**

1. **Check spelling** — use the exact kind name from the error message's valid options list.
2. **Add the provider dependency:**
   ```xml
   <dependency>
       <groupId>io.kaoto.forage</groupId>
       <artifactId>forage-jdbc-postgresql</artifactId>
       <version>{{ forage_version }}</version>
   </dependency>
   ```

### RAG Assembly Failure

**Error:**
```
IllegalStateException: Failed to assemble RAG pipeline: no EmbeddingModelProvider found on the classpath
```

**Causes:**

- Embedding model properties are configured but the embedding provider jar is missing
- Vector store cannot be initialized (e.g., connection refused)

**Solutions:**

1. **Add the embedding provider dependency:**
   ```xml
   <dependency>
       <groupId>io.kaoto.forage</groupId>
       <artifactId>forage-model-embeddings-ollama</artifactId>
       <version>{{ forage_version }}</version>
   </dependency>
   ```
2. Verify the embedding model service is running and reachable.

### Guardrail Creation Failure

**Error:**
```
RuntimeForageException: Failed to create guardrail 'pii-detector': ...
```

**Cause:**

A guardrail was explicitly selected via `forage.agent.guardrails.input` but could not be created (missing dependency, misconfiguration).

**Solution:**

1. Verify the guardrail `@ForageBean` value is correct.
2. Ensure the guardrail dependency is on the classpath.
3. Check the full stack trace for the root cause (missing config, connection failure, etc.).

### Bean Reference Errors

**Error:**
```
No bean found with name 'myDatabase'
```

**Causes:**
- Bean name doesn't match property prefix
- Provider not on classpath
- ServiceLoader registration missing

**Solutions:**

1. **Verify bean name matches prefix:**
   ```properties
   # Bean name is "myDatabase"
   forage.myDatabase.jdbc.url=jdbc:postgresql://localhost:5432/db
   ```
   
   ```yaml
   # Reference must match exactly
   - to:
       uri: sql
       parameters:
         dataSource: "#myDatabase"  # Must match prefix
   ```

2. **Check provider dependency:**
   ```xml
   <!-- Required for PostgreSQL -->
   <dependency>
       <groupId>io.kaoto.forage</groupId>
       <artifactId>forage-jdbc-postgresql</artifactId>
       <version>{{ forage_version }}</version>
   </dependency>
   ```

---

## Runtime-Specific Issues

### Camel JBang

#### Plugin Not Found

**Error:**
```
Unknown command: forage
```

**Solution:**

Install or reinstall the plugin:

=== "Camel LTS ({{ camel_lts_version }})"

    ```bash
    camel plugin add forage --gav io.kaoto.forage:camel-jbang-plugin-forage:{{ forage_version }}
    ```

=== "Camel Latest ({{ camel_latest_version }})"

    ```bash
    camel plugin add forage --gav io.kaoto.forage:camel-jbang-plugin-forage:{{ forage_latest_version }}
    ```

Verify installation:

```bash
camel plugin list | grep forage
```

#### Dependencies Not Resolved

**Error:**
```
ClassNotFoundException: io.kaoto.forage.jdbc.postgresql.PostgresqlJdbc
```

**Cause:**
- No `forage.*` properties in configuration files
- Plugin not detecting properties

**Solution:**

1. Ensure properties file exists with `forage.*` keys
2. Run from directory containing properties files
3. Check plugin is installed: `camel plugin list`

### Spring Boot

#### Beans Not Auto-Configured

**Error:**
```
No qualifying bean of type 'javax.sql.DataSource' available
```

**Causes:**
- Missing starter dependency
- `AutoConfiguration.imports` not found
- Properties not in Spring Environment

**Solutions:**

1. **Add starter dependency:**
   ```xml
   <dependency>
       <groupId>io.kaoto.forage</groupId>
       <artifactId>forage-jdbc-starter</artifactId>
       <version>{{ forage_version }}</version>
   </dependency>
   ```

2. **Check auto-configuration:**
   ```bash
   # Enable debug logging
   java -jar app.jar --debug
   ```
   
   Look for `ForageDataSourceAutoConfiguration` in output.

3. **Verify properties location:**
   - Must be in `application.properties` or `application.yml`
   - Or use `@PropertySource` to load custom files

#### Property Precedence Issues

**Issue:**
Spring Environment properties override Forage ConfigStore.

**Expected behavior:**
1. Environment variables (highest)
2. System properties
3. Spring application.properties
4. Forage properties files (lowest)

**Solution:**

Use Spring's property syntax in `application.properties`:

```properties
# Spring Boot style
forage.myDb.jdbc.url=${DATABASE_URL:jdbc:postgresql://localhost:5432/db}
forage.myDb.jdbc.username=${DB_USER:admin}
```

---

## Debugging Techniques

### Enable Debug Logging

```bash
# Camel JBang
camel run * --logging-level=DEBUG

# Spring Boot
java -jar app.jar --logging.level.io.kaoto.forage=DEBUG

# Quarkus
mvn quarkus:dev -Dquarkus.log.category."io.kaoto.forage".level=DEBUG
```

### Inspect Camel Registry

```java
// List all beans
camelContext.getRegistry().findByType(DataSource.class)
    .forEach((name, bean) -> 
        System.out.println("Bean: " + name + " = " + bean));

// Lookup specific bean
DataSource ds = camelContext.getRegistry()
    .lookupByNameAndType("myDb", DataSource.class);
```

### Validate Properties

```bash
# Strict mode - fail on warnings
camel run * --strict

# Show all warnings
camel run *  # Warnings printed but doesn't fail
```

### Camel JBang Debugging

For Java debugging and standard JBang debugging techniques, see the [Camel JBang documentation](https://camel.apache.org/manual/camel-jbang.html).

---

## Getting Help

If you're still stuck after trying these solutions:

1. **Check the documentation:**
   - [Core Concepts](../concepts/index.md)
   - [Configuration System](../concepts/configuration.md)
   - [Module-specific docs](../modules/index.md)

2. **Search existing issues:**
   - [GitHub Issues](https://github.com/KaotoIO/forage/issues)

3. **Ask for help:**
   - [GitHub Discussions](https://github.com/KaotoIO/forage/discussions)
   - Include error messages, configuration, and Forage version

4. **Report a bug:**
   - [Create an issue](https://github.com/KaotoIO/forage/issues/new)
   - Include minimal reproducible example