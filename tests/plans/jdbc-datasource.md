# Test Plan: JDBC DataSource Provisioning

## Overview

This test plan verifies Forage's JDBC DataSource provisioning end-to-end. Users write a `forage-datasource-factory.properties` file with `forage.jdbc.*` settings, write a Camel YAML route that references `dataSource` (or named beans like `ds1`, `ds2`), and run `camel run`. Forage discovers the JDBC provider via ServiceLoader, reads the properties, creates a DataSource with connection pooling (Agroal) and optional XA transactions (Narayana), and registers it in the Camel registry.

Each phase creates a self-contained project in a temp directory, runs it as a background process, and verifies output via log polling.

## Prerequisites

### Required tools

| Tool | Minimum version | Verify command |
|------|-----------------|----------------|
| `java` | 17+ | `java -version` |
| `camel` (JBang) | 4.16+ | `camel version` |
| `docker` or `podman` | any | `docker --version` or `podman --version` |

If Camel JBang is not installed, see [common/forage-run.md](common/forage-run.md).

### Environment variables

| Variable | Default | Description |
|----------|---------|-------------|
| `POSTGRES_PORT` | `5432` | Host port mapped to the PostgreSQL container |

---

## Phase 0: Prerequisites

Verify all required tools are present.

### Test 0.1: Verify required tools

```bash
java -version 2>&1 | head -1
JAVA_EXIT=$?

camel version 2>&1 | head -1
CAMEL_EXIT=$?

if command -v podman > /dev/null 2>&1; then
  CONTAINER_RUNTIME=podman
elif command -v docker > /dev/null 2>&1; then
  CONTAINER_RUNTIME=docker
else
  echo "FAIL: neither podman nor docker found"
  exit 1
fi

if [ "${JAVA_EXIT}" -eq 0 ] && [ "${CAMEL_EXIT}" -eq 0 ]; then
  echo "PASS: all required tools present (container runtime: ${CONTAINER_RUNTIME})"
else
  echo "FAIL: one or more tools missing (java=${JAVA_EXIT}, camel=${CAMEL_EXIT})"
  exit 1
fi
```

### Test 0.2: Verify Java version is 17+

```bash
JAVA_VERSION=$(java -version 2>&1 | head -1 | sed 's/.*"\([0-9]*\).*/\1/')
if [ "${JAVA_VERSION}" -ge 17 ]; then
  echo "PASS: Java version ${JAVA_VERSION} >= 17"
else
  echo "FAIL: Java version ${JAVA_VERSION} < 17"
  exit 1
fi
```

---

## Phase 1: H2 Embedded Database (no Docker)

The simplest path: an in-memory H2 database requires no external infrastructure.

### Test 1.1: Create project with H2 datasource

```bash
PROJECT_DIR=$(mktemp -d)
echo "Project directory: ${PROJECT_DIR}"

cat > "${PROJECT_DIR}/forage-datasource-factory.properties" <<'PROPS'
forage.jdbc.db.kind=h2
forage.jdbc.url=jdbc:h2:mem:testdb
forage.jdbc.username=sa
forage.jdbc.password=
PROPS

cat > "${PROJECT_DIR}/route.camel.yaml" <<'ROUTE'
- route:
    id: h2-test
    from:
      uri: timer
      parameters:
        timerName: h2poll
        period: "1000"
        repeatCount: "1"
      steps:
        - setBody:
            simple:
              expression: SELECT 1 AS result
        - to:
            uri: jdbc
            parameters:
              dataSourceName: dataSource
        - log:
            message: "H2 result: ${body}"
ROUTE

if [ -f "${PROJECT_DIR}/forage-datasource-factory.properties" ] && [ -f "${PROJECT_DIR}/route.camel.yaml" ]; then
  echo "PASS: H2 project files created"
else
  echo "FAIL: project files not created"
  exit 1
fi
```

### Test 1.2: Run H2 route and verify output

```bash
camel run "${PROJECT_DIR}"/* > "${PROJECT_DIR}/output.log" 2>&1 &
CAMEL_PID=$!

for i in $(seq 1 60); do
  grep -q "H2 result:" "${PROJECT_DIR}/output.log" 2>/dev/null && break
  sleep 1
done

grep -q "H2 result:" "${PROJECT_DIR}/output.log"
if [ $? -eq 0 ]; then
  echo "PASS: H2 query executed successfully"
else
  echo "FAIL: H2 result not found in output"
  cat "${PROJECT_DIR}/output.log"
fi

kill "${CAMEL_PID}" 2>/dev/null || true
wait "${CAMEL_PID}" 2>/dev/null || true
rm -rf "${PROJECT_DIR}"
```

