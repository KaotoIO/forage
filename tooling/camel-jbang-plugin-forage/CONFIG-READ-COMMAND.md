# ConfigReadCommand Architecture

## Overview

`ConfigReadCommand` is a Camel JBang plugin command that reads Forage configuration properties from properties files and generates a JSON list of beans that would be created at runtime. It's designed to support the Kaoto VSCode extension by providing a machine-readable view of configured beans.

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         ConfigReadCommand Flow                           │
└─────────────────────────────────────────────────────────────────────────┘

INPUT
═════
┌─────────────────────────────────────────────────┐
│ Command Options                                  │
│ --dir/-d      : Directory to scan (default: pwd)│
│ --filter/-f   : Filter by factory type          │
│ --strategy/-s : "forage" or "application"       │
└─────────────────────────────────────────────────┘
                │
                ▼

PROCESSING PIPELINE
═══════════════════

1. Find Properties Files
   ┌──────────────────────────────────────────────────┐
   │ findPropertiesFiles(directory)                   │
   │                                                   │
   │ Only the top level of --dir is scanned.          │
   │                                                   │
   │ Strategy "forage":                               │
   │   • Catalog file names (Config.name())           │
   │   • Any other forage-*.properties file           │
   │                                                   │
   │ Strategy "application" (default):                │
   │   • Look for application.properties only        │
   │                                                   │
   │ Returns: List<File>                              │
   └──────────────┬───────────────────────────────────┘
                  │
                  ▼

2. Parse Each Properties File
   ┌──────────────────────────────────────────────────────────┐
   │ parsePropertiesFile(file)                                │
   │                                                           │
   │ For each property matching "forage.*":                   │
   │   1. Parse property key to extract:                      │
   │      • Factory type (jdbc, jms, multi, etc.)            │
   │      • Instance name (optional prefix)                   │
   │      • Bean type (for multi-factory: ollama, etc.)      │
   │      • Property name                                     │
   │                                                           │
   │   2. Group properties by instance:                       │
   │      Key: factoryType:instanceName:beanType             │
   │      Value: InstanceProperties                           │
   │                                                           │
   │   3. Convert to BeanInfo objects                         │
   │                                                           │
   │ Returns: List<BeanInfo>                                  │
   └──────────────┬───────────────────────────────────────────┘
                  │
                  ▼

3. Property Key Parsing
   ┌──────────────────────────────────────────────────────────┐
   │ parsePropertyKey(key)                                    │
   │                                                           │
   │ Examples:                                                │
   │  "jdbc.url"                                              │
   │    → factoryType=jdbc, instanceName=null                │
   │                                                           │
   │  "ds1.jdbc.url"                                          │
   │    → factoryType=jdbc, instanceName=ds1                 │
   │                                                           │
   │  "ollama.model.name"                                     │
   │    → factoryType=multi, beanType=ollama                 │
   │                                                           │
   │  "test.ollama.model.name"                                │
   │    → factoryType=multi, beanType=ollama,                │
   │       instanceName=test                                  │
   │                                                           │
   │ Uses ForageCatalog to:                                   │
   │  • Identify known factory type keys                     │
   │  • Map bean names to factory types                      │
   │  • Match property prefixes                              │
   │                                                           │
   │ Returns: ParsedProperty                                  │
   └──────────────┬───────────────────────────────────────────┘
                  │
                  ▼

4. Create BeanInfo
   ┌──────────────────────────────────────────────────────────┐
   │ createBeanInfo(instance, sourceFile)                     │
   │                                                           │
   │ Determines:                                              │
   │  • Bean name (from instance name or default)            │
   │  • Bean kind (from beanType or kind property)           │
   │  • Java type (from catalog metadata)                    │
   │  • Configuration (all property values)                  │
   │  • Conditional beans (based on enabled features)        │
   │                                                           │
   │ Returns: BeanInfo                                        │
   └──────────────┬───────────────────────────────────────────┘
                  │
                  ▼

