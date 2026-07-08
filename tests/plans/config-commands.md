# Test Plan: Config CLI Commands

## Overview

This test plan verifies Forage's `camel forage config read` and `camel forage config write` CLI commands. These commands are part of the Camel JBang plugin at `tooling/camel-jbang-plugin-forage`.

- `config read` scans a directory for properties files, parses `forage.*` properties, and outputs a structured JSON object with `success`, `directory`, `beanCount`, and a `beans` array. It supports filtering by factory type (`--filter`), directory selection (`--dir`), and strategy selection (`--strategy`).
- `config write` accepts JSON input (via `--input` or stdin), maps properties to factory types using the Forage catalog, and writes them to properties files. It prefixes properties with the bean name when `forage.bean.name` is provided.
- `config write --delete` removes all `forage.{instanceName}.*` properties for a given `--name` and cleans up unused dependencies.

Both commands output structured JSON (success/error) and return exit code 0 on success, 1 on error.

**Important**: The existing unit tests (`ConfigReadCommandTest`, `ConfigWriteCommandTest`) are currently `@Disabled`. This plan tests the commands at the CLI level via `camel forage config read|write`. Flag names and JSON structures are derived from the current source code; verify they match the installed Camel JBang plugin version before execution.

Every step is fully automatable.

## Prerequisites

### Required tools

| Tool | Minimum version | Verify command |
|------|-----------------|----------------|
| `java` | 17+ | `java -version` |
| `camel` (JBang) | 4.16+ | `camel version` |
| `jq` | 1.6+ | `jq --version` |

### Prerequisite check script

```bash
FAIL=0

for CMD in java camel jq; do
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

### Camel JBang setup

If Camel JBang is not installed:
```bash
jbang trust add https://github.com/apache/camel/
jbang app install camel@apache/camel
```

The Forage plugin must be available to the Camel JBang runtime. Verify with:
```bash
camel forage --help
```

---

## Phase 0: Prerequisites

### Test 0.1: Verify tools

```bash
FAIL=0

for CMD in java camel jq; do
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

### Test 0.2: Verify Forage plugin is available

```bash
OUTPUT=$(camel forage --help 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: Forage plugin available"
else
  echo "FAIL: Forage plugin not available (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi
```

### Test 0.3: Verify config subcommand is available

```bash
OUTPUT=$(camel forage config --help 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: config subcommand available"
else
  echo "FAIL: config subcommand not available (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi
```

---

## Phase 1: Config Read — No Properties Files

### Test 1.1: Read from empty directory returns success with zero beans

```bash
PROJECT_DIR=$(mktemp -d)

OUTPUT=$(camel forage config read --dir "${PROJECT_DIR}" --strategy application 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: config read failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  rm -rf "${PROJECT_DIR}"
  exit 1
fi

SUCCESS=$(echo "${OUTPUT}" | jq -r '.success')
BEAN_COUNT=$(echo "${OUTPUT}" | jq -r '.beanCount')
MESSAGE=$(echo "${OUTPUT}" | jq -r '.message // empty')

if [ "${SUCCESS}" = "true" ] && [ "${BEAN_COUNT}" = "0" ]; then
  echo "PASS: empty directory returns success with 0 beans"
else
  echo "FAIL: unexpected result (success=${SUCCESS}, beanCount=${BEAN_COUNT})"
  echo "${OUTPUT}"
fi

if echo "${MESSAGE}" | grep -qi "No Forage properties files found"; then
  echo "PASS: message indicates no properties files found"
else
  echo "INFO: message was: ${MESSAGE}"
fi

rm -rf "${PROJECT_DIR}"
```

### Test 1.2: Read from non-existent directory returns error

```bash
OUTPUT=$(camel forage config read --dir "/tmp/nonexistent-forage-dir-12345" --strategy application 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: config read failed for non-existent directory (exit code ${EXIT_CODE})"
else
  SUCCESS=$(echo "${OUTPUT}" | jq -r '.success')
  if [ "${SUCCESS}" = "false" ]; then
    echo "PASS: config read returned success=false for non-existent directory"
  else
    echo "FAIL: config read should fail for non-existent directory"
  fi
fi
```

---

## Phase 2: Config Read — Parse Beans from Properties

### Test 2.1: Read detects JDBC bean from application.properties

```bash
PROJECT_DIR=$(mktemp -d)

cat > "${PROJECT_DIR}/application.properties" <<'EOF'
forage.myPG.jdbc.db.kind=postgresql
forage.myPG.jdbc.url=jdbc:postgresql://localhost:5432/
forage.myPG.jdbc.username=test
forage.myPG.jdbc.password=test
EOF

OUTPUT=$(camel forage config read --dir "${PROJECT_DIR}" --strategy application 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: config read failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  rm -rf "${PROJECT_DIR}"
  exit 1
fi

SUCCESS=$(echo "${OUTPUT}" | jq -r '.success')
BEAN_COUNT=$(echo "${OUTPUT}" | jq -r '.beanCount')

if [ "${SUCCESS}" = "true" ] && [ "${BEAN_COUNT}" -gt 0 ]; then
  echo "PASS: detected ${BEAN_COUNT} bean(s)"
else
  echo "FAIL: expected success=true and beanCount > 0 (success=${SUCCESS}, beanCount=${BEAN_COUNT})"
  echo "${OUTPUT}"
fi

# Verify the myPG bean is present with expected properties
BEAN_NAME=$(echo "${OUTPUT}" | jq -r '.beans[] | select(.name == "myPG") | .name')
BEAN_KIND=$(echo "${OUTPUT}" | jq -r '.beans[] | select(.name == "myPG") | .kind')

if [ "${BEAN_NAME}" = "myPG" ]; then
  echo "PASS: bean 'myPG' found"
else
  echo "FAIL: bean 'myPG' not found"
  echo "${OUTPUT}" | jq '.beans'
fi

if [ "${BEAN_KIND}" = "postgresql" ]; then
  echo "PASS: bean kind is 'postgresql'"
else
  echo "FAIL: expected kind 'postgresql', got '${BEAN_KIND}'"
fi

rm -rf "${PROJECT_DIR}"
```

