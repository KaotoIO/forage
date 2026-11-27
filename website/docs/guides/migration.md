# Migration Guide

This guide covers both version-to-version upgrade notes and migrating from framework-specific configuration to Forage.

---

## Upgrading to 1.6.3

### Changes

#### Quarkus export writes JDBC and JMS configuration as Quarkus keys

`camel export --runtime=quarkus` now adds `quarkus.*` keys for plain JDBC datasources (kinds with a Quarkus driver in the catalog: PostgreSQL, MySQL, MariaDB, H2) and the default Artemis connection factory, and leaves out their Forage artifacts. The `forage.*` keys stay. Each key is written as `${FORAGE_<KEY>:${forage.<key>:<value>}}`, so `FORAGE_*`, then `-Dforage.*`, then `forage.*` keys in `application.properties` (also a mounted `config/application.properties`) still override it, in Forage's order. `quarkus-pooled-jms` is added only for `forage.jms.pool.enabled=true`, as before.

Keys without effect on Quarkus (catalog `quarkusIgnored`, today `forage.jms.pool.connection.timeout.millis`, which no runtime applies) do not block the translation; the export lists them in a note.

The other modules (agent, CXF, InfluxDB, Spring RabbitMQ, security policies, TLS) stay `forage.*` with their Forage artifacts: no Quarkus extension builds the same bean from `quarkus.*` keys alone. For each one configured, the export prints a note with the module's reason (catalog `quarkusUnsupported`).

The catalog (version `2.1`) and the configuration catalog (version `1.1`) gain new fields. Forage catalog readers before this release (1.7.x and older) reject unknown fields, so update tools that read the catalog with the Forage reader library together with the catalog; readers from this release on ignore unknown fields.

A module stays `forage.*`, with a note, when any of its keys has no Quarkus equivalent (`transaction.*`, `ssl.*`, repositories, named or non-Artemis JMS, ...), a value contains `{` or `}` or starts with `:`, two files set a key differently, it uses `%profile` keys or `application-<profile>.properties`, its module file (or a bean file such as `forage-jms-artemis.properties`) is in `~/.forage` / `FORAGE_CONFIG_DIR`, or the export reuses earlier run data. Set `FORAGE_QUARKUS_TRANSLATION_SKIP=true` (or `-Dforage.quarkus.translation.skip=true`) to keep everything on `forage.*`.

Differences for translated modules: only the keys the export wrote can be overridden at runtime. A key not in the exported configuration (e.g. `FORAGE_DS1_JDBC_TRANSACTION_ENABLED=true`, a new datasource name or `properties.*` driver key) set only at runtime by `FORAGE_*` / `-Dforage.*` is ignored. `forage-*.properties` files are not read at runtime: a module file mounted in the working directory, `FORAGE_CONFIG_DIR` or `~/.forage` after the export is ignored (the export adds a small `ForageConfigFileWarning` bean that logs a warning at startup naming such a file); set the keys as `FORAGE_*` or in `config/application.properties` instead, or export with the off switch.

---

## Upgrading to 1.5.0

### Breaking Changes

#### Guardrails require explicit opt-in

Guardrails no longer activate by classpath presence alone. You must explicitly list the guardrails to enable:

```properties
# AgentCreator (forage-agent)
forage.myAgent.agent.guardrails.input=pii-detector,keyword-filter
forage.myAgent.agent.guardrails.output=pii-redactor

# MultiAgentFactory (forage-agent-factories)
forage.guardrails.input=pii-detector
forage.guardrails.output=pii-redactor
```

Values are comma-separated `@ForageBean` names. The old `forage.guardrails.input.classes` property (FQCN-based) is replaced.

Selected guardrails that fail to create now **throw at startup** instead of being silently skipped (fail closed).

#### Default model names updated

If you relied on the default model name without setting `model.name` explicitly, the defaults have changed:

| Provider | Old Default | New Default |
|---|---|---|
| OpenAI | `gpt-3.5-turbo` | `gpt-4o-mini` |
| Anthropic | `claude-3-haiku-20240307` | `claude-haiku-4-5-20251001` |
| Ollama (chat) | `llama3` | `llama3.2` |
| Ollama (embeddings) | `llama3` | `nomic-embed-text` |

#### Removed configuration entries

The following properties have been removed. Setting them now produces an `UNKNOWN_PROPERTY` warning in strict mode:

