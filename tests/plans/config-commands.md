# Test Plan: Config CLI Commands

## Overview

This test plan verifies Forage's `camel forage config read` and `camel forage config write` CLI commands. These commands are part of the Camel JBang plugin at `tooling/camel-jbang-plugin-forage`.

- `config read` scans a directory for properties files, parses `forage.*` properties, and outputs a structured JSON object with `success`, `directory`, `beanCount`, and a `beans` array. It supports filtering by factory type (`--filter`), directory selection (`--dir`), and strategy selection (`--strategy`).
- `config write` accepts JSON input (via `--input` or stdin), maps properties to factory types using the Forage catalog, and writes them to properties files. It prefixes properties with the bean name when `forage.bean.name` is provided.
- `config write --delete` removes all `forage.{instanceName}.*` properties for a given `--name`.
- Neither command writes `camel.jbang.dependencies*`: the Forage plugin resolves the dependencies when the project runs or is exported. Both remove the Forage dependencies older versions of `config write` stored there, and keep the others.

Both commands output structured JSON (success/error) and return exit code 0 on success, 1 on error, including for an unknown `--strategy` value.

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

### Test 5.4: Write does not store dependencies

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

# The Forage plugin resolves the dependencies when the project runs: none are stored
grep -q "^camel.jbang.dependencies" "${PROPS_FILE}" \
  && echo "FAIL: dependency properties written" \
  || echo "PASS: no dependency properties written"

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
  && echo "FAIL: forage dependency written" \
  || echo "PASS: no forage dependency written"

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

# No dependency properties are written
grep -q "^camel.jbang.dependencies" "${PROPS_FILE}" \
  && echo "FAIL: dependency properties written" \
  || echo "PASS: no dependency properties written"

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

## Phase 9: Config Write Delete — Dependencies Stored by Older Versions

Older versions of `config write` stored the Forage dependencies in `application.properties`. Delete (like write)
removes them, whatever their version, and keeps every other dependency.

### Test 9.1: Delete removes the stored Forage dependencies

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

if grep -q "^camel.jbang.dependencies" "${PROPS_FILE}"; then
  echo "FAIL: stored Forage dependencies still present"
else
  echo "PASS: stored Forage dependencies removed"
fi

rm -rf "${PROJECT_DIR}"
```

### Test 9.2: Delete keeps the other instances and removes every stored Forage dependency

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

grep -q "^forage.myMariaDB.jdbc.db.kind=mariadb" "${PROPS_FILE}" \
  && echo "PASS: MariaDB configuration preserved" \
  || echo "FAIL: MariaDB configuration removed"

# The plugin resolves what MariaDB needs: no stored Forage dependency is left
grep -q "^camel.jbang.dependencies" "${PROPS_FILE}" \
  && echo "FAIL: stored Forage dependencies still present" \
  || echo "PASS: stored Forage dependencies removed"

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

grep -q "io.kaoto.forage:" "${PROPS_FILE}" \
  && echo "FAIL: stored Forage dependencies still present" \
  || echo "PASS: stored Forage dependencies removed"

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

## Phase 11: Forage Strategy (`--strategy forage`)

The `forage` strategy writes each factory to its own catalog-defined file (for JDBC,
`forage-datasource-factory.properties`, named after the module's `Config.name()`), writes no
dependencies, and reads/deletes from the catalog file names plus any
`forage-*.properties` file in the top level of the directory. Deleting an instance found there also removes its
overrides from `application.properties` and the `application-<profile>.properties` files.

### Test 11.1: Write uses the catalog file, read and delete use the same files

```bash
PROJECT_DIR=$(mktemp -d)
fail() { echo "FAIL: $1"; [ -n "$2" ] && echo "$2"; rm -rf "${PROJECT_DIR}"; exit 1; }

FORAGE_FILE="${PROJECT_DIR}/forage-datasource-factory.properties"

JSON_INPUT='{"forage.jdbc.db.kind":"postgresql","forage.jdbc.url":"jdbc:postgresql://localhost:5432/","kind":"postgresql","forage.bean.name":"foragePG"}'
OUTPUT=$(camel forage config write --input "${JSON_INPUT}" --dir "${PROJECT_DIR}" --strategy forage 2>&1) \
  || fail "config write --strategy forage failed" "${OUTPUT}"

grep -q '^forage.foragePG.jdbc.db.kind=postgresql' "${FORAGE_FILE}" 2>/dev/null \
  || fail "forage-datasource-factory.properties not written" "${OUTPUT}"
echo "PASS: forage strategy wrote forage-datasource-factory.properties"