### Test 2.2: Read detects multiple beans of different factory types

```bash
PROJECT_DIR=$(mktemp -d)

cat > "${PROJECT_DIR}/application.properties" <<'EOF'
forage.myH2.jdbc.db.kind=h2
forage.myH2.jdbc.url=jdbc:h2:mem:test
forage.myH2.jdbc.username=sa
forage.myH2.jdbc.password=
forage.myIBMMQ.jms.kind=ibmmq
forage.myIBMMQ.jms.broker.url=127.0.0.1
forage.myIBMMQ.jms.username=user
forage.test.ollama.model.name=granite4:3b
forage.test.ollama.log.requests=true
forage.myMemory.infinispan.server-list=localhost:11223
EOF

OUTPUT=$(camel forage config read --dir "${PROJECT_DIR}" --strategy application 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: config read failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  rm -rf "${PROJECT_DIR}"
  exit 1
fi

BEAN_COUNT=$(echo "${OUTPUT}" | jq -r '.beanCount')

if [ "${BEAN_COUNT}" -ge 4 ]; then
  echo "PASS: detected ${BEAN_COUNT} beans (expected >= 4)"
else
  echo "FAIL: expected >= 4 beans, got ${BEAN_COUNT}"
  echo "${OUTPUT}" | jq '.beans[].name'
fi

# Check each bean by name
for EXPECTED_NAME in myH2 myIBMMQ test myMemory; do
  FOUND=$(echo "${OUTPUT}" | jq -r ".beans[] | select(.name == \"${EXPECTED_NAME}\") | .name")
  if [ "${FOUND}" = "${EXPECTED_NAME}" ]; then
    echo "PASS: bean '${EXPECTED_NAME}' found"
  else
    echo "FAIL: bean '${EXPECTED_NAME}' not found"
  fi
done

rm -rf "${PROJECT_DIR}"
```

### Test 2.3: Read includes configuration key-value pairs in output

```bash
PROJECT_DIR=$(mktemp -d)

cat > "${PROJECT_DIR}/application.properties" <<'EOF'
forage.myH2.jdbc.db.kind=h2
forage.myH2.jdbc.url=jdbc:h2:mem:test
forage.myH2.jdbc.username=sa
EOF

OUTPUT=$(camel forage config read --dir "${PROJECT_DIR}" --strategy application 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: config read failed (exit code ${EXIT_CODE})"
  rm -rf "${PROJECT_DIR}"
  exit 1
fi

URL_VALUE=$(echo "${OUTPUT}" | jq -r '.beans[] | select(.name == "myH2") | .configuration.url')
USERNAME_VALUE=$(echo "${OUTPUT}" | jq -r '.beans[] | select(.name == "myH2") | .configuration.username')

if [ "${URL_VALUE}" = "jdbc:h2:mem:test" ]; then
  echo "PASS: configuration.url is correct"
else
  echo "FAIL: expected url 'jdbc:h2:mem:test', got '${URL_VALUE}'"
fi

if [ "${USERNAME_VALUE}" = "sa" ]; then
  echo "PASS: configuration.username is correct"
else
  echo "FAIL: expected username 'sa', got '${USERNAME_VALUE}'"
fi

rm -rf "${PROJECT_DIR}"
```

### Test 2.4: Read includes sourceFile in output

```bash
PROJECT_DIR=$(mktemp -d)

cat > "${PROJECT_DIR}/application.properties" <<'EOF'
forage.myH2.jdbc.db.kind=h2
forage.myH2.jdbc.url=jdbc:h2:mem:test
EOF

OUTPUT=$(camel forage config read --dir "${PROJECT_DIR}" --strategy application 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: config read failed (exit code ${EXIT_CODE})"
  rm -rf "${PROJECT_DIR}"
  exit 1
fi

SOURCE_FILE=$(echo "${OUTPUT}" | jq -r '.beans[0].sourceFile')

if echo "${SOURCE_FILE}" | grep -q "application.properties"; then
  echo "PASS: sourceFile points to application.properties"
else
  echo "FAIL: unexpected sourceFile: ${SOURCE_FILE}"
fi

rm -rf "${PROJECT_DIR}"
```

---

## Phase 3: Config Read — Filter by Factory Type

### Test 3.1: Filter shows only matching factory type

```bash
PROJECT_DIR=$(mktemp -d)

cat > "${PROJECT_DIR}/application.properties" <<'EOF'
forage.myH2.jdbc.db.kind=h2
forage.myH2.jdbc.url=jdbc:h2:mem:test
forage.myH2.jdbc.username=sa
forage.myIBMMQ.jms.kind=ibmmq
forage.myIBMMQ.jms.broker.url=127.0.0.1
EOF

OUTPUT=$(camel forage config read --dir "${PROJECT_DIR}" --strategy application --filter jdbc 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: config read with --filter failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  rm -rf "${PROJECT_DIR}"
  exit 1
fi

# Should find H2 (jdbc) but not IBM MQ (jms)
FOUND_H2=$(echo "${OUTPUT}" | jq -r '.beans[] | select(.name == "myH2") | .name')
FOUND_IBMMQ=$(echo "${OUTPUT}" | jq -r '.beans[] | select(.name == "myIBMMQ") | .name')

if [ "${FOUND_H2}" = "myH2" ]; then
  echo "PASS: jdbc bean 'myH2' found with --filter jdbc"
else
  echo "FAIL: jdbc bean 'myH2' not found with --filter jdbc"
fi

if [ -z "${FOUND_IBMMQ}" ]; then
  echo "PASS: jms bean 'myIBMMQ' correctly excluded by --filter jdbc"
else
  echo "FAIL: jms bean 'myIBMMQ' should not appear with --filter jdbc"
fi

rm -rf "${PROJECT_DIR}"
```

