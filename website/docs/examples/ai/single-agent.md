# Single Agent

[:material-github: Source](https://github.com/KaotoIO/forage-examples/tree/main/ai/single){ .md-button .md-button--primary }

An AI agent with tool use capabilities and conversation memory, powered by a locally hosted Ollama model.

## What You'll Learn

- How to configure an AI agent with Forage using named bean prefixes
- How to give the agent access to tools via Camel's `ai-tool` component
- How to enable conversation memory with a message-window strategy
- How the agent autonomously decides when to call a tool and how to interpret results

## Prerequisites

- [Camel JBang with the Forage plugin](../../guides/camel-jbang.md) installed
- [Ollama](https://ollama.com/) installed and running

Pull the required model:

```bash
ollama pull granite4:3b
```

## Configuration

Create a `forage-agent-factory.properties` file:

```properties title="forage-agent-factory.properties"
# The prefix "myAgent" becomes the bean name in the Camel registry.
# Reference it in routes as agent: '#myAgent'

# Chat model provider and model selection
forage.myAgent.agent.model.kind=ollama                    # (1)
forage.myAgent.agent.model.name=granite4:3b               # (2)
forage.myAgent.agent.base.url=http://localhost:11434       # (3)

# Enable conversation memory
forage.myAgent.agent.features=memory                      # (4)
forage.myAgent.agent.memory.kind=message-window            # (5)
forage.myAgent.agent.memory.max.messages=20                # (6)
```

1. The LLM provider to use. Forage discovers the matching module automatically.
2. The model served by Ollama. Any Ollama-compatible model works here.
3. The Ollama server URL.
4. Comma-separated list of features to enable on the agent. `memory` adds conversation history.
5. The memory strategy. `message-window` keeps a sliding window of recent messages.
6. Maximum number of messages retained in the memory window.

## Route

The route sends a natural-language question to the agent, which decides whether to invoke a tool to answer it.

```yaml title="agent.camel.yaml"
- route:
    id: ask-agent
    from:
      uri: timer:yaml
      parameters:
        repeatCount: "1"
      steps:
        - setHeader:
            expression:
              simple:
                expression: "1"
            name: CamelLangChain4jAgentMemoryId          # (1)
        - setBody:
            simple: look up user 123 in the user database and return their details  # (2)
        - to:
            uri: langchain4j-agent:agent
            parameters:
              agent: '#myAgent'                           # (3)
              tags: users                                 # (4)
        - log: ${body}
- route:
    id: user-db-tool
    from:
      uri: ai-tool:userDb                                  # (5)
      parameters:
        description: Query user database by user ID       # (6)
        parameter.userId: string                          # (7)
        parameter.userId.description: The user ID to look up
        parameter.userId.required: true
        tags: users                                       # (8)
      steps:
        - setBody:
            simple:
              expression: '{"name": "John Doe", "id": "123"}'
        - log:
            message: ${body}
```

1. The `CamelLangChain4jAgentMemoryId` header identifies the conversation session. Each unique ID maintains its own memory.
2. The natural-language prompt sent to the agent.
3. References the Forage-created bean by its prefix name `myAgent`.
4. Tags link agents to compatible tools. Only tools with matching tags are available to this agent.
5. Declares a tool endpoint named `userDb`. The agent can call this tool when it needs user data.
6. A natural-language description so the LLM understands what this tool does.
7. Declares the tool's input parameter and its type.
8. Tags must match the agent's tags for the tool to be discoverable.

## Running

```bash
camel run *
```

The route fires once, sends the prompt to the agent. The agent recognizes it needs user data, calls the `userDb` tool, receives the JSON response, and formulates a natural-language answer containing the user's name and ID.

## Key Takeaways

- **Named bean prefixes** (`forage.myAgent.agent.*`) define the bean name used in routes via `#myAgent`.
- **Tool routing** is declarative: define an `ai-tool` route, tag it, and the agent discovers it automatically.
- **Memory** is a single property toggle (`features=memory`) with pluggable strategies.
- **Zero boilerplate**: Forage handles all LangChain4j wiring -- model creation, memory setup, and tool binding.
