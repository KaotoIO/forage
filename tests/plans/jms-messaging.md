# JMS Connection Factory Provisioning

End-to-end verification that Forage discovers a JMS provider (Artemis) via ServiceLoader, creates a ConnectionFactory with pooling and optional XA transactions, and registers it in the Camel registry so that `jms:queue.name` routes work out of the box.

## Overview

Users write a `forage-connectionfactory.properties` file with `forage.jms.*` settings and a Camel YAML route that references `jms:queue.name`. On `camel run`, the Forage plugin discovers the JMS provider (based on `forage.jms.kind`), builds a `ConnectionFactory` (optionally pooled via `JmsPoolConnectionFactory`, optionally XA via Narayana), and binds it as `connectionFactory` in the Camel registry.

## Prerequisites

| Tool | Minimum version | Verify command |
|------|-----------------|----------------|
| `java` | 17+ | `java -version` |
| `camel` (JBang) | 4.16+ | `camel version` |
| `docker` or `podman` | any | `docker --version` or `podman --version` |

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `ARTEMIS_PORT` | `61616` | Host port mapped to Artemis AMQP port |

---

## Phase 0: Prerequisites

Verify all required tools are available.

```bash
java -version 2>&1 | head -1
if [ $? -ne 0 ]; then
  echo "FAIL: Java is not installed"
  exit 1
fi

camel version 2>&1 | head -1
if [ $? -ne 0 ]; then
  echo "FAIL: Camel JBang is not installed"
  exit 1
fi

if command -v podman > /dev/null 2>&1; then
  CONTAINER_RUNTIME=podman
elif command -v docker > /dev/null 2>&1; then
  CONTAINER_RUNTIME=docker
else
  echo "FAIL: neither podman nor docker found"
  exit 1
fi

echo "PASS: All prerequisites met (container runtime: ${CONTAINER_RUNTIME})"
```

---

## Phase 1: Simple Artemis Send/Receive

Verify that Forage creates a pooled ConnectionFactory for Artemis and that a producer/consumer route pair can send and receive messages.

### Step 1.1: Start Artemis container

