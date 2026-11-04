# RAG

[:material-github: Source](https://github.com/KaotoIO/forage-examples/tree/main/ai/rag){ .md-button .md-button--primary }

Retrieval-Augmented Generation with Ollama, in-memory vector store, and automatic document chunking -- allowing an agent to answer questions from a local knowledge base.

## What You'll Learn

- How to configure an agent with RAG capabilities using Forage
- How document chunking and embedding work together to populate a vector store
- How the agent retrieves relevant context before answering a question
- The difference in answer quality with and without RAG

## Prerequisites

- [Camel JBang with the Forage plugin](../../guides/camel-jbang.md) installed
- [Ollama](https://ollama.com/) installed and running

Pull the required models:

```bash
ollama pull granite4:3b
ollama pull granite-embedding:latest
```

## Knowledge Base

Create a text file with the information the agent should be able to reference:

```text title="company-knowledge-base.txt"
Miles of Camels Car Rental - Company Information

BUSINESS HOURS:
Monday-Friday: 8:00 AM - 6:00 PM
Saturday: 9:00 AM - 4:00 PM
Sunday: Closed

CANCELLATION POLICY
- Cancellations made 24 hours before pickup: Full refund
- Cancellations made 12-24 hours before pickup: 50% refund
- Cancellations made less than 12 hours before pickup: No refund

...
```

This file is automatically chunked, embedded, and loaded into the in-memory vector store at startup.

## Configuration

Create a `forage-agent-factory.properties` file:

```properties title="forage-agent-factory.properties"
# The prefix "myAgent" becomes the bean name in the Camel registry.
# Reference it in routes as agent: '#myAgent'

# ── Chat model ──────────────────────────────────────────────────
forage.myAgent.agent.model.kind=ollama                               # (1)
forage.myAgent.agent.model.name=granite4:3b
forage.myAgent.agent.base.url=http://localhost:11434

# ── Embedding model ─────────────────────────────────────────────
forage.myAgent.agent.embedding.model.name=granite-embedding:latest   # (2)
forage.myAgent.agent.embedding.model.base.url=http://localhost:11434
forage.myAgent.agent.embedding.model.timeout=PT30S                   # (3)

# ── In-memory vector store ──────────────────────────────────────
forage.myAgent.agent.in.memory.store.file.source=company-knowledge-base.txt  # (4)
forage.myAgent.agent.in.memory.store.max.size=300                    # (5)
forage.myAgent.agent.in.memory.store.overlap.size=100                # (6)

# ── Retrieval settings ──────────────────────────────────────────
forage.myAgent.agent.rag.max.results=3                               # (7)
forage.myAgent.agent.rag.min.score=0.6                               # (8)
```

1. The chat model provider. Same as in the [single agent](single-agent.md) example.
2. The embedding model used to convert text chunks into vectors. Must be an embedding-capable model.
3. Timeout for embedding operations. Embedding an entire document can take longer than a single chat call.
4. Path to the knowledge base file. Forage reads, chunks, embeds, and loads it into the vector store at startup.
5. Maximum chunk size in characters. The document is split into segments of this size.
6. Overlap between consecutive chunks in characters. Overlap prevents information loss at chunk boundaries.
7. Number of most-relevant chunks to retrieve for each query.
8. Minimum similarity score (0.0 to 1.0). Chunks below this threshold are discarded.

## Route

The route sends a question about the company's cancellation policy. The agent retrieves relevant chunks from the knowledge base before generating its answer.

```yaml title="main-route.camel.yaml"
- route:
    id: rag-query
    from:
      uri: timer:yaml
      parameters:
        repeatCount: "1"
      steps:
        - setBody:
            simple: >-                                                 # (1)
              "Describe the Miles of Camels Car Rental cancellations
              policy for cancelling 24 hours before pickup.
              What is the refund amount?"
        - convertBodyTo:
            type: String
        - unmarshal:
            json: {}
        - to:
            uri: langchain4j-agent:agent
            parameters:
              agent: '#myAgent'                                        # (2)
        - log:
            message: ${body}
```

1. The question references specific information only available in the knowledge base.
2. References the Forage-created agent bean. Because RAG is configured, the agent automatically augments the prompt with retrieved context.

## Running

```bash
camel run *
```

No `--dep` flags are needed: the Forage plugin reads the properties and adds the RAG modules next to the Ollama chat and embedding models. The embedding model and in-memory store properties add `forage-in-memory-store` and `forage-default-retrieval-augmentor`; `camel forage export` adds the same modules to the exported project.

The route fires once and asks about the cancellation policy. Because RAG is enabled, the agent retrieves the relevant chunk from the knowledge base and correctly answers that a **full refund** is given when cancelling 24 hours before pickup.

### Without RAG

To see the difference RAG makes, comment out the embedding model, in-memory vector store and retrieval settings in `forage-agent-factory.properties`, and run the route again:

```bash
camel run main-route.camel.yaml forage-agent-factory.properties
```

Without access to the knowledge base, the agent cannot confirm the refund amount and responds with a generic disclaimer that terms may vary.

## Key Takeaways

- **RAG is configuration-only**: adding embedding model, vector store, and retrieval settings to properties is all that is needed. No code changes to the route.
- **Automatic document processing**: Forage handles chunking, embedding, and loading the knowledge base file at startup.
- **Tunable retrieval**: `max.results` and `min.score` control how much context the agent receives, balancing relevance against noise.
- **Same agent pattern**: the route looks identical to a non-RAG agent. The RAG behavior is entirely driven by configuration.
- **Fail-fast on misconfiguration**: if RAG properties are present but the pipeline cannot be assembled (e.g., missing embedding provider dependency), Forage fails at startup with an actionable error instead of silently running without RAG.