### Test 3.2: Filter with non-matching type returns empty list

```bash
PROJECT_DIR=$(mktemp -d)

cat > "${PROJECT_DIR}/application.properties" <<'EOF'
forage.myH2.jdbc.db.kind=h2
forage.myH2.jdbc.url=jdbc:h2:mem:test
EOF

OUTPUT=$(camel forage config read --dir "${PROJECT_DIR}" --strategy application --filter jms 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: config read failed (exit code ${EXIT_CODE})"
  rm -rf "${PROJECT_DIR}"
  exit 1
fi

BEAN_COUNT=$(echo "${OUTPUT}" | jq -r '.beanCount')

if [ "${BEAN_COUNT}" = "0" ]; then
  echo "PASS: --filter jms returns 0 beans when only jdbc exists"
else
  echo "FAIL: expected 0 beans with --filter jms, got ${BEAN_COUNT}"
fi

rm -rf "${PROJECT_DIR}"
```

---

## Phase 4: Config Read — Conditional Beans

### Test 4.1: Read detects conditional beans when features are enabled

```bash
PROJECT_DIR=$(mktemp -d)

cat > "${PROJECT_DIR}/application.properties" <<'EOF'
forage.myPG.jdbc.db.kind=postgresql
forage.myPG.jdbc.url=jdbc:postgresql://localhost:5432/
forage.myPG.jdbc.username=test
forage.myPG.jdbc.password=test
forage.myPG.jdbc.transaction.enabled=true
forage.myPG.jdbc.aggregation.repository.enabled=true
forage.myPG.jdbc.idempotent.repository.enabled=true
EOF

OUTPUT=$(camel forage config read --dir "${PROJECT_DIR}" --strategy application 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: config read failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  rm -rf "${PROJECT_DIR}"
  exit 1
fi

COND_BEAN_COUNT=$(echo "${OUTPUT}" | jq '.beans[] | select(.name == "myPG") | .conditionalBeans | length')

if [ "${COND_BEAN_COUNT}" -gt 0 ]; then
  echo "PASS: found ${COND_BEAN_COUNT} conditional bean(s) for myPG"
else
  echo "FAIL: expected conditional beans for myPG with enabled features"
fi

# Check for PROPAGATION_REQUIRED
FOUND_PROPAGATION=$(echo "${OUTPUT}" | jq -r '.beans[] | select(.name == "myPG") | .conditionalBeans[] | select(.name == "PROPAGATION_REQUIRED") | .name')
if [ "${FOUND_PROPAGATION}" = "PROPAGATION_REQUIRED" ]; then
  echo "PASS: PROPAGATION_REQUIRED conditional bean found"
else
  echo "FAIL: PROPAGATION_REQUIRED conditional bean not found"
fi

rm -rf "${PROJECT_DIR}"
```

### Test 4.2: Read does not produce conditional beans when features are disabled

```bash
PROJECT_DIR=$(mktemp -d)

cat > "${PROJECT_DIR}/application.properties" <<'EOF'
forage.myPG.jdbc.db.kind=postgresql
forage.myPG.jdbc.url=jdbc:postgresql://localhost:5432/
forage.myPG.jdbc.username=test
forage.myPG.jdbc.password=test
EOF

OUTPUT=$(camel forage config read --dir "${PROJECT_DIR}" --strategy application 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: config read failed (exit code ${EXIT_CODE})"
  rm -rf "${PROJECT_DIR}"
  exit 1
fi

# Without transaction.enabled=true, no conditional beans should appear
COND_BEANS=$(echo "${OUTPUT}" | jq '.beans[] | select(.name == "myPG") | .conditionalBeans // [] | length')

if [ "${COND_BEANS}" = "0" ] || [ -z "${COND_BEANS}" ]; then
  echo "PASS: no conditional beans when features are not enabled"
else
  echo "FAIL: unexpected conditional beans present (count=${COND_BEANS})"
fi

rm -rf "${PROJECT_DIR}"
```

---

## Phase 5: Config Write — Generate Properties from JSON

### Test 5.1: Write JDBC PostgreSQL configuration

