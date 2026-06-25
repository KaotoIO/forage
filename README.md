<p align="center">
  <img src="assets/forage-logo-full-transparent.png" alt="Forage Logo" width="400">
</p>

# Forage

A plugin extension for Apache Camel that provides opinionated bean factories for simplified component configuration across various domains including AI, JDBC, and other bean-based components.

## Overview

Forage is a plugin extension that simplifies Apache Camel configuration by providing opinionated bean factories. Instead of manually configuring beans, you choose the appropriate factory and configure it through properties files, environment variables, or system properties. The library offers seamless integration with various components including AI models, JDBC data sources, chat memory providers, and agent factories through a factory-based approach that requires no Java code instantiation.

## Features

- **Opinionated bean factories** - Pre-configured factory classes that eliminate manual bean configuration
- **Configuration-driven** - Configure beans through properties files, environment variables, or system properties
- **Multiple provider support** - Support for various AI models (OpenAI, Google Gemini, Ollama), JDBC databases, and extensible for other providers
- **ServiceLoader-based discovery** - Automatic factory discovery with no manual wiring required
- **Modular architecture** - Pick only the modules you need
- **Zero Java code instantiation** - Everything configurable through properties

## Quick Start

### 1. Add Dependencies

Add the desired modules to your project. For example, to use the default agent factory with OpenAI:

```xml
<!--This component provides support for OpenAI models (GPT-3.5, GPT-4, etc.)-->
<dependency>
    <groupId>io.kaoto.forage</groupId>
    <artifactId>forage-model-open-ai</artifactId>
    <version>1.1-SNAPSHOT</version>
</dependency>
<!--This component provides support for the message window chat memory -->
<dependency>
    <groupId>io.kaoto.forage</groupId>
    <artifactId>forage-memory-message-window</artifactId>
    <version>1.1-SNAPSHOT</version>
</dependency>
<!--This component adds the composable agent implementation (pulls in agent factories transitively) -->
<dependency>
    <groupId>io.kaoto.forage</groupId>
    <artifactId>forage-agent</artifactId>
    <version>1.1-SNAPSHOT</version>
</dependency>
```

### 2. Use in Camel Routes

Simply reference the bean class in your Camel route:

```java
from("direct:start")
    .to("langchain4j-agent:agent?agent=#ollama");
```

The `agent` parameter references a named bean configured via properties. In this example, `#ollama` refers to an Ollama model bean that is automatically discovered and configured through Forage's property-based configuration system.

## Available Modules

### Core Modules

- **forage-core-ai** - Core interfaces and abstractions for AI components
- **forage-core-agent** - Agent interfaces and base classes

### AI Modules

#### Agents
- **forage-agent-factories** - Agent factories for single and multi-agent systems ([Documentation](library/ai/agents/forage-agent-factories/README.md))
- **forage-agent** - Composable agent implementation with optional memory support ([Documentation](library/ai/agents/forage-agent/README.md))

📋 **[Complete Agents Documentation](library/ai/agents/README.md)** - Comprehensive guide to all agent components

#### Models
- **camel-forage-model-open-ai** - OpenAI chat model provider ([Configuration Guide](library/ai/models/chat/forage-model-open-ai/README.md))
- **forage-model-google-gemini** - Google Gemini chat model provider ([Configuration Guide](library/ai/models/chat/forage-model-google-gemini/README.md))
- **forage-model-ollama** - Ollama chat model provider ([Configuration Guide](library/ai/models/chat/forage-model-ollama/README.md))

#### Chat Memory
- **forage-memory-message-window** - Message window chat memory with persistent storage

#### Vector Databases
- **forage-vectordb-default** - Core vector database functionality
- **forage-vectordb-chroma** - Chroma vector database provider
- **forage-vectordb-infinispan** - Infinispan vector database provider
- **forage-vectordb-mariadb** - MariaDB vector database provider
- **forage-vectordb-milvus** - Milvus vector database provider
- **forage-vectordb-neo4j** - Neo4j vector database provider
- **forage-vectordb-pgvector** - Postgres Vector vector database provider
- **forage-vectordb-qdrant** - Qdrant vector database provider
- **forage-vectordb-redis** - Redis vector database provider
- **forage-vectordb-weaviate** - Weaviate vector database prodiver

