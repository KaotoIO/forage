# Common: Start a Container

Reusable procedure for starting containerized infrastructure used by Forage test plans. Works with both Docker and Podman. Each section is self-contained — jump to the one you need.

## Container Runtime Detection

All test plans use the `CONTAINER_RUNTIME` variable. Set it before running any container commands:

```bash
if [ -z "${CONTAINER_RUNTIME}" ]; then
  if command -v podman > /dev/null 2>&1; then
    CONTAINER_RUNTIME=podman
  elif command -v docker > /dev/null 2>&1; then
    CONTAINER_RUNTIME=docker
  else
    echo "FAIL: neither podman nor docker found"
    exit 1
  fi
fi

echo "PASS: using container runtime: ${CONTAINER_RUNTIME}"
```

**Output variable:** `CONTAINER_RUNTIME` (`docker` or `podman`)

---

## PostgreSQL

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

# Wait for ready
for i in $(seq 1 30); do
  ${CONTAINER_RUNTIME} exec "${POSTGRES_CONTAINER}" pg_isready -U test > /dev/null 2>&1 && break
  sleep 1
done

${CONTAINER_RUNTIME} exec "${POSTGRES_CONTAINER}" pg_isready -U test > /dev/null 2>&1
if [ $? -eq 0 ]; then
  echo "PASS: PostgreSQL is ready on port ${POSTGRES_PORT}"
else
  echo "FAIL: PostgreSQL did not become ready"
  ${CONTAINER_RUNTIME} rm -f "${POSTGRES_CONTAINER}" 2>/dev/null || true
  exit 1
fi
```

**Output variables:** `POSTGRES_CONTAINER`, `POSTGRES_PORT`

**Cleanup:**
```bash
${CONTAINER_RUNTIME} rm -f "${POSTGRES_CONTAINER}" 2>/dev/null || true
```

---

## ActiveMQ Artemis

```bash
ARTEMIS_CONTAINER="forage-test-artemis"
ARTEMIS_PORT="${ARTEMIS_PORT:-61616}"

${CONTAINER_RUNTIME} run -d \
  --name "${ARTEMIS_CONTAINER}" \
  -e AMQ_USER=artemis \
  -e AMQ_PASSWORD=artemis \
  -p "${ARTEMIS_PORT}:61616" \
  quay.io/artemiscloud/activemq-artemis-broker:latest

# Wait for ready
for i in $(seq 1 60); do
  ${CONTAINER_RUNTIME} logs "${ARTEMIS_CONTAINER}" 2>&1 | grep -q "Server is now active" && break
  sleep 1
done

${CONTAINER_RUNTIME} logs "${ARTEMIS_CONTAINER}" 2>&1 | grep -q "Server is now active"
if [ $? -eq 0 ]; then
  echo "PASS: Artemis is ready on port ${ARTEMIS_PORT}"
else
  echo "FAIL: Artemis did not become ready"
  ${CONTAINER_RUNTIME} rm -f "${ARTEMIS_CONTAINER}" 2>/dev/null || true
  exit 1
fi
```

**Output variables:** `ARTEMIS_CONTAINER`, `ARTEMIS_PORT`

**Cleanup:**
```bash
${CONTAINER_RUNTIME} rm -f "${ARTEMIS_CONTAINER}" 2>/dev/null || true
```

---

## RabbitMQ

**Podman rootless note:** The standard RabbitMQ image may fail on Podman rootless with an Erlang cookie permission error. Set `RABBITMQ_ERLANG_COOKIE` to work around it.

```bash
RABBITMQ_CONTAINER="forage-test-rabbitmq"
RABBITMQ_PORT="${RABBITMQ_PORT:-5672}"

${CONTAINER_RUNTIME} run -d \
  --name "${RABBITMQ_CONTAINER}" \
  -e RABBITMQ_DEFAULT_USER=guest \
  -e RABBITMQ_DEFAULT_PASS=guest \
  -e RABBITMQ_ERLANG_COOKIE=forage-test-cookie \
  -p "${RABBITMQ_PORT}:5672" \
  rabbitmq:3-management-alpine

# Wait for ready
for i in $(seq 1 60); do
  ${CONTAINER_RUNTIME} exec "${RABBITMQ_CONTAINER}" rabbitmqctl status > /dev/null 2>&1 && break
  sleep 1
done

${CONTAINER_RUNTIME} exec "${RABBITMQ_CONTAINER}" rabbitmqctl status > /dev/null 2>&1
if [ $? -eq 0 ]; then
  echo "PASS: RabbitMQ is ready on port ${RABBITMQ_PORT}"
else
  echo "FAIL: RabbitMQ did not become ready"
  ${CONTAINER_RUNTIME} rm -f "${RABBITMQ_CONTAINER}" 2>/dev/null || true
  exit 1
fi
```

**Output variables:** `RABBITMQ_CONTAINER`, `RABBITMQ_PORT`

**Cleanup:**
```bash
${CONTAINER_RUNTIME} rm -f "${RABBITMQ_CONTAINER}" 2>/dev/null || true
```
