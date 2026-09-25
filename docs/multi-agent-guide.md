# Multi-Agent Development Guide with Forage

This guide explains how to use the Forage library to create sophisticated multi-agent systems using Apache Camel and Kaoto.

## Overview

Forage provides an opinionated library of beans for Apache Camel that simplifies AI integrations, particularly for creating multi-agent systems. The library offers pre-configured components that follow best practices for AI agent orchestration.

## Key Components

### Agent Factories

- **MultiAgentFactory**: Agent orchestration with ServiceLoader discovery, supporting both single-agent and multi-agent configurations

### Agent Types

- **SimpleAgent**: Memory-aware agent with conversation persistence
- **MemorylessAgent**: Stateless agent for independent interactions

### Model Providers

- **GoogleGeminiProvider**: Google Gemini AI models
- **OllamaProvider**: Local Ollama model integration
- **OpenAIProvider**: OpenAI GPT models

### Memory Providers

- **MessageWindowChatMemoryFactory**: Message window memory with persistence
- **InfinispanChatMemoryFactory**: Distributed memory storage
- **RedisChatMemoryFactory**: Redis-based memory for scalability

## Multi-Agent Configuration

### 1. Configuration Files

All agent configuration goes in a single `forage-agent-factory.properties` file. Each agent is identified by a unique prefix (e.g., `foo`, `bar`) and uses the `forage.<prefix>.agent.*` property pattern.

#### forage-agent-factory.properties
```properties
forage.foo.agent.model.kind=google-gemini
forage.foo.agent.model.name=gemini-2.5-flash-lite
forage.foo.agent.api.key=<my-api-key>
forage.foo.agent.features=memory
forage.foo.agent.memory.kind=message-window
forage.foo.agent.memory.max.messages=20

forage.bar.agent.model.kind=ollama
forage.bar.agent.model.name=granite4:3b
forage.bar.agent.base.url=http://localhost:11434
forage.bar.agent.features=memory
forage.bar.agent.memory.kind=message-window
forage.bar.agent.memory.max.messages=20
```

### 2. Agent ID Source Configuration

The MultiAgentFactory supports different strategies for extracting agent IDs from exchanges. By default, it uses the route ID, but you can configure it to extract the agent ID from headers, properties, or variables.

#### Available Agent ID Sources

| Source Type | Description | Configuration Property | Additional Configuration Required |
|-------------|-------------|----------------------|----------------------------------|
| `route` | Extract from route ID (default) | `forage.multi.agent.id.source=route` | None |
| `header` | Extract from exchange header | `forage.multi.agent.id.source=header` | `forage.multi.agent.id.source.header=HeaderName` |
| `property` | Extract from exchange property | `forage.multi.agent.id.source=property` | `forage.multi.agent.id.source.property=PropertyName` |
| `variable` | Extract from exchange variable | `forage.multi.agent.id.source=variable` | `forage.multi.agent.id.source.variable=VariableName` |

#### Configuration Examples

**Route ID Source (Default):**
```properties
# Uses the route ID to determine which agent to use
forage.multi.agent.id.source=route
```

**Header Source:**
```properties
# Extract agent ID from the "AgentType" header
forage.multi.agent.id.source=header
forage.multi.agent.id.source.header=AgentType
```

**Property Source:**
```properties
# Extract agent ID from the "agent.name" exchange property
forage.multi.agent.id.source=property
forage.multi.agent.id.source.property=agent.name
```

**Variable Source:**
```properties
# Extract agent ID from the "selectedAgent" exchange variable
forage.multi.agent.id.source=variable
forage.multi.agent.id.source.variable=selectedAgent
```

#### Route Examples with Different ID Sources

**Using Header for Agent Selection:**
```yaml
- route:
    id: dynamic-agent-header
    from:
      uri: direct:process-with-header
      steps:
        - setHeader:
            name: AgentType
            expression:
              simple: "${body.agentType}"
        - to:
            uri: langchain4j-agent:dynamic
            parameters:
              agent: "#google"
        - log: "Response from ${header.AgentType}: ${body}"
```

**Using Property for Agent Selection:**
```yaml
- route:
    id: dynamic-agent-property
    from:
      uri: direct:process-with-property
      steps:
        - setProperty:
            name: agent.name
            expression:
              simple: "${body.complexity == 'high' ? 'google' : 'ollama'}"
        - to:
            uri: langchain4j-agent:dynamic
            parameters:
              agent: "#google"
        - log: "Response from ${exchangeProperty.agent.name}: ${body}"
```

**Using Variable for Agent Selection:**
```yaml
- route:
    id: dynamic-agent-variable
    from:
      uri: direct:process-with-variable
      steps:
        - setVariable:
            name: selectedAgent
            expression:
              simple: "${body.userPreference}"
        - to:
            uri: langchain4j-agent:dynamic
            parameters:
              agent: "#google"
        - log: "Response from ${variable.selectedAgent}: ${body}"
```

