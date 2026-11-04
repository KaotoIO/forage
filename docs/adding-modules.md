# Adding New Forage Modules

This guide explains how to add a new Forage module with support for all three runtimes: plain Camel, Camel Spring Boot, and Camel Quarkus.

## Runtime Philosophy

Each runtime has a distinct ownership model:

| Runtime | Bean creation | Lifecycle | Configuration |
|---------|--------------|-----------|---------------|
| **Plain Camel** | Forage owns everything | Forage binds to Camel registry | `ConfigStore` loads properties, env vars, system props |
| **Spring Boot** | Forage creates beans, Spring owns lifecycle | `GenericBeanDefinition` with suppliers | `SpringConfigResolver` bridges Spring Environment ↔ `ConfigStore` |
| **Quarkus** | Quarkus creates primary beans natively | Quarkus CDI | `ConfigSourceFactory` translates `forage.*` → `quarkus.*` properties at bootstrap |

## Module Anatomy

A Forage module typically consists of these layers:

```
library/<category>/<module>/
├── forage-<module>-common/           # Shared: config, descriptor, helpers
│   ├── XxxConfigEntries.java         # Configuration module definitions
│   ├── XxxConfig.java                # Configuration accessor class
│   └── XxxModuleDescriptor.java      # Runtime-agnostic module descriptor
├── forage-<module>-<impl>/           # Provider implementation(s)
│   ├── XxxProvider.java              # @ForageBean annotated provider
│   └── META-INF/services/            # ServiceLoader registration
├── spring-boot/forage-<module>-starter/  # Spring Boot integration
│   ├── ForageXxxAutoConfiguration.java
│   ├── ForageXxxBeanRegistrar.java
│   └── META-INF/spring/              # Auto-configuration registration
└── camel-quarkus/<module>/           # Quarkus integration
    ├── deployment/                    # Build-time processor
    │   └── ForageXxxProcessor.java
    └── runtime/                       # Runtime config source
        ├── ForageXxxConfigSourceFactory.java
        └── META-INF/services/         # ConfigSourceFactory registration
```

---

## Step 1: Define Configuration (Required for All Runtimes)

### 1a. Create ConfigEntries

Define all configuration modules (properties) in a `ConfigEntries` subclass.

```java
package io.kaoto.forage.mymodule;

import io.kaoto.forage.core.util.config.ConfigEntries;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigTag;

public final class MyModuleConfigEntries extends ConfigEntries {

    public static final ConfigModule HOST = ConfigModule.of(
            MyModuleConfig.class,
            "forage.mymodule.host",
            "Server hostname",           // description
            "Host",                      // label (for UI)
            "localhost",                 // default value
            "string",                    // type
            true,                        // required
            ConfigTag.COMMON);

    public static final ConfigModule PORT = ConfigModule.of(
            MyModuleConfig.class,
            "forage.mymodule.port",
            "Server port number",
            "Port",
            "8080",
            "integer",
            true,
            ConfigTag.COMMON);

    public static final ConfigModule API_KEY = ConfigModule.of(
            MyModuleConfig.class,
            "forage.mymodule.api.key",
            "API key for authentication",
            "API Key",
            null,                        // no default — required at runtime
            "password",                  // type hint for UI masking
            true,
            ConfigTag.SECURITY);

    // Register all modules in a single static block
    static {
        initModules(MyModuleConfigEntries.class, HOST, PORT, API_KEY);
    }
}
```

**Best practices:**
- Property names follow `forage.<module>.<property>` convention
- Use `ConfigTag.COMMON` for frequently-used properties, `ConfigTag.SECURITY` for credentials, `ConfigTag.ADVANCED` for tuning parameters
- Use `ConfigModule.ofBeanName(...)` for properties that select a bean by name (enables catalog UI dropdowns)
- List all modules in `initModules()` in the same order they appear as field declarations

### 1b. Create Config

Extend `AbstractConfig` to provide typed accessor methods.

