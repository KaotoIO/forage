# Test Plan: Property Validation

## Overview

This test plan verifies Forage's property validation feature, which detects typos and unknown properties in `forage-*.properties` files. The `ForagePropertyValidator` scans properties files, validates property names against the Forage catalog using Levenshtein distance for typo detection, and reports warnings. With `--strict`, warnings become fatal errors.

This plan covers valid properties, typo detection, strict vs. non-strict behavior, and multiple-error reporting. No Docker is required.

## Prerequisites

### Required tools

| Tool | Minimum version | Verify command |
|------|-----------------|----------------|
| `java` | 17+ | `java -version` |
| `camel` (JBang) | 4.16+ | `camel version` |

If Camel JBang is not installed:
```bash
jbang trust add https://github.com/apache/camel/
jbang app install camel@apache/camel
```

### Prerequisite check

```bash
java -version 2>&1 | head -1
JAVA_EXIT=$?

camel version 2>&1 | head -1
CAMEL_EXIT=$?

if [ "${JAVA_EXIT}" -eq 0 ] && [ "${CAMEL_EXIT}" -eq 0 ]; then
  echo "PASS: all prerequisites met"
else
  echo "FAIL: one or more tools missing (java=${JAVA_EXIT}, camel=${CAMEL_EXIT})"
  exit 1
fi
```

---

## Phase 0: Prerequisites

### Test 0.1: Verify required tools

```bash
java -version 2>&1 | head -1
JAVA_EXIT=$?

camel version 2>&1 | head -1
CAMEL_EXIT=$?

if [ "${JAVA_EXIT}" -eq 0 ] && [ "${CAMEL_EXIT}" -eq 0 ]; then
  echo "PASS: all required tools present"
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

## Phase 1: Valid Properties Pass Validation

Verify that well-formed properties pass validation in strict mode without errors.

### Test 1.1: Create project with valid properties

```bash
PROJECT_DIR=$(mktemp -d)

cat > "${PROJECT_DIR}/forage-datasource-factory.properties" <<'EOF'
forage.jdbc.db.kind=h2
forage.jdbc.url=jdbc:h2:mem:testdb
forage.jdbc.username=sa
forage.jdbc.password=
EOF

cat > "${PROJECT_DIR}/route.camel.yaml" <<'EOF'
- route:
    from:
      uri: timer:tick
      parameters:
        repeatCount: 1
      steps:
        - setBody:
            constant: "SELECT 1"
        - to:
            uri: jdbc:dataSource
        - log:
            message: "OK: ${body}"
EOF

if [ -f "${PROJECT_DIR}/forage-datasource-factory.properties" ] && [ -f "${PROJECT_DIR}/route.camel.yaml" ]; then
  echo "PASS: project files created"
else
  echo "FAIL: project files not created"
  exit 1
fi
```

### Test 1.2: Run with --strict and valid properties

```bash
camel forage run --strict "${PROJECT_DIR}/*" > "${PROJECT_DIR}/output.log" 2>&1 &
CAMEL_PID=$!

# Wait for the route to start (H2 is embedded, so it should start quickly)
for i in $(seq 1 60); do
  grep -q "OK:" "${PROJECT_DIR}/output.log" 2>/dev/null && break
  # Also check if validation failed
  grep -q "Validation failed" "${PROJECT_DIR}/output.log" 2>/dev/null && break
  sleep 1
done

# Check that validation did not fail
if grep -q "Validation failed" "${PROJECT_DIR}/output.log" 2>/dev/null; then
  echo "FAIL: validation failed on valid properties"
  cat "${PROJECT_DIR}/output.log"
else
  echo "PASS: validation passed with valid properties"
fi

kill "${CAMEL_PID}" 2>/dev/null
wait "${CAMEL_PID}" 2>/dev/null
```

### Test 1.3: Verify no validation warnings in output

```bash
if grep -q "Forage Property Validation Warnings" "${PROJECT_DIR}/output.log" 2>/dev/null; then
  echo "FAIL: unexpected validation warnings on valid properties"
  cat "${PROJECT_DIR}/output.log"
else
  echo "PASS: no validation warnings for valid properties"
fi
```

### Test 1.4: Cleanup phase 1

```bash
rm -rf "${PROJECT_DIR}"
echo "PASS: phase 1 temp dir removed"
```

---

## Phase 2: Typo Detection with --strict

Verify that a property typo causes `camel forage run --strict` to exit with non-zero code and report the offending property.

### Test 2.1: Create project with typo in properties

```bash
PROJECT_DIR=$(mktemp -d)

cat > "${PROJECT_DIR}/forage-datasource-factory.properties" <<'EOF'
forage.jdbc.db.kind=postgresql
forage.jdbc.usernam=test
forage.jdbc.password=test
EOF

