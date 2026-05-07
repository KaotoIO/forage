# Examples

Working examples demonstrating Forage capabilities across datasources, messaging, transactions, and AI.

All examples are self-contained and can be run with Camel JBang or exported to Spring Boot / Quarkus.

## Datasource

| Example | Description |
|---|---|
| [Single Database](datasource/single.md) | Basic PostgreSQL connection with SQL, JDBC, and Spring-JDBC components |
| [Multiple Databases](datasource/multi.md) | Named datasources connecting to PostgreSQL and MySQL simultaneously |
| [Event Booking](datasource/event-booking.md) | Transactional booking system with ACID guarantees and rollback |
| [Aggregation Repository](datasource/aggregation.md) | IoT event batching with JDBC-backed aggregation |
| [Idempotent Consumer](datasource/idempotent.md) | Duplicate prevention with JDBC idempotent repository |

## JMS

| Example | Description |
|---|---|
| [Basic Messaging](jms/single.md) | JMS producer/consumer with ActiveMQ Artemis |
| [Transactional JMS](jms/transactional.md) | XA transactions with Narayana, rollback, and redelivery |

## CXF

| Example | Description |
|---|---|
| [SOAP Client](cxf/soap-client.md) | Call a SOAP web service -- WSDL-driven configuration walkthrough |
| [Secured SOAP Client](cxf/soap-client-secured.md) | SOAP with SSL/TLS and username/password authentication |
| [SOAP Server](cxf/soap-server.md) | Expose a SOAP server endpoint from properties |

## Transactions

| Example | Description |
|---|---|
| [Distributed XA](transactions/distributed-xa.md) | Single XA transaction spanning JMS and JDBC |

## AI

| Example | Description |
|---|---|
| [Single Agent](ai/single-agent.md) | AI agent with tool use and memory (Ollama) |
| [Multi-Agent](ai/multi-agent.md) | Multiple agents with different providers (Gemini + Ollama) |
| [RAG](ai/rag.md) | Retrieval-Augmented Generation with embeddings and vector store |
