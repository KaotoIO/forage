# Forage Ollama Model

This module provides integration with Ollama AI models for the Forage framework.

## Overview

The Ollama model integration allows you to use locally hosted Ollama models with Forage. Ollama provides an easy way to run large language models locally, supporting models like Llama 3, Mistral, Code Llama, and many others.

## Dependencies

Add this dependency to your Maven project:

```xml
<dependency>
    <groupId>io.kaoto.forage</groupId>
    <artifactId>forage-model-ollama</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

## Configuration

The Ollama provider is configured through environment variables, system properties, or configuration files.

### Required Configuration

| Environment Variable | System Property | Description | Default |
|---------------------|-----------------|-------------|---------|
| `OLLAMA_BASE_URL` | `ollama.base.url` | Base URL of the Ollama server | `http://localhost:11434` |
| `OLLAMA_MODEL_NAME` | `ollama.model.name` | Name of the Ollama model to use | `llama3` |

### Optional Configuration

| Environment Variable | System Property | Description | Default | Valid Range |
|---------------------|-----------------|-------------|---------|-------------|
| `OLLAMA_TEMPERATURE` | `ollama.temperature` | Controls randomness in responses | None | 0.0 - 2.0 |
| `OLLAMA_TOP_K` | `ollama.top.k` | Limits model to top K tokens | None | Positive integers |
| `OLLAMA_TOP_P` | `ollama.top.p` | Nucleus sampling parameter | None | 0.0 - 1.0 |
| `OLLAMA_MIN_P` | `ollama.min.p` | Minimum probability threshold | None | 0.0 - 1.0 |
| `OLLAMA_NUM_CTX` | `ollama.num.ctx` | Context window size | None | Positive integers |
| `OLLAMA_LOG_REQUESTS` | `ollama.log.requests` | Enable request logging | None | true/false |
| `OLLAMA_LOG_RESPONSES` | `ollama.log.responses` | Enable response logging | None | true/false |
| `OLLAMA_TIMEOUT` | `ollama.timeout` | Request timeout duration in ISO-8601 format | None | e.g. `PT120S`, `PT5M` |

### Configuration Sources Priority

Configuration values are resolved in the following order of precedence:

1. Environment variables
2. System properties
3. `forage-model-ollama.properties` file in classpath
4. Default values (only for base URL and model name)

## Usage Example

### Basic Setup

```bash
# Set environment variables
export OLLAMA_BASE_URL="http://localhost:11434"
export OLLAMA_MODEL_NAME="llama3"
```

### Advanced Configuration

```bash
# Environment variables for advanced configuration
export OLLAMA_BASE_URL="http://my-ollama-server:11434"
export OLLAMA_MODEL_NAME="llama3.1"
export OLLAMA_TEMPERATURE="0.7"
export OLLAMA_TOP_K="40"
export OLLAMA_TOP_P="0.9"
export OLLAMA_NUM_CTX="2048"
export OLLAMA_LOG_REQUESTS="false"
export OLLAMA_LOG_RESPONSES="false"
export OLLAMA_TIMEOUT="PT120S"
```

### Configuration File

Create a `forage-model-ollama.properties` file in your classpath:

```properties
# Ollama server configuration
ollama.base.url=http://localhost:11434
ollama.model.name=llama3

# Model parameters
ollama.temperature=0.7
ollama.top.k=40
ollama.top.p=0.9
ollama.min.p=0.05
ollama.num.ctx=2048

# Logging configuration
ollama.log.requests=false
ollama.log.responses=false

# Timeout configuration
ollama.timeout=PT120S
```

## Supported Models

Common Ollama models you can use:

- **llama3** - Meta's Llama 3 model (8B parameters)
- **llama3.1** - Updated Llama 3.1 model
- **llama3:70b** - Larger Llama 3 model (70B parameters)
- **mistral** - Mistral 7B model
- **codellama** - Code-specialized Llama model
- **phi3** - Microsoft's Phi-3 model
- **gemma** - Google's Gemma model

## Parameter Guidelines

### Temperature
- **0.0-0.3**: Very focused and deterministic responses
- **0.4-0.7**: Balanced creativity and coherence
- **0.8-1.0**: More creative and diverse responses
- **1.0+**: Highly creative but potentially less coherent

### Top-K
- **1-10**: Very conservative, limited vocabulary
- **20-40**: Good balance for most use cases
- **50-100**: More diverse vocabulary

### Top-P
- **0.1-0.5**: Conservative, focused responses
- **0.6-0.9**: Balanced approach (recommended)
- **0.9+**: More diverse but potentially inconsistent

### Context Window (num_ctx)
- Depends on your model and available memory
- Larger values allow longer conversations but use more resources
- Common values: 512, 1024, 2048, 4096

### Timeout
- Uses ISO-8601 duration format (e.g., `PT30S` for 30 seconds, `PT5M` for 5 minutes)

## Troubleshooting

### Common Issues

1. **Connection refused**: Ensure Ollama is running and accessible at the configured URL
2. **Model not found**: Verify the model name and ensure it's installed in Ollama
3. **Out of memory**: Reduce context window size or use a smaller model
4. **Slow responses**: Consider using a smaller model or reducing context window

### Verification Commands

```bash
# Check if Ollama is running
curl http://localhost:11434/api/tags

# List available models
ollama list

# Pull a specific model
ollama pull llama3
```

## Security Considerations

- Be cautious with request/response logging in production environments
- Ensure your Ollama server is properly secured if exposed to networks
- Consider using HTTPS for remote Ollama servers
- Monitor resource usage as large models can consume significant memory

## Performance Tips

1. Use smaller models for faster responses when appropriate
2. Adjust context window based on your use case
3. Consider running Ollama on dedicated hardware for production workloads
4. Monitor memory and CPU usage to optimize performance