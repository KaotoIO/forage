# SOAP Server

[:material-github: Source](https://github.com/KaotoIO/forage-examples/tree/main/cxf/soap-server){ .md-button .md-button--primary }

Expose a SOAP web service endpoint using a contract-first approach -- WSDL, properties, and a YAML route. No Java annotations or service interfaces required.

## What You'll Learn

- How to use a WSDL contract to define a SOAP server endpoint
- How Forage creates a CXF server endpoint from the WSDL and properties
- Consuming SOAP requests with `cxf:bean:cxfEndpoint` as a route source
- Building SOAP responses in YAML routes

## Scenario

You need to expose a SOAP service for legacy clients that cannot migrate to REST. Instead of writing JAX-WS annotations, service endpoint interfaces, and CXF configuration, you provide a WSDL contract, a properties file, and a Camel route. Forage handles all CXF server setup.

This is also useful for building a REST-to-SOAP bridge: accept modern HTTP requests on one side, respond as a SOAP service on the other.

## Configuration

The server uses a contract-first approach with a local WSDL file:

```properties title="application.properties"
forage.cxf.kind=soap                                                      # (1)!
forage.cxf.address=http://localhost:8080/services/hello                    # (2)!
forage.cxf.wsdl.url=file:hello.wsdl                                       # (3)!
forage.cxf.service.name={http://example.com/hello}HelloService            # (4)!
forage.cxf.port.name={http://example.com/hello}HelloPort                  # (5)!
forage.cxf.data.format=PAYLOAD                                            # (6)!
forage.cxf.logging.enabled=true
```

1. Selects the SOAP endpoint provider.
2. The URL path where the SOAP service will be exposed.
3. Local WSDL file -- the service contract.
4. Service name from the WSDL, with target namespace prefix.
5. Port name from the WSDL, with target namespace prefix.
6. `PAYLOAD` means raw XML -- no JAX-WS annotations or `@WebService` interfaces needed.

The WSDL defines the service contract (`hello.wsdl`), and Forage creates the CXF endpoint from it. CXF will also serve the WSDL at `http://localhost:8080/services/hello?wsdl`.

## Route

=== "YAML"

    ```yaml title="route.camel.yaml"
    # Server: listens for SOAP requests and returns a response
    - route:
        id: cxf-soap-server
        streamCache: false
        from:
          uri: cxf:bean:cxfEndpoint
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

    # Test caller: sends a SOAP request to verify the server
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
                    <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                      <soap:Body>
                        <sayHello xmlns="http://example.com/hello">
                          <name>Forage</name>
                        </sayHello>
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
                message: "Test caller received response: ${body}"
    ```

=== "Java"

    ```java title="Route.java"
    public class Route extends RouteBuilder {

        @Override
        public void configure() throws Exception {
            // Server: listen for SOAP requests
            from("cxf:bean:cxfEndpoint")
                .log("Server received SOAP request")
                .setBody(constant(
                    "<sayHelloResponse xmlns=\"http://example.com/hello\">"
                    + "<greeting>Hello from CXF server</greeting>"
                    + "</sayHelloResponse>"))
                .log("Server sending SOAP response");

            // Test caller
            from("timer:soap-caller?repeatCount=1&delay=5000")
                .setBody(constant(
                    "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                    + "<soap:Body>"
                    + "<sayHello xmlns=\"http://example.com/hello\">"
                    + "<name>Forage</name></sayHello>"
                    + "</soap:Body></soap:Envelope>"))
                .setHeader("CamelHttpMethod", constant("POST"))
                .setHeader("Content-Type", constant("text/xml"))
                .to("http://localhost:8080/services/hello")
                .log("Test caller received response: ${body}");
        }
    }
    ```

The first route acts as the SOAP server -- it consumes from `cxf:bean:cxfEndpoint`, processes the request, and returns a response. The second route is a test caller that fires once after 5 seconds to verify the server works.

## Prerequisites

- Java 17 or later
- [Camel JBang](https://camel.apache.org/manual/camel-jbang.html) with the Forage plugin installed

## Running

```bash
camel run *
```

You should see:

```
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
    When exporting to Quarkus, you may need to change the address to a relative path (e.g., `/services/hello`) because Quarkus manages the HTTP server.

## Key Takeaways

- **Contract-first** -- the WSDL defines the service contract. CXF validates messages against it and serves it to clients automatically.
- **SOAP service in minutes** -- a WSDL, a properties file, and a YAML route replace JAX-WS annotations, service endpoint interfaces, and CXF server configuration.
- **No annotations needed** -- using `PAYLOAD` data format means raw XML handling. No `@WebService`, `@WebMethod`, or generated JAXB classes required.
- **REST-to-SOAP bridge** -- combine this with Camel's REST DSL to accept modern HTTP requests and expose them as a SOAP service for legacy clients.
- **Self-testing** -- the built-in test caller route verifies the server is working without external tools.
