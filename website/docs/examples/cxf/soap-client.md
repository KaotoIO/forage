# SOAP Client

[:material-github: Source](https://github.com/KaotoIO/forage-examples/tree/main/cxf/soap-client){ .md-button .md-button--primary }

Call an existing SOAP web service with auto-configured CXF endpoint and WSDL binding -- no Java code needed.

## What You'll Learn

- How to read a WSDL and map it to Forage properties
- How Forage auto-configures a CXF `CxfEndpoint` from those properties
- Invoking a SOAP operation using named beans with operation headers
- Enabling CXF message logging for request/response tracing

## Scenario

You have a legacy HelloService SOAP web service. Its WSDL is published at `http://host:8080/services/hello?wsdl`. You need to call the `sayHello` operation from a Camel route.

Here is the relevant part of the WSDL:

```xml
<wsdl:definitions targetNamespace="http://example.com/hello" ...>
  <wsdl:service name="HelloService">
    <wsdl:port name="HelloPort" binding="tns:HelloBinding">
      <soap:address location="http://localhost:8080/services/hello"/>
    </wsdl:port>
  </wsdl:service>
  <wsdl:portType name="HelloPortType">
    <wsdl:operation name="sayHello">
      <wsdl:input message="tns:sayHelloRequest"/>
      <wsdl:output message="tns:sayHelloResponse"/>
    </wsdl:operation>
  </wsdl:portType>
</wsdl:definitions>
```

## Step 1: Map the WSDL to Forage Properties

Each WSDL element maps to a `forage.<name>.cxf.*` property. The name (`helloClient`) becomes the bean name used in routes:

```properties title="application.properties"
forage.helloClient.cxf.kind=soap                                         # (1)!
forage.helloClient.cxf.address=http://localhost:8080/services/hello       # (2)!
forage.helloClient.cxf.wsdl.url=http://localhost:8080/services/hello?wsdl # (3)!
forage.helloClient.cxf.service.name={http://example.com/hello}HelloService # (4)!
forage.helloClient.cxf.port.name={http://example.com/hello}HelloPort      # (5)!
forage.helloClient.cxf.data.format=PAYLOAD                               # (6)!
forage.helloClient.cxf.logging.enabled=true                              # (7)!
```

1. Selects the SOAP endpoint provider.
2. From `<soap:address location="..."/>` in the WSDL.
3. URL where the WSDL document is published.
4. From `<wsdl:service name="...">` with the target namespace as prefix.
5. From `<wsdl:port name="...">` with the target namespace.
6. `PAYLOAD` means raw XML elements -- no JAX-WS annotations needed.
7. Activates CXF's message interceptors for request/response logging.

Forage reads these properties and registers a fully configured `CxfEndpoint` bean as `helloClient` in the Camel registry.

## Step 2: Write the Route

=== "YAML"

    ```yaml title="route.camel.yaml"
    - route:
        id: cxf-soap-client
        from:
          uri: timer
          parameters:
            timerName: soap-caller
            repeatCount: 1
            period: 5000
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
                message: "SOAP response: ${body}"
    ```

=== "Java"

    ```java title="Route.java"
    public class Route extends RouteBuilder {

        @Override
        public void configure() throws Exception {
            from("timer:soap-caller?repeatCount=1&period=5000")
                .setBody(constant(
                    "<sayHello xmlns=\"http://example.com/hello\">"
                    + "<name>Forage</name></sayHello>"))
                .setHeader("operationName", constant("sayHello"))
                .setHeader("operationNamespace",
                    constant("http://example.com/hello"))
                .to("cxf:bean:helloClient")
                .log("SOAP response: ${body}");
        }
    }
    ```

The route constructs the XML body matching the `sayHello` operation's input message, sets the `operationName` and `operationNamespace` headers for CXF dispatch, and sends to the auto-configured endpoint.

## Prerequisites

- Java 17 or later
- [Camel JBang](https://camel.apache.org/manual/camel-jbang.html) with the Forage plugin installed

Start a SOAP server to call. You can use the companion [SOAP Server](soap-server.md) example:

```bash
cd ../soap-server && camel run *
```

## Running

```bash
camel run *
```

You should see:

```
SOAP response: <sayHelloResponse xmlns="http://example.com/hello">
  <greeting>Hello from CXF server</greeting>
</sayHelloResponse>
```

With `logging.enabled=true`, the full SOAP envelope (including HTTP headers) is also logged by CXF's message interceptors.

## Key Takeaways

- **WSDL-driven** -- the WSDL's service name, port name, and endpoint address map directly to `forage.<name>.cxf.*` properties. No need to parse the WSDL programmatically.
- **Zero boilerplate** -- a few properties replace manual `CxfEndpoint` setup, WSDL binding, and service class configuration.
- **Built-in logging** -- `logging.enabled=true` activates CXF's message interceptors for full request/response tracing without custom interceptor code.
- **Named beans** -- the route uses `cxf:bean:helloClient` to reference the endpoint by name. Multiple endpoints can coexist in the same application with different names.