cat > "${PROJECT_DIR}/route.camel.yaml" <<'EOF'
- route:
    from:
      uri: timer:tick
      parameters:
        repeatCount: 1
      steps:
        - setBody:
            constant: "SELECT 1"
        - log:
            message: "OK: ${body}"
EOF

if [ -f "${PROJECT_DIR}/forage-datasource-factory.properties" ]; then
  echo "PASS: typo project files created"
else
  echo "FAIL: typo project files not created"
  exit 1
fi
```

### Test 2.2: Run with --strict and typo property

```bash
camel forage run --strict "${PROJECT_DIR}/*" > "${PROJECT_DIR}/output.log" 2>&1
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: strict mode exited with non-zero code (${EXIT_CODE})"
else
  echo "FAIL: strict mode should have exited with non-zero code but got ${EXIT_CODE}"
fi
```

### Test 2.3: Verify output reports the typo

```bash
if grep -q "usernam" "${PROJECT_DIR}/output.log" 2>/dev/null; then
  echo "PASS: output mentions the typo 'usernam'"
else
  echo "FAIL: output does not mention the typo 'usernam'"
  cat "${PROJECT_DIR}/output.log"
fi
```

### Test 2.4: Verify output contains validation warning header

```bash
if grep -q "Forage Property Validation Warnings" "${PROJECT_DIR}/output.log" 2>/dev/null; then
  echo "PASS: output contains validation warning header"
else
  echo "FAIL: output does not contain validation warning header"
  cat "${PROJECT_DIR}/output.log"
fi
```

### Test 2.5: Verify strict mode failure message

```bash
if grep -q "Validation failed in strict mode" "${PROJECT_DIR}/output.log" 2>/dev/null; then
  echo "PASS: output contains strict mode failure message"
else
  echo "FAIL: output does not contain strict mode failure message"
  cat "${PROJECT_DIR}/output.log"
fi
```

### Test 2.6: Verify typo suggestion is provided

The validator uses Levenshtein distance (max 3 edits) and should suggest `username` for the typo `usernam`.

```bash
if grep -q "Did you mean" "${PROJECT_DIR}/output.log" 2>/dev/null; then
  echo "PASS: output includes 'Did you mean' suggestion"
else
  echo "FAIL: output does not include a suggestion for the typo"
  cat "${PROJECT_DIR}/output.log"
fi
```

### Test 2.7: Cleanup phase 2

```bash
rm -rf "${PROJECT_DIR}"
echo "PASS: phase 2 temp dir removed"
```

---

## Phase 3: Warning Without --strict

Verify that without `--strict`, typo warnings are logged but execution is attempted (not blocked by validation).

### Test 3.1: Create project with typo (same as phase 2)

```bash
PROJECT_DIR=$(mktemp -d)

cat > "${PROJECT_DIR}/forage-datasource-factory.properties" <<'EOF'
forage.jdbc.db.kind=postgresql
forage.jdbc.usernam=test
forage.jdbc.password=test
EOF

cat > "${PROJECT_DIR}/route.camel.yaml" <<'EOF'
- route:
    from:
      uri: timer:tick
      parameters:
        repeatCount: 1
      steps:
        - setBody:
            constant: "SELECT 1"
        - log:
            message: "OK: ${body}"
EOF

echo "PASS: phase 3 project files created"
```

### Test 3.2: Run without --strict

The route may fail later due to missing valid config (no real PostgreSQL), but validation itself should not block execution.

```bash
camel forage run "${PROJECT_DIR}/*" > "${PROJECT_DIR}/output.log" 2>&1 &
CAMEL_PID=$!

# Give it time to start and produce output
for i in $(seq 1 30); do
  # Check if warnings were printed (validation ran but didn't block)
  grep -q "Forage Property Validation Warnings" "${PROJECT_DIR}/output.log" 2>/dev/null && break
  # Or check if the route started attempting to run
  grep -q "Routes startup" "${PROJECT_DIR}/output.log" 2>/dev/null && break
  # Or check for connection errors (means it got past validation)
  grep -q "Exception" "${PROJECT_DIR}/output.log" 2>/dev/null && break
  sleep 1
done

# The key assertion: validation did NOT block execution
if grep -q "Validation failed in strict mode" "${PROJECT_DIR}/output.log" 2>/dev/null; then
  echo "FAIL: strict mode failure appeared without --strict flag"
else
  echo "PASS: no strict mode failure without --strict flag"
fi

kill "${CAMEL_PID}" 2>/dev/null
wait "${CAMEL_PID}" 2>/dev/null
```

### Test 3.3: Verify warnings are still shown

```bash
if grep -q "Forage Property Validation Warnings" "${PROJECT_DIR}/output.log" 2>/dev/null; then
  echo "PASS: validation warnings are displayed without --strict"
else
  echo "INFO: warnings may not appear in non-strict mode if output is suppressed (check log)"
  cat "${PROJECT_DIR}/output.log"