[ ! -e "${PROJECT_DIR}/application.properties" ] || fail "application.properties written by the forage strategy"
echo "PASS: no dependencies written"

# A file matched only by the forage-*.properties wildcard
echo 'forage.customPG.jdbc.db.kind=postgresql' > "${PROJECT_DIR}/forage-custom.properties"

OUTPUT=$(camel forage config read --dir "${PROJECT_DIR}" --strategy forage 2>&1) \
  || fail "config read --strategy forage failed" "${OUTPUT}"
NAMES=$(echo "${OUTPUT}" | jq -r '[.beans[].name] | sort | join(",")')
[ "${NAMES}" = "customPG,foragePG" ] || fail "expected 'customPG,foragePG', got '${NAMES}'" "${OUTPUT}"
echo "PASS: read lists beans from catalog and wildcard files"

OUTPUT=$(camel forage config write --delete --name customPG --dir "${PROJECT_DIR}" --strategy forage 2>&1) \
  || fail "config write --delete customPG failed" "${OUTPUT}"
[ "$(echo "${OUTPUT}" | jq -r '.success')" = "true" ] || fail "delete did not return success" "${OUTPUT}"
! grep -q 'customPG' "${PROJECT_DIR}/forage-custom.properties" || fail "customPG still present after delete"
echo "PASS: delete removes an instance that read listed from a wildcard file"

rm -rf "${PROJECT_DIR}"
```

### Test 11.2: Delete under one strategy keeps the instances of the other

```bash
PROJECT_DIR=$(mktemp -d)
fail() { echo "FAIL: $1"; [ -n "$2" ] && echo "$2"; rm -rf "${PROJECT_DIR}"; exit 1; }

FORAGE_FILE="${PROJECT_DIR}/forage-datasource-factory.properties"

APP_JSON='{"forage.jdbc.db.kind":"postgresql","forage.jdbc.url":"jdbc:postgresql://localhost:5432/","kind":"postgresql","forage.bean.name":"appPG"}'
FORAGE_JSON='{"forage.jdbc.db.kind":"postgresql","forage.jdbc.url":"jdbc:postgresql://localhost:5432/","kind":"postgresql","forage.bean.name":"foragePG"}'

OUTPUT=$(camel forage config write --input "${APP_JSON}" --dir "${PROJECT_DIR}" --strategy application 2>&1) \
  || fail "config write --strategy application failed" "${OUTPUT}"
OUTPUT=$(camel forage config write --input "${FORAGE_JSON}" --dir "${PROJECT_DIR}" --strategy forage 2>&1) \
  || fail "config write --strategy forage failed" "${OUTPUT}"
grep -q '^forage.foragePG.jdbc.db.kind=postgresql' "${FORAGE_FILE}" 2>/dev/null \
  || fail "foragePG not written to forage-datasource-factory.properties"

OUTPUT=$(camel forage config write --delete --name foragePG --dir "${PROJECT_DIR}" --strategy forage 2>&1) \
  || fail "config write --delete foragePG failed" "${OUTPUT}"
[ "$(echo "${OUTPUT}" | jq -r '.success')" = "true" ] || fail "delete did not return success" "${OUTPUT}"
! grep -q 'foragePG' "${FORAGE_FILE}" || fail "foragePG still present after delete"
echo "PASS: foragePG removed from forage-datasource-factory.properties"

grep -q '^forage.appPG.jdbc.db.kind=postgresql' "${PROJECT_DIR}/application.properties" \
  || fail "appPG was removed by deleting foragePG"
echo "PASS: appPG in application.properties kept"

rm -rf "${PROJECT_DIR}"
```

### Test 11.3: Unknown strategy is rejected with a JSON error

```bash
PROJECT_DIR=$(mktemp -d)
fail() { echo "FAIL: $1"; [ -n "$2" ] && echo "$2"; rm -rf "${PROJECT_DIR}"; exit 1; }

OUTPUT=$(camel forage config read --dir "${PROJECT_DIR}" --strategy app 2>/dev/null)
EXIT_CODE=$?
[ "${EXIT_CODE}" -eq 1 ] || fail "expected exit code 1 for --strategy app, got ${EXIT_CODE}" "${OUTPUT}"
[ "$(echo "${OUTPUT}" | jq -r '.success')" = "false" ] || fail "expected JSON success=false" "${OUTPUT}"
echo "${OUTPUT}" | jq -r '.error' | grep -q "Unknown properties file strategy 'app'" \
  || fail "error does not name the unknown strategy" "${OUTPUT}"
echo "PASS: unknown strategy 'app' rejected with a JSON error"

