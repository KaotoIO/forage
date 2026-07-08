# Test Plan: Route Policy Provisioning

## Overview

End-to-end verification that Forage discovers route policy providers (flip, schedule) via ServiceLoader, creates policy instances from properties-based configuration, and applies them to Camel routes so that active/passive flipping and time-based scheduling work out of the box.

Users write a `forage-policy.properties` file with `forage.route.policy.*` settings and Camel YAML routes. On `camel run`, the Forage plugin discovers enabled policies via `DefaultCamelForageRoutePolicyFactoryBean`, resolves per-route policy names from `forage.route.policy.<routeId>.name`, and delegates to the matching `RoutePolicyProvider` (flip or schedule) which configures and attaches a `RoutePolicy` to each route.

Every step is fully automatable.

### Known limitation

Route policy modules (`forage-policy-factory`, `forage-policy-flip`, `forage-policy-schedule`) are **not** in the Forage catalog. This means the JBang plugin's catalog-driven dependency resolution does not automatically add them to the classpath. These tests will fail until route policy support is added to the catalog or a manual `--dep` flag is used. See the project issue tracker for status.

## Prerequisites

### Required tools

| Tool | Minimum version | Verify command |
|------|-----------------|----------------|
| `java` | 17+ | `java -version` |
| `camel` (JBang) | 4.16+ | `camel version` |

No Docker is required. All tests use Camel timer routes and log output verification.

If Camel JBang is not installed, see [common/forage-run.md](common/forage-run.md).

### Prerequisite check script

```bash
FAIL=0

for CMD in java camel; do
  if ! command -v "${CMD}" > /dev/null 2>&1; then
    echo "FAIL: ${CMD} is not installed"
    FAIL=1
  else
    echo "PASS: ${CMD} found at $(command -v ${CMD})"
  fi
done

if [ "${FAIL}" -ne 0 ]; then
  echo ""
  echo "FAIL: one or more prerequisites missing"
  exit 1
fi

echo ""
echo "PASS: all prerequisites met"
```

---

## Phase 0: Prerequisites

### Test 0.1: Verify tools

```bash
FAIL=0

for CMD in java camel; do
  if ! command -v "${CMD}" > /dev/null 2>&1; then
    echo "FAIL: ${CMD} is not installed"
    FAIL=1
  else
    echo "PASS: ${CMD} found"
  fi
done

if [ "${FAIL}" -ne 0 ]; then
  echo "FAIL: prerequisites not met"
  exit 1
fi
echo "PASS: all prerequisites met"
```

---

## Phase 1: Flip Route Policy — Active/Passive

Verify that when a flip policy is configured, only the initially-active route runs. The paired (passive) route should be stopped at startup.

### Step 1.1: Create project directory

```bash
PROJECT_DIR=$(mktemp -d)

cat > "${PROJECT_DIR}/forage-policy.properties" <<'PROPS'
forage.route.policy.enabled=true

forage.route.policy.route-primary.name=flip
forage.route.policy.route-primary.flip.initially-active=true
forage.route.policy.route-primary.flip.enabled=true
forage.route.policy.route-primary.flip.paired-route=route-secondary

forage.route.policy.route-secondary.name=flip
forage.route.policy.route-secondary.flip.enabled=true
forage.route.policy.route-secondary.flip.paired-route=route-primary
PROPS

cat > "${PROJECT_DIR}/route.camel.yaml" <<'ROUTE'
- route:
    id: route-primary
    from:
      uri: timer
      parameters:
        timerName: primary
        period: "2000"
      steps:
        - log:
            message: "PRIMARY is running"
- route:
    id: route-secondary
    from:
      uri: timer
      parameters:
        timerName: secondary
        period: "2000"
      steps:
        - log:
            message: "SECONDARY is running"
ROUTE

echo "PASS: project directory created at ${PROJECT_DIR}"
```

### Step 1.2: Run Camel in background

```bash
camel run "${PROJECT_DIR}"/* > "${PROJECT_DIR}/output.log" 2>&1 &
CAMEL_PID=$!
echo "Camel started with PID ${CAMEL_PID}"
```

### Test 1.3: Verify PRIMARY route is running

```bash
for i in $(seq 1 30); do
  grep -q "PRIMARY is running" "${PROJECT_DIR}/output.log" 2>/dev/null && break
  sleep 1
done

grep -q "PRIMARY is running" "${PROJECT_DIR}/output.log"
if [ $? -eq 0 ]; then
  echo "PASS: PRIMARY route is running"
else
  echo "FAIL: PRIMARY route log message not found"
  cat "${PROJECT_DIR}/output.log"
fi
```

### Test 1.4: Verify SECONDARY route is NOT running

The secondary route is configured with `initially-active=false`, so it should be stopped by the flip policy at startup.

```bash
sleep 5

if grep -q "SECONDARY is running" "${PROJECT_DIR}/output.log"; then
  echo "FAIL: SECONDARY route should NOT be running (it is the passive route)"
else
  echo "PASS: SECONDARY route is correctly stopped (passive)"
fi
```