```java
package io.kaoto.forage.mymodule;

import io.kaoto.forage.core.util.config.AbstractConfig;

import static io.kaoto.forage.mymodule.MyModuleConfigEntries.API_KEY;
import static io.kaoto.forage.mymodule.MyModuleConfigEntries.HOST;
import static io.kaoto.forage.mymodule.MyModuleConfigEntries.PORT;

public class MyModuleConfig extends AbstractConfig {

    public MyModuleConfig() {
        this(null);
    }

    public MyModuleConfig(String prefix) {
        super(prefix, MyModuleConfigEntries.class);
    }

    @Override
    public String name() {
        return "forage-mymodule";  // matches properties file name
    }

    // Required property — throws MissingConfigException if absent
    public String apiKey() {
        return getRequired(API_KEY, "Missing MyModule API key");
    }

    // Optional property with default from ConfigModule
    public String host() {
        return get(HOST).orElse(HOST.defaultValue());
    }

    // Optional property with type conversion
    public int port() {
        return get(PORT).map(Integer::parseInt).orElse(Integer.parseInt(PORT.defaultValue()));
    }
}
```

**Best practices:**
- The `name()` return value must match the properties file name: `forage-mymodule.properties`
- Use `getRequired(MODULE, "message")` for mandatory properties
- Use `get(MODULE).orElse(defaultValue)` for optional properties with defaults
- Use `get(MODULE).map(Type::parse).orElse(null)` for optional properties without defaults
- The no-arg constructor must delegate to `this(null)` for non-prefixed usage

**Configuration precedence** (highest to lowest):
1. Environment variables: `FORAGE_MYMODULE_HOST`
2. System properties: `-Dforage.mymodule.host=value`
3. Properties files: `forage-mymodule.properties`

---

## Step 2: Create Provider Implementation (Required for All Runtimes)

### 2a. Define the Provider Interface

Create a provider interface in the appropriate core module (or reuse an existing one like `ModelProvider`, `DataSourceProvider`).

```java
package io.kaoto.forage.core.mymodule;

import io.kaoto.forage.core.common.BeanProvider;

public interface MyModuleProvider extends BeanProvider<MyBean> {
    // Add domain-specific methods if needed
}
```

### 2b. Implement the Provider

```java
package io.kaoto.forage.mymodule.impl;

import io.kaoto.forage.core.annotations.ForageBean;
import io.kaoto.forage.core.mymodule.MyModuleProvider;
import io.kaoto.forage.mymodule.MyModuleConfig;

@ForageBean(
        value = "my-impl",
        components = {"camel-my-component"},
        description = "My module implementation",
        feature = "MyBean")
public class MyImplProvider implements MyModuleProvider {

    @Override
    public MyBean create(String id) {
        MyModuleConfig config = new MyModuleConfig(id);
        // Build and return the bean using config values
        return MyBean.builder()
                .host(config.host())
                .port(config.port())
                .apiKey(config.apiKey())
                .build();
    }
}
```

### 2c. Register via ServiceLoader

Create `META-INF/services/io.kaoto.forage.core.mymodule.MyModuleProvider`:
```
io.kaoto.forage.mymodule.impl.MyImplProvider
```

**At this point, the module works with plain Camel.** The remaining steps add Spring Boot and Quarkus support.

---

## Step 3: Create Module Descriptor (Required for Spring Boot / Quarkus)

The `ForageModuleDescriptor` captures all module-specific knowledge in one place, so the runtime adapters don't need per-module code.

```java
package io.kaoto.forage.mymodule;

import java.util.HashMap;
import java.util.Map;
import io.kaoto.forage.core.common.ForageModuleDescriptor;
import io.kaoto.forage.core.mymodule.MyModuleProvider;

public class MyModuleDescriptor implements ForageModuleDescriptor<MyModuleConfig, MyModuleProvider> {

    @Override
    public String modulePrefix() {
        return "mymodule";  // used in property regex: forage.(<prefix>).mymodule.*
    }

    @Override
    public MyModuleConfig createConfig(String prefix) {
        return prefix == null ? new MyModuleConfig() : new MyModuleConfig(prefix);
    }

    @Override
    public Class<MyModuleProvider> providerClass() {
        return MyModuleProvider.class;
    }

    @Override
    public String resolveProviderClassName(MyModuleConfig config) {
        // Map config value to provider class name
        // e.g., config.kind() → "io.kaoto.forage.mymodule.impl.MyImplProvider"
        return "io.kaoto.forage.mymodule.impl.MyImplProvider";
    }

    @Override
    public String defaultBeanName() {
        return "myBean";  // bean name when registering the default instance
    }

    @Override
    public Class<?> primaryBeanClass() {
        return MyBean.class;  // the class type for bean registration
    }

    @Override
    public boolean transactionEnabled(MyModuleConfig config) {
        return false;  // override if transactions are relevant
    }

    @Override
    public Map<String, String> translateProperties(String prefix, MyModuleConfig config) {
        // Translate forage.* properties to quarkus.* properties
        // Only needed if Quarkus has native support for this bean type
        Map<String, String> props = new HashMap<>();
        props.put("quarkus.mymodule.host", config.host());
        props.put("quarkus.mymodule.port", String.valueOf(config.port()));
        return props;
    }
}
```