**Hugging Face** — 6 unsupported entries removed: `top.k`, `top.p`, `do.sample`, `repetition.penalty`, `max.retries`, `log.requests.and.responses`

**WatsonX AI** — 5 unsupported entries removed: `top.k`, `min.new.tokens`, `max.retries`, `repetition.penalty`, `timeout`

**Agent config** — 5 dead memory entries removed: `agent.memory.redis.host`, `agent.memory.redis.port`, `agent.memory.infinispan.host`, `agent.memory.infinispan.port`, `agent.memory.infinispan.cache.name` (standalone memory modules have their own config)

### Behavioral Changes

#### Misconfiguration now fails fast

Previously, several misconfiguration scenarios were silently ignored. They now fail at startup with actionable error messages:

- **Unknown JMS kind** (e.g., `forage.jms.kind=typo`): previously fell back to Artemis silently; now throws `IllegalArgumentException` listing valid kinds.
- **Unknown JDBC db.kind**: previously resulted in a `NullPointerException`; now throws `IllegalStateException` listing available providers.
- **RAG assembly failure**: previously logged at TRACE and returned null (agent ran without RAG); now throws at startup when embedding config is present but the pipeline can't be assembled.
- **Agent creation failure**: previously logged at WARN and swallowed; now rethrows at startup.

#### Azure OpenAI and Chroma logging defaults

Request/response logging for Azure OpenAI and Chroma vector database was **enabled by default** due to an inverted null-check. This has been corrected — logging is now **off by default**, matching all other providers. If you relied on the implicit logging, set `log.requests.and.responses=true` explicitly.

#### JMS per-broker transaction scoping