---

## Phase 2: PostgreSQL via Docker

Verifies Forage connects to an external PostgreSQL database, seeds data, and queries it.

### Test 2.1: Start PostgreSQL container

Follow [common/start-container.md](common/start-container.md) -- PostgreSQL section.

```bash
POSTGRES_CONTAINER="forage-test-postgres"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"

${CONTAINER_RUNTIME} run -d \
  --name "${POSTGRES_CONTAINER}" \
  -e POSTGRES_USER=test \
  -e POSTGRES_PASSWORD=test \
  -e POSTGRES_DB=testdb \
  -p "${POSTGRES_PORT}:5432" \
  postgres:14-alpine

for i in $(seq 1 30); do
  ${CONTAINER_RUNTIME} exec "${POSTGRES_CONTAINER}" pg_isready -U test > /dev/null 2>&1 && break
  sleep 1
done

${CONTAINER_RUNTIME} exec "${POSTGRES_CONTAINER}" pg_isready -U test > /dev/null 2>&1
if [ $? -eq 0 ]; then
  echo "PASS: PostgreSQL is ready on port ${POSTGRES_PORT}"
else
  echo "FAIL: PostgreSQL did not become ready"
  exit 1
fi
```

### Test 2.2: Seed the database

```bash
${CONTAINER_RUNTIME} exec "${POSTGRES_CONTAINER}" psql -U test -d testdb -c "
  CREATE TABLE bar (id INT, content TEXT);
  INSERT INTO bar VALUES (1, 'hello'), (2, 'world');
"

if [ $? -eq 0 ]; then
  echo "PASS: database seeded with test data"
else
  echo "FAIL: could not seed database"
  exit 1
fi
```

### Test 2.3: Create project with PostgreSQL datasource

```bash
PROJECT_DIR=$(mktemp -d)
echo "Project directory: ${PROJECT_DIR}"

cat > "${PROJECT_DIR}/forage-datasource-factory.properties" <<PROPS
forage.jdbc.db.kind=postgresql
forage.jdbc.url=jdbc:postgresql://localhost:${POSTGRES_PORT}/testdb
forage.jdbc.username=test
forage.jdbc.password=test
forage.jdbc.pool.initial.size=2
forage.jdbc.pool.max.size=10
PROPS

cat > "${PROJECT_DIR}/route.camel.yaml" <<'ROUTE'
- route:
    id: pg-test
    from:
      uri: timer
      parameters:
        timerName: pgpoll
        period: "1000"
        repeatCount: "1"
      steps:
        - setBody:
            simple:
              expression: select * from bar
        - to:
            uri: jdbc
            parameters:
              dataSourceName: dataSource
        - log:
            message: "PG result: ${body}"
ROUTE

if [ -f "${PROJECT_DIR}/forage-datasource-factory.properties" ] && [ -f "${PROJECT_DIR}/route.camel.yaml" ]; then
  echo "PASS: PostgreSQL project files created"
else
  echo "FAIL: project files not created"
  exit 1
fi
```

### Test 2.4: Run PostgreSQL route and verify output

```bash
camel run "${PROJECT_DIR}"/* > "${PROJECT_DIR}/output.log" 2>&1 &
CAMEL_PID=$!

for i in $(seq 1 60); do
  grep -q "PG result:" "${PROJECT_DIR}/output.log" 2>/dev/null && break
  sleep 1
done

grep -q "PG result:" "${PROJECT_DIR}/output.log"
if [ $? -eq 0 ]; then
  echo "PASS: PostgreSQL query executed"
else
  echo "FAIL: PG result not found in output"
  cat "${PROJECT_DIR}/output.log"
fi
```

### Test 2.5: Verify query returned expected data

