# Test Plan: CXF SOAP Endpoint Provisioning

## Overview

This test plan verifies that Forage correctly provisions CXF SOAP endpoints from properties files. Tests create sample projects with `forage-cxf.properties` and Camel YAML routes, run them with `camel run`, and verify that the CXF server starts, handles SOAP requests, and responds correctly.

Forage reads `forage.cxf.*` settings (address, data format, logging) and creates a CXF endpoint bean registered in the Camel context. Routes reference endpoints as `cxf:bean:<name>`.

This plan covers:
- Default (unnamed) CXF endpoint provisioning
- Named CXF endpoints for multi-bean configurations
- CXF message logging feature

## Prerequisites

### Required tools

| Tool | Minimum version | Verify command |
|------|-----------------|----------------|
| `java` | 17+ | `java -version` |
| `camel` (JBang) | 4.16+ | `camel version` |
| `curl` | any | `curl --version` |
| `grep` | any | `grep --version` |

No Docker required -- CXF starts its own embedded HTTP server via Camel JBang.

### Prerequisite check

```bash
java -version 2>&1 | head -1
JAVA_EXIT=$?

camel version 2>&1 | head -1
CAMEL_EXIT=$?

curl --version > /dev/null 2>&1
CURL_EXIT=$?

if [ "${JAVA_EXIT}" -eq 0 ] && [ "${CAMEL_EXIT}" -eq 0 ] && [ "${CURL_EXIT}" -eq 0 ]; then
  echo "PASS: all prerequisites met"
else
  echo "FAIL: one or more tools missing (java=${JAVA_EXIT}, camel=${CAMEL_EXIT}, curl=${CURL_EXIT})"
  exit 1
fi
```

### Environment variables

```bash
export CXF_SERVER_PORT="${CXF_SERVER_PORT:-8080}"
export CXF_SERVER_ADDRESS="http://localhost:${CXF_SERVER_PORT}/services/hello"
```

| Variable | Default | Description |
|----------|---------|-------------|
| `CXF_SERVER_PORT` | `8080` | Port for the CXF embedded HTTP server |
| `CXF_SERVER_ADDRESS` | `http://localhost:8080/services/hello` | Full address for the CXF SOAP endpoint |

---

## Phase 0: Prerequisites

Verify all required tools are present and at the correct versions.

### Test 0.1: Verify required tools

```bash
java -version 2>&1 | head -1
JAVA_EXIT=$?

camel version 2>&1 | head -1
CAMEL_EXIT=$?

curl --version > /dev/null 2>&1
CURL_EXIT=$?

if [ "${JAVA_EXIT}" -eq 0 ] && [ "${CAMEL_EXIT}" -eq 0 ] && [ "${CURL_EXIT}" -eq 0 ]; then
  echo "PASS: all required tools present"
else
  echo "FAIL: one or more tools missing (java=${JAVA_EXIT}, camel=${CAMEL_EXIT}, curl=${CURL_EXIT})"
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

## Phase 1: CXF SOAP Server (Default Endpoint)

Create a temporary project with a default (unnamed) CXF endpoint, run it, and verify the server processes a SOAP request end-to-end.

The default bean name is `cxfEndpoint` when no prefix/name is used in the properties.

### Test 1.1: Create project directory and files

```bash
PROJECT_DIR=$(mktemp -d)
echo "Project directory: ${PROJECT_DIR}"

cat > "${PROJECT_DIR}/forage-cxf.properties" << 'PROPS'
forage.cxf.address=http://localhost:8080/services/hello
forage.cxf.data.format=PAYLOAD
forage.cxf.logging.enabled=false
PROPS

cat > "${PROJECT_DIR}/route.camel.yaml" << 'ROUTE'
- route:
    id: cxf-soap-server
    streamCaching: false
    from:
      uri: cxf:bean:cxfEndpoint
      steps:
        - log:
            message: "Server received request"
        - setBody:
            constant:
              expression: >
                <sayHelloResponse xmlns="http://example.com/hello"><greeting>Hello from CXF</greeting></sayHelloResponse>
        - log:
            message: "Server sending response"
