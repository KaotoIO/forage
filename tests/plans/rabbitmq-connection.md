# Test Plan: RabbitMQ Connection Factory Provisioning

## Overview

This test plan verifies that Forage correctly provisions Spring RabbitMQ `CachingConnectionFactory` beans from `forage-spring-rabbitmq.properties` configuration. It creates sample projects and runs them with `camel forage run`, confirming that:

1. Default (unprefixed) properties create a `rabbitConnectionFactory` bean
2. Named/prefixed properties (e.g., `forage.mq1.rabbitmq.*`) create a bean named by the prefix (e.g., `#mq1`)

Both phases verify end-to-end message flow: a timer-driven producer sends messages to a RabbitMQ exchange, and a consumer reads them back, confirming the connection factory is wired correctly.

## Prerequisites

### Required tools

| Tool | Minimum version | Verify command |
|------|-----------------|----------------|
| `java` | 17+ | `java -version` |
| `camel` (JBang) | 4.16+ | `camel version` |
| `docker` or `podman` | any | `docker --version` or `podman --version` |

### Prerequisite check

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
  echo "PASS: all prerequisites met (container runtime: ${CONTAINER_RUNTIME})"
else
  echo "FAIL: one or more tools missing (java=${JAVA_EXIT}, camel=${CAMEL_EXIT})"
  exit 1
fi
```

If Camel JBang is not installed, see [common/forage-run.md](common/forage-run.md).

### Environment variables

| Variable | Default | Description |
|----------|---------|-------------|
| `RABBITMQ_PORT` | `5672` | Host port mapped to RabbitMQ AMQP port |

---

## Phase 0: Prerequisites

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

## Phase 1: Default RabbitMQ Connection

This phase verifies that unprefixed `forage.rabbitmq.*` properties create a bean named `rabbitConnectionFactory` and that routes can use it via `connectionFactory=#rabbitConnectionFactory`.

### Step 1.1: Start RabbitMQ container

