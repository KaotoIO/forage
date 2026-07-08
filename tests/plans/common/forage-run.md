# Common: Running a Forage Project

## Prerequisites

| Tool | Minimum version | Verify command |
|------|-----------------|----------------|
| `java` | 17+ | `java -version` |
| `jbang` | 0.114+ | `jbang version` |
| `camel` (JBang) | 4.16+ | `camel version` |

### Installing Camel JBang

If Camel JBang is not installed:
```bash
jbang trust add https://github.com/apache/camel/
jbang app install camel@apache/camel
```

### Installing the Forage Plugin

The Forage plugin must be installed before `camel forage run` or automatic bean provisioning works. Without it, `camel run` will not discover Forage providers.

**From a released version:**
```bash
FORAGE_VERSION="${FORAGE_VERSION:-1.4.0}"
camel plugin add forage \
  --gav io.kaoto.forage:camel-jbang-plugin-forage:${FORAGE_VERSION}
```

**From a local build (development/testing):**
```bash
# Build and install Forage into the local Maven repository
cd "${FORAGE_REPO_ROOT}"
mvn clean install -DskipTests -B

# Read the version from the POM
FORAGE_VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)

# Install the plugin from the local repo
camel plugin add forage \
  --gav io.kaoto.forage:camel-jbang-plugin-forage:${FORAGE_VERSION}
```

**From snapshots:**
```bash
FORAGE_SNAPSHOT_VERSION="${FORAGE_SNAPSHOT_VERSION:-1.4.0-SNAPSHOT}"
camel plugin add forage \
  --repos=https://central.sonatype.com/repository/maven-snapshots/ \
  --gav io.kaoto.forage:camel-jbang-plugin-forage:${FORAGE_SNAPSHOT_VERSION}
```

**Verify the plugin is installed:**
```bash
camel plugin list | grep -q forage
if [ $? -eq 0 ]; then
  echo "PASS: Forage plugin is installed"
else
  echo "FAIL: Forage plugin is not installed"
  exit 1
fi
```

### Environment variables

| Variable | Default | Description |
|----------|---------|-------------|
| `FORAGE_REPO_ROOT` | `.` | Path to the Forage repository root (for local builds) |
| `FORAGE_VERSION` | `1.4.0` | Forage version to install |

### Running with snapshots

When running routes with a snapshot version, Camel needs access to the snapshots repo for dependency resolution:
```bash
camel run --repos=https://central.sonatype.com/repository/maven-snapshots/ project/*
```

Or add to the project's properties file:
```properties
camel.jbang.repos=https://central.sonatype.com/repository/maven-snapshots/
```

---

## Running a Forage project

A Forage project is a directory containing:
- One or more `forage-*.properties` files with `forage.*` configuration
- One or more `.camel.yaml` route files

### Option A: Run a directory

```bash
camel run myproject/*
```

The Forage plugin hooks in via SPI, discovers the properties files, and creates the beans automatically.

### Option B: Run with explicit files

```bash
camel forage run route.camel.yaml
```

The `forage run` subcommand adds property validation before delegating to the standard `camel run`.

### Running with validation

```bash
camel forage run --strict myproject/*
```

With `--strict`, any property validation warnings (typos, unknown keys) become fatal errors.

## Verifying output

Forage routes run as a foreground process. Verification is done by:

1. **Log grepping** — run in background, wait for a log message, then kill:
   ```bash
   camel run project/* > output.log 2>&1 &
   CAMEL_PID=$!

   # Poll for expected log message
   for i in $(seq 1 60); do
     grep -q "EXPECTED_MESSAGE" output.log 2>/dev/null && break
     sleep 1
   done

   grep -q "EXPECTED_MESSAGE" output.log
   if [ $? -eq 0 ]; then
     echo "PASS: expected message found in output"
   else
     echo "FAIL: expected message not found"
     cat output.log
   fi

   kill "${CAMEL_PID}" 2>/dev/null
   wait "${CAMEL_PID}" 2>/dev/null
   ```

2. **HTTP endpoint check** — if the route exposes an HTTP or CXF endpoint:
   ```bash
   HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/path)
   if [ "${HTTP_CODE}" = "200" ]; then
     echo "PASS: endpoint reachable"
   else
     echo "FAIL: endpoint returned ${HTTP_CODE}"
   fi
   ```

## Cleanup

Always kill the Camel process and remove temporary files:
```bash
kill "${CAMEL_PID}" 2>/dev/null
wait "${CAMEL_PID}" 2>/dev/null
rm -rf "${PROJECT_DIR}"
```