OUTPUT=$(camel forage config read --dir "${PROJECT_DIR}" --strategy FORAGE 2>&1) \
  || fail "--strategy FORAGE rejected" "${OUTPUT}"
[ "$(echo "${OUTPUT}" | jq -r '.success')" = "true" ] || fail "--strategy FORAGE did not return success" "${OUTPUT}"
echo "PASS: strategy value is case-insensitive"

rm -rf "${PROJECT_DIR}"
```

### Test 11.4: Forage strategy rejects factories without a dedicated file

```bash
PROJECT_DIR=$(mktemp -d)
fail() { echo "FAIL: $1"; [ -n "$2" ] && echo "$2"; rm -rf "${PROJECT_DIR}"; exit 1; }

# WebSearch is in the catalog but has no properties file of its own
OUTPUT=$(camel forage config write --input '{"forage.tavily.api.key":"secret"}' --dir "${PROJECT_DIR}" --strategy forage 2>/dev/null)
EXIT_CODE=$?
[ "${EXIT_CODE}" -eq 1 ] || fail "expected exit code 1, got ${EXIT_CODE}" "${OUTPUT}"
[ "$(echo "${OUTPUT}" | jq -r '.success')" = "false" ] || fail "expected JSON success=false" "${OUTPUT}"
[ -z "$(ls -A "${PROJECT_DIR}")" ] || fail "files were written although the write failed" "$(ls -A "${PROJECT_DIR}")"
echo "PASS: WebSearch configuration rejected under --strategy forage, nothing written"

rm -rf "${PROJECT_DIR}"
```

### Test 11.5: Delete keeps the default instance and the instances of other profile files

```bash
PROJECT_DIR=$(mktemp -d)
fail() { echo "FAIL: $1"; [ -n "$2" ] && echo "$2"; rm -rf "${PROJECT_DIR}"; exit 1; }
APP_FILE="${PROJECT_DIR}/application.properties"

# Default (unnamed) RabbitMQ instance plus a named one
OUTPUT=$(camel forage config write --input '{"forage.rabbitmq.host":"a"}' --dir "${PROJECT_DIR}" --strategy application 2>&1) \
  || fail "write of default rabbitmq instance failed" "${OUTPUT}"
OUTPUT=$(camel forage config write --input '{"forage.rabbitmq.host":"b","forage.bean.name":"second"}' --dir "${PROJECT_DIR}" --strategy application 2>&1) \
  || fail "write of named rabbitmq instance failed" "${OUTPUT}"
OUTPUT=$(camel forage config write --delete --name second --dir "${PROJECT_DIR}" --strategy application 2>&1) \
  || fail "delete of 'second' failed" "${OUTPUT}"
grep -q '^forage.rabbitmq.host=a' "${APP_FILE}" || fail "default rabbitmq instance removed"
grep -q '^forage.second' "${APP_FILE}" && fail "instance 'second' left behind"
echo "PASS: default rabbitmq instance kept"

rm -rf "${PROJECT_DIR}"
PROJECT_DIR=$(mktemp -d)
APP_FILE="${PROJECT_DIR}/application.properties"

# Another instance only defined in a profile file
OUTPUT=$(camel forage config write --input '{"forage.jdbc.db.kind":"postgresql","kind":"postgresql","forage.bean.name":"myPG"}' --dir "${PROJECT_DIR}" --strategy application 2>&1) \
  || fail "write of myPG failed" "${OUTPUT}"
echo 'forage.prodPG.jdbc.db.kind=postgresql' > "${PROJECT_DIR}/application-prod.properties"
OUTPUT=$(env -u CAMEL_MAIN_PROFILE camel forage config write --delete --name myPG --dir "${PROJECT_DIR}" --strategy application 2>&1) \
  || fail "delete of myPG failed" "${OUTPUT}"
grep -q '^forage.prodPG.jdbc.db.kind=postgresql' "${PROJECT_DIR}/application-prod.properties" \
  || fail "prodPG removed from application-prod.properties"
echo "PASS: prodPG in application-prod.properties kept"

rm -rf "${PROJECT_DIR}"
```

### Test 11.6: Delete parses instances like the runtime (agents, bean-named and dotted instances)

```bash
PROJECT_DIR=$(mktemp -d)
fail() { echo "FAIL: $1"; [ -n "$2" ] && echo "$2"; rm -rf "${PROJECT_DIR}"; exit 1; }
APP_FILE="${PROJECT_DIR}/application.properties"
write() {
  OUTPUT=$(camel forage config write --input "$1" --dir "${PROJECT_DIR}" --strategy application 2>&1) \
    || fail "write failed: $1" "${OUTPUT}"
}
delete() {
  OUTPUT=$(camel forage config write --delete --name "$1" --dir "${PROJECT_DIR}" --strategy application 2>&1) \
    || fail "delete of '$1' failed" "${OUTPUT}"
}

