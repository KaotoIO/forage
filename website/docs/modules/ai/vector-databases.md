# Vector Databases

Vector databases store embeddings for similarity search, used in RAG (Retrieval-Augmented Generation) pipelines.

## Available Providers

{{ forage_beans_table("Agent", "Embedding Store") }}

## RAG Configuration

{{ forage_bean_properties("Agent", "RAG", "defaultRag") }}

## Provider Notes

### In-Memory Store

Zero-dependency store for development. Loads documents from a file, chunks them, and embeds them at startup.

```properties
forage.myAgent.agent.in.memory.store.file.source=knowledge-base.txt
forage.myAgent.agent.in.memory.store.max.size=300
forage.myAgent.agent.in.memory.store.overlap.size=100
```

These properties are enough for `camel forage run` and `camel forage export` to add the `forage-in-memory-store` module and, since the file source turns RAG on, `forage-default-retrieval-augmentor`. Setting `forage.myAgent.agent.embedding.store.kind=in-memory-store` selects the store explicitly.

### Pinecone

Supports automatic index creation when `create.index=true` is set alongside `dimension`, `cloud`, and `region`.

### Milvus

The `database.name` property is now wired to the builder. Port defaults to `19530` if not set.

### Weaviate

Port defaults to `8080` (REST) and `50051` (gRPC). gRPC is disabled by default (`use.grpc.for.inserts=false`).

### Qdrant

Port defaults to `6334` if not set.

### PgVector

Port defaults to `5432` if not set.