5. Detect Conditional Beans
   ┌──────────────────────────────────────────────────────────┐
   │ detectConditionalBeans(instance)                         │
   │                                                           │
   │ For each conditional bean definition in catalog:         │
   │   1. Check if config entry is enabled (e.g.,            │
   │      "transaction.enabled" = "true")                     │
   │   2. If enabled, add all beans from that definition     │
   │                                                           │
   │ Examples:                                                │
   │  • transaction.enabled=true                              │
   │    → PROPAGATION_REQUIRED (TransactedPolicy)            │
   │  • aggregation.repository.enabled=true                   │
   │    → JdbcAggregationRepository                          │
   │  • idempotent.repository.enabled=true                    │
   │    → JdbcMessageIdRepository                            │
   │                                                           │
   │ Returns: List<ConditionalBeanInfo>                       │
   └──────────────┬───────────────────────────────────────────┘
                  │
                  ▼

6. Apply Filter (Optional)
   ┌──────────────────────────────────────────────────────────┐
   │ If --filter specified:                                   │
   │   Filter beans by factoryType matching filter value     │
   └──────────────┬───────────────────────────────────────────┘
                  │
                  ▼

OUTPUT
══════
┌─────────────────────────────────────────────────────────────┐
│ JSON Response (stdout)                                       │
│ {                                                            │
│   "success": true,                                           │
│   "directory": "/path/to/dir",                              │
│   "beanCount": 5,                                            │
│   "beans": [                                                 │
│     {                                                        │
│       "name": "myPG",                                        │
│       "kind": "postgresql",                                  │
│       "javaType": "javax.sql.DataSource",                   │
│       "sourceFile": "/path/to/application.properties",      │
│       "configuration": {                                     │
│         "db.kind": "postgresql",                            │
│         "url": "jdbc:postgresql://localhost:5432/",         │
│         "username": "test",                                  │
│         "password": "test",                                  │
│         "transaction.enabled": "true"                        │
│       },                                                     │
│       "conditionalBeans": [                                  │
│         {                                                    │
│           "name": "PROPAGATION_REQUIRED",                   │
│           "javaType": "o.a.c.spi.TransactedPolicy",         │
│           "description": "Transaction policy"               │
│         }                                                    │
│       ]                                                      │
│     }                                                        │
│   ]                                                          │
│ }                                                            │
└─────────────────────────────────────────────────────────────┘
```

## Key Components

### ForageCatalog (Singleton)
- Metadata about all factory types
- Bean name to factory type mapping
- Property prefix to factory type mapping
- Conditional bean definitions

### Data Structures

**ParsedProperty (Record)**
- `factoryType`: String (jdbc, jms, multi, etc.)
- `instanceName`: String (optional bean name prefix)
- `beanType`: String (for multi-factory: ollama, infinispan, etc.)
- `propertyName`: String (the actual property name)

**InstanceProperties (Class)**
- `factoryType`: String
- `instanceName`: String
- `beanType`: String
- `properties`: Map<String, String>

**BeanInfo (Record)**
- `name`: String (bean name)
- `kind`: String (bean kind/type)
- `factoryType`: String
- `javaType`: String (Java interface/class)
- `sourceFile`: String (path to properties file)
- `configuration`: Map<String, String>
- `conditionalBeans`: List<ConditionalBeanInfo>

**ConditionalBeanInfo (Record)**
- `name`: String
- `javaType`: String
- `description`: String

## Usage Examples

### Example 1: Read All Beans

**Properties File (application.properties):**
```properties
forage.myPG.jdbc.db.kind=postgresql
forage.myPG.jdbc.url=jdbc:postgresql://localhost:5432/
forage.myPG.jdbc.username=test
forage.myPG.jdbc.password=test
forage.myPG.jdbc.transaction.enabled=true
forage.myPG.jdbc.aggregation.repository.enabled=true
forage.myPG.jdbc.idempotent.repository.enabled=true

forage.myMemory.infinispan.server-list=localhost:11223

