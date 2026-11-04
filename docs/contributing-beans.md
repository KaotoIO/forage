# Contributing Beans Guide

This guide explains how to prepare an Apache Camel component for usage with the Forage plugin extension and how to create new bean providers and factories.

## Overview

Integrating a component with Forage involves two main steps:

1. **Modify the Apache Camel component** to support a factory-based approach
2. **Create the corresponding Forage bean factory and provider** that can be configured through properties

## Part 1: Adjusting the Apache Camel Component

### 1.1 Add Factory Parameter

First, add a factory parameter to your Camel component's configuration. This allows users to specify a factory that will create the required beans.

**Example from `camel-langchain4j-assistant`:**

```java
@UriParam(description = "The agent factory to use for creating agents if no Agent is provided")
@Metadata(autowired = true)
private AgentFactory agentFactory;
```

### 1.2 Initialize Factory in Producer

In your producer's `doStart()` method, initialize the factory and provide it with the Camel context:

```java
@Override
protected void doStart() throws Exception {
    super.doStart();

    agentFactory = endpoint.getConfiguration().getAgentFactory();
    if (agentFactory != null) {
        agentFactory.setCamelContext(this.endpoint.getCamelContext());
    }
}
```

### 1.3 Use Factory in Processing Logic

When processing exchanges, use the factory to create beans if available, falling back to direct configuration if not:

```java
public void process(Exchange exchange) throws Exception {
    // ... other processing logic
    
    Agent agent;
    if (agentFactory != null) {
        agent = agentFactory.createAgent();
    } else {
        agent = endpoint.getConfiguration().getAgent();
    }
    
    // ... continue processing with the agent
}
```

## Part 2: Creating Forage Bean Factories and Providers

### 2.1 Creating a New Component Category

If you're creating an entirely new category of components (e.g., vector databases), you need to create both core and common modules:

#### Core Module Structure

Create a core component that contains the foundational interfaces:

**Example: `core/forage-vector-db`**

```
core/forage-vector-db/
├── src/main/java/io/kaoto/forage/core/vector/db/
│   ├── VectorDBFactory.java          # Factory interface
│   └── VectorDBProvider.java         # Provider interface
└── pom.xml
```

**VectorDBFactory.java:**
```java
package io.kaoto.forage.core.vector.db;

import org.apache.camel.CamelContext;

public interface VectorDBFactory {
    void setCamelContext(CamelContext camelContext);
    VectorDatabase createVectorDB();
}
```

**VectorDBProvider.java:**
```java
package io.kaoto.forage.core.vector.db;

import io.kaoto.forage.core.common.BeanProvider;

public interface VectorDBProvider extends BeanProvider<VectorDatabase> {
    // Inherits create() and create(String id) methods from BeanProvider
}
```

#### Common Module Structure

Create a common component with default implementations:

**Example: `library/vector-dbs/forage-default-vector-db-factory`**

```
library/vector-dbs/forage-default-vector-db-factory/
├── src/main/java/io/kaoto/forage/vector/db/
│   └── DefaultVectorDBFactory.java   # Default factory implementation
└── pom.xml
```

**DefaultVectorDBFactory.java:**
```java
package io.kaoto.forage.vector.db;

import io.kaoto.forage.core.vector.db.VectorDBFactory;
import io.kaoto.forage.core.vector.db.VectorDBProvider;
import java.util.ServiceLoader;

public class DefaultVectorDBFactory implements VectorDBFactory {
    private CamelContext camelContext;
    
    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }
    
    @Override
    public VectorDatabase createVectorDB() {
        ServiceLoader<VectorDBProvider> providers = ServiceLoader.load(VectorDBProvider.class);
        VectorDBProvider provider = providers.findFirst()
            .orElseThrow(() -> new IllegalStateException("No VectorDBProvider found"));
        return provider.newVectorDB();
    }
}
```

### 2.2 Creating Provider Implementations

Create specific provider implementations for each technology:

**Example: `library/vector-dbs/forage-milvus`**

```
library/vector-dbs/forage-milvus/
├── src/main/java/io/kaoto/forage/vector/db/milvus/
│   ├── MilvusProvider.java            # Provider implementation
│   └── MilvusConfig.java              # Configuration class
├── src/main/resources/META-INF/services/
│   └── io.kaoto.forage.core.vector.db.VectorDBProvider
└── pom.xml
```

#### Provider Implementation

**MilvusProvider.java:**
```java
package io.kaoto.forage.vector.db.milvus;

import io.kaoto.forage.core.vector.db.VectorDBProvider;
import io.kaoto.forage.core.annotations.ForageBean;

@ForageBean(value = "milvus", components = {"camel-langchain4j-embeddings"}, description = "Milvus vector database provider")
public class MilvusProvider implements VectorDBProvider {
    
    @Override
    public VectorDatabase create(String id) {
        MilvusConfig config = new MilvusConfig(id);
        
        return MilvusClient.builder()
            .host(config.host())
            .port(config.port())
            .username(config.username())
            .password(config.password())
            .build();
    }
}
```

