# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Forage** is a plugin extension for Apache Camel that provides opinionated bean factories for simplified component configuration. The library eliminates manual Java bean instantiation by providing factory classes configurable through properties files, environment variables, or system properties.

**Technology Stack:**
- Java 17+
- Apache Camel 4.16.0
- LangChain4j 1.7.1 (with beta 1.9.0-beta16 for some features)
- Maven, Spotless (Palantir Java Format), JUnit 5, AssertJ, Testcontainers, Citrus Test Framework

## Build Commands

```bash
# Full build with tests
mvn clean install

# Compile only (includes automatic code formatting)
mvn clean compile

# Apply code formatting manually
mvn spotless:apply

# Check code formatting
mvn spotless:check

# Run all tests
mvn verify

# Run a single test class
mvn test -Dtest=ClassName

# Run a single test method
mvn test -Dtest=ClassName#methodName

# Run integration tests for a specific module
mvn verify -f integration-tests/jdbc

# Run integration tests with specific runtime (plain, quarkus, spring-boot)
export INTEGRATION_TEST_RUNTIME=quarkus
mvn clean verify -f integration-tests/jdbc -Dit.test=JdbcTest

# Skip tests
mvn install -DskipTests
```

## Project Structure

```
forage/
├── core/                           # Core interfaces and utilities
│   ├── forage-core-ai/            # AI interfaces (ModelProvider, ChatMemoryFactory)
│   ├── forage-core-common/        # Config system (ConfigStore, ConfigModule, ConfigEntry)
│   ├── forage-core-vectordb/      # EmbeddingStoreProvider interface
│   ├── forage-core-jdbc/          # DataSourceProvider interface
│   ├── forage-core-jms/           # JMS interfaces
│   ├── forage-core-jta/           # JTA transaction interfaces
│   ├── forage-core-cloud/         # Cloud provider interfaces
│   └── forage-core-vertx/         # Vert.x interfaces
├── library/                        # Implementation modules
│   ├── ai/                        # AI implementations
│   │   ├── agents/                # forage-agent, forage-agent-factories
│   │   ├── chat-memory/           # Memory providers (message-window, infinispan, redis)
│   │   ├── models/chat/           # Model providers (openai, ollama, gemini, anthropic, etc.)
│   │   └── vector-dbs/            # Vector DB providers (qdrant, milvus, pgvector, etc.)
│   ├── jdbc/                      # JDBC data source providers
│   ├── jms/                       # JMS connection factories
│   ├── cloud/                     # Cloud provider implementations
│   └── vertx/                     # Vert.x implementations
├── integration-tests/              # Citrus-based integration tests
├── tooling/                        # Build tooling
│   ├── camel-jbang-plugin-forage/ # Camel JBang plugin
│   └── forage-maven-catalog-plugin/ # Catalog generation plugin
├── forage-catalog/                 # Generated catalog
└── docs/                          # Documentation
```

## Key Architectural Patterns

### 1. ServiceLoader Discovery

Components are discovered via Java ServiceLoader:
- `io.kaoto.forage.core.ai.ModelProvider` - Chat models
- `io.kaoto.forage.core.ai.ChatMemoryBeanProvider` - Memory providers
- `io.kaoto.forage.core.ai.EmbeddingStoreProvider` - Vector databases

### 2. BeanProvider Pattern

All providers extend `BeanProvider<T>`:
```java
public interface BeanProvider<T> {
    default T create() { return create(null); }
    T create(String id);  // id enables named/prefixed configurations
}
```

### 3. Required Annotations

**@ForageBean** - Required on all provider classes:
```java
@ForageBean(value = "provider-name", component = "camel-langchain4j-agent", description = "Description")
public class MyProvider implements ModelProvider { ... }
```

**@ForageFactory** - Required on all factory classes:
```java
@ForageFactory(value = "factory-name", component = "camel-langchain4j-agent",
               description = "Description", factoryType = "Agent")
public class MyFactory implements AgentFactory { ... }
```

### 4. Configuration Two-Class Pattern

Each module requires two configuration classes:

**ConfigEntries class** - Defines configuration modules:
```java
public final class ExampleConfigEntries extends ConfigEntries {
    public static final ConfigModule API_KEY = ConfigModule.of(ExampleConfig.class, "forage.example.api.key");
    private static final Map<ConfigModule, ConfigEntry> CONFIG_MODULES = new ConcurrentHashMap<>();

    static { init(); }
    static void init() { CONFIG_MODULES.put(API_KEY, ConfigEntry.fromModule()); }

    public static Map<ConfigModule, ConfigEntry> entries() { return Collections.unmodifiableMap(CONFIG_MODULES); }
    public static Optional<ConfigModule> find(String prefix, String name) { return find(CONFIG_MODULES, prefix, name); }
    public static void register(String prefix) { /* register prefixed configs */ }
    public static void loadOverrides(String prefix) { load(CONFIG_MODULES, prefix); }
}
```

**Config class** - Implements Config interface:
```java
public class ExampleConfig implements Config {
    private final String prefix;

    public ExampleConfig() { this(null); }
    public ExampleConfig(String prefix) {
        this.prefix = prefix;
        ExampleConfigEntries.register(prefix);  // 1. Register prefixed modules
        ConfigStore.getInstance().load(ExampleConfig.class, this, this::register);  // 2. Load from properties
        ExampleConfigEntries.loadOverrides(prefix);  // 3. Load env/system overrides
    }

    @Override public String name() { return "forage-module-example"; }
    @Override public void register(String name, String value) {
        ExampleConfigEntries.find(prefix, name).ifPresent(m -> ConfigStore.getInstance().set(m, value));
    }

    public String apiKey() {
        return ConfigStore.getInstance().get(ExampleConfigEntries.API_KEY.asNamed(prefix))
            .orElseThrow(() -> new MissingConfigException("Missing API key"));
    }
}
```

**Configuration precedence** (highest to lowest):
1. Environment variables (`FORAGE_EXAMPLE_API_KEY`)
2. System properties (`-Dforage.example.api.key=value`)
3. Properties files (`<module-name>.properties`)

### 5. ServiceLoader Registration

Create `META-INF/services/<interface-name>` files listing implementation classes.

## Naming Conventions

- **Artifacts**: `forage-<category>-<technology>` (e.g., `forage-model-openai`)
- **Packages**: `io.kaoto.forage.<category>.<technology>`
- **Config env vars**: `FORAGE_<TECHNOLOGY>_<PROPERTY>` (e.g., `FORAGE_OPENAI_API_KEY`)
- **Config properties**: `forage.<technology>.<property>` (e.g., `forage.openai.api.key`)

## Integration Tests

Tests use Citrus Test Framework with custom Forage actions. Tests run against three runtimes: plain Camel, Quarkus, and Spring Boot.

```java
@CitrusSupport
@ExtendWith(IntegrationTestSetupExtension.class)
public class MyTest implements ForageIntegrationTest {
    @Test
    void testRoute(ForageTestCaseRunner runner) {
        runner.when(forageRun("process-name", "config.properties", "route.camel.yaml")
            .dumpIntegrationOutput(true));  // Enable logs
    }
}
```

## Development Guidelines

- Code formatting is applied automatically during compile phase via Spotless
- All provider classes must have `@ForageBean` annotation
- All factory classes must have `@ForageFactory` annotation
- Use `MissingConfigException` for required missing configuration
- Properties files named `<module-name>.properties` in resources