- route:
    id: cxf-soap-caller
    from:
      uri: timer
      parameters:
        timerName: soap-caller
        repeatCount: 1
        delay: 5000
      steps:
        - setBody:
            constant:
              expression: >
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <sayHello xmlns="http://example.com/hello"><name>Forage</name></sayHello>
                  </soap:Body>
                </soap:Envelope>
        - setHeader:
            name: CamelHttpMethod
            constant:
              expression: POST
        - setHeader:
            name: Content-Type
            constant:
              expression: text/xml
        - to:
            uri: "http://localhost:8080/services/hello"
        - log:
            message: "Response: ${body}"
ROUTE

if [ -f "${PROJECT_DIR}/forage-cxf.properties" ] && [ -f "${PROJECT_DIR}/route.camel.yaml" ]; then
  echo "PASS: project files created in ${PROJECT_DIR}"
else
  echo "FAIL: project files not created"
  exit 1
fi
```

### Test 1.2: Start Camel with Forage in background

```bash
camel run "${PROJECT_DIR}"/* > "${PROJECT_DIR}/output.log" 2>&1 &
CAMEL_PID=$!
echo "Camel started with PID ${CAMEL_PID}"

sleep 2
kill -0 "${CAMEL_PID}" 2>/dev/null
if [ $? -eq 0 ]; then
  echo "PASS: Camel process ${CAMEL_PID} is running"
else
  echo "FAIL: Camel process ${CAMEL_PID} is not running"
  cat "${PROJECT_DIR}/output.log"
  exit 1
fi
```

### Test 1.3: Wait for server response in logs

```bash
FOUND=false
for i in $(seq 1 60); do
  grep -q "Response:" "${PROJECT_DIR}/output.log" 2>/dev/null && { FOUND=true; break; }
  sleep 1
done

if [ "${FOUND}" = "true" ]; then
  echo "PASS: response logged within timeout"
else
  echo "FAIL: response not found in logs within 60 seconds"
  cat "${PROJECT_DIR}/output.log"
fi
```

### Test 1.4: Verify response contains expected SOAP body

```bash
grep "Response:" "${PROJECT_DIR}/output.log" | grep -q "Hello from CXF"
if [ $? -eq 0 ]; then
  echo "PASS: response contains 'Hello from CXF'"
else
  echo "FAIL: response does not contain expected greeting"
  grep "Response:" "${PROJECT_DIR}/output.log"
fi
```

### Test 1.5: Verify server received the request

```bash
grep -q "Server received request" "${PROJECT_DIR}/output.log"
if [ $? -eq 0 ]; then
  echo "PASS: server logged incoming request"
else
  echo "FAIL: server did not log incoming request"
fi
```

### Test 1.6: Verify external curl call to SOAP endpoint

```bash
CURL_RESPONSE=$(curl -s -X POST "http://localhost:${CXF_SERVER_PORT}/services/hello" \
  -H "Content-Type: text/xml" \
  -d '<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
        <soap:Body>
          <sayHello xmlns="http://example.com/hello">
            <name>CurlTest</name>
          </sayHello>
        </soap:Body>
      </soap:Envelope>' 2>/dev/null)

echo "${CURL_RESPONSE}" | grep -q "Hello from CXF"
if [ $? -eq 0 ]; then
  echo "PASS: curl SOAP call returned expected response"
else
  echo "FAIL: curl SOAP call did not return expected response"
  echo "Response: ${CURL_RESPONSE}"
fi
```

### Test 1.7: Stop Camel and cleanup

```bash
kill "${CAMEL_PID}" 2>/dev/null || true
wait "${CAMEL_PID}" 2>/dev/null || true

kill -0 "${CAMEL_PID}" 2>/dev/null
if [ $? -ne 0 ]; then
  echo "PASS: Camel process ${CAMEL_PID} stopped"
else
  echo "FAIL: Camel process ${CAMEL_PID} still running"
fi

rm -rf "${PROJECT_DIR}" || true
```

---

## Phase 2: Named CXF Endpoints

Create a project with named CXF beans (`helloServer`, `helloClient`), verifying that Forage creates separate named beans and routes can reference them individually.

### Test 2.1: Create project directory and files

```bash
PROJECT_DIR=$(mktemp -d)
echo "Project directory: ${PROJECT_DIR}"

cat > "${PROJECT_DIR}/forage-cxf.properties" << 'PROPS'
# Server endpoint (named bean)
forage.helloServer.cxf.address=http://localhost:8080/services/hello
forage.helloServer.cxf.data.format=PAYLOAD

# Client endpoint (named bean)
forage.helloClient.cxf.address=http://localhost:8080/services/hello
forage.helloClient.cxf.data.format=PAYLOAD
PROPS

cat > "${PROJECT_DIR}/route.camel.yaml" << 'ROUTE'
- route:
    id: cxf-named-server
    streamCaching: false
    from:
      uri: cxf:bean:helloServer
      steps:
        - log:
            message: "Server received SOAP request"
        - setBody:
            constant:
              expression: >
                <sayHelloResponse xmlns="http://example.com/hello"><greeting>Hello from named CXF server</greeting></sayHelloResponse>
        - log:
            message: "Server sending SOAP response"
- route:
    id: cxf-named-caller
    from:
      uri: timer
      parameters:
        timerName: soap-caller
        repeatCount: 1
        delay: 5000
      steps:
        - setBody:
            constant:
              expression: >
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <sayHello xmlns="http://example.com/hello"><name>Forage</name></sayHello>
                  </soap:Body>
                </soap:Envelope>
        - setHeader:
            name: operationName
            constant:
              expression: sayHello
        - setHeader:
            name: operationNamespace
            constant:
              expression: "http://example.com/hello"
        - to:
            uri: cxf:bean:helloClient
        - log:
            message: "Client received response: ${body}"
ROUTE

if [ -f "${PROJECT_DIR}/forage-cxf.properties" ] && [ -f "${PROJECT_DIR}/route.camel.yaml" ]; then
  echo "PASS: named endpoints project files created in ${PROJECT_DIR}"
else
  echo "FAIL: project files not created"
  exit 1
fi
```

### Test 2.2: Start Camel with named endpoints

```bash
camel run "${PROJECT_DIR}"/* > "${PROJECT_DIR}/output.log" 2>&1 &
CAMEL_PID=$!
echo "Camel started with PID ${CAMEL_PID}"

sleep 2
kill -0 "${CAMEL_PID}" 2>/dev/null
if [ $? -eq 0 ]; then
  echo "PASS: Camel process ${CAMEL_PID} is running"
else
  echo "FAIL: Camel process ${CAMEL_PID} is not running"
  cat "${PROJECT_DIR}/output.log"
  exit 1
fi
```

### Test 2.3: Wait for client response in logs

```bash
FOUND=false
for i in $(seq 1 60); do
  grep -q "Client received response:" "${PROJECT_DIR}/output.log" 2>/dev/null && { FOUND=true; break; }
  sleep 1
done

if [ "${FOUND}" = "true" ]; then
  echo "PASS: client response logged within timeout"
else
  echo "FAIL: client response not found in logs within 60 seconds"
  cat "${PROJECT_DIR}/output.log"
fi
```

### Test 2.4: Verify named server received request

```bash
grep -q "Server received SOAP request" "${PROJECT_DIR}/output.log"
if [ $? -eq 0 ]; then
  echo "PASS: named server logged incoming request"
else
  echo "FAIL: named server did not log incoming request"
fi
```

### Test 2.5: Verify response contains named server greeting

```bash
grep "Client received response:" "${PROJECT_DIR}/output.log" | grep -q "Hello from named CXF server"
if [ $? -eq 0 ]; then
  echo "PASS: response contains 'Hello from named CXF server'"
else
  echo "FAIL: response does not contain expected named server greeting"
  grep "Client received response:" "${PROJECT_DIR}/output.log"
fi
```

### Test 2.6: Verify external curl call to named server endpoint

```bash
CURL_RESPONSE=$(curl -s -X POST "http://localhost:${CXF_SERVER_PORT}/services/hello" \
  -H "Content-Type: text/xml" \
  -d '<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
        <soap:Body>
          <sayHello xmlns="http://example.com/hello">
            <name>CurlNamedTest</name>
          </sayHello>
        </soap:Body>
      </soap:Envelope>' 2>/dev/null)

echo "${CURL_RESPONSE}" | grep -q "Hello from named CXF server"
if [ $? -eq 0 ]; then
  echo "PASS: curl call to named server returned expected response"
else
  echo "FAIL: curl call to named server did not return expected response"
  echo "Response: ${CURL_RESPONSE}"
fi
```

### Test 2.7: Stop Camel and cleanup

```bash
kill "${CAMEL_PID}" 2>/dev/null || true
wait "${CAMEL_PID}" 2>/dev/null || true

kill -0 "${CAMEL_PID}" 2>/dev/null
if [ $? -ne 0 ]; then
  echo "PASS: Camel process ${CAMEL_PID} stopped"
else
  echo "FAIL: Camel process ${CAMEL_PID} still running"
fi

rm -rf "${PROJECT_DIR}" || true
```

---

## Phase 3: CXF with Logging Enabled

Same as Phase 1 but with `forage.cxf.logging.enabled=true`. Verifies that CXF's message interceptor logging appears in the output.

### Test 3.1: Create project directory and files

```bash
PROJECT_DIR=$(mktemp -d)
echo "Project directory: ${PROJECT_DIR}"

cat > "${PROJECT_DIR}/forage-cxf.properties" << 'PROPS'
forage.cxf.address=http://localhost:8080/services/hello
forage.cxf.data.format=PAYLOAD
forage.cxf.logging.enabled=true
PROPS

cat > "${PROJECT_DIR}/route.camel.yaml" << 'ROUTE'
- route:
    id: cxf-soap-server-logging
    streamCaching: false
    from:
      uri: cxf:bean:cxfEndpoint
      steps:
        - log:
            message: "Server received request"
        - setBody:
            constant:
              expression: >
                <sayHelloResponse xmlns="http://example.com/hello"><greeting>Hello from CXF with logging</greeting></sayHelloResponse>
        - log:
            message: "Server sending response"
- route:
    id: cxf-soap-caller-logging
    from:
      uri: timer
      parameters:
        timerName: soap-caller
        repeatCount: 1
        delay: 5000
      steps:
        - setBody:
            constant:
              expression: >
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <sayHello xmlns="http://example.com/hello"><name>Forage</name></sayHello>
                  </soap:Body>
                </soap:Envelope>
        - setHeader:
            name: CamelHttpMethod
            constant:
              expression: POST
        - setHeader:
            name: Content-Type
            constant:
              expression: text/xml
        - to:
            uri: "http://localhost:8080/services/hello"
        - log:
            message: "Response: ${body}"
ROUTE

if [ -f "${PROJECT_DIR}/forage-cxf.properties" ] && [ -f "${PROJECT_DIR}/route.camel.yaml" ]; then
  echo "PASS: logging project files created in ${PROJECT_DIR}"
else
  echo "FAIL: project files not created"
  exit 1
fi
```

### Test 3.2: Start Camel with logging enabled

```bash
camel run "${PROJECT_DIR}"/* > "${PROJECT_DIR}/output.log" 2>&1 &
CAMEL_PID=$!
echo "Camel started with PID ${CAMEL_PID}"

sleep 2
kill -0 "${CAMEL_PID}" 2>/dev/null
if [ $? -eq 0 ]; then
  echo "PASS: Camel process ${CAMEL_PID} is running"
else
  echo "FAIL: Camel process ${CAMEL_PID} is not running"
  cat "${PROJECT_DIR}/output.log"
  exit 1
fi
```

### Test 3.3: Wait for server response in logs

```bash
FOUND=false
for i in $(seq 1 60); do
  grep -q "Response:" "${PROJECT_DIR}/output.log" 2>/dev/null && { FOUND=true; break; }
  sleep 1
done

if [ "${FOUND}" = "true" ]; then
  echo "PASS: response logged within timeout"
else
  echo "FAIL: response not found in logs within 60 seconds"
  cat "${PROJECT_DIR}/output.log"
fi
```

### Test 3.4: Verify response contains expected SOAP body

```bash
grep "Response:" "${PROJECT_DIR}/output.log" | grep -q "Hello from CXF with logging"
if [ $? -eq 0 ]; then
  echo "PASS: response contains 'Hello from CXF with logging'"
else
  echo "FAIL: response does not contain expected greeting"
  grep "Response:" "${PROJECT_DIR}/output.log"
fi
```

### Test 3.5: Verify CXF message logging output appears

When `logging.enabled=true`, CXF's `LoggingInInterceptor` and `LoggingOutInterceptor` produce log lines containing the inbound/outbound SOAP payloads. Look for CXF interceptor markers.

```bash
CXF_LOG_FOUND=false

grep -qi "Inbound Message\|Outbound Message\|REQ_OUT\|RESP_IN\|Payload:" "${PROJECT_DIR}/output.log" 2>/dev/null && CXF_LOG_FOUND=true

if [ "${CXF_LOG_FOUND}" = "true" ]; then
  echo "PASS: CXF message logging output found"
else
  echo "FAIL: CXF message logging output not found -- logging.enabled may not be working"
  echo "--- Full log output ---"
  cat "${PROJECT_DIR}/output.log"
fi
```

### Test 3.6: Stop Camel and cleanup

```bash
kill "${CAMEL_PID}" 2>/dev/null || true
wait "${CAMEL_PID}" 2>/dev/null || true

kill -0 "${CAMEL_PID}" 2>/dev/null
if [ $? -ne 0 ]; then
  echo "PASS: Camel process ${CAMEL_PID} stopped"
else
  echo "FAIL: Camel process ${CAMEL_PID} still running"
fi

rm -rf "${PROJECT_DIR}" || true
```

---

## Phase 4: Cleanup

Final cleanup to ensure no stale processes or temp directories remain.

### Step 4.1: Kill any remaining Camel processes from this test run

```bash
if [ -n "${CAMEL_PID}" ]; then
  kill "${CAMEL_PID}" 2>/dev/null || true
  wait "${CAMEL_PID}" 2>/dev/null || true
  echo "PASS: cleanup killed PID ${CAMEL_PID} (or already stopped)"
else
  echo "PASS: no CAMEL_PID set, nothing to stop"
fi
```

### Step 4.2: Remove temporary project directories

```bash
if [ -n "${PROJECT_DIR}" ] && [ -d "${PROJECT_DIR}" ]; then
  rm -rf "${PROJECT_DIR}" || true
  echo "PASS: removed ${PROJECT_DIR}"
else
  echo "PASS: no PROJECT_DIR to remove"
fi
```

---

## Test Summary

| Phase | Test ID | Test Name | Priority |
|-------|---------|-----------|----------|
| 0 | 0.1 | Verify required tools | Critical |
| 0 | 0.2 | Verify Java version >= 17 | Critical |
| 1 | 1.1 | Create default endpoint project files | Critical |
| 1 | 1.2 | Start Camel with Forage in background | Critical |
| 1 | 1.3 | Wait for server response in logs | Critical |
| 1 | 1.4 | Verify response contains expected SOAP body | Critical |
| 1 | 1.5 | Verify server received the request | High |
| 1 | 1.6 | Verify external curl call to SOAP endpoint | High |
| 1 | 1.7 | Stop Camel and cleanup | Critical |
| 2 | 2.1 | Create named endpoints project files | Critical |
| 2 | 2.2 | Start Camel with named endpoints | Critical |
| 2 | 2.3 | Wait for client response in logs | Critical |
| 2 | 2.4 | Verify named server received request | High |
| 2 | 2.5 | Verify response contains named server greeting | Critical |
| 2 | 2.6 | Verify external curl call to named server | High |
| 2 | 2.7 | Stop Camel and cleanup | Critical |
| 3 | 3.1 | Create logging-enabled project files | Critical |
| 3 | 3.2 | Start Camel with logging enabled | Critical |
| 3 | 3.3 | Wait for server response in logs | Critical |
| 3 | 3.4 | Verify response contains expected SOAP body | Critical |
| 3 | 3.5 | Verify CXF message logging output appears | High |
| 3 | 3.6 | Stop Camel and cleanup | Critical |
| 4 | 4.1 | Kill any remaining Camel processes | Critical |
| 4 | 4.2 | Remove temporary project directories | Critical |
