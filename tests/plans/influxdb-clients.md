# Test Plan: InfluxDB 1 and 2 Clients

Verify both clients using real databases, then repeat with exported runtimes.

## Prerequisites

| Tool | Version | Check |
|------|---------|-------|
| Java | 17+ | `java -version` |
| Camel JBang | Matching Forage's Camel version | `camel version` |
| Docker or Podman | Any supported version | `docker --version` / `podman --version` |
| curl | Any | `curl --version` |

Install the locally built Forage plugin following
[common/forage-run.md](common/forage-run.md). Run the phases in the same shell.
Set `CONTAINER_RUNTIME` to docker or podman. `INFLUXDB1_PORT` and `INFLUXDB2_PORT`
default to 18086 and 28086.

## Phase 1: Start databases

```bash
CONTAINER_RUNTIME=${CONTAINER_RUNTIME:-podman}
INFLUXDB1_PORT=${INFLUXDB1_PORT:-18086}
INFLUXDB2_PORT=${INFLUXDB2_PORT:-28086}
INFLUXDB_PLAN_DIR=$(mktemp -d)
INFLUXDB1_CONTAINER=forage-influxdb1-$$
INFLUXDB2_CONTAINER=forage-influxdb2-$$

${CONTAINER_RUNTIME} run -d --name "${INFLUXDB1_CONTAINER}" \
  -p "127.0.0.1:${INFLUXDB1_PORT}:8086" \
  -e INFLUXDB_DB=metrics -e INFLUXDB_ADMIN_USER=writer \
  -e INFLUXDB_ADMIN_PASSWORD=test-password \
  -e INFLUXDB_HTTP_AUTH_ENABLED=true influxdb:1.8.10
${CONTAINER_RUNTIME} run -d --name "${INFLUXDB2_CONTAINER}" \
  -p "127.0.0.1:${INFLUXDB2_PORT}:8086" \
  -e DOCKER_INFLUXDB_INIT_MODE=setup \
  -e DOCKER_INFLUXDB_INIT_USERNAME=writer \
  -e DOCKER_INFLUXDB_INIT_PASSWORD=test-password \
  -e DOCKER_INFLUXDB_INIT_ORG=acme -e DOCKER_INFLUXDB_INIT_BUCKET=metrics \
  -e DOCKER_INFLUXDB_INIT_ADMIN_TOKEN=forage-test-token influxdb:2.7

for attempt in $(seq 1 90); do
  curl -fsS "http://localhost:${INFLUXDB1_PORT}/ping" >/dev/null &&
    curl -fsS "http://localhost:${INFLUXDB2_PORT}/api/v2/setup" |
      grep -q '"allowed"[[:space:]]*:[[:space:]]*false' && break
  sleep 1
done
curl -fsS "http://localhost:${INFLUXDB1_PORT}/ping" >/dev/null &&
  curl -fsS "http://localhost:${INFLUXDB2_PORT}/api/v2/setup" |
    grep -q '"allowed"[[:space:]]*:[[:space:]]*false' && echo 'PASS: both databases ready'
```

## Phase 2: Create and run the sample project

Copy the two complete YAML routes from the
[configuration guide](../../library/influxdb/README.md) into
`${INFLUXDB_PLAN_DIR}/v1.camel.yaml` and `${INFLUXDB_PLAN_DIR}/v2.camel.yaml`.

```bash
cat > "${INFLUXDB_PLAN_DIR}/forage-influxdb.properties" <<EOF
forage.influxdb.url=http://localhost:${INFLUXDB1_PORT}
forage.influxdb.username=writer
EOF
cat > "${INFLUXDB_PLAN_DIR}/forage-influxdb2.properties" <<EOF
forage.influxdb2.url=http://localhost:${INFLUXDB2_PORT}
EOF
export FORAGE_INFLUXDB_PASSWORD=test-password
export FORAGE_INFLUXDB2_TOKEN=forage-test-token
test -s "${INFLUXDB_PLAN_DIR}/v1.camel.yaml" &&
  test -s "${INFLUXDB_PLAN_DIR}/v2.camel.yaml" && echo 'PASS: sample routes present'

camel forage run "${INFLUXDB_PLAN_DIR}"/*.properties \
  "${INFLUXDB_PLAN_DIR}"/*.camel.yaml > "${INFLUXDB_PLAN_DIR}/output.log" 2>&1 &
INFLUXDB_PLAN_PID=$!
```

## Phase 3: Verify stored data

Poll for persistence because the InfluxDB 2 Camel producer writes asynchronously.

```bash
for attempt in $(seq 1 120); do
  curl -fsS -u writer:test-password -G \
    "http://localhost:${INFLUXDB1_PORT}/query" \
    --data-urlencode 'db=metrics' --data-urlencode 'q=SELECT * FROM temperature' \
    > "${INFLUXDB_PLAN_DIR}/v1.json"
  curl -fsS "http://localhost:${INFLUXDB2_PORT}/api/v2/query?org=acme" \
    -H 'Authorization: Token forage-test-token' \
    -H 'Content-Type: application/vnd.flux' -H 'Accept: application/csv' \
    --data 'from(bucket:"metrics") |> range(start:-1h) |> filter(fn:(r) => r._measurement == "temperature")' \
    > "${INFLUXDB_PLAN_DIR}/v2.csv"
  grep -q 'temperature' "${INFLUXDB_PLAN_DIR}/v1.json" &&
    grep -q 'temperature' "${INFLUXDB_PLAN_DIR}/v2.csv" && break
  sleep 1
done
if grep -q 'temperature' "${INFLUXDB_PLAN_DIR}/v1.json" &&
   grep -q '21' "${INFLUXDB_PLAN_DIR}/v1.json" &&
   grep -q 'temperature' "${INFLUXDB_PLAN_DIR}/v2.csv" &&
   grep -q '21' "${INFLUXDB_PLAN_DIR}/v2.csv"; then
  echo 'PASS: both factories persisted their points'
else
  echo 'FAIL: expected points missing'
  cat "${INFLUXDB_PLAN_DIR}/output.log"
fi
```

Repeat with distinct named clients `legacy` and `modern` following the guide.
For Spring Boot, repeat with `--runtime=spring-boot`. For Quarkus, create a
project containing only the v1 properties and route and use `--runtime=quarkus`.
InfluxDB 2 is not supported on Quarkus.

## Phase 4: Cleanup

```bash
kill "${INFLUXDB_PLAN_PID}"
wait "${INFLUXDB_PLAN_PID}" 2>/dev/null || true
${CONTAINER_RUNTIME} rm -f "${INFLUXDB1_CONTAINER}" "${INFLUXDB2_CONTAINER}"
unset FORAGE_INFLUXDB_PASSWORD FORAGE_INFLUXDB2_TOKEN
echo "Test artifacts retained in ${INFLUXDB_PLAN_DIR}"
```
