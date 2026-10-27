# Write Metrics

Create an InfluxDB 2 client from Forage properties, write a temperature point,
and query the stored value. This example supports Camel JBang and Spring Boot.
Forage does not currently support InfluxDB 2 on Quarkus.

Install Camel JBang and the Forage plugin using the
[getting started guide](../../getting-started/index.md). Docker or Podman and
`curl` are required for this local example.

## Start InfluxDB 2

The credentials below are for a disposable local database. HTTP is bound to
loopback; use HTTPS and your own credentials for remote deployments.

```bash
CONTAINER_RUNTIME=${CONTAINER_RUNTIME:-podman}
${CONTAINER_RUNTIME} run --rm -d --name forage-influxdb-example \
  -p 127.0.0.1:28086:8086 \
  -e DOCKER_INFLUXDB_INIT_MODE=setup \
  -e DOCKER_INFLUXDB_INIT_USERNAME=writer \
  -e DOCKER_INFLUXDB_INIT_PASSWORD=test-password \
  -e DOCKER_INFLUXDB_INIT_ORG=acme \
  -e DOCKER_INFLUXDB_INIT_BUCKET=metrics \
  -e DOCKER_INFLUXDB_INIT_ADMIN_TOKEN=forage-test-token \
  influxdb:2.7
```

Wait until setup completes. The following command must report `"allowed": false`
(the response may contain whitespace):

```bash
curl -fsS http://localhost:28086/api/v2/setup
```

## Configure and run the route

Create these two files in a new directory:

```properties title="forage-influxdb2.properties"
forage.influxdb2.url=http://localhost:28086
```

```yaml title="metrics.camel.yaml"
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

Provide the token through the environment and start Camel:

```bash
export FORAGE_INFLUXDB2_TOKEN=forage-test-token
camel forage run forage-influxdb2.properties metrics.camel.yaml
```

Add `--runtime=spring-boot` to run the same configuration with Spring Boot.
The organization and bucket are endpoint options; the factory configures the
client URL and authentication token.

## Verify the point

In another terminal, query the last hour of data. Writes are asynchronous, so
allow a few seconds for the point to appear:

```bash
curl -fsS 'http://localhost:28086/api/v2/query?org=acme' \
  -H 'Authorization: Token forage-test-token' \
  -H 'Content-Type: application/vnd.flux' \
  -H 'Accept: application/csv' \
  --data 'from(bucket:"metrics") |> range(start:-1h) |> filter(fn:(r) => r._measurement == "temperature")'
```

The CSV response contains a `temperature` measurement with field `value` equal
to `21`.

Stop Camel with Ctrl+C, then stop the example database in the original terminal:

```bash
${CONTAINER_RUNTIME} stop forage-influxdb-example
```

See the [InfluxDB module guide](../../modules/influxdb.md) for InfluxDB 1,
named clients, configuration precedence, and runtime support.