```bash
PROJECT_DIR=$(mktemp -d)

JSON_INPUT='{"forage.jdbc.db.kind":"postgresql","forage.jdbc.url":"jdbc:postgresql://localhost:5432/","forage.jdbc.username":"test","forage.jdbc.password":"test","kind":"postgresql","forage.bean.name":"myPG"}'

OUTPUT=$(camel forage config write --input "${JSON_INPUT}" --dir "${PROJECT_DIR}" --strategy application 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: config write failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  rm -rf "${PROJECT_DIR}"
  exit 1
fi

SUCCESS=$(echo "${OUTPUT}" | jq -r '.success')
if [ "${SUCCESS}" = "true" ]; then
  echo "PASS: config write returned success"
else
  echo "FAIL: config write did not return success"
  echo "${OUTPUT}"
fi

# Verify properties file was created
PROPS_FILE="${PROJECT_DIR}/application.properties"
if [ -f "${PROPS_FILE}" ]; then
  echo "PASS: application.properties created"
else
  echo "FAIL: application.properties not created"
  rm -rf "${PROJECT_DIR}"
  exit 1
fi

# Verify properties are prefixed with bean name
grep -q "forage.myPG.jdbc.url=jdbc:postgresql://localhost:5432/" "${PROPS_FILE}" \
  && echo "PASS: forage.myPG.jdbc.url is correct" \
  || echo "FAIL: forage.myPG.jdbc.url not found or incorrect"

grep -q "forage.myPG.jdbc.username=test" "${PROPS_FILE}" \
  && echo "PASS: forage.myPG.jdbc.username is correct" \
  || echo "FAIL: forage.myPG.jdbc.username not found or incorrect"

grep -q "forage.myPG.jdbc.password=test" "${PROPS_FILE}" \
  && echo "PASS: forage.myPG.jdbc.password is correct" \
  || echo "FAIL: forage.myPG.jdbc.password not found or incorrect"

grep -q "forage.myPG.jdbc.db.kind=postgresql" "${PROPS_FILE}" \
  && echo "PASS: forage.myPG.jdbc.db.kind is correct" \
  || echo "FAIL: forage.myPG.jdbc.db.kind not found or incorrect"

rm -rf "${PROJECT_DIR}"
```

### Test 5.2: Write JMS configuration

```bash
PROJECT_DIR=$(mktemp -d)

JSON_INPUT='{"forage.jms.kind":"ibmmq","forage.jms.broker.url":"127.0.0.1","forage.jms.username":"user","kind":"ibmmq","forage.bean.name":"myIBMMQ"}'

OUTPUT=$(camel forage config write --input "${JSON_INPUT}" --dir "${PROJECT_DIR}" --strategy application 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: config write failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  rm -rf "${PROJECT_DIR}"
  exit 1
fi

PROPS_FILE="${PROJECT_DIR}/application.properties"

grep -q "forage.myIBMMQ.jms.kind=ibmmq" "${PROPS_FILE}" \
  && echo "PASS: forage.myIBMMQ.jms.kind is correct" \
  || echo "FAIL: forage.myIBMMQ.jms.kind not found"

grep -q "forage.myIBMMQ.jms.broker.url=127.0.0.1" "${PROPS_FILE}" \
  && echo "PASS: forage.myIBMMQ.jms.broker.url is correct" \
  || echo "FAIL: forage.myIBMMQ.jms.broker.url not found"

grep -q "forage.myIBMMQ.jms.username=user" "${PROPS_FILE}" \
  && echo "PASS: forage.myIBMMQ.jms.username is correct" \
  || echo "FAIL: forage.myIBMMQ.jms.username not found"

rm -rf "${PROJECT_DIR}"
```

### Test 5.3: Write Ollama model configuration

```bash
PROJECT_DIR=$(mktemp -d)

JSON_INPUT='{"forage.ollama.model.name":"granite4:3b","forage.ollama.log.requests":"true","forage.ollama.log.responses":"true","kind":"ollama","forage.bean.name":"test"}'

OUTPUT=$(camel forage config write --input "${JSON_INPUT}" --dir "${PROJECT_DIR}" --strategy application 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: config write failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  rm -rf "${PROJECT_DIR}"
  exit 1
fi

PROPS_FILE="${PROJECT_DIR}/application.properties"

grep -q "forage.test.ollama.model.name=granite4:3b" "${PROPS_FILE}" \
  && echo "PASS: forage.test.ollama.model.name is correct" \
  || echo "FAIL: forage.test.ollama.model.name not found"

grep -q "forage.test.ollama.log.requests=true" "${PROPS_FILE}" \
  && echo "PASS: forage.test.ollama.log.requests is correct" \
  || echo "FAIL: forage.test.ollama.log.requests not found"

rm -rf "${PROJECT_DIR}"
```

### Test 5.4: Write includes dependency information in output

```bash
PROJECT_DIR=$(mktemp -d)

JSON_INPUT='{"forage.jdbc.db.kind":"postgresql","forage.jdbc.url":"jdbc:postgresql://localhost:5432/","kind":"postgresql","forage.bean.name":"myPG"}'

OUTPUT=$(camel forage config write --input "${JSON_INPUT}" --dir "${PROJECT_DIR}" --strategy application 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: config write failed (exit code ${EXIT_CODE})"
  rm -rf "${PROJECT_DIR}"
  exit 1
fi

PROPS_FILE="${PROJECT_DIR}/application.properties"

# Verify dependency properties were written
grep -q "camel.jbang.dependencies=.*forage-jdbc-postgresql" "${PROPS_FILE}" \
  && echo "PASS: base dependency for forage-jdbc-postgresql written" \
  || echo "FAIL: base dependency for forage-jdbc-postgresql not found"

grep -q "camel.jbang.dependencies.main=.*forage-jdbc" "${PROPS_FILE}" \
  && echo "PASS: main dependency for forage-jdbc written" \
  || echo "FAIL: main dependency for forage-jdbc not found"

rm -rf "${PROJECT_DIR}"
```

---

## Phase 6: Config Write — Merge with Existing Properties

### Test 6.1: Write merges with existing properties without overwriting

