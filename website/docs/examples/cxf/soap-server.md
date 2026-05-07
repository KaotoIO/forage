# SOAP Server

[:material-github: Source](https://github.com/KaotoIO/forage-examples/tree/main/cxf/soap-server){ .md-button .md-button--primary }

Expose a SOAP web service endpoint using a contract-first approach -- WSDL, properties, and a YAML route. No Java annotations or service interfaces required.

## What You'll Learn

- How to use a WSDL contract to define a SOAP server endpoint
- How Forage creates a CXF server endpoint from the WSDL and properties
- Consuming SOAP requests with `cxf:bean:helloServer` as a route source
- Defining multiple CXF endpoints (server + client) in the same application
- Building SOAP responses in YAML routes

## Scenario

You need to expose a SOAP service for legacy clients that cannot migrate to REST. Instead of writing JAX-WS annotations, service endpoint interfaces, and CXF configuration, you provide a WSDL contract, a properties file, and a Camel route. Forage handles all CXF server setup.

This is also useful for building a REST-to-SOAP bridge: accept modern HTTP requests on one side, respond as a SOAP service on the other.

## Configuration

The server uses a contract-first approach with a local WSDL file:

```properties title="application.properties"
# Server endpoint
forage.helloServer.cxf.address=http://localhost:8080/services/hello        # (1)!
forage.helloServer.cxf.wsdl.url=file:hello.wsdl                           # (2)!
forage.helloServer.cxf.service.name={http://example.com/hello}HelloService # (3)!
forage.helloServer.cxf.port.name={http://example.com/hello}HelloPort      # (4)!
forage.helloServer.cxf.data.format=PAYLOAD                                # (5)!
forage.helloServer.cxf.logging.enabled=true

# Client endpoint (used by the test caller route)
forage.helloClient.cxf.address=http://localhost:8080/services/hello        # (6)!
forage.helloClient.cxf.wsdl.url=file:hello.wsdl
forage.helloClient.cxf.service.name={http://example.com/hello}HelloService
forage.helloClient.cxf.port.name={http://example.com/hello}HelloPort
forage.helloClient.cxf.data.format=PAYLOAD
```

1. The URL path where the SOAP service will be exposed.
2. Local WSDL file -- the service contract.
3. Service name from the WSDL, with target namespace prefix.
4. Port name from the WSDL, with target namespace prefix.
5. `PAYLOAD` means raw XML -- no JAX-WS annotations or `@WebService` interfaces needed.
6. A separate client endpoint pointing to the same server -- shows how multiple CXF beans coexist.

The WSDL defines the service contract (`hello.wsdl`), and Forage creates the CXF endpoint from it. CXF will also serve the WSDL at `http://localhost:8080/services/hello?wsdl`. Both `helloServer` and `helloClient` are registered as separate beans in the Camel registry.

## Route

=== "YAML"

    ```yaml title="route.camel.yaml"
    # Server: listens for SOAP requests and returns a response
    - route:
        id: cxf-soap-server
        streamCache: false
        from:
          uri: cxf:bean:helloServer
          steps:
            - log:
                message: "Server received SOAP request"
            - setBody:
                constant:
                  expression: >
                    <sayHelloResponse xmlns="http://example.com/hello">
                      <greeting>Hello from CXF server</greeting>
                    </sayHelloResponse>
            - log:
                message: "Server sending SOAP response"

    # Test caller: sends a SOAP request via the client endpoint
    - route:
        id: cxf-soap-test-caller
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
                    <sayHello xmlns="http://example.com/hello">
                      <name>Forage</name>
                    </sayHello>
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
                message: "Test caller received response: ${body}"
    ```

=== "Java"

    ```java title="Route.java"
    public class Route extends RouteBuilder {

        @Override
        public void configure() throws Exception {
            // Server: listen for SOAP requests
            from("cxf:bean:helloServer")
                .log("Server received SOAP request")
                .setBody(constant(
                    "<sayHelloResponse xmlns=\"http://example.com/hello\">"
                    + "<greeting>Hello from CXF server</greeting>"
                    + "</sayHelloResponse>"))
                .log("Server sending SOAP response");

            // Test caller: uses the client endpoint
            from("timer:soap-caller?repeatCount=1&delay=5000")
                .setBody(constant(
                    "<sayHello xmlns=\"http://example.com/hello\">"
                    + "<name>Forage</name></sayHello>"))
                .setHeader("operationName", constant("sayHello"))
                .setHeader("operationNamespace",
                    constant("http://example.com/hello"))
                .to("cxf:bean:helloClient")
                .log("Test caller received response: ${body}");
        }
    }
    ```

The first route acts as the SOAP server -- it consumes from `cxf:bean:helloServer`, processes the request, and returns a response. The second route is a test caller that uses `cxf:bean:helloClient` to send a SOAP request to the server, demonstrating how multiple CXF endpoints coexist in the same application.

## Prerequisites

- Java 17 or later
- [Camel JBang](https://camel.apache.org/manual/camel-jbang.html) with the Forage plugin installed

## Running

```bash
camel run *
```

You should see:

```text
Server received SOAP request
Server sending SOAP response
Test caller received response: <sayHelloResponse ...>Hello from CXF server</sayHelloResponse>
```

You can also test with curl:

```bash
curl -X POST http://localhost:8080/services/hello \
  -H "Content-Type: text/xml" \
  -d '<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
        <soap:Body>
          <sayHello xmlns="http://example.com/hello">
            <name>World</name>
          </sayHello>
        </soap:Body>
      </soap:Envelope>'
```

The WSDL is also served automatically:

```bash
curl http://localhost:8080/services/hello?wsdl
```

!!! note "Quarkus export"
    When exporting to Quarkus, Forage automatically adapts the server address (e.g., `http://localhost:8080/services/hello`) to a relative path (`/hello`) for the Quarkus CXF servlet. This only happens for server endpoints (used as route `from:`); client endpoints keep their absolute URL. See the [CXF module docs](../../modules/cxf.md) for details.

## Key Takeaways

- **Contract-first** -- the WSDL defines the service contract. CXF validates messages against it and serves it to clients automatically.
- **SOAP service in minutes** -- a WSDL, a properties file, and a YAML route replace JAX-WS annotations, service endpoint interfaces, and CXF server configuration.
- **No annotations needed** -- using `PAYLOAD` data format means raw XML handling. No `@WebService`, `@WebMethod`, or generated JAXB classes required.
- **REST-to-SOAP bridge** -- combine this with Camel's REST DSL to accept modern HTTP requests and expose them as a SOAP service for legacy clients.
- **Multiple endpoints** -- the same application defines both a server (`helloServer`) and a client (`helloClient`) CXF endpoint, each as a named bean. This pattern extends to any number of endpoints.
- **Self-testing** -- the built-in test caller route uses the CXF client endpoint to verify the server is working.
