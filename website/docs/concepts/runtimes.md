# Runtime Support

Forage works with all major Apache Camel runtimes. The same properties and routes work everywhere — only the packaging changes.

## Supported Runtimes

### Camel JBang

The fastest way to develop. Run routes directly from the command line:

```bash
camel run *
```

With the Forage plugin installed, the standard `camel run` command automatically resolves Forage dependencies, validates properties, and starts your routes. No build step needed — ideal for prototyping and development.

### Spring Boot

For production deployments with the Spring ecosystem. Export your project:

```bash
camel export --runtime=spring-boot --directory=./my-app
```

Forage provides Spring Boot starters that integrate with Spring's auto-configuration. Your Forage beans are registered as Spring beans and participate in Spring's dependency injection, health checks, and lifecycle management.

### Quarkus

For cloud-native deployments with fast startup and low memory. Export your project:

```bash
camel export --runtime=quarkus --directory=./my-app
```

Forage provides Quarkus deployment extensions that process configuration at build time. This enables GraalVM native compilation for minimal container images.

## Same Configuration, Every Runtime

The key benefit of Forage is that your configuration is runtime-agnostic. These properties work identically on all three runtimes:

```properties
forage.myDb.jdbc.db.kind=postgresql
forage.myDb.jdbc.url=jdbc:postgresql://localhost:5432/orders
forage.myDb.jdbc.username=admin
forage.myDb.jdbc.password=secret
```

Your Camel routes don't change either:

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
              query: select * from orders
              dataSource: "#myDb"
        - log:
            message: "${body}"
```

## Development Workflow

A typical workflow uses all three stages:

1. **Develop** with Camel JBang — fast iteration, no build step

    ```bash
    camel run *
    ```

2. **Export** to your target runtime when ready

    ```bash
    camel export --runtime=spring-boot --directory=./my-app
    ```

3. **Deploy** using standard tooling for that runtime

    ```bash
    cd my-app
    mvn package
    java -jar target/my-app.jar
    ```

## Runtime Differences

While the configuration is the same, there are a few runtime-specific behaviors to be aware of:

| Feature | Camel JBang | Spring Boot | Quarkus |
|---|---|---|---|
| Startup time | Fast (JVM) | Moderate (Spring context) | Fast (build-time processing) |
| Native compilation | No | No | Yes (GraalVM) |
| Configuration sources | Properties files, env vars | Spring Environment + Forage properties | SmallRye Config + Forage properties |
| Bean registration | Camel registry | Spring beans + Camel registry | CDI beans + Camel registry |
| Hot reload | `camel run --dev` | Spring DevTools | Quarkus dev mode |
{% raw %}
| Property placeholders | `{{env:KEY}}` + `{{secret:...}}`, vaults | `{{env:KEY}}` + `{{secret:...}}`, vaults + `${KEY}` | `{{env:KEY}}` + `${KEY}` |
{% endraw %}

In Spring Boot, Forage properties are available through Spring's `@Value` and `@ConditionalOnProperty` annotations. In Quarkus, Forage properties are translated to Quarkus-native configuration at build time.

For most use cases, you don't need to think about these differences — Forage handles the integration transparently.

{% raw %}
### Property Placeholders Across Runtimes

Forage resolves `{{env:KEY}}` and `{{sys:KEY}}` placeholders in all runtimes at property-loading time — before values reach any runtime-specific code.

Secret placeholders such as `{{secret:name/key}}` (Kubernetes) and `{{aws:name}}` (vaults) are resolved by Camel when Forage creates the bean, on Camel JBang and Spring Boot. They are not supported on Quarkus, where Forage hands its properties to Quarkus extensions before any CamelContext exists: Forage fails with an explanation instead. See [Secrets](configuration.md#secrets).

Spring Boot and Quarkus also support their own placeholder syntax (`${KEY}`), which is resolved by the respective framework. Both approaches work in Forage property files:

```properties
# Camel/Forage syntax — works in all runtimes
forage.myDb.jdbc.username={{env:DB_USER}}

# Spring Boot syntax — works in Spring Boot only
forage.myDb.jdbc.username=${DB_USER}

# MicroProfile/SmallRye syntax — works in Quarkus only
forage.myDb.jdbc.username=${DB_USER}
```

When using `{{env:KEY}}`, Forage resolves the placeholder first. The resolved value is what Spring Boot or Quarkus sees. If you use `${KEY}` instead, Forage passes it through unchanged and the framework handles resolution.

Pick one syntax consistently per property file. `{{env:KEY}}` is recommended for configurations that need to work across all runtimes.
{% endraw %}
