# Snapshot Builds

!!! warning "Development Builds"
    Snapshot builds are published from the `main` branch every two days. They may contain breaking changes, incomplete features, or bugs. Use the [stable release](index.md) for production.

The latest snapshot version is **1.4-SNAPSHOT**, published to the Maven Central Portal Snapshots repository.

## Install the Snapshot Plugin

```bash
camel plugin add forage \
  --repos=https://central.sonatype.com/repository/maven-snapshots/ \
  --gav io.kaoto.forage:camel-jbang-plugin-forage:1.4-SNAPSHOT
```

Verify it installed correctly:

```bash
camel forage --help
```

## Running Routes

When running routes with a snapshot version of Forage, you need to tell Camel where to download snapshot dependencies. Use the `--repos` flag:

```bash
camel run --repos=https://central.sonatype.com/repository/maven-snapshots/ *
```

Alternatively, add the repository to your `application.properties` so you don't need the flag every time:

```properties
camel.jbang.repos=https://central.sonatype.com/repository/maven-snapshots/
```

Then run as usual:

```bash
camel run *
```

## Exporting to Production

The `--repos` flag also works with `camel export`:

=== "Spring Boot"

    ```bash
    camel export --runtime=spring-boot \
      --repos=https://central.sonatype.com/repository/maven-snapshots/ \
      --directory=./my-app
    ```

=== "Quarkus"

    ```bash
    camel export --runtime=quarkus \
      --repos=https://central.sonatype.com/repository/maven-snapshots/ \
      --directory=./my-app
    ```

## Using Snapshots in a Maven Project

If you are using Forage as a Maven dependency (without Camel JBang), add the snapshot repository to your `pom.xml`:

```xml
<repositories>
    <repository>
        <name>Central Portal Snapshots</name>
        <id>central-portal-snapshots</id>
        <url>https://central.sonatype.com/repository/maven-snapshots/</url>
        <releases>
            <enabled>false</enabled>
        </releases>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>
</repositories>
```

Then use the snapshot version in your dependencies:

```xml
<dependency>
    <groupId>io.kaoto.forage</groupId>
    <artifactId>forage-jdbc-postgresql</artifactId>
    <version>1.4-SNAPSHOT</version>
</dependency>
```