```bash
grep "PG result:" "${PROJECT_DIR}/output.log" | grep -q "hello"
HELLO_FOUND=$?

grep "PG result:" "${PROJECT_DIR}/output.log" | grep -q "world"
WORLD_FOUND=$?

if [ "${HELLO_FOUND}" -eq 0 ] && [ "${WORLD_FOUND}" -eq 0 ]; then
  echo "PASS: output contains both 'hello' and 'world'"
else
  echo "FAIL: expected data not found (hello=${HELLO_FOUND}, world=${WORLD_FOUND})"
  grep "PG result:" "${PROJECT_DIR}/output.log"
fi

kill "${CAMEL_PID}" 2>/dev/null || true
wait "${CAMEL_PID}" 2>/dev/null || true
rm -rf "${PROJECT_DIR}"
```

---

## Phase 3: Multi-DataSource (Named/Prefixed)

Verifies that Forage can create multiple named DataSource beans from prefixed properties (`forage.ds1.jdbc.*`, `forage.ds2.jdbc.*`).

### Test 3.1: Seed PostgreSQL with a second table

```bash
${CONTAINER_RUNTIME} exec "${POSTGRES_CONTAINER}" psql -U test -d testdb -c "
  CREATE TABLE IF NOT EXISTS baz (id INT, label TEXT);
  INSERT INTO baz VALUES (10, 'alpha'), (20, 'beta');
"

if [ $? -eq 0 ]; then
  echo "PASS: second table seeded"
else
  echo "FAIL: could not seed second table"
  exit 1
fi
```

### Test 3.2: Create multi-datasource project

```bash
PROJECT_DIR=$(mktemp -d)
echo "Project directory: ${PROJECT_DIR}"

cat > "${PROJECT_DIR}/forage-datasource-factory.properties" <<PROPS
forage.ds1.jdbc.db.kind=postgresql
forage.ds1.jdbc.url=jdbc:postgresql://localhost:${POSTGRES_PORT}/testdb
forage.ds1.jdbc.username=test
forage.ds1.jdbc.password=test

forage.ds2.jdbc.db.kind=h2
forage.ds2.jdbc.url=jdbc:h2:mem:secondary
forage.ds2.jdbc.username=sa
forage.ds2.jdbc.password=
PROPS

cat > "${PROJECT_DIR}/route.camel.yaml" <<'ROUTE'
- route:
    id: multi-ds-test
    from:
      uri: timer
      parameters:
        timerName: multipoll
        period: "1000"
        repeatCount: "1"
      steps:
        - setBody:
            simple:
              expression: select * from bar
        - to:
            uri: jdbc
            parameters:
              dataSourceName: ds1
        - log:
            message: "ds1 result: ${body}"
        - setBody:
            simple:
              expression: SELECT 1 AS x
        - to:
            uri: jdbc
            parameters:
              dataSourceName: ds2
        - log:
            message: "ds2 result: ${body}"
ROUTE

if [ -f "${PROJECT_DIR}/forage-datasource-factory.properties" ] && [ -f "${PROJECT_DIR}/route.camel.yaml" ]; then
  echo "PASS: multi-datasource project files created"
else
  echo "FAIL: project files not created"
  exit 1
fi
```

### Test 3.3: Run multi-datasource route and verify both outputs

```bash
camel run "${PROJECT_DIR}"/* > "${PROJECT_DIR}/output.log" 2>&1 &
CAMEL_PID=$!

for i in $(seq 1 60); do
  grep -q "ds2 result:" "${PROJECT_DIR}/output.log" 2>/dev/null && break
  sleep 1
done

grep -q "ds1 result:" "${PROJECT_DIR}/output.log"
DS1_FOUND=$?

grep -q "ds2 result:" "${PROJECT_DIR}/output.log"
DS2_FOUND=$?

if [ "${DS1_FOUND}" -eq 0 ] && [ "${DS2_FOUND}" -eq 0 ]; then
  echo "PASS: both datasources returned results"
else
  echo "FAIL: missing datasource output (ds1=${DS1_FOUND}, ds2=${DS2_FOUND})"
  cat "${PROJECT_DIR}/output.log"
fi

kill "${CAMEL_PID}" 2>/dev/null || true
wait "${CAMEL_PID}" 2>/dev/null || true
rm -rf "${PROJECT_DIR}"
```

---

## Phase 4: Connection Pool Settings

Verifies that explicit pool configuration and XA transaction settings are accepted.

### Test 4.1: Create project with full pool configuration