```bash
PROJECT_DIR=$(mktemp -d)

# Pre-populate application.properties with existing content
cat > "${PROJECT_DIR}/application.properties" <<'EOF'
# Existing configuration
some.existing.property=value
camel.jbang.dependencies=com.example:existing-lib:1.0
EOF

JSON_INPUT='{"forage.jdbc.db.kind":"postgresql","forage.jdbc.url":"jdbc:postgresql://localhost:5432/","kind":"postgresql","forage.bean.name":"myPG"}'

OUTPUT=$(camel forage config write --input "${JSON_INPUT}" --dir "${PROJECT_DIR}" --strategy application 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: config write failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  rm -rf "${PROJECT_DIR}"
  exit 1
fi

PROPS_FILE="${PROJECT_DIR}/application.properties"

# Verify existing properties are preserved
grep -q "some.existing.property=value" "${PROPS_FILE}" \
  && echo "PASS: existing property preserved" \
  || echo "FAIL: existing property was overwritten or removed"

# Verify existing dependency is preserved along with new one
grep -q "com.example:existing-lib:1.0" "${PROPS_FILE}" \
  && echo "PASS: existing dependency preserved" \
  || echo "FAIL: existing dependency was removed"

grep -q "forage-jdbc-postgresql" "${PROPS_FILE}" \
  && echo "PASS: new forage dependency added" \
  || echo "FAIL: new forage dependency not added"

# Verify new forage properties are present
grep -q "forage.myPG.jdbc.url" "${PROPS_FILE}" \
  && echo "PASS: new forage properties written" \
  || echo "FAIL: new forage properties not written"

rm -rf "${PROJECT_DIR}"
```

### Test 6.2: Sequential writes accumulate configurations

```bash
PROJECT_DIR=$(mktemp -d)

# First write: JDBC
JSON_INPUT_1='{"forage.jdbc.db.kind":"postgresql","forage.jdbc.url":"jdbc:postgresql://localhost:5432/","kind":"postgresql","forage.bean.name":"myPG"}'
camel forage config write --input "${JSON_INPUT_1}" --dir "${PROJECT_DIR}" --strategy application 2>&1

# Second write: JMS
JSON_INPUT_2='{"forage.jms.kind":"artemis","forage.jms.broker.url":"tcp://localhost:61616","forage.jms.username":"admin","kind":"artemis","forage.bean.name":"myArtemis"}'
OUTPUT=$(camel forage config write --input "${JSON_INPUT_2}" --dir "${PROJECT_DIR}" --strategy application 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: second config write failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  rm -rf "${PROJECT_DIR}"
  exit 1
fi

PROPS_FILE="${PROJECT_DIR}/application.properties"

# Verify both configurations exist
grep -q "forage.myPG.jdbc.url" "${PROPS_FILE}" \
  && echo "PASS: JDBC properties preserved after second write" \
  || echo "FAIL: JDBC properties lost after second write"

grep -q "forage.myArtemis.jms.broker.url" "${PROPS_FILE}" \
  && echo "PASS: JMS properties written" \
  || echo "FAIL: JMS properties not written"

# Verify dependencies contain both
grep -q "forage-jdbc-postgresql" "${PROPS_FILE}" \
  && echo "PASS: JDBC dependency preserved" \
  || echo "FAIL: JDBC dependency lost"

grep -q "forage-jms-artemis" "${PROPS_FILE}" \
  && echo "PASS: JMS dependency added" \
  || echo "FAIL: JMS dependency not added"

rm -rf "${PROJECT_DIR}"
```

---

## Phase 7: Config Write — Error Handling

### Test 7.1: Write with empty input returns error

```bash
PROJECT_DIR=$(mktemp -d)

OUTPUT=$(camel forage config write --input "" --dir "${PROJECT_DIR}" --strategy application 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: config write with empty input failed (exit code ${EXIT_CODE})"
else
  SUCCESS=$(echo "${OUTPUT}" | jq -r '.success')
  if [ "${SUCCESS}" = "false" ]; then
    echo "PASS: config write returned success=false for empty input"
  else
    echo "FAIL: config write should fail for empty input"
  fi
fi

rm -rf "${PROJECT_DIR}"
```

### Test 7.2: Write with empty JSON object returns error

```bash
PROJECT_DIR=$(mktemp -d)

OUTPUT=$(camel forage config write --input '{}' --dir "${PROJECT_DIR}" --strategy application 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: config write with empty JSON failed (exit code ${EXIT_CODE})"
else
  SUCCESS=$(echo "${OUTPUT}" | jq -r '.success')
  if [ "${SUCCESS}" = "false" ]; then
    echo "PASS: config write returned success=false for empty JSON"
  else
    echo "FAIL: config write should fail for empty JSON"
  fi
fi

ERROR_MSG=$(echo "${OUTPUT}" | jq -r '.error // empty')
if echo "${ERROR_MSG}" | grep -qi "Empty configuration"; then
  echo "PASS: error message indicates empty configuration"
else
  echo "INFO: error message was: ${ERROR_MSG}"
fi

rm -rf "${PROJECT_DIR}"
```

### Test 7.3: Write with invalid JSON returns error

```bash
PROJECT_DIR=$(mktemp -d)

OUTPUT=$(camel forage config write --input 'not-valid-json' --dir "${PROJECT_DIR}" --strategy application 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: config write with invalid JSON failed (exit code ${EXIT_CODE})"
else
  SUCCESS=$(echo "${OUTPUT}" | jq -r '.success // empty')
  if [ "${SUCCESS}" = "false" ]; then
    echo "PASS: config write returned success=false for invalid JSON"
  else
    echo "FAIL: config write should fail for invalid JSON"
  fi
fi

rm -rf "${PROJECT_DIR}"
```

---

## Phase 8: Config Write Delete — Remove Instance Configuration

### Test 8.1: Delete removes single instance properties

