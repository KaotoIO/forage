# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Forage** is a plugin extension for Apache Camel that provides opinionated bean factories for simplified component configuration. The library eliminates manual Java bean instantiation by providing factory classes configurable through properties files, environment variables, or system properties.

**Technology Stack:**
- Java 17+
- Apache Camel 4.x
- LangChain4j 1.x
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
│   ├── forage-core-common/        # Config system (ConfigStore, ConfigModule, ConfigEntry, ConfigEntries, AbstractConfig)
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
@ForageBean(value = "provider-name", components = {"camel-langchain4j-agent"}, description = "Description")
public class MyProvider implements ModelProvider { ... }
```

**@ForageFactory** - Required on all factory classes:
```java
@ForageFactory(value = "factory-name", components = {"camel-langchain4j-agent"},
               description = "Description", type = FactoryType.AGENT)
public class MyFactory implements AgentFactory { ... }
```

### 4. Configuration Two-Class Pattern

Each module requires two configuration classes:

**ConfigEntries class** - Defines configuration modules using the central registry in the `ConfigEntries` base class. Subclasses contain only field declarations and a `static { initModules(...) }` block — no methods:
```java
public final class ExampleConfigEntries extends ConfigEntries {
    public static final ConfigModule API_KEY = ConfigModule.of(ExampleConfig.class, "forage.example.api.key");
    public static final ConfigModule MODEL_NAME = ConfigModule.of(ExampleConfig.class, "forage.example.model.name",
            "Model name", "Model Name", "default-model", "string", true, ConfigTag.COMMON);

    static {
        initModules(ExampleConfigEntries.class, API_KEY, MODEL_NAME);
    }
}
```

Callers use `ConfigEntries` base class methods directly: `ConfigEntries.entriesOf(ExampleConfigEntries.class)`, `ConfigEntries.registerPrefix(ExampleConfigEntries.class, prefix)`, `ConfigEntries.find(ConfigEntries.getModules(ExampleConfigEntries.class), prefix, name)`, `ConfigEntries.loadOverridesFor(ExampleConfigEntries.class, prefix)`.

**Config class** - Extends `AbstractConfig`, which handles constructor boilerplate (prefix registration, properties loading, overrides) and provides `get(ConfigModule)` / `getRequired(ConfigModule, String)` helpers:
```java
public class ExampleConfig extends AbstractConfig {

    public ExampleConfig() { this(null); }
    public ExampleConfig(String prefix) {
        super(prefix, ExampleConfigEntries.class);
    }

    @Override public String name() { return "forage-module-example"; }

    public String apiKey() {
        return getRequired(API_KEY, "Missing API key");
    }
    public String modelName() {
        return get(MODEL_NAME).orElse(MODEL_NAME.defaultValue());
    }
}
```

**Key base class infrastructure:**
- `ConfigEntries.initModules(Class, ConfigModule...)` — registers modules in a central registry (replaces per-class `CONFIG_MODULES` map and `init()` method)
- `ConfigEntries.entriesOf/getModules/registerPrefix/loadOverridesFor/find` — public base class helpers called directly by callers (no subclass delegation)
- `AbstractConfig` — handles the 3-step constructor pattern (register prefix → load properties → load overrides), provides `get()`, `getRequired()`, and auto-implements `register(String, String)`
- `AbstractConfig.ensureInitialized()` — forces ConfigEntries subclass static initialization when only a `Class` literal is passed

**Configuration precedence** (highest to lowest):
1. Environment variables (`FORAGE_EXAMPLE_API_KEY`)
2. System properties (`-Dforage.example.api.key=value`)
3. Properties files (`<module-name>.properties`)

### 5. ServiceLoader Registration

Create `META-INF/services/<interface-name>` files listing implementation classes.

## Naming Conventions

- **Artifacts**: `forage-<category>-<technology>` (e.g., `forage-model-open-ai`)
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

## Adding New Modules

See **[docs/adding-modules.md](docs/adding-modules.md)** for the complete guide on adding new Forage modules with support for plain Camel, Spring Boot, and Quarkus runtimes. The guide covers:
- Configuration two-class pattern (`ConfigEntries` + `AbstractConfig`)
- Provider implementation with `@ForageBean`
- `ForageModuleDescriptor` for runtime adapters
- Spring Boot auto-configuration and bean registration
- Quarkus `ConfigSourceFactory` and deployment processors
- Integration testing with Citrus

## Development Guidelines

- Code formatting is applied automatically during compile phase via Spotless
- All provider classes must have `@ForageBean` annotation
- All factory classes must have `@ForageFactory` annotation
- Config classes must extend `AbstractConfig` (not implement `Config` directly)
- ConfigEntries classes must use `initModules()` in their static block — no delegator methods (callers use `ConfigEntries` base class methods directly)
- Use `getRequired(MODULE, "error message")` for required config, `get(MODULE)` for optional config
- Use `MissingConfigException` for required missing configuration (or `getRequired()` which wraps it)
- Properties files named `<module-name>.properties` in resources
- **Special cases:** `FlipRoutePolicyConfig` and `ScheduleRoutePolicyConfig` extend `AbstractConfig` and follow the standard configuration pattern. Their ConfigEntries classes (`FlipRoutePolicyConfigEntries`, `ScheduleRoutePolicyConfigEntries`) use a dynamic ConfigModule pattern with factory methods (e.g., `pairedRoute(prefix)`) instead of static fields + `initModules()`, because property names are route-ID-dependent.

## Active Technologies
- Java 17+ + Apache Camel 4.16+, camel-api (RoutePolicyFactory, RoutePolicy) (001-route-policies)
- N/A (stateless policy configuration) (001-route-policies)
- Java 17+ + Apache Camel 4.16+ (camel-api: RoutePolicyFactory, RoutePolicy, RoutePolicySupport) (001-route-policies)

## Recent Changes
- 001-route-policies: Added Java 17+ + Apache Camel 4.16+, camel-api (RoutePolicyFactory, RoutePolicy)