```bash
PROJECT_DIR=$(mktemp -d)
echo "Project directory: ${PROJECT_DIR}"

cat > "${PROJECT_DIR}/forage-datasource-factory.properties" <<PROPS
forage.jdbc.db.kind=postgresql
forage.jdbc.url=jdbc:postgresql://localhost:${POSTGRES_PORT}/testdb
forage.jdbc.username=test
forage.jdbc.password=test
forage.jdbc.pool.initial.size=2
forage.jdbc.pool.min.size=1
forage.jdbc.pool.max.size=5
forage.jdbc.pool.acquisition.timeout.seconds=10
forage.jdbc.pool.validation.timeout.seconds=5
forage.jdbc.pool.leak.timeout.minutes=15
forage.jdbc.pool.idle.validation.timeout.minutes=5
PROPS

cat > "${PROJECT_DIR}/route.camel.yaml" <<'ROUTE'
- route:
    id: pool-test
    from:
      uri: timer
      parameters:
        timerName: poolpoll
        period: "1000"
        repeatCount: "1"
      steps:
        - setBody:
            simple:
              expression: select * from bar
        - to:
            uri: jdbc
            parameters:
              dataSourceName: dataSource
        - log:
            message: "Pool test result: ${body}"
ROUTE

if [ -f "${PROJECT_DIR}/forage-datasource-factory.properties" ] && [ -f "${PROJECT_DIR}/route.camel.yaml" ]; then
  echo "PASS: pool config project files created"
else
  echo "FAIL: project files not created"
  exit 1
fi
```

### Test 4.2: Run route with pool configuration and verify

```bash
camel run "${PROJECT_DIR}"/* > "${PROJECT_DIR}/output.log" 2>&1 &
CAMEL_PID=$!

for i in $(seq 1 60); do
  grep -q "Pool test result:" "${PROJECT_DIR}/output.log" 2>/dev/null && break
  sleep 1
done

grep -q "Pool test result:" "${PROJECT_DIR}/output.log"
if [ $? -eq 0 ]; then
  echo "PASS: route with explicit pool settings executed successfully"
else
  echo "FAIL: pool test result not found in output"
  cat "${PROJECT_DIR}/output.log"
fi

kill "${CAMEL_PID}" 2>/dev/null || true
wait "${CAMEL_PID}" 2>/dev/null || true
rm -rf "${PROJECT_DIR}"
```

### Test 4.3: Create project with XA transactions enabled

```bash
PROJECT_DIR=$(mktemp -d)
echo "Project directory: ${PROJECT_DIR}"

cat > "${PROJECT_DIR}/forage-datasource-factory.properties" <<PROPS
forage.jdbc.db.kind=postgresql
forage.jdbc.url=jdbc:postgresql://localhost:${POSTGRES_PORT}/testdb
forage.jdbc.username=test
forage.jdbc.password=test
forage.jdbc.transaction.enabled=true
forage.jdbc.transaction.timeout.seconds=30
PROPS

cat > "${PROJECT_DIR}/route.camel.yaml" <<'ROUTE'
- route:
    id: xa-test
    from:
      uri: timer
      parameters:
        timerName: xapoll
        period: "1000"
        repeatCount: "1"
      steps:
        - setBody:
            simple:
              expression: select * from bar
        - to:
            uri: jdbc
            parameters:
              dataSourceName: dataSource
        - log:
            message: "XA test result: ${body}"
ROUTE

if [ -f "${PROJECT_DIR}/forage-datasource-factory.properties" ] && [ -f "${PROJECT_DIR}/route.camel.yaml" ]; then
  echo "PASS: XA transaction project files created"
else
  echo "FAIL: project files not created"
  exit 1
fi
```

### Test 4.4: Run route with XA transactions and verify

```bash
camel run "${PROJECT_DIR}"/* > "${PROJECT_DIR}/output.log" 2>&1 &
CAMEL_PID=$!

for i in $(seq 1 60); do
  grep -q "XA test result:" "${PROJECT_DIR}/output.log" 2>/dev/null && break
  sleep 1
done

grep -q "XA test result:" "${PROJECT_DIR}/output.log"
if [ $? -eq 0 ]; then
  echo "PASS: route with XA transactions executed successfully"
else
  echo "FAIL: XA test result not found in output"
  cat "${PROJECT_DIR}/output.log"
fi

kill "${CAMEL_PID}" 2>/dev/null || true
wait "${CAMEL_PID}" 2>/dev/null || true
rm -rf "${PROJECT_DIR}"
```

