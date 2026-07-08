# Writing Test Plans

Test plans are Markdown documents under `tests/plans/` designed for execution by both humans and AI agents.

## What a test plan tests

Each test plan creates a **sample project** — a temporary directory with `forage-*.properties` and `.camel.yaml` route files — starts any required infrastructure (databases, brokers), runs the project with `camel run` or `camel forage run`, and verifies behavior by polling log output or making HTTP requests.

Test plans do **not** wrap Maven unit tests. They exercise Forage the way a user actually uses it.

## Structure

A test plan has three layers:

1. **Main plan** (`tests/plans/<name>.md`) — the test scenario with phases and assertions.
2. **Common steps** (`tests/plans/common/*.md`) — reusable procedures shared across plans (container startup, Forage plugin installation).
3. **Environment variables** — all configurable values declared upfront so the same plan works across versions and environments.

## Prerequisites

Every plan must declare required tools in a prerequisites table:

| Tool | Minimum version | Verify command |
|------|-----------------|----------------|
| `java` | 17+ | `java -version` |
| `camel` (JBang) | 4.16+ | `camel version` |
| `docker` or `podman` | any | `docker --version` or `podman --version` |
| `mvn` | 3.9+ (for local builds) | `mvn -version` |

The Forage plugin must be installed before running. See [common/forage-run.md](../tests/plans/common/forage-run.md) for installation instructions.

## Phases, not scripts

Organize tests into numbered phases that run sequentially. Each phase groups related assertions.

```text
Phase 0: Prerequisites
Phase 1-N: Test scenarios (each creates a sample project, runs it, verifies)
Phase N+1: Cleanup
```

## Every step must be verifiable

Each step needs: a command, an expected outcome, and a PASS/FAIL assertion. Avoid steps that only run a command without checking the result.

```bash
# Bad — runs but doesn't verify
camel run project/*

# Good — runs, polls for output, asserts
camel run project/* > output.log 2>&1 &
CAMEL_PID=$!

for i in $(seq 1 60); do
  grep -q "expected message" output.log 2>/dev/null && break
  sleep 1
done

if grep -q "expected message" output.log; then
  echo "PASS: expected message found"
else
  echo "FAIL: expected message not found"
  cat output.log
  exit 1
fi

kill "${CAMEL_PID}" 2>/dev/null
wait "${CAMEL_PID}" 2>/dev/null
```

## Sample project structure

Every test creates its project in a temp directory:

```bash
PROJECT_DIR=$(mktemp -d)

# Properties file — configures Forage beans
cat > "${PROJECT_DIR}/forage-datasource-factory.properties" <<'EOF'
forage.jdbc.db.kind=postgresql
forage.jdbc.url=jdbc:postgresql://localhost:5432/testdb
forage.jdbc.username=test
forage.jdbc.password=test
EOF

# Route YAML — uses the Forage-created beans
cat > "${PROJECT_DIR}/route.camel.yaml" <<'EOF'
- route:
    id: my-route
    from:
      uri: timer:tick
      parameters:
        repeatCount: 1
      steps:
        - setBody:
            simple:
              expression: select * from bar
        - to:
            uri: jdbc
            parameters:
              dataSourceName: dataSource
        - log:
            message: "Result: ${body}"
EOF
```

**YAML format note:** In Camel YAML DSL, `steps` must be nested **under** `from`, not as a sibling. This is a common mistake.

## Container runtime support

Test plans must work with both Docker and Podman. Use `${CONTAINER_RUNTIME}` instead of hardcoding `docker`:

```bash
if command -v podman > /dev/null 2>&1; then
  CONTAINER_RUNTIME=podman
elif command -v docker > /dev/null 2>&1; then
  CONTAINER_RUNTIME=docker
else
  echo "FAIL: neither podman nor docker found"
  exit 1
fi

${CONTAINER_RUNTIME} run -d --name my-container ...
${CONTAINER_RUNTIME} exec my-container ...
${CONTAINER_RUNTIME} rm -f my-container 2>/dev/null || true
```

## Prefer polling over sleeping

Poll for expected output instead of using fixed `sleep` calls:

```bash
# Bad
sleep 30
grep "expected" output.log

# Good — polling loop
for i in $(seq 1 60); do
  grep -q "expected" output.log 2>/dev/null && break
  sleep 1
done
```

## Parametrize versions and ports

Never hard-code ports or versions. Use environment variables with defaults:

```bash
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
ARTEMIS_PORT="${ARTEMIS_PORT:-61616}"
```

## Extract reusable steps into common docs

If a procedure appears in more than one plan, move it to `tests/plans/common/`. Current common docs:

- `common/forage-run.md` — JBang and Forage plugin installation, running projects
- `common/start-container.md` — Starting PostgreSQL, Artemis, and RabbitMQ containers

Reference common docs with a link:

```markdown
Follow [common/start-container.md#postgresql](common/start-container.md#postgresql).
After completion, `POSTGRES_CONTAINER` and `POSTGRES_PORT` must be set.
```

## Include negative tests

Verify that invalid inputs are rejected:

```bash
# Typo in property name + strict mode → should fail
if camel forage run --strict project/*; then
  echo "FAIL: strict mode accepted invalid properties"
  exit 1
else
  echo "PASS: strict mode rejected invalid properties"
fi
```

## Cleanup must be idempotent

Use `2>/dev/null || true` on all cleanup commands:

```bash
kill "${CAMEL_PID}" 2>/dev/null || true
wait "${CAMEL_PID}" 2>/dev/null || true
${CONTAINER_RUNTIME} rm -f "${CONTAINER_NAME}" 2>/dev/null || true
rm -rf "${PROJECT_DIR}" || true
```

## Shell compatibility

Use POSIX-compatible constructs. Avoid bash-only features like `${!VAR}` (indirect expansion) since the executor may use zsh or sh.

## End with a test summary

Every plan ends with a summary table:

```markdown
| Phase | Test ID | Test Name | Priority |
|-------|---------|-----------|----------|
| 0 | 0.1 | Verify required tools | Critical |
| 1 | 1.1 | Create H2 project | Critical |
| 1 | 1.2 | Run and verify query result | Critical |
```

## Checklist for new plans

- [ ] Creates a sample project (properties + YAML route) in a temp directory
- [ ] Uses `camel run` or `camel forage run` — not `mvn test`
- [ ] All configurable values are environment variables with defaults
- [ ] Container commands use `${CONTAINER_RUNTIME}`, not hardcoded `docker`
- [ ] Every step has a PASS/FAIL assertion
- [ ] No fixed `sleep` — uses polling loops
- [ ] `steps` is nested under `from` in YAML routes
- [ ] Negative tests are included
- [ ] Cleanup is idempotent
- [ ] Shell constructs are POSIX-compatible
- [ ] Ends with a test summary table