See [Common: Start a Docker Container — ActiveMQ Artemis](common/start-container.md#activemq-artemis).

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

### Step 1.2: Create project directory

```bash
PROJECT_DIR=$(mktemp -d)

cat > "${PROJECT_DIR}/forage-connectionfactory.properties" <<'PROPS'
forage.jms.kind=artemis
forage.jms.broker.url=tcp://localhost:${ARTEMIS_PORT}
forage.jms.username=artemis
forage.jms.password=artemis
forage.jms.pool.enabled=true
forage.jms.pool.max.connections=5
PROPS

# Substitute ARTEMIS_PORT
sed -i.bak "s/\${ARTEMIS_PORT}/${ARTEMIS_PORT}/" "${PROJECT_DIR}/forage-connectionfactory.properties"
rm -f "${PROJECT_DIR}/forage-connectionfactory.properties.bak"

cat > "${PROJECT_DIR}/route.camel.yaml" <<'ROUTE'
- route:
    id: producer-route
    from:
      uri: timer
      parameters:
        timerName: producer
        repeatCount: 3
        period: "2000"
      steps:
        - setBody:
            simple:
              expression: "Message ${exchangeProperty.CamelTimerCounter}"
        - to:
            uri: jms
            parameters:
              destinationName: test.queue
              destinationType: queue
        - log:
            message: "Sent: ${body}"
- route:
    id: consumer-route
    from:
      uri: jms
      parameters:
        destinationName: test.queue
        destinationType: queue
      steps:
        - log:
            message: "Received: ${body}"
ROUTE

echo "PASS: Project created at ${PROJECT_DIR}"
```

### Step 1.3: Run and verify

```bash
camel run "${PROJECT_DIR}"/* > "${PROJECT_DIR}/output.log" 2>&1 &
CAMEL_PID=$!

for i in $(seq 1 60); do
  grep -q "Received: Message" "${PROJECT_DIR}/output.log" 2>/dev/null && break
  sleep 1
done

grep -c "Received: Message" "${PROJECT_DIR}/output.log" 2>/dev/null
RECEIVED_COUNT=$(grep -c "Received: Message" "${PROJECT_DIR}/output.log" 2>/dev/null || echo "0")

if [ "${RECEIVED_COUNT}" -ge 1 ]; then
  echo "PASS: Received ${RECEIVED_COUNT} message(s) via JMS"
else
  echo "FAIL: No 'Received: Message' found in output"
  cat "${PROJECT_DIR}/output.log"
fi

kill "${CAMEL_PID}" 2>/dev/null
wait "${CAMEL_PID}" 2>/dev/null
rm -rf "${PROJECT_DIR}"
```

---

## Phase 2: Transactional JMS with XA

Verify that Forage creates an XA-enabled ConnectionFactory with Narayana transaction manager, and that transacted routes commit/rollback correctly.

### Step 2.1: Create project directory

Uses the same Artemis container from Phase 1.

```bash
PROJECT_DIR=$(mktemp -d)

cat > "${PROJECT_DIR}/forage-connectionfactory.properties" <<PROPS
forage.jms.kind=artemis
forage.jms.broker.url=tcp://localhost:${ARTEMIS_PORT}
forage.jms.username=artemis
forage.jms.password=artemis
forage.jms.pool.enabled=true
forage.jms.pool.max.connections=10
forage.jms.pool.max.sessions.per.connection=500
forage.jms.transaction.enabled=true
forage.jms.transaction.timeout.seconds=30
forage.jms.transaction.node.id=node1
forage.jms.transaction.enable.recovery=true
forage.jms.transaction.object.store.directory=tx-object-store
forage.jms.transaction.object.store.type=file-system
PROPS

cat > "${PROJECT_DIR}/route.camel.yaml" <<'ROUTE'
- route:
    id: producer-route
    from:
      uri: timer
      parameters:
        timerName: producer
        repeatCount: 3
        period: "5000"
      steps:
        - setBody:
            simple:
              expression: "Transactional message ${exchangeProperty.CamelTimerCounter}"
        - setHeader:
            name: counter
            simple:
              expression: "${exchangeProperty.CamelTimerCounter}"
        - log:
            message: "Message sent: ${body}"
        - to:
            uri: jms
            parameters:
              destinationName: input.queue
              destinationType: queue
- route:
    id: transactional-consumer-route
    from:
      uri: jms
      parameters:
        destinationName: input.queue
        destinationType: queue
        transacted: "true"
        cacheLevelName: CACHE_NONE
      steps:
        - transacted:
            ref: PROPAGATION_REQUIRED
        - log:
            message: "Processing message: ${body}"
        - to:
            uri: jms
            parameters:
              destinationName: output.queue
              destinationType: queue
        - log:
            message: "Forwarded to output queue"
- route:
    id: output-consumer-route
    from:
      uri: jms
      parameters:
        destinationName: output.queue
        destinationType: queue
      steps:
        - log:
            message: "Successfully processed message: ${body}"
ROUTE

echo "PASS: XA project created at ${PROJECT_DIR}"
```

### Step 2.2: Run and verify

```bash
camel run "${PROJECT_DIR}"/* > "${PROJECT_DIR}/output.log" 2>&1 &
CAMEL_PID=$!

for i in $(seq 1 60); do
  grep -q "Successfully processed message:" "${PROJECT_DIR}/output.log" 2>/dev/null && break
  sleep 1
done

PROCESSED_COUNT=$(grep -c "Successfully processed message:" "${PROJECT_DIR}/output.log" 2>/dev/null || echo "0")

if [ "${PROCESSED_COUNT}" -ge 1 ]; then
  echo "PASS: ${PROCESSED_COUNT} message(s) flowed through the transactional chain"
else
  echo "FAIL: No messages reached output.queue"
  cat "${PROJECT_DIR}/output.log"
fi

kill "${CAMEL_PID}" 2>/dev/null
wait "${CAMEL_PID}" 2>/dev/null
rm -rf "${PROJECT_DIR}"
```

---

## Phase 3: Connection Pool Settings

Verify that explicit pool tuning properties are accepted and the route functions with non-default pool configuration.

### Step 3.1: Create project directory

Uses the same Artemis container from Phase 1.

```bash
PROJECT_DIR=$(mktemp -d)

cat > "${PROJECT_DIR}/forage-connectionfactory.properties" <<PROPS
forage.jms.kind=artemis
forage.jms.broker.url=tcp://localhost:${ARTEMIS_PORT}
forage.jms.username=artemis
forage.jms.password=artemis
forage.jms.pool.enabled=true
forage.jms.pool.max.connections=2
forage.jms.pool.max.sessions.per.connection=10
forage.jms.pool.idle.timeout.millis=15000
forage.jms.pool.block.if.full=true
forage.jms.pool.block.if.full.timeout.millis=5000
PROPS

cat > "${PROJECT_DIR}/route.camel.yaml" <<'ROUTE'
- route:
    id: producer-route
    from:
      uri: timer
      parameters:
        timerName: producer
        repeatCount: 3
        period: "2000"
      steps:
        - setBody:
            simple:
              expression: "Pooled message ${exchangeProperty.CamelTimerCounter}"
        - to:
            uri: jms
            parameters:
              destinationName: pool.test.queue
              destinationType: queue
        - log:
            message: "Sent: ${body}"
- route:
    id: consumer-route
    from:
      uri: jms
      parameters:
        destinationName: pool.test.queue
        destinationType: queue
      steps:
        - log:
            message: "Pool received: ${body}"
ROUTE

echo "PASS: Pool-tuned project created at ${PROJECT_DIR}"
```

### Step 3.2: Run and verify

```bash
camel run "${PROJECT_DIR}"/* > "${PROJECT_DIR}/output.log" 2>&1 &
CAMEL_PID=$!

for i in $(seq 1 60); do
  grep -q "Pool received: Pooled message" "${PROJECT_DIR}/output.log" 2>/dev/null && break
  sleep 1
done

RECEIVED_COUNT=$(grep -c "Pool received: Pooled message" "${PROJECT_DIR}/output.log" 2>/dev/null || echo "0")

if [ "${RECEIVED_COUNT}" -ge 1 ]; then
  echo "PASS: Received ${RECEIVED_COUNT} message(s) with custom pool settings"
else
  echo "FAIL: No messages received with custom pool settings"
  cat "${PROJECT_DIR}/output.log"
fi

kill "${CAMEL_PID}" 2>/dev/null
wait "${CAMEL_PID}" 2>/dev/null
rm -rf "${PROJECT_DIR}"
```

---

## Phase 4: Cleanup

Remove infrastructure and temporary files.

```bash
${CONTAINER_RUNTIME} rm -f "${ARTEMIS_CONTAINER}" 2>/dev/null || true
echo "PASS: Cleanup complete"
```

---

## Test Summary

| Phase | Description | Validates |
|-------|-------------|-----------|
| 0 | Prerequisites | Java, Camel JBang, Docker installed |
| 1 | Simple Artemis send/receive | ServiceLoader discovery, pooled ConnectionFactory, basic messaging |
| 2 | Transactional JMS with XA | XA ConnectionFactory, Narayana TM, PROPAGATION_REQUIRED, transacted route |
| 3 | Connection pool settings | Non-default pool tuning (max connections, sessions, idle timeout, block-if-full) |
| 4 | Cleanup | Container and temp dir removal |
