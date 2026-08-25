# Multi-Agent

[:material-github: Source](https://github.com/KaotoIO/forage-examples/tree/main/ai/multi){ .md-button .md-button--primary }

Multiple AI agents using different LLM providers in the same application, each with independent configuration and memory.

## What You'll Learn

- How to run multiple agents side-by-side with different model providers
- How named bean prefixes isolate each agent's configuration
- How to scope tools to specific agents using tags
- How to mix cloud and local models in a single Camel application

## Prerequisites

- [Camel JBang with the Forage plugin](../../guides/camel-jbang.md) installed
- [Ollama](https://ollama.com/) installed and running
- A Google Gemini API key

Pull the required model:

```bash
ollama pull granite4:3b
```

## Configuration

Create a `forage-agent-factory.properties` file with two named agent blocks:

```properties title="forage-agent-factory.properties"
# ── Agent "foo": Google Gemini (cloud) ──────────────────────────
forage.foo.agent.model.kind=google-gemini                  # (1)
forage.foo.agent.model.name=gemini-2.5-flash-lite          # (2)
forage.foo.agent.api.key=<my-api-key>                      # (3)
forage.foo.agent.features=memory
forage.foo.agent.memory.kind=message-window
forage.foo.agent.memory.max.messages=20

# ── Agent "bar": Ollama (local) ─────────────────────────────────
forage.bar.agent.model.kind=ollama                         # (4)
forage.bar.agent.model.name=granite4:3b
forage.bar.agent.base.url=http://localhost:11434
forage.bar.agent.features=memory
forage.bar.agent.memory.kind=message-window
forage.bar.agent.memory.max.messages=20
```

1. The first agent uses Google Gemini as its LLM provider.
2. The specific Gemini model to use.
3. The API key. You can also set this via environment variable: `FORAGE_FOO_AGENT_API_KEY=<value>`.
4. The second agent uses a locally hosted Ollama model. Each prefix (`foo`, `bar`) creates an independent bean.

## Route

Two routes send different questions to different agents. A shared tool is scoped to only the `foo` agent via tags.

```yaml title="multi-agent.camel.yaml"
- route:
    id: google
    description: This route uses an Agent that leverages Google Gemini
    from:
      uri: timer
      parameters:
        repeatCount: 1
        timerName: yaml
      steps:
        - setHeader:
            expression:
              simple:
                expression: "1"
            name: CamelLangChain4jAgentMemoryId
        - setBody:
            simple: look up user 123 in the user database and return their details
        - to:
            uri: langchain4j-agent:google
            parameters:
              agent: "#foo"                                # (1)
              tags: users                                  # (2)
        - log: ${body}
- route:
    id: ollama
    description: This route uses an Agent that leverages a locally hosted ollama
    from:
      uri: timer
      parameters:
        repeatCount: 1
        timerName: yaml
      steps:
        - setHeader:
            expression:
              simple:
                expression: "1"
            name: CamelLangChain4jAgentMemoryId
        - setBody:
            simple: What is the timezone in Brasilia?
        - to:
            uri: langchain4j-agent:ollama
            parameters:
              agent: "#bar"                                # (3)
        - log: ${body}
- route:
    id: user-db-tool
    from:
      uri: ai-tool:userDb
      parameters:
        description: Query user database by user ID
        parameter.userId: string
        parameter.userId.description: The user ID to look up
        parameter.userId.required: true
        tags: users                                        # (4)
      steps:
        - setBody:
            simple:
              expression: '{"name": "John Doe", "id": "123"}'
        - log:
            message: ${body}
```

1. References the Gemini agent bean by its prefix name `foo`.
2. The `users` tag links this agent to the `userDb` tool.
3. References the Ollama agent bean by its prefix name `bar`. No `tags` means this agent has no tool access.
4. The tool is only available to agents that declare the `users` tag.

## Running

```bash
camel run *
```

Both routes fire once. The Gemini agent receives the user lookup prompt, calls the `userDb` tool, and returns user details. The Ollama agent receives "What is the timezone in Brasilia?" and answers from its training data -- it has no tools available.

## Key Takeaways

- **Multiple named prefixes** (`forage.foo.agent.*`, `forage.bar.agent.*`) create independent agent beans in the same application.
- **Mix providers freely**: combine cloud APIs (Gemini, OpenAI, Anthropic) with local models (Ollama) by changing `model.kind`.
- **Tags control tool access**: only agents with matching tags can invoke a given tool. Agents without tags operate without tools.
- **Configuration isolation**: each agent has its own model, memory, and API credentials. Changing one agent does not affect the other.
- **Deterministic provider selection**: when multiple providers of the same type are on the classpath, Forage selects deterministically by `@ForageBean` value rather than classpath order.