#### Configuration Classes

Configuration in Forage uses a two-class pattern supporting named/prefixed configurations:

**MilvusConfigEntries.java:**
```java
package io.kaoto.forage.vector.db.milvus;

import io.kaoto.forage.core.util.config.ConfigEntries;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigTag;

public final class MilvusConfigEntries extends ConfigEntries {
    public static final ConfigModule HOST = ConfigModule.of(
            MilvusConfig.class, "forage.milvus.host",
            "Milvus server host", "Host", "localhost", "string", false, ConfigTag.COMMON);
    public static final ConfigModule PORT = ConfigModule.of(
            MilvusConfig.class, "forage.milvus.port",
            "Milvus server port", "Port", "19530", "integer", false, ConfigTag.COMMON);
    public static final ConfigModule USERNAME = ConfigModule.of(
            MilvusConfig.class, "forage.milvus.username",
            "Username for authentication", "Username", null, "string", false, ConfigTag.COMMON);
    public static final ConfigModule PASSWORD = ConfigModule.of(
            MilvusConfig.class, "forage.milvus.password",
            "Password for authentication", "Password", null, "string", true, ConfigTag.COMMON);

    static {
        initModules(MilvusConfigEntries.class, HOST, PORT, USERNAME, PASSWORD);
    }
}
```

**MilvusConfig.java:**
```java
package io.kaoto.forage.vector.db.milvus;

import io.kaoto.forage.core.util.config.AbstractConfig;

import static io.kaoto.forage.vector.db.milvus.MilvusConfigEntries.HOST;
import static io.kaoto.forage.vector.db.milvus.MilvusConfigEntries.PORT;
import static io.kaoto.forage.vector.db.milvus.MilvusConfigEntries.USERNAME;
import static io.kaoto.forage.vector.db.milvus.MilvusConfigEntries.PASSWORD;

public class MilvusConfig extends AbstractConfig {

    public MilvusConfig() {
        this(null);
    }

    public MilvusConfig(String prefix) {
        super(prefix, MilvusConfigEntries.class);
    }

    @Override
    public String name() {
        return "forage-vector-db-milvus";
    }

    public String host() {
        return get(HOST).orElse(HOST.defaultValue());
    }

    public Integer port() {
        return get(PORT).map(Integer::parseInt).orElse(Integer.parseInt(PORT.defaultValue()));
    }

    public String username() {
        return get(USERNAME).orElse(null);
    }

    public String password() {
        return get(PASSWORD).orElse(null);
    }
}
```

#### ServiceLoader Registration

Create the ServiceLoader registration file:

**src/main/resources/META-INF/services/io.kaoto.forage.core.vector.db.VectorDBProvider:**
```
io.kaoto.forage.vector.db.milvus.MilvusProvider
```

### 2.3 Maven Configuration

**Provider module pom.xml:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>io.kaoto.forage</groupId>
        <artifactId>vector-dbs</artifactId>
        <version>1.1-SNAPSHOT</version>
    </parent>

    <artifactId>forage-vector-db-milvus</artifactId>
    <name>Forage :: Library :: Vector DBs :: Milvus</name>

    <dependencies>
        <dependency>
            <groupId>io.kaoto.forage</groupId>
            <artifactId>forage-core-vector-db</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>io.kaoto.forage</groupId>
            <artifactId>forage-core-common</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>io.milvus</groupId>
            <artifactId>milvus-sdk-java</artifactId>
            <version>2.3.4</version>
        </dependency>
    </dependencies>
</project>
```

## Part 3: Using the New Component

### 3.1 In Camel Routes

Reference the factory class in your Camel route:

```java
from("direct:vector-search")
    .to("vector-db:search?connectionFactory=#class:io.kaoto.forage.vector.db.DefaultVectorDBFactory");
```

### 3.2 Classpath Dependencies

Make sure to include the specific provider on your classpath:

```xml
<dependency>
    <groupId>io.kaoto.forage</groupId>
    <artifactId>forage-vector-db-milvus</artifactId>
    <version>1.1-SNAPSHOT</version>
</dependency>
```

### 3.3 Configuration

#### Default Configuration
Configure using environment variables:

```bash
export FORAGE_MILVUS_HOST="milvus.example.com"
export FORAGE_MILVUS_PORT="19530"
export FORAGE_MILVUS_USERNAME="admin"
export FORAGE_MILVUS_PASSWORD="password123"
```

Or using system properties:

```bash
-Dforage.milvus.host=milvus.example.com
-Dforage.milvus.port=19530
-Dforage.milvus.username=admin
-Dforage.milvus.password=password123
```

Or using configuration files (`forage-vector-db-milvus.properties`):

```properties
forage.milvus.host=milvus.example.com
forage.milvus.port=19530
forage.milvus.username=admin
forage.milvus.password=password123
```

#### Named/Prefixed Configuration
For multi-instance setups using named configurations:

```bash
# Environment variables (prefix becomes uppercase)
export FORAGE_MILVUS_HOST="default.milvus.com"           # Default config
export forage.vectorstore.milvus.host="vs.milvus.com"   # "vectorstore" config
export forage.embeddings.milvus.host="emb.milvus.com"   # "embeddings" config

