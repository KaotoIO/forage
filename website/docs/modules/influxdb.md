# InfluxDB

Forage creates InfluxDB clients for Camel from properties, environment variables,
or system properties.

| Version | Plain Camel / JBang | Spring Boot | Quarkus |
|---------|---------------------|-------------|---------|
| InfluxDB 1 | `forage-influxdb` | `forage-influxdb-starter` | `forage-quarkus-influxdb` |
| InfluxDB 2 | `forage-influxdb2` | `forage-influxdb2-starter` | Not supported |

All artifacts use group ID `io.kaoto.forage` and the same version as Forage.
Camel Quarkus 3.39.0 provides an InfluxDB 1 extension, but no InfluxDB 2
extension. This change does not add v2 Quarkus support.

HTTP endpoints remain supported for local development. HTTP does not encrypt
InfluxDB 1 credentials or InfluxDB 2 tokens; use HTTPS for non-loopback deployments
and whenever credentials leave a trusted local or development network.

## InfluxDB 1

Create `forage-influxdb.properties`:

```properties
forage.influxdb.url=http://localhost:8086
forage.influxdb.username=writer
```

Set `FORAGE_INFLUXDB_PASSWORD` in the environment. Username and password must be
configured together; omit both for a server with authentication disabled.

The default bean is `influxdb`, of type `org.influxdb.InfluxDB`:

```yaml
- route:
    id: influxdb-write
    from:
      uri: timer:write
      parameters:
        repeatCount: 1
      steps:
        - setBody:
            constant: '{"camelInfluxDB.MeasurementName":"temperature","value":21}'
        - unmarshal:
            json:
              library: Jackson
        - to:
            uri: influxdb:influxdb
            parameters:
              databaseName: metrics
              retentionPolicy: autogen
```

The database and retention policy must exist. The example uses InfluxDB's
automatically created `autogen` policy; set `retentionPolicy` to match your server.
The endpoint can explicitly enable `autoCreateDatabase=true`.

## InfluxDB 2

Create `forage-influxdb2.properties`:

```properties
forage.influxdb2.url=http://localhost:8086
```

Set `FORAGE_INFLUXDB2_TOKEN` in the environment. Both URL and token are required.
The default bean is `influxdb2`, of type `com.influxdb.client.InfluxDBClient`.

```yaml
- route:
    id: influxdb2-write
    from:
      uri: timer:write
      parameters:
        repeatCount: 1
      steps:
        - setBody:
            constant: '{"CamelInfluxDB2MeasurementName":"temperature","value":21}'
        - unmarshal:
            json:
              library: Jackson
        - to:
            uri: influxdb2:influxdb2
            parameters:
              org: acme
              bucket: metrics
              autoCreateOrg: false
              autoCreateBucket: false
```

Organization and bucket are Camel endpoint options and remain required even if
the client library supports defaults. The example uses an existing organization
and bucket. Camel checks these through the server API when initializing the
endpoint, so the token must permit those lookups as well as writes.

## Running and exporting

With the Forage JBang plugin installed, place the properties and route in the same
directory and run:

```bash
camel forage run *.properties *.camel.yaml
```

The catalog discovers the appropriate factory dependencies. Add
`--runtime=spring-boot` for either version, or `--runtime=quarkus` for v1.
For Maven applications, add the artifact for the chosen runtime from the table.
Spring Boot also accepts the same properties in `application.properties` or
`application.yaml`.

## Named clients and configuration

The configuration prefix becomes the bean name:

```properties
forage.legacy.influxdb.url=http://influxdb1:8086
forage.legacy.influxdb.username=writer
forage.modern.influxdb2.url=http://influxdb2:8086
```

Supply credentials through `FORAGE_LEGACY_INFLUXDB_PASSWORD` and
`FORAGE_MODERN_INFLUXDB2_TOKEN`. Use `influxdb:legacy?databaseName=metrics&retentionPolicy=autogen` and
`influxdb2:modern?org=acme&bucket=metrics` in routes.
Bean names share Camel's registry, so use distinct names across all factories.
When named configurations exist for a version, Forage creates those clients
instead of an additional unprefixed client.

### InfluxDB 1 properties

{{ forage_properties("InfluxDB 1 Client") }}

### InfluxDB 2 properties

{{ forage_properties("InfluxDB 2 Client") }}

The standard precedence is environment variables, system properties, then
properties files. A module without configuration creates no client. Existing
beans with the configured name and expected type are preserved.

Declare mixed-case names or names containing underscores in a properties file,
system property, or runtime configuration source. Environment overrides then
preserve that exact name. For environment-only configurations, names are inferred
in lowercase with underscores interpreted as dots.

Quarkus decides which client beans to register during application augmentation.
Declare the clients in application configuration and supply their required values
at runtime as well. A temporary build-only environment variable must not be the
only declaration of a client that will be missing from the runtime configuration.

Forage closes the clients it creates at shutdown. On a full plain Camel route
reload (the default), old clients remain usable until all old routes have been
stopped and removed, then Forage closes them and evicts their cached endpoints.
Clients created without any routes are released immediately during cleanup.
Partial reloads conservatively retain old clients while any routes remain,
because dynamic sends may still use them; removing all routes or stopping the
context releases them. Spring Boot and Quarkus own client shutdown in their
respective adapters.

See [Write Metrics](../examples/influxdb/write-metrics.md) for a runnable InfluxDB 2 example.