**Best practices:**
- `modulePrefix()` must match the segment after `forage.` in property names
- `translateProperties()` is only needed when Quarkus creates the primary bean natively — return empty map if Forage creates all beans
- `auxiliaryBeans()` returns beans that must be created alongside the primary bean (e.g., aggregation repositories for JDBC)

---

## Step 4: Spring Boot Integration

### 4a. Auto-Configuration Class

```java
package io.kaoto.forage.springboot.mymodule;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import io.kaoto.forage.core.annotations.FactoryType;
import io.kaoto.forage.core.annotations.FactoryVariant;
import io.kaoto.forage.core.annotations.ForageFactory;
import io.kaoto.forage.core.mymodule.MyModuleProvider;
import io.kaoto.forage.mymodule.MyModuleConfig;
import io.kaoto.forage.mymodule.MyModuleDescriptor;
import io.kaoto.forage.springboot.common.ForageSpringBootModuleAdapter;

@ForageFactory(
        value = "MyModule (Spring Boot)",
        components = {"camel-my-component"},
        description = "Auto-configured MyModule for Spring Boot",
        type = FactoryType.MY_TYPE,       // add to FactoryType enum if needed
        autowired = true,
        configClass = MyModuleConfig.class,
        variant = FactoryVariant.SPRING_BOOT)
@AutoConfiguration
@Import(ForageMyModuleBeanRegistrar.class)
public class ForageMyModuleAutoConfiguration {

    @Bean
    static ForageSpringBootModuleAdapter<MyModuleConfig, MyModuleProvider> forageMyModuleAdapter(
            Environment environment) {
        return new ForageSpringBootModuleAdapter<>(new MyModuleDescriptor(), environment);
    }
}
```

### 4b. Bean Registrar (for early registration before `@ConditionalOnMissingBean` evaluation)

```java
package io.kaoto.forage.springboot.mymodule;

import java.util.Set;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import io.kaoto.forage.core.util.config.ConfigHelper;
import io.kaoto.forage.mymodule.MyModuleDescriptor;
import io.kaoto.forage.springboot.common.ForageSpringBootModuleAdapter;
import io.kaoto.forage.springboot.common.SpringPropertyHelper;

class ForageMyModuleBeanRegistrar implements ImportBeanDefinitionRegistrar,
        org.springframework.context.EnvironmentAware {

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        MyModuleDescriptor descriptor = new MyModuleDescriptor();
        Set<String> prefixes = SpringPropertyHelper.discoverPrefixes(
                environment, ConfigHelper.getNamedPropertyRegexp(descriptor.modulePrefix()));

        if (prefixes.isEmpty()) return;

        boolean isFirst = true;
        for (String name : prefixes) {
            if (!registry.containsBeanDefinition(name)) {
                GenericBeanDefinition bd = new GenericBeanDefinition();
                bd.setBeanClass(descriptor.primaryBeanClass());
                bd.setInstanceSupplier(() ->
                        new ForageSpringBootModuleAdapter<>(descriptor, environment).createBean(name));
                registry.registerBeanDefinition(name, bd);

                if (isFirst) {
                    String defaultName = descriptor.defaultBeanName();
                    if (!registry.containsBeanDefinition(defaultName)) {
                        GenericBeanDefinition defaultBd = new GenericBeanDefinition();
                        defaultBd.setBeanClass(descriptor.primaryBeanClass());
                        defaultBd.setInstanceSupplier(() ->
                                new ForageSpringBootModuleAdapter<>(descriptor, environment).createBean(name));
                        registry.registerBeanDefinition(defaultName, defaultBd);
                    }
                    isFirst = false;
                }
            }
        }
    }
}
```

### 4c. Register Auto-Configuration