```bash
PROJECT_DIR=$(mktemp -d)

cat > "${PROJECT_DIR}/application.properties" <<'EOF'
# PostgreSQL configuration
forage.myPG.jdbc.db.kind=postgresql
forage.myPG.jdbc.url=jdbc:postgresql://localhost:5432/
forage.myPG.jdbc.username=test
forage.myPG.jdbc.password=test
EOF

OUTPUT=$(camel forage config write --delete --name myPG --dir "${PROJECT_DIR}" --strategy application 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: config write --delete failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  rm -rf "${PROJECT_DIR}"
  exit 1
fi

SUCCESS=$(echo "${OUTPUT}" | jq -r '.success')
OPERATION=$(echo "${OUTPUT}" | jq -r '.operation')

if [ "${SUCCESS}" = "true" ] && [ "${OPERATION}" = "delete" ]; then
  echo "PASS: delete returned success with operation=delete"
else
  echo "FAIL: unexpected result (success=${SUCCESS}, operation=${OPERATION})"
fi

PROPS_FILE="${PROJECT_DIR}/application.properties"

# Verify forage.myPG.* properties are removed
if grep -q "forage.myPG" "${PROPS_FILE}"; then
  echo "FAIL: forage.myPG properties still present"
else
  echo "PASS: forage.myPG properties removed"
fi

rm -rf "${PROJECT_DIR}"
```

### Test 8.2: Delete preserves other instances

```bash
PROJECT_DIR=$(mktemp -d)

cat > "${PROJECT_DIR}/application.properties" <<'EOF'
forage.myPG.jdbc.db.kind=postgresql
forage.myPG.jdbc.url=jdbc:postgresql://localhost:5432/
forage.myPG.jdbc.username=pguser
forage.myMariaDB.jdbc.db.kind=mariadb
forage.myMariaDB.jdbc.url=jdbc:mariadb://localhost:3306/
forage.myMariaDB.jdbc.username=mariauser
EOF

OUTPUT=$(camel forage config write --delete --name myPG --dir "${PROJECT_DIR}" --strategy application 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: config write --delete failed (exit code ${EXIT_CODE})"
  rm -rf "${PROJECT_DIR}"
  exit 1
fi

PROPS_FILE="${PROJECT_DIR}/application.properties"

# Verify myPG is gone
if grep -q "forage.myPG" "${PROPS_FILE}"; then
  echo "FAIL: forage.myPG properties still present"
else
  echo "PASS: forage.myPG properties removed"
fi

# Verify myMariaDB is preserved
grep -q "forage.myMariaDB.jdbc.db.kind=mariadb" "${PROPS_FILE}" \
  && echo "PASS: forage.myMariaDB properties preserved" \
  || echo "FAIL: forage.myMariaDB properties were removed"

grep -q "forage.myMariaDB.jdbc.url=jdbc:mariadb://localhost:3306/" "${PROPS_FILE}" \
  && echo "PASS: forage.myMariaDB.jdbc.url preserved" \
  || echo "FAIL: forage.myMariaDB.jdbc.url removed"

rm -rf "${PROJECT_DIR}"
```

### Test 8.3: Delete preserves other factory type configurations

```bash
PROJECT_DIR=$(mktemp -d)

cat > "${PROJECT_DIR}/application.properties" <<'EOF'
forage.myPG.jdbc.db.kind=postgresql
forage.myPG.jdbc.url=jdbc:postgresql://localhost:5432/
forage.myArtemis.jms.kind=artemis
forage.myArtemis.jms.broker.url=tcp://localhost:61616
forage.myArtemis.jms.username=admin
EOF

OUTPUT=$(camel forage config write --delete --name myPG --dir "${PROJECT_DIR}" --strategy application 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: config write --delete failed (exit code ${EXIT_CODE})"
  rm -rf "${PROJECT_DIR}"
  exit 1
fi

PROPS_FILE="${PROJECT_DIR}/application.properties"

# Verify myPG is gone
if grep -q "forage.myPG" "${PROPS_FILE}"; then
  echo "FAIL: forage.myPG properties still present"
else
  echo "PASS: forage.myPG properties removed"
fi

# Verify JMS config is preserved
grep -q "forage.myArtemis.jms.kind=artemis" "${PROPS_FILE}" \
  && echo "PASS: JMS configuration preserved after JDBC delete" \
  || echo "FAIL: JMS configuration removed by JDBC delete"

rm -rf "${PROJECT_DIR}"
```

### Test 8.4: Delete non-existent instance returns error

```bash
PROJECT_DIR=$(mktemp -d)

cat > "${PROJECT_DIR}/application.properties" <<'EOF'
forage.myPG.jdbc.db.kind=postgresql
forage.myPG.jdbc.url=jdbc:postgresql://localhost:5432/
EOF

OUTPUT=$(camel forage config write --delete --name nonExistent --dir "${PROJECT_DIR}" --strategy application 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: delete non-existent instance failed (exit code ${EXIT_CODE})"
else
  SUCCESS=$(echo "${OUTPUT}" | jq -r '.success')
  if [ "${SUCCESS}" = "false" ]; then
    echo "PASS: delete non-existent instance returned success=false"
  else
    echo "FAIL: delete non-existent instance should fail"
  fi
fi

ERROR_MSG=$(echo "${OUTPUT}" | jq -r '.error // empty')
if echo "${ERROR_MSG}" | grep -qi "No configuration found"; then
  echo "PASS: error message indicates no configuration found"
else
  echo "INFO: error message was: ${ERROR_MSG}"
fi

# Verify original properties are unchanged
grep -q "forage.myPG.jdbc.db.kind=postgresql" "${PROJECT_DIR}/application.properties" \
  && echo "PASS: original properties unchanged" \
  || echo "FAIL: original properties were modified"

rm -rf "${PROJECT_DIR}"
```