### Step 1.5: Cleanup

```bash
kill "${CAMEL_PID}" 2>/dev/null || true
wait "${CAMEL_PID}" 2>/dev/null || true
rm -rf "${PROJECT_DIR}" || true
echo "PASS: Phase 1 cleanup complete"
```

---

## Phase 2: Schedule Route Policy — Within Active Window

Verify that a route with a schedule policy runs when the current time falls within the configured start/stop window.

### Step 2.1: Create project directory

```bash
PROJECT_DIR=$(mktemp -d)

cat > "${PROJECT_DIR}/forage-policy.properties" <<'PROPS'
forage.route.policy.enabled=true
forage.route.policy.scheduled-route.name=schedule
forage.route.policy.scheduled-route.schedule.start-time=00:00
forage.route.policy.scheduled-route.schedule.stop-time=23:59
PROPS

cat > "${PROJECT_DIR}/route.camel.yaml" <<'ROUTE'
- route:
    id: scheduled-route
    from:
      uri: timer
      parameters:
        timerName: scheduled
        period: "2000"
      steps:
        - log:
            message: "SCHEDULED is running"
ROUTE

echo "PASS: project directory created at ${PROJECT_DIR}"
```

### Step 2.2: Run Camel in background

```bash
camel run "${PROJECT_DIR}"/* > "${PROJECT_DIR}/output.log" 2>&1 &
CAMEL_PID=$!
echo "Camel started with PID ${CAMEL_PID}"
```

### Test 2.3: Verify scheduled route is running

```bash
for i in $(seq 1 30); do
  grep -q "SCHEDULED is running" "${PROJECT_DIR}/output.log" 2>/dev/null && break
  sleep 1
done

grep -q "SCHEDULED is running" "${PROJECT_DIR}/output.log"
if [ $? -eq 0 ]; then
  echo "PASS: SCHEDULED route is running within active window"
else
  echo "FAIL: SCHEDULED route log message not found"
  cat "${PROJECT_DIR}/output.log"
fi
```

### Step 2.4: Cleanup

```bash
kill "${CAMEL_PID}" 2>/dev/null || true
wait "${CAMEL_PID}" 2>/dev/null || true
rm -rf "${PROJECT_DIR}" || true
echo "PASS: Phase 2 cleanup complete"
```

---

## Phase 3: Schedule Route Policy — Outside Active Window

Verify that a route with a schedule policy does NOT run when the current time falls outside the configured start/stop window. Uses a narrow one-minute window at 03:00-03:01 which is unlikely to overlap with test execution.

### Step 3.1: Create project directory

```bash
PROJECT_DIR=$(mktemp -d)

cat > "${PROJECT_DIR}/forage-policy.properties" <<'PROPS'
forage.route.policy.enabled=true
forage.route.policy.off-hours-route.name=schedule
forage.route.policy.off-hours-route.schedule.start-time=03:00
forage.route.policy.off-hours-route.schedule.stop-time=03:01
PROPS

cat > "${PROJECT_DIR}/route.camel.yaml" <<'ROUTE'
- route:
    id: off-hours-route
    from:
      uri: timer
      parameters:
        timerName: offhours
        period: "2000"
      steps:
        - log:
            message: "OFF-HOURS should not run"
ROUTE

echo "PASS: project directory created at ${PROJECT_DIR}"
```

### Step 3.2: Run Camel in background

```bash
camel run "${PROJECT_DIR}"/* > "${PROJECT_DIR}/output.log" 2>&1 &
CAMEL_PID=$!
echo "Camel started with PID ${CAMEL_PID}"
```

### Test 3.3: Verify route does NOT produce log messages

Wait for the schedule policy's initial check (runs immediately on start, then every 60 seconds). Give it 15 seconds — if the schedule check works correctly, the route should be stopped and no log messages should appear.

```bash
sleep 15

if grep -q "OFF-HOURS should not run" "${PROJECT_DIR}/output.log"; then
  echo "FAIL: OFF-HOURS route should NOT be running (outside schedule window)"
  echo "Note: This test assumes execution is NOT between 03:00-03:01 local time"
else
  echo "PASS: OFF-HOURS route correctly stopped (outside schedule window)"
fi
```

### Test 3.4: Verify schedule policy logged its decision

```bash
if grep -q "exiting schedule window\|not active on\|schedule check" "${PROJECT_DIR}/output.log"; then
  echo "PASS: schedule policy log messages found"
else
  echo "INFO: no schedule policy log messages found (may need DEBUG log level)"
fi
```

### Step 3.5: Cleanup

```bash
kill "${CAMEL_PID}" 2>/dev/null || true
wait "${CAMEL_PID}" 2>/dev/null || true
rm -rf "${PROJECT_DIR}" || true
echo "PASS: Phase 3 cleanup complete"
```

---

## Phase 4: Policy Disabled

