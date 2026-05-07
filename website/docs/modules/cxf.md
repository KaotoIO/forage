# CXF

Forage creates CXF SOAP endpoints for web service integration, handling endpoint configuration, WSDL binding, message logging, and security setup.

## Quick Start

```properties
forage.helloClient.cxf.kind=soap
forage.helloClient.cxf.address=http://localhost:8080/ws/hello
forage.helloClient.cxf.wsdl.url=http://localhost:8080/ws/hello?wsdl
forage.helloClient.cxf.data.format=PAYLOAD
forage.helloClient.cxf.logging.enabled=true
```

```yaml
- to:
    uri: cxf:bean:helloClient
```

## Supported Endpoint Types

{{ forage_beans_table("CXF Web Service", "org.apache.camel.component.cxf.jaxws.CxfEndpoint") }}

## Properties

{{ forage_properties("CXF Web Service") }}

## Multiple Endpoints

Use different names to configure multiple SOAP services -- for example, a server endpoint and two client endpoints:

```properties
forage.helloServer.cxf.kind=soap
forage.helloServer.cxf.address=http://localhost:8080/services/hello
forage.helloServer.cxf.wsdl.url=file:hello.wsdl
forage.helloServer.cxf.data.format=PAYLOAD

forage.payment.cxf.kind=soap
forage.payment.cxf.address=http://payment-svc:8080/ws/payment
forage.payment.cxf.wsdl.url=http://payment-svc:8080/ws/payment?wsdl
forage.payment.cxf.data.format=PAYLOAD

forage.inventory.cxf.kind=soap
forage.inventory.cxf.address=http://inventory-svc:8080/ws/stock
forage.inventory.cxf.wsdl.url=http://inventory-svc:8080/ws/stock?wsdl
forage.inventory.cxf.data.format=PAYLOAD
```

Reference each by name in routes:

```yaml
- from:
    uri: cxf:bean:helloServer
- to:
    uri: cxf:bean:payment
- to:
    uri: cxf:bean:inventory
```

!!! tip "MTOM Attachments"
    Enable `forage.cxf.mtom.enabled=true` to use MTOM (Message Transmission Optimization Mechanism) for efficient binary attachment transfer over SOAP.

!!! note "SSL/TLS"
    Set `forage.cxf.ssl.context.parameters` to the name of a Camel `SSLContextParameters` bean for HTTPS transport security. See the [Secured SOAP Client](../examples/cxf/soap-client-secured.md) example.

!!! info "Quarkus: Automatic Address Adaptation"
    When running on Quarkus, if a CXF endpoint is used as a **server** (route `from:`), Forage automatically converts its absolute localhost address (e.g., `http://localhost:8080/services/hello`) to a relative path (`/hello`) suitable for the Quarkus CXF servlet. The servlet root path is read from `quarkus.cxf.path` (default: `/services`).

    Client endpoints (route `to:`) are never adapted -- their absolute URL is preserved so they can reach external services. This means the same configuration works across JBang, Spring Boot, and Quarkus without changes.

    If you use a non-default CXF servlet root, set `quarkus.cxf.path` accordingly.