### 3. Camel Route Configuration

#### Single Agent Example (agent.camel.yaml)

Uses `application.properties` for configuration:
```properties
forage.ollama.agent.model.kind=ollama
forage.ollama.agent.model.name=granite4:3b
forage.ollama.agent.base.url=http://localhost:11434
forage.ollama.agent.features=memory
forage.ollama.agent.memory.kind=message-window
forage.ollama.agent.memory.max.messages=20
```

```yaml
- route:
    id: single-agent-route
    from:
      uri: timer:yaml
      parameters:
        repeatCount: "1"
      steps:
        - setHeader:
            expression:
              simple:
                expression: "1"
            name: CamelLangChain4jAgentMemoryId
        - setBody:
            simple: look up user 123 in the user database and return their details
        - to:
            uri: langchain4j-agent:test
            parameters:
              agent: '#ollama'
              tags: users
        - log: ${body}

- route:
    id: tool-route
    from:
      uri: ai-tool:userDb
      parameters:
        description: Query user database by user ID
        parameter.userId: string
        parameter.userId.description: The user ID to look up
        parameter.userId.required: true
        tags: users
      steps:
        - setBody:
            simple:
              expression: '{"name": "John Doe", "id": "123"}'
        - log: ${body}
```

#### Multi-Agent Example (multi-agent.camel.yaml)

Uses `forage-agent-factory.properties` for configuration (see section 1 above).

```yaml
- route:
    id: foo-agent
    description: Route using Google Gemini Agent
    from:
      uri: timer:foo
      parameters:
        repeatCount: "1"
      steps:
        - setHeader:
            expression:
              simple:
                expression: "1"
            name: CamelLangChain4jAgentMemoryId
        - setBody:
            simple: look up user 123 in the user database and return their details
        - to:
            uri: langchain4j-agent:foo-test
            parameters:
              agent: "#foo"
              tags: users
        - log: "Foo Agent Response: ${body}"

- route:
    id: bar-agent
    description: Route using local Ollama Agent
    from:
      uri: timer:bar
      parameters:
        repeatCount: "1"
      steps:
        - setHeader:
            expression:
              simple:
                expression: "1"
            name: CamelLangChain4jAgentMemoryId
        - setBody:
            simple: What is the timezone in Brasilia?
        - to:
            uri: langchain4j-agent:bar-test
            parameters:
              agent: "#bar"
        - log: "Bar Agent Response: ${body}"

# Shared tools available to all agents
- route:
    id: user-database-tool
    from:
      uri: ai-tool:userDb
      parameters:
        description: Query user database by user ID
        parameter.userId: string
        parameter.userId.description: The user ID to look up
        parameter.userId.required: true
        tags: users
      steps:
        - setBody:
            simple:
              expression: '{"name": "John Doe", "id": "123"}'
        - log: "User DB Tool Response: ${body}"
```

## Running the Examples

### Prerequisites

1. **Camel JBang 4.22+**
2. **JBang 0.129.0+**
3. **API Keys**: Configure Google API key in `forage-agent-factory.properties`
4. **Ollama**: For local model integration, ensure Ollama is running on localhost:11434

### Single Agent Execution

```bash
camel run agent.camel.yaml \
  --dep=mvn:io.kaoto.forage:forage-agent:1.1-SNAPSHOT \
  --dep=mvn:io.kaoto.forage:forage-memory-message-window:1.1-SNAPSHOT \
  --dep=mvn:io.kaoto.forage:forage-model-ollama:1.1-SNAPSHOT
```

### Multi-Agent Execution

```bash
camel run multi-agent.camel.yaml \
  --dep=mvn:io.kaoto.forage:forage-agent:1.1-SNAPSHOT \
  --dep=mvn:io.kaoto.forage:forage-memory-message-window:1.1-SNAPSHOT \
  --dep=mvn:io.kaoto.forage:forage-model-google-gemini:1.1-SNAPSHOT \
  --dep=mvn:io.kaoto.forage:forage-model-ollama:1.1-SNAPSHOT
```

## Kaoto Integration

### Visual Route Design

Kaoto, the visual designer for Apache Camel, can be used to create and modify multi-agent routes visually:

1. **Import Camel YAML**: Load your `multi-agent.camel.yaml` into Kaoto
2. **Visual Editing**: Use the drag-and-drop interface to modify routes
3. **Agent Configuration**: Configure agent factories and parameters through the properties panel
4. **Export**: Generate updated YAML configurations for deployment

### Kaoto-Compatible Route Structure

When designing routes in Kaoto, ensure the following structure:

```yaml
# Route with proper IDs and descriptions for Kaoto
- route:
    id: unique-route-id
    description: Human-readable description
    from:
      id: from-component-id
      uri: component:name
      parameters:
        key: value
      steps:
        - processor:
            id: processor-id
        - to:
            id: to-component-id
            uri: langchain4j-agent:agent-name
            parameters:
              agent: "#agent-prefix"
```

### Best Practices for Kaoto

1. **Use Descriptive IDs**: Each component should have a unique, descriptive ID
2. **Add Descriptions**: Include route descriptions for better visual clarity
3. **Group Related Routes**: Organize routes logically for complex multi-agent systems
4. **Parameter Documentation**: Use clear parameter names and values

## Agent ID Source Use Cases

### When to Use Different Agent ID Sources

#### **Route ID Source (`route`)**
- **Best for**: Static agent assignment, simple routing scenarios
- **Use case**: Each route is dedicated to a specific agent type
- **Example**: Separate routes for different AI models or business domains

#### **Header Source (`header`)**
- **Best for**: External systems, API endpoints, user preferences
- **Use case**: When the client/caller specifies which agent to use
- **Example**: REST API where client sends `AgentType: google` header

#### **Property Source (`property`)**
- **Best for**: Complex routing logic, intermediate processing decisions
- **Use case**: When agent selection is determined by business logic within the route
- **Example**: Routing based on message complexity, user tier, or content analysis

#### **Variable Source (`variable`)**
- **Best for**: Cross-route agent selection, shared state scenarios
- **Use case**: When agent selection needs to be shared across multiple routes or processing steps
- **Example**: Multi-step workflows where earlier routes determine agent for later processing

### Real-World Examples

#### **Customer Support Routing**
```properties
# Route high-priority tickets to advanced AI
forage.multi.agent.id.source=property
forage.multi.agent.id.source.property=ticket.priority

# Configure agents
forage.basic.agent.model.kind=ollama
forage.basic.agent.model.name=granite4:3b
forage.basic.agent.base.url=http://localhost:11434

forage.advanced.agent.model.kind=google-gemini
forage.advanced.agent.model.name=gemini-2.5-flash-lite
forage.advanced.agent.api.key=<my-api-key>
```

#### **User Preference-Based Routing**
```properties
# Let users choose their preferred AI model
forage.multi.agent.id.source=header
forage.multi.agent.id.source.header=X-Preferred-Agent
```

## Advanced Multi-Agent Patterns

### Agent Chaining

Chain agents to create sophisticated workflows:

```yaml
- route:
    id: agent-chain
    from:
      uri: direct:start
      steps:
        - to:
            uri: langchain4j-agent:analysis
            parameters:
              agent: "#analysis"
        - to:
            uri: langchain4j-agent:synthesis
            parameters:
              agent: "#synthesis"
        - log: "Final result: ${body}"
```

### Conditional Agent Selection

Use Camel's choice component for dynamic agent selection:

```yaml
- route:
    id: conditional-agent
    from:
      uri: direct:process
      steps:
        - choice:
            when:
              - expression:
                  simple: "${header.complexity} == 'high'"
                steps:
                  - to:
                      uri: langchain4j-agent:advanced
                      parameters:
                        agent: "#advanced"
            otherwise:
              steps:
                - to:
                    uri: langchain4j-agent:simple
                    parameters:
                      agent: "#simple"
```

### Memory Management

Configure different memory strategies per agent:

```properties
# Long-term memory agent
forage.agent1.agent.memory.kind=redis

# Short-term memory agent
forage.agent2.agent.memory.kind=message-window
forage.agent2.agent.memory.max.messages=20

# Stateless agent
forage.agent3.agent.features=memoryless
```

### Guardrails

Guardrails allow you to add validation, filtering, or transformation logic that runs before (input guardrails) or after (output guardrails) an agent processes a request. Guardrails are configured as fully-qualified class names.

#### Configuring Guardrails

Configure guardrails in your `forage-agent-factory.properties` file:

```properties
# Input guardrails - executed before the agent processes input
forage.guardrails.input.classes=com.example.ContentFilterGuardrail,com.example.RateLimitGuardrail

# Output guardrails - executed after the agent produces output
forage.guardrails.output.classes=com.example.PiiRedactionGuardrail,com.example.ToxicityFilterGuardrail
```

#### Named Agent Guardrails

For multi-agent configurations, use prefixed guardrails for each agent:

```properties
# Secure agent with strict guardrails
forage.secure.agent.model.kind=openai
forage.secure.agent.model.name=gpt-4
forage.secure.agent.api.key=<my-api-key>
forage.secure.guardrails.input.classes=com.example.StrictContentFilter,com.example.AuthorizationGuardrail
forage.secure.guardrails.output.classes=com.example.PiiRedactionGuardrail,com.example.ComplianceGuardrail

# Standard agent with minimal guardrails
forage.standard.agent.model.kind=ollama
forage.standard.agent.model.name=granite4:3b
forage.standard.agent.base.url=http://localhost:11434
forage.standard.guardrails.output.classes=com.example.BasicOutputFilter
```