Create `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:
```
io.kaoto.forage.springboot.mymodule.ForageMyModuleAutoConfiguration
```

**Best practices:**
- The bean registrar runs during configuration class processing (same phase as `@ConditionalOnMissingBean`), ensuring Spring Boot's own auto-configurations see Forage beans
- The `ForageSpringBootModuleAdapter` runs as a post-processor for auxiliary beans and late discovery
- Use `@ConditionalOnProperty` for conditional features (e.g., transaction management)
- The first discovered prefix is also registered under the `defaultBeanName()` so that default injection works

---

## Step 5: Quarkus Integration

### 5a. ConfigSourceFactory (runtime module)

```java
package io.kaoto.forage.quarkus.mymodule;

import io.kaoto.forage.core.common.ForageModuleDescriptor;
import io.kaoto.forage.core.common.ForageQuarkusConfigSourceAdapter;
import io.kaoto.forage.mymodule.MyModuleConfig;
import io.kaoto.forage.mymodule.MyModuleDescriptor;

public class ForageMyModuleConfigSourceFactory
        extends ForageQuarkusConfigSourceAdapter<MyModuleConfig> {

    @Override
    protected ForageModuleDescriptor<MyModuleConfig, ?> descriptor() {
        return new MyModuleDescriptor();
    }
}
```

Register via `META-INF/services/io.smallrye.config.ConfigSourceFactory`:
```
io.kaoto.forage.quarkus.mymodule.ForageMyModuleConfigSourceFactory
```

### 5b. Deployment Processor

```java
package io.kaoto.forage.quarkus.mymodule.deployment;

import io.kaoto.forage.core.annotations.FactoryType;
import io.kaoto.forage.core.annotations.FactoryVariant;
import io.kaoto.forage.core.annotations.ForageFactory;
import io.kaoto.forage.mymodule.MyModuleConfig;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

@ForageFactory(
        value = "MyModule (Quarkus)",
        components = {"camel-my-component"},
        description = "Native MyModule for Quarkus",
        type = FactoryType.MY_TYPE,
        autowired = true,
        configClass = MyModuleConfig.class,
        variant = FactoryVariant.QUARKUS)
public class ForageMyModuleProcessor {

    private static final String FEATURE = "forage-mymodule";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    // Add additional @BuildStep methods for auxiliary beans if needed
}
```

**Best practices:**
- The `ForageQuarkusConfigSourceAdapter` handles all prefix discovery and property translation via the descriptor
- Config source uses ordinal 275 (below system properties at 400), so `-D` overrides work
- Use Quarkus recorders (`@Record`) when creating runtime beans that need Quarkus CDI context
- Produce `CamelRuntimeBeanBuildItem`s for beans that must be in the Camel registry

---

## Step 6: Integration Tests

Integration tests use the Citrus Test Framework and run against all three runtimes.

### Test Class

```java
package io.kaoto.forage.mymodule;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import io.kaoto.forage.integration.tests.ForageIntegrationTest;
import io.kaoto.forage.integration.tests.ForageTestCaseRunner;
import io.kaoto.forage.integration.tests.IntegrationTestSetupExtension;
import org.citrusframework.annotations.CitrusSupport;

import static io.kaoto.forage.integration.tests.ForageTestActions.forageRun;

@CitrusSupport
@ExtendWith(IntegrationTestSetupExtension.class)
public class MyModuleTest implements ForageIntegrationTest {

    @Test
    void testMyModule(ForageTestCaseRunner runner) {
        runner.when(forageRun("my-process", "mymodule.properties", "route.camel.yaml")
                .dumpIntegrationOutput(true));
    }
}
```

### Running Tests

```bash
# Run with plain Camel (default)
mvn clean verify -f integration-tests/mymodule