Follow [common/start-container.md#rabbitmq](common/start-container.md#rabbitmq) to start RabbitMQ.

After completion, the following variables must be set:

- `RABBITMQ_CONTAINER` -- container name
- `RABBITMQ_PORT` -- mapped AMQP port

### Test 1.2: Create project with default configuration

```bash
PROJECT_DIR=$(mktemp -d)

cat > "${PROJECT_DIR}/forage-spring-rabbitmq.properties" <<EOF
forage.rabbitmq.host=localhost
forage.rabbitmq.port=${RABBITMQ_PORT}
forage.rabbitmq.username=guest
forage.rabbitmq.password=guest
forage.rabbitmq.virtual.host=/
camel.component.spring-rabbitmq.auto-declare=true
EOF

cat > "${PROJECT_DIR}/route.camel.yaml" <<'EOF'
- route:
    id: producer-route
    from:
      uri: timer
      parameters:
        timerName: foo
        period: "2000"
        repeatCount: 3
      steps:
        - setBody:
            simple:
              expression: Hello RabbitMQ ${exchangeProperty.CamelTimerCounter}
        - to:
            id: to-rabbitmq
            uri: spring-rabbitmq
            parameters:
              exchangeName: fooExchange
              routingKey: test
              connectionFactory: "#rabbitConnectionFactory"
- route:
    id: consumer-route
    from:
      id: from-rabbitmq
      uri: spring-rabbitmq
      parameters:
        exchangeName: fooExchange
        queues: myqueue
        routingKey: test
        connectionFactory: "#rabbitConnectionFactory"
      steps:
        - log:
            id: log-message
            message: "Received: ${body}"
EOF

if [ -f "${PROJECT_DIR}/forage-spring-rabbitmq.properties" ] && [ -f "${PROJECT_DIR}/route.camel.yaml" ]; then
  echo "PASS: project files created in ${PROJECT_DIR}"
else
  echo "FAIL: project files not created"
  exit 1
fi
```

### Test 1.3: Run Camel and verify message flow

```bash
camel run "${PROJECT_DIR}"/* > "${PROJECT_DIR}/output.log" 2>&1 &
CAMEL_PID=$!

FOUND=0
for i in $(seq 1 60); do
  grep -q "Received: Hello RabbitMQ" "${PROJECT_DIR}/output.log" 2>/dev/null && { FOUND=1; break; }
  sleep 1
done

if [ "${FOUND}" -eq 1 ]; then
  echo "PASS: default connection factory delivered messages successfully"
else
  echo "FAIL: expected log message not found within 60 seconds"
  echo "--- output.log ---"
  cat "${PROJECT_DIR}/output.log"
  echo "--- end ---"
fi

kill "${CAMEL_PID}" 2>/dev/null || true
wait "${CAMEL_PID}" 2>/dev/null || true
```

### Step 1.4: Clean up default project

```bash
rm -rf "${PROJECT_DIR}" || true
```

---

## Phase 2: Named Connection Factory

This phase verifies that prefixed properties (e.g., `forage.mq1.rabbitmq.*`) create a bean named by the prefix (`mq1`), and routes can reference it via `connectionFactory=#mq1`.

Uses the same RabbitMQ container from Phase 1.

### Test 2.1: Create project with named/prefixed configuration

```bash
PROJECT_DIR=$(mktemp -d)

cat > "${PROJECT_DIR}/forage-spring-rabbitmq.properties" <<EOF
forage.mq1.rabbitmq.host=localhost
forage.mq1.rabbitmq.port=${RABBITMQ_PORT}
forage.mq1.rabbitmq.username=guest
forage.mq1.rabbitmq.password=guest
forage.mq1.rabbitmq.virtual.host=/
camel.component.spring-rabbitmq.auto-declare=true
EOF

cat > "${PROJECT_DIR}/route.camel.yaml" <<'EOF'
- route:
    id: named-producer-route
    from:
      uri: timer
      parameters:
        timerName: foo
        period: "2000"
        repeatCount: 3
      steps:
        - setBody:
            simple:
              expression: Hello Named RabbitMQ ${exchangeProperty.CamelTimerCounter}
        - to:
            id: to-rabbitmq
            uri: spring-rabbitmq
            parameters:
              exchangeName: fooExchange
              routingKey: test
              connectionFactory: "#mq1"
- route:
    id: named-consumer-route
    from:
      id: from-rabbitmq
      uri: spring-rabbitmq
      parameters:
        exchangeName: fooExchange
        queues: myqueue
        routingKey: test
        connectionFactory: "#mq1"
      steps:
        - log:
            id: log-message
            message: "Received: ${body}"
EOF

if [ -f "${PROJECT_DIR}/forage-spring-rabbitmq.properties" ] && [ -f "${PROJECT_DIR}/route.camel.yaml" ]; then
  echo "PASS: named project files created in ${PROJECT_DIR}"
else
  echo "FAIL: named project files not created"
  exit 1
fi
```

### Test 2.2: Run Camel and verify named bean message flow

```bash
camel run "${PROJECT_DIR}"/* > "${PROJECT_DIR}/output.log" 2>&1 &
CAMEL_PID=$!

FOUND=0
for i in $(seq 1 60); do
  grep -q "Received: Hello Named RabbitMQ" "${PROJECT_DIR}/output.log" 2>/dev/null && { FOUND=1; break; }
  sleep 1
done

if [ "${FOUND}" -eq 1 ]; then
  echo "PASS: named connection factory (mq1) delivered messages successfully"
else
  echo "FAIL: expected log message not found within 60 seconds"
  echo "--- output.log ---"
  cat "${PROJECT_DIR}/output.log"
  echo "--- end ---"
fi

kill "${CAMEL_PID}" 2>/dev/null || true
wait "${CAMEL_PID}" 2>/dev/null || true
```

### Step 2.3: Clean up named project

```bash
rm -rf "${PROJECT_DIR}" || true
```

---

## Phase 3: Cleanup

### Step 3.1: Kill Camel process (if still running)

```bash
if [ -n "${CAMEL_PID}" ]; then
  kill "${CAMEL_PID}" 2>/dev/null || true
  wait "${CAMEL_PID}" 2>/dev/null || true
  echo "PASS: Camel process ${CAMEL_PID} stopped"
else
  echo "PASS: no CAMEL_PID set, nothing to stop"
fi
```

### Step 3.2: Remove RabbitMQ container

```bash
${CONTAINER_RUNTIME} rm -f "${RABBITMQ_CONTAINER}" 2>/dev/null || true
echo "PASS: RabbitMQ container removed"
```

### Step 3.3: Remove temporary directories

```bash
rm -rf "${PROJECT_DIR}" 2>/dev/null || true
echo "PASS: temporary directories cleaned up"
```

### Step 3.4: Verify RabbitMQ container is gone

```bash
${CONTAINER_RUNTIME} ps -a --filter "name=${RABBITMQ_CONTAINER}" --format '{{.Names}}' | grep -q "${RABBITMQ_CONTAINER}"
if [ $? -ne 0 ]; then
  echo "PASS: RabbitMQ container is no longer present"
else
  echo "FAIL: RabbitMQ container still exists"
fi
```

---

## Test Summary

| Phase | Test ID | Test Name | Priority |
|-------|---------|-----------|----------|
| 0 | 0.1 | Verify required tools | Critical |
| 0 | 0.2 | Verify Java version >= 17 | Critical |
| 1 | 1.1 | Start RabbitMQ container | Critical |
| 1 | 1.2 | Create project with default configuration | Critical |
| 1 | 1.3 | Run Camel and verify message flow (default bean) | Critical |
| 1 | 1.4 | Clean up default project | High |
| 2 | 2.1 | Create project with named/prefixed configuration | Critical |
| 2 | 2.2 | Run Camel and verify named bean message flow | Critical |
| 2 | 2.3 | Clean up named project | High |
| 3 | 3.1 | Kill Camel process | Critical |
| 3 | 3.2 | Remove RabbitMQ container | Critical |
| 3 | 3.3 | Remove temporary directories | High |
| 3 | 3.4 | Verify RabbitMQ container is gone | High |