#### Environment Variable Configuration

Guardrails can also be configured via environment variables:

```bash
# Single guardrail
export FORAGE_GUARDRAILS_INPUT_CLASSES=com.example.ContentFilterGuardrail

# Multiple guardrails (comma-separated)
export FORAGE_GUARDRAILS_OUTPUT_CLASSES=com.example.PiiRedactionGuardrail,com.example.ToxicityFilterGuardrail

# Named agent guardrails (prefix with agent name in uppercase)
export FORAGE_SECURE_GUARDRAILS_INPUT_CLASSES=com.example.StrictContentFilter
```

## Configuration Reference

### Environment Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `FORAGE_GOOGLE_API_KEY` | Google Gemini API key | `sk-...` |
| `FORAGE_OLLAMA_BASE_URL` | Ollama server URL | `http://localhost:11434` |
| `FORAGE_OLLAMA_MODEL_NAME` | Ollama model name | `granite4:3b` |
| `FORAGE_MULTI_AGENT_ID_SOURCE` | Agent ID extraction strategy | `route`, `header`, `property`, `variable` |
| `FORAGE_MULTI_AGENT_ID_SOURCE_HEADER` | Header name for agent ID (when using header source) | `AgentType` |
| `FORAGE_MULTI_AGENT_ID_SOURCE_PROPERTY` | Property name for agent ID (when using property source) | `agent.name` |
| `FORAGE_MULTI_AGENT_ID_SOURCE_VARIABLE` | Variable name for agent ID (when using variable source) | `selectedAgent` |
| `FORAGE_GUARDRAILS_INPUT_CLASSES` | Comma-separated input guardrail class names | `com.example.InputFilter` |
| `FORAGE_GUARDRAILS_OUTPUT_CLASSES` | Comma-separated output guardrail class names | `com.example.OutputFilter` |

### System Properties

Configure via `-D` flags:

```bash
-Dforage.google.api.key=your-key
-Dforage.ollama.base.url=http://localhost:11434
-Dforage.multi.agent.id.source=header
-Dforage.multi.agent.id.source.header=AgentType
-Dforage.guardrails.input.classes=com.example.InputFilter
-Dforage.guardrails.output.classes=com.example.OutputFilter
```

### Named Configurations

Support multiple instances with prefixes:

```properties
# Named configurations using the kind-based pattern
forage.google.agent.model.kind=google-gemini
forage.google.agent.model.name=gemini-2.5-flash-lite
forage.google.agent.api.key=<my-api-key>

forage.ollama.agent.model.kind=ollama
forage.ollama.agent.model.name=granite4:3b
forage.ollama.agent.base.url=http://localhost:11434
```

## Troubleshooting

### Common Issues

1. **Missing Dependencies**: Ensure all required Maven dependencies are included
2. **API Key Configuration**: Verify API keys are properly set in configuration files
3. **Model Availability**: Check that configured models are available and accessible
4. **Memory Configuration**: Ensure memory providers are properly configured for stateful agents
5. **Agent ID Source Issues**:
   - **Unknown Agent Error**: Check that the extracted agent ID matches one of the configured agent prefixes
   - **Header/Property/Variable Not Found**: Verify the header, property, or variable is set before reaching the agent endpoint
   - **Missing Source Configuration**: When using `header`, `property`, or `variable` sources, ensure the corresponding name configuration is set
   - **Invalid Source Type**: Check that `forage.multi.agent.id.source` is set to one of: `route`, `header`, `property`, `variable`
6. **Guardrail Issues**:
   - **ClassNotFoundException**: Ensure guardrail class names are fully-qualified (e.g., `com.example.MyGuardrail`) and the classes are available on the classpath
   - **RuntimeForageException**: Check that guardrail classes can be loaded by the application's class loader; in Quarkus or Spring Boot, ensure classes are properly packaged
   - **Guardrails Not Executing**: Verify the configuration property names are correct (`forage.guardrails.input.classes` or `forage.guardrails.output.classes`) and for named agents, include the prefix (e.g., `forage.myagent.guardrails.input.classes`)

### Debug Mode

Enable debug logging for troubleshooting:

```bash
camel run multi-agent.camel.yaml --logging-level=DEBUG
```

## Next Steps

1. **Custom Agents**: Extend the library with custom agent implementations
2. **Tool Integration**: Add custom tools for specific business logic
3. **Monitoring**: Implement monitoring and observability for multi-agent systems
4. **Scaling**: Consider distributed deployment for production workloads

For more advanced topics, see the [Contributing Beans Guide](contributing-beans.md) and [Project Structure Documentation](structure.md).