Verify that when `forage.route.policy.enabled=false`, no policies are applied and all routes run normally regardless of flip/schedule configuration.

### Step 4.1: Create project directory

```bash
PROJECT_DIR=$(mktemp -d)

cat > "${PROJECT_DIR}/forage-policy.properties" <<'PROPS'
forage.route.policy.enabled=false
forage.route.policy.route-primary.name=flip
forage.route.policy.route-primary.flip.paired-route=route-secondary
forage.route.policy.route-primary.flip.initially-active=true
forage.route.policy.route-secondary.name=flip
forage.route.policy.route-secondary.flip.paired-route=route-primary
forage.route.policy.route-secondary.flip.initially-active=false
PROPS

cat > "${PROJECT_DIR}/route.camel.yaml" <<'ROUTE'
- route:
    id: route-primary
    from:
      uri: timer
      parameters:
        timerName: primary
        period: "2000"
      steps:
        - log:
            message: "PRIMARY is running"
- route:
    id: route-secondary
    from:
      uri: timer
      parameters:
        timerName: secondary
        period: "2000"
      steps:
        - log:
            message: "SECONDARY is running"
ROUTE

echo "PASS: project directory created at ${PROJECT_DIR}"
```

### Step 4.2: Run Camel in background

```bash
camel run "${PROJECT_DIR}"/* > "${PROJECT_DIR}/output.log" 2>&1 &
CAMEL_PID=$!
echo "Camel started with PID ${CAMEL_PID}"
```

### Test 4.3: Verify BOTH routes are running

With policies disabled, no flip policy is applied, so both routes should run normally.

```bash
for i in $(seq 1 30); do
  grep -q "PRIMARY is running" "${PROJECT_DIR}/output.log" 2>/dev/null && \
  grep -q "SECONDARY is running" "${PROJECT_DIR}/output.log" 2>/dev/null && break
  sleep 1
done

PASS_COUNT=0

grep -q "PRIMARY is running" "${PROJECT_DIR}/output.log"
if [ $? -eq 0 ]; then
  echo "PASS: PRIMARY route is running"
  PASS_COUNT=$((PASS_COUNT + 1))
else
  echo "FAIL: PRIMARY route log message not found"
fi

grep -q "SECONDARY is running" "${PROJECT_DIR}/output.log"
if [ $? -eq 0 ]; then
  echo "PASS: SECONDARY route is running"
  PASS_COUNT=$((PASS_COUNT + 1))
else
  echo "FAIL: SECONDARY route log message not found"
fi

if [ "${PASS_COUNT}" -eq 2 ]; then
  echo "PASS: both routes running with policies disabled"
else
  echo "FAIL: expected both routes to run when policies are disabled"
  cat "${PROJECT_DIR}/output.log"
fi
```

### Test 4.4: Verify policy factory logged as disabled

```bash
if grep -q "Route policy factory is disabled" "${PROJECT_DIR}/output.log"; then
  echo "PASS: policy factory correctly reports disabled"
else
  echo "INFO: disabled message not found (may need INFO log level)"
fi
```

### Step 4.5: Cleanup

```bash
kill "${CAMEL_PID}" 2>/dev/null || true
wait "${CAMEL_PID}" 2>/dev/null || true
rm -rf "${PROJECT_DIR}" || true
echo "PASS: Phase 4 cleanup complete"
```

---

## Phase 5: Cleanup

Final cleanup in case any earlier phase was interrupted.

### Step 5.1: Kill any remaining Camel process

```bash
if [ -n "${CAMEL_PID}" ]; then
  kill "${CAMEL_PID}" 2>/dev/null || true
  wait "${CAMEL_PID}" 2>/dev/null || true
  echo "PASS: Camel process stopped"
else
  echo "PASS: no Camel process to stop"
fi
```

### Step 5.2: Remove temporary directory

```bash
if [ -n "${PROJECT_DIR}" ] && [ -d "${PROJECT_DIR}" ]; then
  rm -rf "${PROJECT_DIR}" || true
  echo "PASS: temporary directory removed"
else
  echo "PASS: no temporary directory to remove"
fi
```

---

## Summary Matrix

| Phase | Test ID | Test Name | Priority |
|-------|---------|-----------|----------|
| 0 | 0.1 | Verify prerequisites (java, camel) | High |
| 1 | 1.3 | PRIMARY route runs (flip policy, initially-active=true) | Critical |
| 1 | 1.4 | SECONDARY route stopped (flip policy, initially-active=false) | Critical |
| 2 | 2.3 | Scheduled route runs within active window (00:00-23:59) | Critical |
| 3 | 3.3 | Off-hours route stopped outside schedule window (03:00-03:01) | Critical |
| 3 | 3.4 | Schedule policy logs its decision | Low |
| 4 | 4.3 | Both routes run when policies disabled | Critical |
| 4 | 4.4 | Policy factory logs disabled status | Low |
| 5 | 5.1-5.2 | Final cleanup | High |