### Test 8.5: Delete without --name returns error

```bash
PROJECT_DIR=$(mktemp -d)

cat > "${PROJECT_DIR}/application.properties" <<'EOF'
forage.myPG.jdbc.url=test
EOF

OUTPUT=$(camel forage config write --delete --dir "${PROJECT_DIR}" --strategy application 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: delete without --name failed (exit code ${EXIT_CODE})"
else
  SUCCESS=$(echo "${OUTPUT}" | jq -r '.success')
  if [ "${SUCCESS}" = "false" ]; then
    echo "PASS: delete without --name returned success=false"
  else
    echo "FAIL: delete without --name should fail"
  fi
fi

ERROR_MSG=$(echo "${OUTPUT}" | jq -r '.error // empty')
if echo "${ERROR_MSG}" | grep -qi "Instance name.*required"; then
  echo "PASS: error message indicates instance name is required"
else
  echo "INFO: error message was: ${ERROR_MSG}"
fi

rm -rf "${PROJECT_DIR}"
```

### Test 8.6: Delete from non-existent file returns error

```bash
PROJECT_DIR=$(mktemp -d)

# No application.properties file exists

OUTPUT=$(camel forage config write --delete --name myPG --dir "${PROJECT_DIR}" --strategy application 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: delete from non-existent file failed (exit code ${EXIT_CODE})"
else
  SUCCESS=$(echo "${OUTPUT}" | jq -r '.success')
  if [ "${SUCCESS}" = "false" ]; then
    echo "PASS: delete from non-existent file returned success=false"
  else
    echo "FAIL: delete from non-existent file should fail"
  fi
fi

rm -rf "${PROJECT_DIR}"
```

---

## Phase 9: Config Write Delete — Dependency Cleanup

### Test 9.1: Delete last instance removes its dependencies

```bash
PROJECT_DIR=$(mktemp -d)

cat > "${PROJECT_DIR}/application.properties" <<'EOF'
forage.myPG.jdbc.db.kind=postgresql
forage.myPG.jdbc.url=jdbc:postgresql://localhost:5432/
forage.myPG.jdbc.username=test
camel.jbang.dependencies=io.kaoto.forage:forage-jdbc-postgresql:0.0.0
camel.jbang.dependencies.main=io.kaoto.forage:forage-jdbc:0.0.0
camel.jbang.dependencies.spring-boot=io.kaoto.forage:forage-jdbc-starter:0.0.0
camel.jbang.dependencies.quarkus=io.kaoto.forage:forage-quarkus-jdbc-deployment:0.0.0
EOF

camel forage config write --delete --name myPG --dir "${PROJECT_DIR}" --strategy application 2>&1
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: delete failed (exit code ${EXIT_CODE})"
  rm -rf "${PROJECT_DIR}"
  exit 1
fi

PROPS_FILE="${PROJECT_DIR}/application.properties"

# After deleting the only JDBC instance, JDBC dependencies should be removed
if grep -q "forage-jdbc-postgresql" "${PROPS_FILE}"; then
  echo "FAIL: forage-jdbc-postgresql dependency still present after last instance deleted"
else
  echo "PASS: forage-jdbc-postgresql dependency removed"
fi

rm -rf "${PROJECT_DIR}"
```

### Test 9.2: Delete one of multiple same-factory instances preserves shared dependencies

```bash
PROJECT_DIR=$(mktemp -d)

cat > "${PROJECT_DIR}/application.properties" <<'EOF'
forage.myPG.jdbc.db.kind=postgresql
forage.myPG.jdbc.url=jdbc:postgresql://localhost:5432/
forage.myMariaDB.jdbc.db.kind=mariadb
forage.myMariaDB.jdbc.url=jdbc:mariadb://localhost:3306/
camel.jbang.dependencies=io.kaoto.forage:forage-jdbc-postgresql:0.0.0,io.kaoto.forage:forage-jdbc-mariadb:0.0.0
camel.jbang.dependencies.main=io.kaoto.forage:forage-jdbc:0.0.0
camel.jbang.dependencies.spring-boot=io.kaoto.forage:forage-jdbc-starter:0.0.0
camel.jbang.dependencies.quarkus=io.kaoto.forage:forage-quarkus-jdbc-deployment:0.0.0
EOF

camel forage config write --delete --name myPG --dir "${PROJECT_DIR}" --strategy application 2>&1
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: delete failed (exit code ${EXIT_CODE})"
  rm -rf "${PROJECT_DIR}"
  exit 1
fi

PROPS_FILE="${PROJECT_DIR}/application.properties"

# MariaDB still uses jdbc factory, so factory-level dependencies must be preserved
grep -q "forage-jdbc-mariadb" "${PROPS_FILE}" \
  && echo "PASS: MariaDB bean dependency preserved" \
  || echo "FAIL: MariaDB bean dependency removed"

grep -q "camel.jbang.dependencies.main" "${PROPS_FILE}" \
  && echo "PASS: main dependency preserved (MariaDB still uses jdbc)" \
  || echo "FAIL: main dependency removed despite MariaDB still present"

rm -rf "${PROJECT_DIR}"
```

### Test 9.3: Delete preserves non-forage dependencies