# A Gemini agent (bean prefix "google") and a DataSource that happens to be called "ollama", written by hand:
# config write does not create instances with such an ambiguous name (Test 11.8)
write '{"forage.google.api.key":"x","forage.google.model.name":"gemini","kind":"google-gemini","forage.bean.name":"myG"}'
echo 'forage.ollama.jdbc.db.kind=postgresql' >> "${APP_FILE}"
delete ollama
grep -q '^forage.myG.google.api.key=x' "${APP_FILE}" || fail "Gemini agent removed by deleting the DataSource 'ollama'"
grep -q '^forage.ollama.jdbc' "${APP_FILE}" && fail "DataSource 'ollama' left behind"
echo "PASS: deleting an instance named like a bean only removes its own keys"

# A second agent: deleting it keeps the Gemini agent
write '{"forage.ollama.model.name":"llama3","kind":"ollama","forage.bean.name":"myOllama"}'
delete myOllama
grep -q '^forage.myOllama' "${APP_FILE}" && fail "myOllama left behind"
grep -q '^forage.myG.google.api.key=x' "${APP_FILE}" || fail "myG removed by deleting myOllama"
echo "PASS: only the deleted agent is removed"

# Dotted instance name: "my" is not an instance, "my.ds" is
write '{"forage.jdbc.db.kind":"postgresql","kind":"postgresql","forage.bean.name":"myPG"}'
echo 'forage.my.ds.jdbc.url=jdbc:h2:mem:x' >> "${APP_FILE}"
camel forage config write --delete --name my --dir "${PROJECT_DIR}" --strategy application > /dev/null 2>&1 \
  && fail "--name my matched the instance my.ds"
grep -q '^forage.my.ds.jdbc.url=' "${APP_FILE}" || fail "my.ds removed by --name my"
delete my.ds
grep -q '^forage.my.ds' "${APP_FILE}" && fail "my.ds left behind"
grep -q '^forage.myPG.jdbc.db.kind=postgresql' "${APP_FILE}" || fail "myPG removed by deleting my.ds"
echo "PASS: dotted instance names are kept apart"

rm -rf "${PROJECT_DIR}"
```

### Test 11.7: Delete removes profile overrides and finds any entry layout

```bash
PROJECT_DIR=$(mktemp -d)
fail() { echo "FAIL: $1"; [ -n "$2" ] && echo "$2"; rm -rf "${PROJECT_DIR}"; exit 1; }
APP_FILE="${PROJECT_DIR}/application.properties"
write() {
  OUTPUT=$(camel forage config write --input "$1" --dir "${PROJECT_DIR}" --strategy "${2:-application}" 2>&1) \
    || fail "write failed: $1" "${OUTPUT}"
}
delete() {
  OUTPUT=$(camel forage config write --delete --name "$1" --dir "${PROJECT_DIR}" --strategy application 2>&1) \
    || fail "delete of '$1' failed" "${OUTPUT}"
}

# A profile override of the instance is deleted with it
write '{"forage.jdbc.db.kind":"postgresql","kind":"postgresql","forage.bean.name":"myPG"}'
printf 'camel.main.name=dev\nforage.myPG.jdbc.url=jdbc:postgresql://dev/db\n' > "${PROJECT_DIR}/application-dev.properties"
delete myPG
grep -q '^forage.myPG' "${PROJECT_DIR}/application-dev.properties" && fail "profile override of myPG kept"
grep -q '^camel.main.name=dev' "${PROJECT_DIR}/application-dev.properties" || fail "other profile properties removed"
echo "PASS: profile overrides of the instance are deleted"

# Entries written with ':' or whitespace separators, or continued on the next line, are deleted too
printf 'forage.ds1.jdbc.db.kind: postgresql\nforage.ds1.jdbc.url jdbc:h2:mem:x\nforage.ds1.jdbc.password=long\\\n    -secret\n' >> "${APP_FILE}"
delete ds1
grep -q 'forage.ds1\|-secret' "${APP_FILE}" && fail "entries of ds1 left behind" "$(cat "${APP_FILE}")"
echo "PASS: entries are found whatever their separator or line layout"

# An OpenAI instance named "azure": forage.azure.openai.api.key is also the default Azure OpenAI entry
printf 'forage.azure.openai.api.key=k\nforage.azure.openai.model.name=gpt\n' >> "${APP_FILE}"
delete azure
grep -q '^forage.azure' "${APP_FILE}" && fail "keys of the instance 'azure' left behind"
echo "PASS: every key of an instance named like a bean prefix is deleted"