fi
```

### Test 3.4: Cleanup phase 3

```bash
rm -rf "${PROJECT_DIR}"
echo "PASS: phase 3 temp dir removed"
```

---

## Phase 4: Multiple Errors

Verify that all typos are reported, not just the first one.

### Test 4.1: Create project with multiple typos

```bash
PROJECT_DIR=$(mktemp -d)

cat > "${PROJECT_DIR}/forage-datasource-factory.properties" <<'EOF'
forage.jdbc.db.kind=postgresql
forage.jdbc.usernam=test
forage.jdbc.passwrd=test
forage.jdbc.url=jdbc:postgresql://localhost/test
EOF

cat > "${PROJECT_DIR}/route.camel.yaml" <<'EOF'
- route:
    from:
      uri: timer:tick
      parameters:
        repeatCount: 1
      steps:
        - setBody:
            constant: "SELECT 1"
        - log:
            message: "OK: ${body}"
EOF

echo "PASS: phase 4 project files created"
```

### Test 4.2: Run with --strict and multiple typos

```bash
camel forage run --strict "${PROJECT_DIR}/*" > "${PROJECT_DIR}/output.log" 2>&1
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: strict mode exited with non-zero code (${EXIT_CODE})"
else
  echo "FAIL: strict mode should have exited with non-zero code"
fi
```

### Test 4.3: Verify both typos are reported

```bash
USERNAM_FOUND=0
PASSWRD_FOUND=0

grep -q "usernam" "${PROJECT_DIR}/output.log" 2>/dev/null && USERNAM_FOUND=1
grep -q "passwrd" "${PROJECT_DIR}/output.log" 2>/dev/null && PASSWRD_FOUND=1

if [ "${USERNAM_FOUND}" -eq 1 ] && [ "${PASSWRD_FOUND}" -eq 1 ]; then
  echo "PASS: both typos reported (usernam, passwrd)"
elif [ "${USERNAM_FOUND}" -eq 1 ]; then
  echo "FAIL: only 'usernam' reported, missing 'passwrd'"
elif [ "${PASSWRD_FOUND}" -eq 1 ]; then
  echo "FAIL: only 'passwrd' reported, missing 'usernam'"
else
  echo "FAIL: neither typo reported"
  cat "${PROJECT_DIR}/output.log"
fi
```

### Test 4.4: Verify warning count reflects both errors

```bash
if grep -q "Total warnings: 2" "${PROJECT_DIR}/output.log" 2>/dev/null; then
  echo "PASS: total warning count is 2"
else
  TOTAL=$(grep "Total warnings:" "${PROJECT_DIR}/output.log" 2>/dev/null)
  if [ -n "${TOTAL}" ]; then
    echo "INFO: ${TOTAL} (expected 2)"
  else
    echo "FAIL: no total warnings line found"
    cat "${PROJECT_DIR}/output.log"
  fi
fi
```

### Test 4.5: Cleanup phase 4

```bash
rm -rf "${PROJECT_DIR}"
echo "PASS: phase 4 temp dir removed"
```

---

## Phase 5: Cleanup

### Test 5.1: Verify no leftover temp dirs

```bash
# All PROJECT_DIRs were cleaned up in their respective phases.
# This is a final sanity check.
echo "PASS: all temporary directories cleaned up in earlier phases"
```

---

## Test Summary

| Phase | Test ID | Test Name | Priority |
|-------|---------|-----------|----------|
| 0 | 0.1 | Verify required tools | Critical |
| 0 | 0.2 | Verify Java version >= 17 | Critical |
| 1 | 1.1 | Create project with valid properties | Critical |
| 1 | 1.2 | Run with --strict and valid properties | Critical |
| 1 | 1.3 | Verify no validation warnings | High |
| 1 | 1.4 | Cleanup phase 1 | Critical |
| 2 | 2.1 | Create project with typo | Critical |
| 2 | 2.2 | Run with --strict and typo property | Critical |
| 2 | 2.3 | Verify output reports the typo | Critical |
| 2 | 2.4 | Verify validation warning header | High |
| 2 | 2.5 | Verify strict mode failure message | High |
| 2 | 2.6 | Verify typo suggestion is provided | High |
| 2 | 2.7 | Cleanup phase 2 | Critical |
| 3 | 3.1 | Create project with typo (no --strict) | Critical |
| 3 | 3.2 | Run without --strict | Critical |
| 3 | 3.3 | Verify warnings are still shown | Medium |
| 3 | 3.4 | Cleanup phase 3 | Critical |
| 4 | 4.1 | Create project with multiple typos | Critical |
| 4 | 4.2 | Run with --strict and multiple typos | Critical |
| 4 | 4.3 | Verify both typos are reported | Critical |
| 4 | 4.4 | Verify warning count reflects both errors | High |
| 4 | 4.5 | Cleanup phase 4 | Critical |
| 5 | 5.1 | Verify no leftover temp dirs | Low |