#### Embeddings
- **embeddings** - Embedding model providers (coming soon)

### JDBC Modules

Forage provides JDBC data source factories that simplify database connectivity with pre-configured, pooled data sources for various database systems.

#### Available JDBC Modules

- **forage-jdbc** - Core pooled JDBC functionality
- **forage-jdbc-postgresql** - PostgreSQL data source provider
- **forage-jdbc-mysql** - MySQL data source provider  
- **forage-jdbc-mariadb** - MariaDB data source provider
- **forage-jdbc-oracle** - Oracle data source provider
- **forage-jdbc-mssql** - Microsoft SQL Server data source provider
- **forage-jdbc-db2** - IBM DB2 data source provider
- **forage-jdbc-h2** - H2 database data source provider
- **forage-jdbc-hsqldb** - HSQLDB data source provider

#### JDBC Configuration Properties

All JDBC modules support the following configuration properties with a flexible precedence hierarchy (environment variables → system properties → configuration files → defaults):

**Database Connection:**
- `forage.jdbc.db.kind` - Database kind (required, e.g., `postgresql`, `mysql`)
- `forage.jdbc.url` - JDBC connection URL (required)
- `forage.jdbc.username` - Database username (required)
- `forage.jdbc.password` - Database password (required)

**Connection Pool Settings:**
- `forage.jdbc.pool.initial.size` - Initial pool size (default: 5)
- `forage.jdbc.pool.min.size` - Minimum pool size (default: 2)
- `forage.jdbc.pool.max.size` - Maximum pool size (default: 20)
- `forage.jdbc.pool.acquisition.timeout.seconds` - Connection acquisition timeout (default: 5)
- `forage.jdbc.pool.validation.timeout.seconds` - Connection validation timeout (default: 3)
- `forage.jdbc.pool.leak.timeout.minutes` - Connection leak detection timeout (default: 10)
- `forage.jdbc.pool.idle.validation.timeout.minutes` - Idle connection validation timeout (default: 3)

**Transaction Settings:**
- `forage.jdbc.transaction.timeout.seconds` - Transaction timeout (default: 30)

#### Quick Start with PostgreSQL

1. **Start PostgreSQL database:**
```bash
camel infra run postgres
```

2. **Add dependencies to your project:**
```xml
<dependency>
    <groupId>io.kaoto.forage</groupId>
    <artifactId>forage-jdbc</artifactId>
    <version>1.1-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>io.kaoto.forage</groupId>
    <artifactId>forage-jdbc-postgresql</artifactId>
    <version>1.1-SNAPSHOT</version>
</dependency>
```

3. **Create a Camel route:**
```java
import org.apache.camel.builder.RouteBuilder;

public class Test extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("timer:java?period=1000")
                .to("sql:select * from acme")
                .log("${body}");
    }
}
```

4. **Run with JBang:**
```bash
camel run Test.java \
  --dep=mvn:io.kaoto.forage:forage-jdbc:1.1-SNAPSHOT \
  --dep=mvn:io.kaoto.forage:forage-jdbc-postgresql:1.1-SNAPSHOT
```

This provides a fully configured, pooled data source out-of-the-box with sensible defaults.

#### Configuration Examples

**Environment Variables:**
```bash
export FORAGE_JDBC_URL="jdbc:postgresql://localhost:5432/mydb"
export FORAGE_JDBC_USERNAME="myuser"
export FORAGE_JDBC_PASSWORD="mypassword"
export FORAGE_JDBC_POOL_MAX_SIZE="50"
```

**System Properties:**
```bash
-Dforage.jdbc.url=jdbc:postgresql://localhost:5432/mydb
-Dforage.jdbc.username=myuser
-Dforage.jdbc.password=mypassword
-Dforage.jdbc.pool.max.size=50
```

**Configuration File (forage-datasource-factory.properties):**
```properties
forage.jdbc.url=jdbc:postgresql://localhost:5432/mydb
forage.jdbc.username=myuser
forage.jdbc.password=mypassword
forage.jdbc.pool.max.size=50
```

## Configuration