---

## Phase 5: Negative -- Invalid Properties with --strict

Verifies that `camel forage run --strict` rejects typos in property names.

### Test 5.1: Create project with typo in property name

```bash
PROJECT_DIR=$(mktemp -d)
echo "Project directory: ${PROJECT_DIR}"

cat > "${PROJECT_DIR}/forage-datasource-factory.properties" <<'PROPS'
forage.jdbc.db.kind=postgresql
forage.jdbc.usernam=test
forage.jdbc.password=test
PROPS

cat > "${PROJECT_DIR}/route.camel.yaml" <<'ROUTE'
- route:
    id: strict-test
    from:
      uri: timer
      parameters:
        timerName: strictpoll
        period: "1000"
        repeatCount: "1"
      steps:
        - log:
            message: "This should not appear"
ROUTE

if [ -f "${PROJECT_DIR}/forage-datasource-factory.properties" ] && [ -f "${PROJECT_DIR}/route.camel.yaml" ]; then
  echo "PASS: invalid properties project files created"
else
  echo "FAIL: project files not created"
  exit 1
fi
```

### Test 5.2: Run with --strict and verify failure

```bash
camel forage run --strict "${PROJECT_DIR}"/* > "${PROJECT_DIR}/strict-output.log" 2>&1
STRICT_EXIT=$?

if [ "${STRICT_EXIT}" -ne 0 ]; then
  echo "PASS: camel forage run --strict failed with exit code ${STRICT_EXIT}"
else
  echo "FAIL: camel forage run --strict should have failed but exited with 0"
fi
```

### Test 5.3: Verify error mentions invalid property

```bash
if grep -qi "usernam" "${PROJECT_DIR}/strict-output.log"; then
  echo "PASS: error output mentions the typo property 'usernam'"
else
  echo "FAIL: error output does not reference the invalid property"
  cat "${PROJECT_DIR}/strict-output.log"
fi

rm -rf "${PROJECT_DIR}"
```

---

## Phase 6: Cleanup

### Step 6.1: Kill any remaining Camel processes

```bash
if [ -n "${CAMEL_PID}" ]; then
  kill "${CAMEL_PID}" 2>/dev/null || true
  wait "${CAMEL_PID}" 2>/dev/null || true
  echo "PASS: Camel process stopped"
else
  echo "PASS: no Camel process to stop"
fi
```

### Step 6.2: Remove Docker containers

```bash
${CONTAINER_RUNTIME} rm -f "${POSTGRES_CONTAINER}" 2>/dev/null || true
echo "PASS: PostgreSQL container removed (or already absent)"
```

### Step 6.3: Remove temp directories

```bash
rm -rf "${PROJECT_DIR}" 2>/dev/null || true
echo "PASS: temp directories cleaned up"
```

---

## Test Summary

| Phase | Test ID | Test Name | Priority |
|-------|---------|-----------|----------|
| 0 | 0.1 | Verify required tools | Critical |
| 0 | 0.2 | Verify Java version >= 17 | Critical |
| 1 | 1.1 | Create H2 project files | Critical |
| 1 | 1.2 | Run H2 route and verify output | Critical |
| 2 | 2.1 | Start PostgreSQL container | Critical |
| 2 | 2.2 | Seed the database | Critical |
| 2 | 2.3 | Create PostgreSQL project files | Critical |
| 2 | 2.4 | Run PostgreSQL route and verify output | Critical |
| 2 | 2.5 | Verify query returned expected data | High |
| 3 | 3.1 | Seed PostgreSQL with second table | High |
| 3 | 3.2 | Create multi-datasource project | Critical |
| 3 | 3.3 | Run multi-datasource route and verify both outputs | Critical |
| 4 | 4.1 | Create project with full pool configuration | High |
| 4 | 4.2 | Run route with pool settings and verify | High |
| 4 | 4.3 | Create project with XA transactions | High |
| 4 | 4.4 | Run route with XA transactions and verify | High |
| 5 | 5.1 | Create project with typo in property | High |
| 5 | 5.2 | Run with --strict and verify failure | High |
| 5 | 5.3 | Verify error mentions invalid property | Medium |
| 6 | 6.1 | Kill remaining Camel processes | Critical |
| 6 | 6.2 | Remove Docker containers | Critical |
| 6 | 6.3 | Remove temp directories | Critical |