# Run with specific runtime
export INTEGRATION_TEST_RUNTIME=quarkus   # or spring-boot
mvn clean verify -f integration-tests/mymodule -Dit.test=MyModuleTest
```

---

## Step 7: Add the Module to the Catalog

Kaoto and `camel forage run/export` only know what is in `forage-catalog.json` (see the [Forage Catalog guide](../website/docs/guides/forage-catalog.md)). To get there:

1. Add every new artifact holding a `@ForageBean` or `@ForageFactory` (provider, factory, Spring Boot starter, Quarkus deployment module) as an `<optional>true</optional>` dependency of `forage-catalog/pom.xml`.
2. Make sure each `@ForageBean` declares at least one of the `components` of the `@ForageFactory` it belongs to: a bean is only listed under the factories that share one of its components.

The build enforces both. The `forage-maven-catalog-plugin` fails on a bean that no factory picks up, and `CatalogCompletenessTest` in `forage-catalog` fails on any `@ForageBean` or `@ForageFactory` class of the repository that is not in the generated catalog:

```bash
mvn -pl forage-catalog -am package
```

If a bean is deliberately kept out of the catalog, for example because its factory is tracked by another issue, add it to `<excludedBeans>` in the plugin configuration of `forage-catalog/pom.xml`, with a `<reason>` that names the issue. Remove the entry once the factory exists: the build fails on entries whose bean is now in the catalog.

---

## Checklist for Adding a New Module

### Core (all runtimes)
- [ ] `XxxConfigEntries extends ConfigEntries` — with `initModules()` static block
- [ ] `XxxConfig extends AbstractConfig` — with typed accessors
- [ ] Provider interface extending `BeanProvider<T>` (or reuse existing)
- [ ] `@ForageBean` annotated provider implementation
- [ ] `META-INF/services/<ProviderInterface>` — ServiceLoader registration
- [ ] Properties file: `forage-xxx.properties` (if defaults are needed)
- [ ] Unit tests for ConfigEntries and Config

### Module Descriptor (for Spring Boot / Quarkus)
- [ ] `XxxModuleDescriptor implements ForageModuleDescriptor<C, P>`
- [ ] `defaultBeanName` on the base `@ForageFactory`, matching `XxxModuleDescriptor#defaultBeanName()` (used by `camel forage config read`), and a matching entry in `forage-catalog`'s `DefaultBeanNamesTest`

### Spring Boot
- [ ] `@AutoConfiguration` class with `@ForageFactory(variant = SPRING_BOOT)`
- [ ] `ImportBeanDefinitionRegistrar` for early bean registration
- [ ] `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

### Quarkus
- [ ] `ForageXxxConfigSourceFactory extends ForageQuarkusConfigSourceAdapter<C>`
- [ ] `META-INF/services/io.smallrye.config.ConfigSourceFactory`
- [ ] `@BuildStep` deployment processor with `@ForageFactory(variant = QUARKUS)`

### Integration Tests
- [ ] Citrus test class implementing `ForageIntegrationTest`
- [ ] Test properties and route files
- [ ] Test passes on plain, Spring Boot, and Quarkus runtimes

### Catalog
- [ ] Every new artifact with a `@ForageBean` or `@ForageFactory` is an optional dependency of `forage-catalog/pom.xml`
- [ ] Each `@ForageBean` shares a component with a `@ForageFactory`, or is listed in `<excludedBeans>` with a reason
- [ ] `mvn -pl forage-catalog -am package` passes (catalog plugin checks and `CatalogCompletenessTest`)

---

## Reference Implementations

Use these existing modules as templates:

| Module | Complexity | Good for |
|--------|-----------|----------|
| AI chat model (e.g., `forage-model-open-ai`) | Simple | Provider-only modules (no Spring Boot/Quarkus integration needed) |
| Guardrails (e.g., `forage-guardrail-input-length`) | Simple | Config + provider with minimal boilerplate |
| JDBC (`forage-jdbc-common` + starters) | Complex | Full three-runtime support with transactions and auxiliary beans |
| JMS (`forage-jms-common` + starters) | Medium | Three-runtime support with property translation |

### Key Files to Study

- **ConfigEntries pattern:** `library/ai/models/chat/forage-model-open-ai/.../OpenAIConfigEntries.java`
- **Config pattern:** `library/ai/models/chat/forage-model-open-ai/.../OpenAIConfig.java`
- **Provider pattern:** `library/ai/models/chat/forage-model-open-ai/.../OpenAIProvider.java`
- **Module descriptor:** `library/jdbc/forage-jdbc-common/.../JdbcModuleDescriptor.java`
- **Spring Boot auto-config:** `library/jdbc/spring-boot/forage-jdbc-starter/.../ForageDataSourceAutoConfiguration.java`
- **Spring Boot registrar:** `library/jdbc/spring-boot/forage-jdbc-starter/.../ForageJdbcBeanRegistrar.java`
- **Quarkus config source:** `library/jdbc/camel-quarkus/jdbc/runtime/.../ForageJdbcConfigSourceFactory.java`
- **Quarkus processor:** `library/jdbc/camel-quarkus/jdbc/deployment/.../ForageJdbcProcessor.java`