Forage uses a flexible configuration system that supports multiple sources with a defined precedence hierarchy:

1. **Environment variables** (highest precedence)
2. **System properties** 
3. **Configuration files** (e.g., `forage-model-*.properties`)
4. **Default values** (where applicable)

### Model Configuration

Each AI model provider has its own configuration requirements and options. For detailed configuration instructions, including environment variables, system properties, and configuration files, please refer to the respective model documentation:

- **OpenAI**: See [OpenAI Configuration Guide](library/ai/models/chat/forage-model-open-ai/README.md)
- **Google Gemini**: See [Google Gemini Configuration Guide](library/ai/models/chat/forage-model-google-gemini/README.md)  
- **Ollama**: See [Ollama Configuration Guide](library/ai/models/chat/forage-model-ollama/README.md)

### Quick Configuration Examples

For immediate setup, here are minimal configuration examples:

#### OpenAI
```bash
export FORAGE_OPENAI_API_KEY="sk-your-api-key-here"
export FORAGE_OPENAI_MODEL_NAME="gpt-4"  # Optional, defaults to gpt-3.5-turbo
```

#### Google Gemini
```bash
export FORAGE_GOOGLE_API_KEY="your-google-api-key"
export FORAGE_GOOGLE_MODEL_NAME="gemini-pro"
```

#### Ollama
```bash
export FORAGE_OLLAMA_BASE_URL="http://localhost:11434"  # Optional, this is the default
export FORAGE_OLLAMA_MODEL_NAME="llama3"                # Optional, this is the default
```

## Architecture

Forage uses a ServiceLoader-based discovery mechanism with opinionated bean factories:

1. **Bean Factories** - Factory classes that create configured beans (DataSourceFactory, AgentFactory, ModelProvider, etc.)
2. **Configuration System** - Properties-based configuration with environment variable and system property support
3. **ServiceLoader Discovery** - Automatic factory discovery without manual wiring
4. **Catalog Integration** - Build-time catalog generation for tooling integration (e.g., Kaoto)

Factories automatically discover and combine components using Java's ServiceLoader mechanism, with all configuration handled through properties rather than Java code.

## Examples

### Basic AI Agent Route

Example for a memory-less agent.

```java
from("timer:ai?period=30000")
    .setBody(constant("Tell me a joke"))
    .to("langchain4j-agent:agent?agent=#ollama")
    .log("AI Response: ${body}");
```

### Custom Configuration

```java
public class MyRoutes extends RouteBuilder {
    @Override
    public void configure() throws Exception {
        from("direct:chat")
            .to("langchain4j-agent:agent?agent=#ollama")
            .log("Chat response: ${body}");
    }
}
```

## Requirements

- Java 17+
- Apache Camel 4.14.0+
- LangChain4j 1.2.0+

## Contributing

This project follows standard Maven conventions. To build:

```bash
mvn clean install
```

### AI-assisted contributions

Contributors using AI coding assistants (such as [Claude Code](https://github.com/anthropics/claude-code) or similar tools) can take advantage of the project rules in the `.oss-ai-helper-rules/` directory and the `CLAUDE.md` file at the repository root.

These files provide AI agents with project-specific context such as build commands, code style restrictions, branching conventions, issue tracker details, and contribution guidelines.
They are maintained as part of the repository so that AI-assisted contributions follow the same standards as manual ones.

The `.oss-ai-helper-rules/` files are designed for use with the [OSS Helper](https://github.com/Open-Harness-Engineering/ai-agents-oss-helper) toolset, which provides ready-made skills for common contribution tasks (fixing issues, creating PRs, running CI checks, and more).

### Integrating Apache Camel Components

If you're developing Apache Camel components and want to integrate them with the Forage library, or if you want to create new providers for AI models, vector databases, or other services, please refer to our comprehensive [Contributing Beans Guide](docs/contributing-beans.md).

This guide is essential reading for:
- **Component developers** who want to add factory-based configuration to their Apache Camel components
- **Library contributors** who want to create new AI model providers, vector database integrations, or other service providers
- **Maintainers** who need to understand the architecture and patterns used throughout the Forage library

## License

This project is licensed under the Apache License 2.0.