Each named JMS broker prefix now gets its own `JmsComponent` registered under the prefix name, with self-contained transaction semantics. See the [JMS module docs](../modules/jms.md#per-broker-components) for details.

---

## Migrating to Forage

This section helps you migrate from framework-specific configuration to Forage's unified approach.

### Why Migrate to Forage?

Forage solves a fundamental problem: **configuration fragmentation across runtimes**. Spring Boot and Quarkus use completely different property naming conventions for the same functionality, making it difficult to:

- Maintain consistent configuration across environments
- Migrate between runtimes
- Support multiple databases or message brokers without custom code

**Forage provides a single configuration format that works identically across all runtimes.**

---

### The Configuration Fragmentation Problem

### JDBC Example: Three Different Ways

The same PostgreSQL datasource requires different configuration in each runtime:

=== "Spring Boot"

    ```properties
    spring.datasource.url=jdbc:postgresql://localhost:5432/orders
    spring.datasource.username=admin
    spring.datasource.password=secret
    spring.datasource.driver-class-name=org.postgresql.Driver
    spring.datasource.hikari.maximum-pool-size=20
    spring.datasource.hikari.minimum-idle=5
    spring.datasource.hikari.connection-timeout=30000
    ```

=== "Quarkus"

    ```properties
    quarkus.datasource.db-kind=postgresql
    quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/orders
    quarkus.datasource.username=admin
    quarkus.datasource.password=secret
    quarkus.datasource.jdbc.max-size=20
    quarkus.datasource.jdbc.min-size=5
    quarkus.datasource.jdbc.acquisition-timeout=30
    ```

=== "Forage (All Runtimes)"

    ```properties
    forage.ordersDb.jdbc.db.kind=postgresql
    forage.ordersDb.jdbc.url=jdbc:postgresql://localhost:5432/orders
    forage.ordersDb.jdbc.username=admin
    forage.ordersDb.jdbc.password=secret
    forage.ordersDb.jdbc.pool.max.size=20
    forage.ordersDb.jdbc.pool.min.size=5
    forage.ordersDb.jdbc.pool.acquisition.timeout.seconds=30
    ```

**Key differences:**
- Property prefixes: `spring.datasource.*` vs `quarkus.datasource.*` vs `forage.<name>.jdbc.*`
- Pool configuration: `hikari.*` vs `jdbc.*` vs `pool.*`
- Timeout units: milliseconds vs duration strings vs seconds
- Pool implementation: HikariCP vs Agroal vs Agroal (unified)

---

### JDBC Migration

### From Spring Boot

#### Before: Spring Boot Configuration

**application.properties:**
```properties
# Primary datasource
spring.datasource.url=jdbc:postgresql://localhost:5432/orders
spring.datasource.username=admin
spring.datasource.password=secret
spring.datasource.driver-class-name=org.postgresql.Driver

# HikariCP connection pool
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.max-lifetime=1800000

# Multiple datasources require custom configuration
orders.datasource.url=jdbc:postgresql://localhost:5432/orders
orders.datasource.username=orders_user
orders.datasource.password=orders_pass

analytics.datasource.url=jdbc:mysql://localhost:3306/analytics
analytics.datasource.username=analytics_user
analytics.datasource.password=analytics_pass
```

**DataSourceConfig.java (required for multiple datasources):**
```java
@Configuration
public class DataSourceConfig {
    
    @Bean
    @ConfigurationProperties("orders.datasource")
    public DataSourceProperties ordersDataSourceProperties() {
        return new DataSourceProperties();
    }
    
    @Bean
    @ConfigurationProperties("orders.datasource.hikari")
    public DataSource ordersDataSource() {
        return ordersDataSourceProperties()
            .initializeDataSourceBuilder()
            .type(HikariDataSource.class)
            .build();
    }
    
    @Bean
    @ConfigurationProperties("analytics.datasource")
    public DataSourceProperties analyticsDataSourceProperties() {
        return new DataSourceProperties();
    }
    
    @Bean
    @ConfigurationProperties("analytics.datasource.hikari")
    public DataSource analyticsDataSource() {
        return analyticsDataSourceProperties()
            .initializeDataSourceBuilder()
            .type(HikariDataSource.class)
            .build();
    }
}
```

#### After: Forage Configuration

**application.properties (all runtimes):**
```properties
# Orders database - no Java code needed
forage.ordersDb.jdbc.db.kind=postgresql
forage.ordersDb.jdbc.url=jdbc:postgresql://localhost:5432/orders
forage.ordersDb.jdbc.username=orders_user
forage.ordersDb.jdbc.password=orders_pass
forage.ordersDb.jdbc.pool.max.size=20
forage.ordersDb.jdbc.pool.min.size=5
forage.ordersDb.jdbc.pool.acquisition.timeout.seconds=30
forage.ordersDb.jdbc.pool.idle.validation.timeout.minutes=10
forage.ordersDb.jdbc.pool.leak.timeout.minutes=30

# Analytics database - different database type, same pattern
forage.analyticsDb.jdbc.db.kind=mysql
forage.analyticsDb.jdbc.url=jdbc:mysql://localhost:3306/analytics
forage.analyticsDb.jdbc.username=analytics_user
forage.analyticsDb.jdbc.password=analytics_pass
forage.analyticsDb.jdbc.pool.max.size=15
```

**No Java code required** - Delete `DataSourceConfig.java`

#### Migration Steps

1. **Add Forage dependencies:**

   ```xml
   <!-- Remove Spring Boot JDBC starter -->
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-jdbc</artifactId>
   </dependency>
   
   <!-- Add Forage JDBC starter -->
   <dependency>
       <groupId>io.kaoto.forage</groupId>
       <artifactId>forage-jdbc-starter</artifactId>
       <version>{{ forage_version }}</version>
   </dependency>
   <dependency>
       <groupId>io.kaoto.forage</groupId>
       <artifactId>forage-jdbc-postgresql</artifactId>
       <version>{{ forage_version }}</version>
   </dependency>
   <dependency>
       <groupId>io.kaoto.forage</groupId>
       <artifactId>forage-jdbc-mysql</artifactId>
       <version>{{ forage_version }}</version>
   </dependency>
   ```

2. **Convert properties:**

   | Spring Boot | Forage |
   |-------------|--------|
   | `spring.datasource.url` | `forage.<name>.jdbc.url` |
   | `spring.datasource.username` | `forage.<name>.jdbc.username` |
   | `spring.datasource.password` | `forage.<name>.jdbc.password` |
   | `spring.datasource.hikari.maximum-pool-size` | `forage.<name>.jdbc.pool.max.size` |
   | `spring.datasource.hikari.minimum-idle` | `forage.<name>.jdbc.pool.min.size` |
   | `spring.datasource.hikari.connection-timeout` (ms) | `forage.<name>.jdbc.pool.acquisition.timeout.seconds` |

3. **Delete configuration classes:**

   Remove all `@Configuration` classes that create `DataSource` beans.

4. **Update route references:**

   ```java
   // Before
   @Autowired
   @Qualifier("ordersDataSource")
   private DataSource dataSource;
   
   from("timer:query")
       .to("sql:select * from orders?dataSource=#ordersDataSource");
   ```
   
   ```yaml
   # After
   - route:
       from:
         uri: timer:query
         steps:
           - to:
               uri: sql
               parameters:
                 query: select * from orders
                 dataSource: "#ordersDb"
   ```

5. **Test:**

   ```bash
   mvn spring-boot:run
   ```

### From Quarkus

#### Before: Quarkus Configuration

**application.properties:**
```properties
# Primary datasource
quarkus.datasource.db-kind=postgresql
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/orders
quarkus.datasource.username=admin
quarkus.datasource.password=secret

# Agroal connection pool
quarkus.datasource.jdbc.max-size=20
quarkus.datasource.jdbc.min-size=5
quarkus.datasource.jdbc.acquisition-timeout=30
quarkus.datasource.jdbc.idle-removal-interval=10M
quarkus.datasource.jdbc.max-lifetime=30M

# Multiple datasources use named configuration
quarkus.datasource.orders.db-kind=postgresql
quarkus.datasource.orders.jdbc.url=jdbc:postgresql://localhost:5432/orders
quarkus.datasource.orders.username=orders_user
quarkus.datasource.orders.password=orders_pass
quarkus.datasource.orders.jdbc.max-size=20

quarkus.datasource.analytics.db-kind=mysql
quarkus.datasource.analytics.jdbc.url=jdbc:mysql://localhost:3306/analytics
quarkus.datasource.analytics.username=analytics_user
quarkus.datasource.analytics.password=analytics_pass
quarkus.datasource.analytics.jdbc.max-size=15
```

#### After: Forage Configuration

**application.properties (all runtimes):**
```properties
# Orders database
forage.ordersDb.jdbc.db.kind=postgresql
forage.ordersDb.jdbc.url=jdbc:postgresql://localhost:5432/orders
forage.ordersDb.jdbc.username=orders_user
forage.ordersDb.jdbc.password=orders_pass
forage.ordersDb.jdbc.pool.max.size=20
forage.ordersDb.jdbc.pool.min.size=5
forage.ordersDb.jdbc.pool.acquisition.timeout.seconds=30
forage.ordersDb.jdbc.pool.idle.validation.timeout.minutes=10
forage.ordersDb.jdbc.pool.leak.timeout.minutes=30

# Analytics database
forage.analyticsDb.jdbc.db.kind=mysql
forage.analyticsDb.jdbc.url=jdbc:mysql://localhost:3306/analytics
forage.analyticsDb.jdbc.username=analytics_user
forage.analyticsDb.jdbc.password=analytics_pass
forage.analyticsDb.jdbc.pool.max.size=15
```

#### Migration Steps

1. **Add Forage dependencies:**

   ```xml
   <!-- Remove Quarkus JDBC extension -->
   <dependency>
       <groupId>io.quarkus</groupId>
       <artifactId>quarkus-jdbc-postgresql</artifactId>
   </dependency>
   
   <!-- Add Forage Quarkus extension -->
   <dependency>
       <groupId>io.kaoto.forage</groupId>
       <artifactId>forage-quarkus-jdbc-deployment</artifactId>
       <version>{{ forage_version }}</version>
   </dependency>
   <dependency>
       <groupId>io.kaoto.forage</groupId>
       <artifactId>forage-jdbc-postgresql</artifactId>
       <version>{{ forage_version }}</version>
   </dependency>
   <dependency>
       <groupId>io.kaoto.forage</groupId>
       <artifactId>forage-jdbc-mysql</artifactId>
       <version>{{ forage_version }}</version>
   </dependency>
   ```

2. **Convert properties:**

   | Quarkus | Forage |
   |---------|--------|
   | `quarkus.datasource.db-kind` | `forage.<name>.jdbc.db.kind` |
   | `quarkus.datasource.jdbc.url` | `forage.<name>.jdbc.url` |
   | `quarkus.datasource.username` | `forage.<name>.jdbc.username` |
   | `quarkus.datasource.password` | `forage.<name>.jdbc.password` |
   | `quarkus.datasource.jdbc.max-size` | `forage.<name>.jdbc.pool.max.size` |
   | `quarkus.datasource.jdbc.min-size` | `forage.<name>.jdbc.pool.min.size` |
   | `quarkus.datasource.jdbc.acquisition-timeout` (duration) | `forage.<name>.jdbc.pool.acquisition.timeout.seconds` |

3. **Update route references:**

   ```yaml
   # Before
   - route:
       from:
         uri: timer:query
         steps:
           - to:
               uri: sql
               parameters:
                 query: select * from orders
                 dataSource: "#orders"  # Quarkus named datasource
   
   # After
   - route:
       from:
         uri: timer:query
         steps:
           - to:
               uri: sql
               parameters:
                 query: select * from orders
                 dataSource: "#ordersDb"  # Forage bean name
   ```

4. **Test:**

   ```bash
   mvn quarkus:dev
   ```

5. **Build native image:**

   ```bash
   mvn clean package -Pnative
   ```

---

### JMS Migration

### From Spring Boot

#### Before: Spring Boot Configuration

**application.properties:**
```properties
# ActiveMQ Artemis
spring.artemis.mode=native
spring.artemis.host=localhost
spring.artemis.port=61616
spring.artemis.user=admin
spring.artemis.password=secret

# Connection pool
spring.artemis.pool.enabled=true
spring.artemis.pool.max-connections=10
spring.artemis.pool.idle-timeout=30000

# Multiple brokers require custom configuration
primary.artemis.host=broker1.example.com
primary.artemis.port=61616

backup.artemis.host=broker2.example.com
backup.artemis.port=61617
```

**JmsConfig.java (required for multiple brokers):**
```java
@Configuration
public class JmsConfig {
    
    @Bean
    @ConfigurationProperties("primary.artemis")
    public ActiveMQConnectionFactory primaryConnectionFactory() {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory();
        factory.setBrokerURL("tcp://broker1.example.com:61616");
        factory.setUser("admin");
        factory.setPassword("secret");
        return factory;
    }
    
    @Bean
    public JmsTemplate primaryJmsTemplate() {
        return new JmsTemplate(primaryConnectionFactory());
    }
    
    @Bean
    @ConfigurationProperties("backup.artemis")
    public ActiveMQConnectionFactory backupConnectionFactory() {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory();
        factory.setBrokerURL("tcp://broker2.example.com:61617");
        factory.setUser("admin");
        factory.setPassword("secret");
        return factory;
    }
    
    @Bean
    public JmsTemplate backupJmsTemplate() {
        return new JmsTemplate(backupConnectionFactory());
    }
}
```

#### After: Forage Configuration

**application.properties (all runtimes):**
```properties
# Primary broker - no Java code needed
forage.primaryBroker.jms.kind=artemis
forage.primaryBroker.jms.broker.url=tcp://broker1.example.com:61616
forage.primaryBroker.jms.username=admin
forage.primaryBroker.jms.password=secret
forage.primaryBroker.jms.pool.max.connections=10
forage.primaryBroker.jms.pool.idle.timeout.millis=30000

# Backup broker - same pattern
forage.backupBroker.jms.kind=artemis
forage.backupBroker.jms.broker.url=tcp://broker2.example.com:61617
forage.backupBroker.jms.username=admin
forage.backupBroker.jms.password=secret
forage.backupBroker.jms.pool.max.connections=10
```

**No Java code required** - Delete `JmsConfig.java`

#### Migration Steps

1. **Add Forage dependencies:**

   ```xml
   <!-- Remove Spring Boot JMS starter -->
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-artemis</artifactId>
   </dependency>
   
   <!-- Add Forage JMS starter -->
   <dependency>
       <groupId>io.kaoto.forage</groupId>
       <artifactId>forage-jms-starter</artifactId>
       <version>{{ forage_version }}</version>
   </dependency>
   <dependency>
       <groupId>io.kaoto.forage</groupId>
       <artifactId>forage-jms-artemis</artifactId>
       <version>{{ forage_version }}</version>
   </dependency>
   ```

2. **Convert properties:**

   | Spring Boot | Forage |
   |-------------|--------|
   | `spring.artemis.host` + `port` | `forage.<name>.jms.broker.url` (tcp://host:port) |
   | `spring.artemis.user` | `forage.<name>.jms.username` |
   | `spring.artemis.password` | `forage.<name>.jms.password` |
   | `spring.artemis.pool.max-connections` | `forage.<name>.jms.pool.max.connections` |
   | `spring.artemis.pool.idle-timeout` (ms) | `forage.<name>.jms.pool.idle.timeout.millis` |

3. **Delete configuration classes:**

   Remove all `@Configuration` classes that create `ConnectionFactory` beans.

4. **Update route references:**

   ```yaml
   # Before
   - route:
       from:
         uri: jms:queue:orders
         parameters:
           connectionFactory: "#primaryConnectionFactory"
   
   # After
   - route:
       from:
         uri: jms:queue:orders
         parameters:
           connectionFactory: "#primaryBroker"
   ```

### From Quarkus

#### Before: Quarkus Configuration

**application.properties:**
```properties
# ActiveMQ Artemis
quarkus.artemis.url=tcp://localhost:61616
quarkus.artemis.username=admin
quarkus.artemis.password=secret

# Connection pool
quarkus.pooled-jms.max-connections=10
quarkus.pooled-jms.idle-timeout=30s

# Multiple brokers use named configuration
quarkus.artemis.primary.url=tcp://broker1.example.com:61616
quarkus.artemis.primary.username=admin
quarkus.artemis.primary.password=secret

quarkus.artemis.backup.url=tcp://broker2.example.com:61617
quarkus.artemis.backup.username=admin
quarkus.artemis.backup.password=secret
```

#### After: Forage Configuration

**application.properties (all runtimes):**
```properties
# Primary broker
forage.primaryBroker.jms.kind=artemis
forage.primaryBroker.jms.broker.url=tcp://broker1.example.com:61616
forage.primaryBroker.jms.username=admin
forage.primaryBroker.jms.password=secret
forage.primaryBroker.jms.pool.max.connections=10
forage.primaryBroker.jms.pool.idle.timeout.millis=30000

# Backup broker
forage.backupBroker.jms.kind=artemis
forage.backupBroker.jms.broker.url=tcp://broker2.example.com:61617
forage.backupBroker.jms.username=admin
forage.backupBroker.jms.password=secret
forage.backupBroker.jms.pool.max.connections=10
```

#### Migration Steps

1. **Add Forage dependencies:**

   ```xml
   <!-- Remove Quarkus Artemis extension -->
   <dependency>
       <groupId>io.quarkus</groupId>
       <artifactId>quarkus-artemis-jms</artifactId>
   </dependency>
   
   <!-- Add Forage Quarkus extension -->
   <dependency>
       <groupId>io.kaoto.forage</groupId>
       <artifactId>forage-quarkus-jms-deployment</artifactId>
       <version>{{ forage_version }}</version>
   </dependency>
   <dependency>
       <groupId>io.kaoto.forage</groupId>
       <artifactId>forage-jms-artemis</artifactId>
       <version>{{ forage_version }}</version>
   </dependency>
   ```

2. **Convert properties:**

   | Quarkus | Forage |
   |---------|--------|
   | `quarkus.artemis.url` | `forage.<name>.jms.broker.url` |
   | `quarkus.artemis.username` | `forage.<name>.jms.username` |
   | `quarkus.artemis.password` | `forage.<name>.jms.password` |
   | `quarkus.pooled-jms.max-connections` | `forage.<name>.jms.pool.max.connections` |
   | `quarkus.pooled-jms.idle-timeout` (duration) | `forage.<name>.jms.pool.idle.timeout.millis` |

---

### AI Agent Migration

### From Manual LangChain4j Setup

#### Before: Manual Configuration

**AgentConfig.java:**
```java
@Configuration
public class AgentConfig {
    
    @Bean
    public ChatLanguageModel openAiModel() {
        return OpenAiChatModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .modelName("gpt-4")
            .temperature(0.7)
            .build();
    }
    
    @Bean
    public ChatLanguageModel ollamaModel() {
        return OllamaChatModel.builder()
            .baseUrl("http://localhost:11434")
            .modelName("granite4:3b")
            .build();
    }
    
    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.withMaxMessages(20);
    }
    
    @Bean
    public Agent openAiAgent(ChatLanguageModel openAiModel, ChatMemory chatMemory) {
        return Agent.builder()
            .chatLanguageModel(openAiModel)
            .chatMemory(chatMemory)
            .tools(toolRegistry)
            .build();
    }
    
    @Bean
    public Agent ollamaAgent(ChatLanguageModel ollamaModel) {
        return Agent.builder()
            .chatLanguageModel(ollamaModel)
            .build();
    }
}
```

#### After: Forage Configuration

**application.properties (all runtimes):**
```properties
# OpenAI agent with memory
forage.openAiAgent.agent.model.kind=openai
forage.openAiAgent.agent.model.name=gpt-4
forage.openAiAgent.agent.api.key=${OPENAI_API_KEY}
forage.openAiAgent.agent.temperature=0.7
forage.openAiAgent.agent.features=memory
forage.openAiAgent.agent.memory.kind=message-window
forage.openAiAgent.agent.memory.max.messages=20

# Ollama agent (memoryless)
forage.ollamaAgent.agent.model.kind=ollama
forage.ollamaAgent.agent.model.name=granite4:3b
forage.ollamaAgent.agent.base.url=http://localhost:11434
forage.ollamaAgent.agent.features=memoryless
```

**No Java code required** - Delete `AgentConfig.java`

#### Migration Steps

1. **Add Forage dependencies:**

   ```xml
   <!-- Add Forage agent dependencies -->
   <dependency>
       <groupId>io.kaoto.forage</groupId>
       <artifactId>forage-agent</artifactId>
       <version>{{ forage_version }}</version>
   </dependency>
   <dependency>
       <groupId>io.kaoto.forage</groupId>
       <artifactId>forage-model-open-ai</artifactId>
       <version>{{ forage_version }}</version>
   </dependency>
   <dependency>
       <groupId>io.kaoto.forage</groupId>
       <artifactId>forage-model-ollama</artifactId>
       <version>{{ forage_version }}</version>
   </dependency>
   <dependency>
       <groupId>io.kaoto.forage</groupId>
       <artifactId>forage-memory-message-window</artifactId>
       <version>{{ forage_version }}</version>
   </dependency>
   ```

2. **Convert configuration:**

   | Manual Setup | Forage |
   |--------------|--------|
   | `OpenAiChatModel.builder().apiKey()` | `forage.<name>.agent.api.key` |
   | `OpenAiChatModel.builder().modelName()` | `forage.<name>.agent.model.name` |
   | `OpenAiChatModel.builder().temperature()` | `forage.<name>.agent.temperature` |
   | `OllamaChatModel.builder().baseUrl()` | `forage.<name>.agent.base.url` |
   | `MessageWindowChatMemory.withMaxMessages()` | `forage.<name>.agent.memory.max.messages` |

3. **Update route references:**

   ```yaml
   # Before
   - route:
       from:
         uri: timer:chat
         steps:
           - to:
               uri: langchain4j-agent:chat
               parameters:
                 agent: "#openAiAgent"  # Bean from @Configuration
   
   # After
   - route:
       from:
         uri: timer:chat
         steps:
           - to:
               uri: langchain4j-agent:chat
               parameters:
                 agent: "#openAiAgent"  # Bean from Forage
   ```

---

### Configuration Comparison Tables

### JDBC Configuration

| Aspect | Spring Boot | Quarkus | **Forage (All Runtimes)** |
|--------|-------------|---------|---------------------------|
| **Property prefix** | `spring.datasource.*` | `quarkus.datasource.*` | `forage.<name>.jdbc.*` |
| **Database kind** | `driver-class-name` | `db-kind` | `db.kind` |
| **Pool max size** | `hikari.maximum-pool-size` | `jdbc.max-size` | `pool.max.size` |
| **Pool min size** | `hikari.minimum-idle` | `jdbc.min-size` | `pool.min.size` |
| **Timeout format** | Milliseconds | Duration string | Seconds (consistent) |
| **Multiple datasources** | Custom `@Configuration` | Named config | Named config (no code) |
| **Pool implementation** | HikariCP | Agroal | Agroal (all runtimes) |

### JMS Configuration

| Aspect | Spring Boot | Quarkus | **Forage (All Runtimes)** |
|--------|-------------|---------|---------------------------|
| **Property prefix** | `spring.artemis.*` | `quarkus.artemis.*` | `forage.<name>.jms.*` |
| **Connection URL** | `host` + `port` | `url` | `broker.url` (consistent) |
| **Pool max** | `pool.max-connections` | `pooled-jms.max-connections` | `pool.max.connections` |
| **Timeout format** | Milliseconds | Duration string | Milliseconds (consistent) |
| **Multiple brokers** | Custom `@Configuration` | Named config | Named config (no code) |

---

### Real-World Example: Multi-Database Application

### Scenario

An organization has:
- **Development**: Camel JBang (manual bean creation)
- **Staging**: Spring Boot (HikariCP, custom configuration)
- **Production**: Quarkus (Agroal, native compilation)

Each environment requires different configuration files and code.

### Before: Three Different Configurations

=== "Dev (Camel JBang)"

    ```java
    // Manual bean creation
    DataSource ordersDb = createDataSource(
        "jdbc:postgresql://localhost:5432/orders",
        "admin", "secret");
    
    DataSource analyticsDb = createDataSource(
        "jdbc:mysql://localhost:3306/analytics",
        "admin", "secret");
    
    camelContext.getRegistry().bind("ordersDb", ordersDb);
    camelContext.getRegistry().bind("analyticsDb", analyticsDb);
    ```

=== "Staging (Spring Boot)"

    ```properties
    spring.datasource.orders.url=jdbc:postgresql://staging-db:5432/orders
    spring.datasource.orders.username=staging_user
    spring.datasource.orders.password=staging_pass
    spring.datasource.orders.hikari.maximum-pool-size=20
    
    spring.datasource.analytics.url=jdbc:mysql://staging-db:3306/analytics
    spring.datasource.analytics.username=staging_user
    spring.datasource.analytics.password=staging_pass
    spring.datasource.analytics.hikari.maximum-pool-size=15
    ```
    
    ```java
    @Configuration
    public class DataSourceConfig {
        @Bean
        public DataSource ordersDb() { ... }
        
        @Bean
        public DataSource analyticsDb() { ... }
    }
    ```

=== "Production (Quarkus)"

    ```properties
    quarkus.datasource.orders.db-kind=postgresql
    quarkus.datasource.orders.jdbc.url=jdbc:postgresql://prod-db:5432/orders
    quarkus.datasource.orders.username=prod_user
    quarkus.datasource.orders.password=prod_pass
    quarkus.datasource.orders.jdbc.max-size=20
    
    quarkus.datasource.analytics.db-kind=mysql
    quarkus.datasource.analytics.jdbc.url=jdbc:mysql://prod-db:3306/analytics
    quarkus.datasource.analytics.username=prod_user
    quarkus.datasource.analytics.password=prod_pass
    quarkus.datasource.analytics.jdbc.max-size=15
    ```

### After: Single Unified Configuration

**application.properties (all environments):**
```properties
# Orders database (PostgreSQL)
forage.ordersDb.jdbc.db.kind=postgresql
forage.ordersDb.jdbc.url=jdbc:postgresql://${DB_HOST:localhost}:5432/orders
forage.ordersDb.jdbc.username=${ORDERS_USER:admin}
forage.ordersDb.jdbc.password=${ORDERS_PASS:secret}
forage.ordersDb.jdbc.pool.max.size=20

# Analytics database (MySQL)
forage.analyticsDb.jdbc.db.kind=mysql
forage.analyticsDb.jdbc.url=jdbc:mysql://${DB_HOST:localhost}:3306/analytics
forage.analyticsDb.jdbc.username=${ANALYTICS_USER:admin}
forage.analyticsDb.jdbc.password=${ANALYTICS_PASS:secret}
forage.analyticsDb.jdbc.pool.max.size=15

# Inventory database (Oracle)
forage.inventoryDb.jdbc.db.kind=oracle
forage.inventoryDb.jdbc.url=jdbc:oracle:thin:@${DB_HOST:localhost}:1521:inventory
forage.inventoryDb.jdbc.username=${INVENTORY_USER:admin}
forage.inventoryDb.jdbc.password=${INVENTORY_PASS:secret}
forage.inventoryDb.jdbc.pool.max.size=10
```

**Environment-specific values via environment variables:**

```bash
# Development
export DB_HOST=localhost
export ORDERS_USER=dev_user
export ORDERS_PASS=dev_pass

# Staging
export DB_HOST=staging-db
export ORDERS_USER=staging_user
export ORDERS_PASS=staging_pass

# Production
export DB_HOST=prod-db
export ORDERS_USER=prod_user
export ORDERS_PASS=prod_pass
```

**Benefits:**

- Same configuration file across all environments
- Environment-specific values via environment variables
- No code changes when switching runtimes
- Consistent pool behavior across dev/staging/prod
- Easy to add new databases without code changes

---

### Getting Help

If you encounter issues during migration:

1. **Check the troubleshooting guide:**
   - [Troubleshooting](troubleshooting.md)

2. **Review examples:**
   - [JDBC Examples](../examples/datasource/single.md)
   - [JMS Examples](../examples/jms/single.md)
   - [AI Agent Examples](../examples/ai/single-agent.md)

3. **Ask for help:**
   - [GitHub Discussions](https://github.com/KaotoIO/forage/discussions)
   - Include your current configuration and target runtime

4. **Report migration issues:**
   - [Create an issue](https://github.com/KaotoIO/forage/issues/new)
   - Tag with `migration` label