rm -rf "${PROJECT_DIR}"
PROJECT_DIR=$(mktemp -d)

# google.custom.search.* is WebSearch (no dedicated file), not the Gemini agent bean
camel forage config write --input '{"forage.google.custom.search.api.key":"k"}' --dir "${PROJECT_DIR}" --strategy forage > /dev/null 2>&1 \
  && fail "WebSearch keys written under the forage strategy"
echo "PASS: WebSearch keys are rejected under the forage strategy"

rm -rf "${PROJECT_DIR}"
```

### Test 11.8: Instance names, instance lists and bean files

```bash
PROJECT_DIR=$(mktemp -d)
fail() { echo "FAIL: $1"; [ -n "$2" ] && echo "$2"; rm -rf "${PROJECT_DIR}"; exit 1; }
APP_FILE="${PROJECT_DIR}/application.properties"

# A new instance cannot be named like the start of Forage keys (here the Ollama bean prefix)
OUTPUT=$(camel forage config write --input '{"forage.jdbc.db.kind":"h2","forage.bean.name":"ollama"}' --dir "${PROJECT_DIR}" 2>&1) \
  && fail "instance named 'ollama' created" "${OUTPUT}"
echo "${OUTPUT}" | jq -e '.error | contains("Invalid instance name")' > /dev/null || fail "unexpected error" "${OUTPUT}"
[ -z "$(ls -A "${PROJECT_DIR}")" ] || fail "files written for a rejected instance"
echo "PASS: ambiguous names are rejected for new instances"

# Deleting an agent removes it from forage.multi.agent.names
printf 'forage.multi.agent.names=a1,a2\nforage.a1.agent.model.kind=ollama\nforage.a2.agent.model.kind=ollama\n' > "${APP_FILE}"
OUTPUT=$(camel forage config write --delete --name a1 --dir "${PROJECT_DIR}" 2>&1) || fail "delete of a1 failed" "${OUTPUT}"
grep -q '^forage.multi.agent.names=a2$' "${APP_FILE}" || fail "a1 still listed" "$(cat "${APP_FILE}")"
OUTPUT=$(camel forage config write --delete --name a2 --dir "${PROJECT_DIR}" 2>&1) || fail "delete of a2 failed" "${OUTPUT}"
grep -q 'multi.agent.names' "${APP_FILE}" && fail "empty agent list kept"
echo "PASS: deleted agents are removed from forage.multi.agent.names"

rm -rf "${PROJECT_DIR}"
PROJECT_DIR=$(mktemp -d)

# Under the forage strategy, a bean with its own file gets its properties there; one without gets a warning
OUTPUT=$(camel forage config write --input '{"forage.agent.model.kind":"bedrock","forage.bedrock.region":"eu-west-1","forage.bean.name":"a1"}' --dir "${PROJECT_DIR}" --strategy forage 2>&1) \
  || fail "bedrock write failed" "${OUTPUT}"
grep -q '^forage.a1.bedrock.region=eu-west-1' "${PROJECT_DIR}/forage-model-bedrock.properties" \
  || fail "bedrock properties not in forage-model-bedrock.properties" "${OUTPUT}"
OUTPUT=$(camel forage config write --input '{"forage.ollama.model.name":"llama3","forage.bean.name":"a2"}' --dir "${PROJECT_DIR}" --strategy forage 2>&1) \
  || fail "ollama write failed" "${OUTPUT}"
echo "${OUTPUT}" | jq -e '.factories.agent.warnings | length == 1' > /dev/null || fail "no warning for ollama" "${OUTPUT}"
echo "PASS: bean properties go to the bean's own file, or come with a warning"

rm -rf "${PROJECT_DIR}"
```

---

## Phase 12: Cleanup

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
| 5 | 5.1-5.4 | Write JDBC, JMS, Ollama, no stored dependencies | Critical |
| 6 | 6.1-6.2 | Write merge with existing, sequential writes | Critical |
| 7 | 7.1-7.3 | Write error handling (empty, invalid JSON) | High |
| 8 | 8.1-8.6 | Delete instance, preserves others, error cases | Critical |
| 9 | 9.1-9.3 | Delete removes the Forage dependencies older versions stored | High |
| 10 | 10.1-10.2 | Round-trip write-read, write-delete-read | Critical |
| 11 | 11.1-11.8 | Forage strategy write/read/delete, invalid values, files of each strategy, profiles, default and dotted instances, bean-named instances, profile overrides, entry layouts, instance names, instance lists, bean files | High |
| 12 | 12 | Cleanup | Low |