# System properties
-Dforage.milvus.host=default.milvus.com                  # Default config
-Dforage.vectorstore.milvus.host=vs.milvus.com          # "vectorstore" config
-Dforage.embeddings.milvus.host=emb.milvus.com          # "embeddings" config
```

In your provider code:
```java
@ForageBean(value = "milvus-multi", components = {"camel-langchain4j-embeddings"}, description = "Multi-instance Milvus vector database provider")
public class MultiInstanceMilvusProvider implements VectorDBProvider {
    @Override
    public VectorDatabase create(String id) {
        // Use named configuration with the provided id
        MilvusConfig config = new MilvusConfig(id);
        return MilvusClient.builder()
            .host(config.host())
            .port(config.port())
            .build();
    }
}
```

## Best Practices

### 1. ForageBean Annotation Requirements

**MANDATORY**: All provider classes must be annotated with `@ForageBean`:

```java
@ForageBean(value = "unique-name", components = {"supported-component"}, description = "What it does")
public class YourProvider implements ProviderInterface {
    // Implementation
}
```

**Annotation Parameters:**
- `value`: Unique identifier for the bean (e.g., "milvus", "azure-openai", "message-window")
- `components`: Supported Camel components (String array). The catalog lists the bean under every `@ForageFactory` that declares one of these components; the catalog build fails if no factory does, unless the bean is listed in `<excludedBeans>` in `forage-catalog/pom.xml` with a reason
- `description`: Human-readable description of what the provider does
- `feature`: Feature group (e.g., "Chat Model", "Memory") — optional
- `configClass`: Config class for properties file association — optional

**Required Import:**
```java
import io.kaoto.forage.core.annotations.ForageBean;
```

### 2. ForageFactory Annotation Requirements

**MANDATORY**: All factory classes must be annotated with `@ForageFactory`:

```java
@ForageFactory(
    value = "unique-factory-name",
    components = {"supported-component"},
    description = "What the factory creates",
    type = FactoryType.AGENT)
public class YourFactory implements FactoryInterface {
    // Implementation
}
```

The annotation parameters:
- `value`: Unique identifier for the factory (e.g., "default-agent", "multi-agent", "redis-memory")
- `components`: Supported Camel components (String array)
- `description`: Human-readable description of what the factory creates
- `type`: Type of objects this factory creates (enum `FactoryType`, e.g., `FactoryType.AGENT`)

**Required Import:**
```java
import io.kaoto.forage.core.annotations.ForageFactory;
```

### 3. Configuration and Provider Guidelines
- Always follow the Forage two-class configuration pattern with `Config` and `ConfigEntries` classes
- Create a `*ConfigEntries` class extending `ConfigEntries` with static fields and maps
- Support both default and named/prefixed configurations in the main `Config` class
- Use meaningful environment variable names with a consistent prefix (e.g., `FORAGE_MILVUS_*`)
- Use dot-notation for ConfigModule names (e.g., `forage.milvus.host`)
- Provide sensible defaults where appropriate
- Document all configuration parameters comprehensively including named configuration examples

**BeanProvider Pattern:**
- All provider interfaces must extend `BeanProvider<T>` for consistency
- Implement the `create(String id)` method to support named configurations
- Use the provided `id` parameter to create named configurations: `new Config(id)`
- The `create()` method is provided automatically and calls `create(null)` for default configuration

### 3. ServiceLoader Registration
- Don't forget to create the ServiceLoader resource file
- Use the full interface name as the filename
- List all implementations in the file

### 4. Error Handling
- Provide clear error messages when configuration is missing
- Use `MissingConfigException` for required parameters
- Validate configuration values where appropriate

### 5. Testing
- Create unit tests for your provider and configuration classes
- Test the ServiceLoader discovery mechanism
- Verify that configuration sources work correctly

### 6. Documentation
- Add comprehensive Javadoc to all public classes and methods
- Include usage examples in class-level documentation
- Document configuration precedence clearly

## Example Directory Structure

Here's how your final module structure should look:

```
forage/
├── core/
│   └── forage-core-vector-db/           # Core interfaces
├── library/
│   └── vector-dbs/
│       ├── forage-default-vector-db-factory/  # Common factory
│       ├── forage-vector-db-milvus/            # Milvus implementation
│       ├── forage-vector-db-pinecone/          # Pinecone implementation
│       └── forage-vector-db-weaviate/          # Weaviate implementation
└── docs/
    └── contributing-beans.md           # This guide
```

This structure allows for easy extension with new vector database providers while maintaining a consistent API and configuration approach across all implementations.