forage.test.ollama.model.name=granite4:3b
forage.test.ollama.log.requests=true
```

**Command:**
```bash
camel forage config read --strategy application
```

**Output:**
```json
{
  "success": true,
  "directory": "/path/to/project",
  "beanCount": 3,
  "beans": [
    {
      "name": "myPG",
      "kind": "postgresql",
      "javaType": "javax.sql.DataSource",
      "sourceFile": "/path/to/application.properties",
      "configuration": {
        "db.kind": "postgresql",
        "url": "jdbc:postgresql://localhost:5432/",
        "username": "test",
        "password": "test",
        "transaction.enabled": "true",
        "aggregation.repository.enabled": "true",
        "idempotent.repository.enabled": "true"
      },
      "conditionalBeans": [
        {
          "name": "PROPAGATION_REQUIRED",
          "javaType": "org.apache.camel.spi.TransactedPolicy",
          "description": "Transaction policy for PROPAGATION_REQUIRED"
        },
        {
          "name": "myPG-aggregation",
          "javaType": "org.apache.camel.processor.aggregate.jdbc.JdbcAggregationRepository",
          "description": "JDBC aggregation repository"
        },
        {
          "name": "myPG-idempotent",
          "javaType": "org.apache.camel.processor.idempotent.jdbc.JdbcMessageIdRepository",
          "description": "JDBC idempotent repository"
        }
      ]
    },
    {
      "name": "myMemory",
      "kind": "infinispan",
      "javaType": "dev.langchain4j.memory.ChatMemory",
      "sourceFile": "/path/to/application.properties",
      "configuration": {
        "server-list": "localhost:11223"
      },
      "conditionalBeans": []
    },
    {
      "name": "test",
      "kind": "ollama",
      "javaType": "dev.langchain4j.model.chat.ChatLanguageModel",
      "sourceFile": "/path/to/application.properties",
      "configuration": {
        "model.name": "granite4:3b",
        "log.requests": "true"
      },
      "conditionalBeans": []
    }
  ]
}
```

### Example 2: Filter by Factory Type

**Command:**
```bash
camel forage config read --strategy application --filter jdbc
```

**Output:** Only beans with `factoryType=jdbc` (e.g., myPG)

### Example 3: No Properties Files Found

**Command:**
```bash
camel forage config read --dir /empty/directory
```

**Output:**
```json
{
  "success": true,
  "message": "No Forage properties files found",
  "directory": "/empty/directory",
  "beanCount": 0,
  "beans": []
}
```

## Property Key Parsing Logic

The command parses property keys following these patterns:

| Property Key | Parsed Result |
|--------------|---------------|
| `forage.jdbc.url` | factoryType=jdbc, instanceName=null, propertyName=url |
| `forage.ds1.jdbc.url` | factoryType=jdbc, instanceName=ds1, propertyName=url |
| `forage.jms.broker.url` | factoryType=jms, instanceName=null, propertyName=broker.url |
| `forage.cf1.jms.broker.url` | factoryType=jms, instanceName=cf1, propertyName=broker.url |
| `forage.ollama.model.name` | factoryType=multi, beanType=ollama, propertyName=model.name |
| `forage.test.ollama.model.name` | factoryType=multi, beanType=ollama, instanceName=test, propertyName=model.name |
| `forage.myMemory.infinispan.server-list` | factoryType=multi, beanType=infinispan, instanceName=myMemory, propertyName=server-list |

## Bean Name Determination

1. **If instance name exists**: Use instance name as bean name
2. **Otherwise**: Derive from factory type's Java class
   - `javax.sql.DataSource` → `dataSource`
   - `jakarta.jms.ConnectionFactory` → `connectionFactory`
   - `org.apache.camel.component.langchain4j.agent.api.Agent` → `agent`

## Conditional Beans Detection

Conditional beans are automatically detected based on enabled configuration entries:

| Config Entry | Conditional Beans Created |
|--------------|---------------------------|
| `transaction.enabled=true` | PROPAGATION_REQUIRED, PROPAGATION_REQUIRES_NEW, etc. (TransactedPolicy beans) |
| `aggregation.repository.enabled=true` | JdbcAggregationRepository |
| `idempotent.repository.enabled=true` | JdbcMessageIdRepository |

## Command Options

| Option | Short | Description | Default |
|--------|-------|-------------|---------|
| `--dir` | `-d` | Directory to scan for properties files (top level only, not subdirectories) | current directory |
| `--filter` | `-f` | Filter by factory type (e.g., 'jdbc', 'jms', 'agent') | none (show all) |
| `--strategy` | `-s` | Property file strategy: 'application' or 'forage' (case-insensitive) | application |

## Error Handling

The command returns exit code 1 and JSON error response for:
- Unknown `--strategy` value
- Invalid directory path
- File system errors
- Parsing errors

**Error Response Format:**
```json
{
  "success": false,
  "error": "Error message description"
}
```

## Integration with Kaoto

This command is designed to support the Kaoto VSCode extension by:
1. Providing a machine-readable list of configured beans
2. Including Java types for proper component binding
3. Exposing conditional beans that would be created at runtime
4. Supporting filtering for focused views
5. Maintaining source file references for navigation
