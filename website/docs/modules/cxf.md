# CXF

Forage creates CXF SOAP endpoints for web service integration, handling endpoint configuration, WSDL binding, message logging, and security setup.

## Quick Start

```properties
forage.cxf.kind=soap
forage.cxf.address=http://localhost:8080/ws/hello
forage.cxf.wsdl.url=http://localhost:8080/ws/hello?wsdl
forage.cxf.data.format=PAYLOAD
forage.cxf.logging.enabled=true
```

```yaml
- to:
    uri: cxf:bean:cxfEndpoint
```

## Supported Endpoint Types

{{ forage_beans_table("CXF Web Service", "org.apache.camel.component.cxf.jaxws.CxfEndpoint") }}

## Properties

{{ forage_properties("CXF Web Service") }}

## Multiple Endpoints

Use different names to configure multiple SOAP services -- for example, a payment gateway and an inventory system:

```properties
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
- to:
    uri: cxf:bean:payment
- to:
    uri: cxf:bean:inventory
```

!!! tip "MTOM Attachments"
    Enable `forage.cxf.mtom.enabled=true` to use MTOM (Message Transmission Optimization Mechanism) for efficient binary attachment transfer over SOAP.

!!! note "SSL/TLS"
    Set `forage.cxf.ssl.context.parameters` to the name of a Camel `SSLContextParameters` bean for HTTPS transport security. See the [Secured SOAP Client](../examples/cxf/soap-client-secured.md) example.