```bash
PROJECT_DIR=$(mktemp -d)

cat > "${PROJECT_DIR}/application.properties" <<'EOF'
forage.myPG.jdbc.db.kind=postgresql
forage.myPG.jdbc.url=jdbc:postgresql://localhost:5432/
camel.jbang.dependencies=com.example:custom-lib:1.0,io.kaoto.forage:forage-jdbc-postgresql:0.0.0
camel.jbang.dependencies.main=com.example:custom-main:1.0,io.kaoto.forage:forage-jdbc:0.0.0
EOF

camel forage config write --delete --name myPG --dir "${PROJECT_DIR}" --strategy application 2>&1
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: delete failed (exit code ${EXIT_CODE})"
  rm -rf "${PROJECT_DIR}"
  exit 1
fi

PROPS_FILE="${PROJECT_DIR}/application.properties"

grep -q "com.example:custom-lib:1.0" "${PROPS_FILE}" \
  && echo "PASS: custom base dependency preserved" \
  || echo "FAIL: custom base dependency removed"

grep -q "com.example:custom-main:1.0" "${PROPS_FILE}" \
  && echo "PASS: custom main dependency preserved" \
  || echo "FAIL: custom main dependency removed"

rm -rf "${PROJECT_DIR}"
```

---

## Phase 10: Round-Trip — Write Then Read

### Test 10.1: Write configuration and verify read parses it correctly

```bash
PROJECT_DIR=$(mktemp -d)

JSON_INPUT='{"forage.jdbc.db.kind":"postgresql","forage.jdbc.url":"jdbc:postgresql://localhost:5432/testdb","forage.jdbc.username":"admin","forage.jdbc.password":"secret","kind":"postgresql","forage.bean.name":"roundTrip"}'

# Write
camel forage config write --input "${JSON_INPUT}" --dir "${PROJECT_DIR}" --strategy application 2>&1

# Read
OUTPUT=$(camel forage config read --dir "${PROJECT_DIR}" --strategy application 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: config read after write failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  rm -rf "${PROJECT_DIR}"
  exit 1
fi

BEAN_NAME=$(echo "${OUTPUT}" | jq -r '.beans[] | select(.name == "roundTrip") | .name')
BEAN_KIND=$(echo "${OUTPUT}" | jq -r '.beans[] | select(.name == "roundTrip") | .kind')
URL_VALUE=$(echo "${OUTPUT}" | jq -r '.beans[] | select(.name == "roundTrip") | .configuration.url')

if [ "${BEAN_NAME}" = "roundTrip" ]; then
  echo "PASS: round-trip bean name correct"
else
  echo "FAIL: round-trip bean name not found"
  echo "${OUTPUT}" | jq '.beans'
fi

if [ "${BEAN_KIND}" = "postgresql" ]; then
  echo "PASS: round-trip bean kind correct"
else
  echo "FAIL: expected kind 'postgresql', got '${BEAN_KIND}'"
fi

if [ "${URL_VALUE}" = "jdbc:postgresql://localhost:5432/testdb" ]; then
  echo "PASS: round-trip URL value correct"
else
  echo "FAIL: expected URL 'jdbc:postgresql://localhost:5432/testdb', got '${URL_VALUE}'"
fi

rm -rf "${PROJECT_DIR}"
```

### Test 10.2: Write, delete, then read confirms instance is gone

```bash
PROJECT_DIR=$(mktemp -d)

# Write
JSON_INPUT='{"forage.jdbc.db.kind":"postgresql","forage.jdbc.url":"jdbc:postgresql://localhost:5432/","kind":"postgresql","forage.bean.name":"toDelete"}'
camel forage config write --input "${JSON_INPUT}" --dir "${PROJECT_DIR}" --strategy application 2>&1

# Delete
camel forage config write --delete --name toDelete --dir "${PROJECT_DIR}" --strategy application 2>&1

# Read
OUTPUT=$(camel forage config read --dir "${PROJECT_DIR}" --strategy application 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: config read after delete failed (exit code ${EXIT_CODE})"
  rm -rf "${PROJECT_DIR}"
  exit 1
fi

BEAN_COUNT=$(echo "${OUTPUT}" | jq -r '.beanCount')
FOUND=$(echo "${OUTPUT}" | jq -r '.beans[] | select(.name == "toDelete") | .name')

if [ -z "${FOUND}" ]; then
  echo "PASS: deleted instance 'toDelete' no longer appears in read output"
else
  echo "FAIL: deleted instance 'toDelete' still appears in read output"
fi

rm -rf "${PROJECT_DIR}"
```

---

## Phase 11: Cleanup

No persistent state is created. All tests use `mktemp -d` and clean up via `rm -rf "${PROJECT_DIR}"`.

```bash
echo "PASS: all temporary directories cleaned up by individual tests"
```

---

## Summary Matrix

| Phase | Test ID | Test Name | Priority |
|-------|---------|-----------|----------|
| 0 | 0.1-0.3 | Prerequisites and plugin availability | Critical |
| 1 | 1.1-1.2 | Read with no/non-existent directory | High |
| 2 | 2.1-2.4 | Read parses beans, properties, sourceFile | Critical |
| 3 | 3.1-3.2 | Read filter by factory type | High |
| 4 | 4.1-4.2 | Read conditional beans (enabled/disabled) | High |
| 5 | 5.1-5.4 | Write JDBC, JMS, Ollama, dependencies | Critical |
| 6 | 6.1-6.2 | Write merge with existing, sequential writes | Critical |
| 7 | 7.1-7.3 | Write error handling (empty, invalid JSON) | High |
| 8 | 8.1-8.6 | Delete instance, preserves others, error cases | Critical |
| 9 | 9.1-9.3 | Delete dependency cleanup | High |
| 10 | 10.1-10.2 | Round-trip write-read, write-delete-read | Critical |
| 11 | 11 | Cleanup | Low |
