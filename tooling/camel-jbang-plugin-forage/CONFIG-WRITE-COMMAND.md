# ConfigWriteCommand Architecture

## Overview

`ConfigWriteCommand` is a Camel JBang plugin command that writes Forage configuration properties from JSON input to properties files. It's designed to support the Kaoto VSCode extension by providing a machine-writable interface for configuration management.

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         ConfigWriteCommand Flow                          │
└─────────────────────────────────────────────────────────────────────────┘

INPUT SOURCES
═════════════
┌──────────────┐     ┌──────────────┐
│ --input JSON │ OR  │ stdin (pipe) │
└──────┬───────┘     └──────┬───────┘
       │                    │
       └────────┬───────────┘
                │
                ▼
        ┌───────────────┐
        │  JSON Input   │
        │  {            │
        │   "forage.jdbc.url": "...",
        │   "forage.bean.name": "myPG",
        │   "kind": "postgresql"
        │  }            │
        └───────┬───────┘
                │
                ▼

COMMAND OPTIONS
═══════════════
┌─────────────────────────────────────────────────┐
│ --dir/-d      : Target directory (default: pwd) │
│ --strategy/-s : "forage" or "application"       │
│ --delete      : Delete mode flag                │
│ --name/-n     : Instance name (for delete)      │
└─────────────────────────────────────────────────┘
                │
                ▼

PROCESSING PIPELINE
═══════════════════

1. Parse JSON Input
   ┌──────────────────────────────────────┐
   │ parseJsonInput(String json)          │
   │ → Map<String, String> configMap      │
   └──────────────┬───────────────────────┘
                  │
                  ▼

2. Group by Factory Type
   ┌──────────────────────────────────────────────────────┐
   │ groupByFactory(Map<String, String> configMap)        │
   │                                                       │
   │ Extracts:                                            │
   │  • Bean name from "forage.bean.name" or "bean.name" │
   │  • Kind from "kind" or "forage.kind"                │
   │  • Factory type from property keys or kind          │
   │                                                       │
   │ Uses ForageCatalog to:                               │
   │  • Normalize property keys (strip forage. prefix)   │
   │  • Find factory type for each property              │
   │  • Build prefixed keys: forage.{name}.{property}    │
   │                                                       │
   │ Returns: Map<String, FactoryConfig>                  │
   │   FactoryConfig(beanName, properties)               │
   └──────────────┬───────────────────────────────────────┘
                  │
                  ▼

3. Determine Properties File
   ┌──────────────────────────────────────────┐
   │ getPropertiesFileName(factoryTypeKey)    │
   │                                           │
   │ Strategy "forage":                        │
   │   → catalog file (Config.name()), e.g.   │
   │     forage-datasource-factory.properties │
   │   → error if the factory has no file     │
   │     (Security Policy, WebSearch)         │
   │                                           │
   │ Strategy "application" (default):         │
   │   → application.properties               │
   └──────────────┬────────────────────────────┘
                  │
                  ▼

4. Write/Update Properties File
   ┌──────────────────────────────────────────────────────┐
   │ writePropertiesFile(file, properties, factoryType)   │
   │                                                       │
   │ If file doesn't exist:                               │
   │   • Create with header comment                       │
   │   • Write all properties                             │
   │                                                       │
   │ If file exists:                                      │
   │   • Read existing lines (preserve comments/order)    │
   │   • Update matching properties in-place              │
   │   • Append new properties at end                     │
   │   • Write back to file                               │
   └──────────────┬───────────────────────────────────────┘
                  │
                  ▼

OUTPUT
══════
┌─────────────────────────────────────────────────────────┐
│ JSON Response (stdout)                                   │
│ {                                                        │
│   "success": true,                                       │
│   "propertiesFile": "/path/to/file.properties",         │
│   "operation": "create" | "update",                      │
│   "message": "Successfully created/updated...",          │
│   "beanName": "myPG"                                     │
│ }                                                        │
└─────────────────────────────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────────┐
│ Properties File (filesystem)                             │
│                                                          │
│ # Forage PostgreSQL JDBC Configuration                  │
│ # Generated by Forage JBang Plugin                │
│                                                          │
│ forage.myPG.jdbc.db.kind=postgresql                     │
│ forage.myPG.jdbc.url=jdbc:postgresql://localhost:5432/ │
│ forage.myPG.jdbc.username=test                          │
│ forage.myPG.jdbc.password=test                          │
│ forage.myPG.jdbc.transaction.enabled=true               │
└─────────────────────────────────────────────────────────┘


DELETE MODE
═══════════
┌──────────────────────────────────────────────────────────┐
│ handleDelete()                                            │
│                                                           │
│ 1. Requires --name parameter                             │
│ 2. Scans the same files `config read` lists for the      │
│    strategy (top level of --dir only); the application   │
│    strategy also scans every application-*.properties,   │
│    so profile overrides of the instance are deleted too  │
│ 3. Reads every file before modifying any                 │
│ 4. Removes the keys that belong to the instance, parsed  │
│    like `config read` does (ForagePropertyParser):       │
│    forage.my.ds.* is instance "my.ds", not "my"; a key   │
│    under forage.<name>. whose rest is a catalog entry    │
│    belongs to <name> even if it is also a default entry  │
│    (forage.azure.openai.api.key for an OpenAI "azure")   │
│ 5. Finds entries like Properties.load does:              │
│    '=', ':' or whitespace separators, continuation lines │
│ 6. Cleans up dependencies (below)                        │
│ 7. Preserves comments and other properties               │
│                                                           │
│ Returns JSON with deletion and dependency cleanup results│
└──────────────────────────────────────────────────────────┘
                  │
                  ▼
┌──────────────────────────────────────────────────────────┐
│ Dependency cleanup (ForageDependencyResolver)             │
│                                                           │
│ 1. Dependencies of the deleted keys: factory variant     │
│    GAVs (base, springboot, quarkus) plus bean GAVs (the  │
│    bean the key belongs to, and the beans a value        │
│    selects: catalog bean-name entries such as db.kind,   │
│    agent.model.kind or agent.memory.kind, and any other  │
│    *.kind property)                                      │
│ 2. Dependencies still needed by the remaining keys of    │
│    every file, whatever the strategy or active profile:  │
│    application.properties, every application-*.properties│
│    and forage-*.properties; each file counts on its own, │
│    so the same key with another value elsewhere is kept  │
│ 3. Removes (1) minus (2) from camel.jbang.dependencies*  │
│    in application.properties                             │
└──────────────────────────────────────────────────────────┘
```

## Key Components

### ForageCatalog (Singleton)
- Metadata about all factory types
- Property key normalization
- Factory type detection from properties
- Properties file name mapping
- Bean GAV lookup by bean name
- Factory variant GAV lookup

### FactoryConfig (Record)
- `beanName`: String (optional prefix for properties)
- `kind`: String (bean type, e.g., "postgresql", "ollama")
- `properties`: Map<String, String> (key-value pairs)

### ForageDependencyResolver.Dependencies (Record)
- `base`: Set<String> (bean-specific GAVs, `camel.jbang.dependencies`)
- `main`: Set<String> (base factory GAVs, `camel.jbang.dependencies.main`)
- `springBoot`: Set<String> (Spring Boot factory GAVs, `camel.jbang.dependencies.spring-boot`)
- `quarkus`: Set<String> (Quarkus factory GAVs, `camel.jbang.dependencies.quarkus`)

Write and delete compute dependencies with the same `ForageDependencyResolver`, from keys parsed by
`ForagePropertyParser`.

### PropertiesDocument
- A properties file kept as its lines, so entries can be updated or removed in place
- Finds entries with the rules of `Properties.load`, so anything `config read` lists can be edited

## Usage Examples

### Example 1: PostgreSQL with All Features

**Input JSON:**
```json
{
  "forage.jdbc.db.kind": "postgresql",
  "forage.jdbc.url": "jdbc:postgresql://localhost:5432/",
  "forage.jdbc.username": "test",
  "forage.jdbc.password": "test",
  "forage.jdbc.transaction.enabled": "true",
  "forage.jdbc.aggregation.repository.enabled": "true",
  "forage.jdbc.idempotent.repository.enabled": "true",
  "kind": "postgresql",
  "forage.bean.name": "myPG"
}
```

**Command:**
```bash
camel forage config write --input '{"forage.jdbc.db.kind":"postgresql",...}' --strategy application
```

**Output (application.properties):**
```properties
forage.myPG.jdbc.db.kind=postgresql
forage.myPG.jdbc.url=jdbc:postgresql://localhost:5432/
forage.myPG.jdbc.username=test
forage.myPG.jdbc.password=test
forage.myPG.jdbc.transaction.enabled=true
forage.myPG.jdbc.aggregation.repository.enabled=true
forage.myPG.jdbc.idempotent.repository.enabled=true
```

### Example 2: Infinispan Chat Memory

**Input JSON:**
```json
{
  "forage.infinispan.server-list": "localhost:11223",
  "kind": "infinispan",
  "forage.bean.name": "myMemory"
}
```

**Output (application.properties):**
```properties
forage.myMemory.infinispan.server-list=localhost:11223
```

### Example 3: Multiple Configs to Same File

First call writes PostgreSQL config → creates file  
Second call writes Infinispan config → appends to same file

**Result:** Both configurations coexist in application.properties

### Example 4: Delete Operation (Single Bean Type)

**Scenario:** PostgreSQL and MariaDB are both configured. Delete PostgreSQL only.

**Before (application.properties):**
```properties
forage.myPG.jdbc.url=jdbc:postgresql://localhost:5432/
forage.myPG.jdbc.username=test
forage.myMariaDB.jdbc.url=jdbc:mariadb://localhost:3306/
forage.myMariaDB.jdbc.username=test
camel.jbang.dependencies=io.kaoto.forage:forage-jdbc-postgresql:1.0-SNAPSHOT,io.kaoto.forage:forage-jdbc-mariadb:1.0-SNAPSHOT
camel.jbang.dependencies.main=io.kaoto.forage:forage-jdbc:1.0-SNAPSHOT
```

**Command:**
```bash
camel forage config write --delete --name myPG
```

**After (application.properties):**
```properties
forage.myMariaDB.jdbc.url=jdbc:mariadb://localhost:3306/
forage.myMariaDB.jdbc.username=test
camel.jbang.dependencies=io.kaoto.forage:forage-jdbc-mariadb:1.0-SNAPSHOT
camel.jbang.dependencies.main=io.kaoto.forage:forage-jdbc:1.0-SNAPSHOT
```

**Effect:**
- Removes all `forage.myPG.*` properties
- Removes `forage-jdbc-postgresql` from dependencies (no longer needed)
- Keeps `forage-jdbc` factory dependency (still used by MariaDB)
- Preserves MariaDB configuration

### Example 5: Delete Operation (Last Bean of Factory Type)

**Scenario:** Only Ollama is configured. Delete it entirely.

**Before (application.properties):**
```properties
forage.myOllama.ollama.model.name=llama3
forage.myOllama.ollama.base.url=http://localhost:11434
camel.jbang.dependencies=io.kaoto.forage:forage-model-ollama:1.0-SNAPSHOT
camel.jbang.dependencies.main=io.kaoto.forage:forage-agent:1.0-SNAPSHOT
camel.jbang.dependencies.spring-boot=io.kaoto.forage:forage-agent-starter:1.0-SNAPSHOT
camel.jbang.dependencies.quarkus=io.kaoto.forage:forage-quarkus-agent-deployment:1.0-SNAPSHOT
```

**Command:**
```bash
camel forage config write --delete --name myOllama
```

**After (application.properties):**
```properties
# Empty - all forage properties and dependencies removed
```

**Effect:**
- Removes all `forage.myOllama.*` properties
- Removes `forage-model-ollama` bean dependency
- Removes all agent factory dependencies (no other agent beans configured)

## Command Options

| Option | Short | Description | Default |
|--------|-------|-------------|---------|
| `--input` | `-i` | JSON input string containing configuration values | stdin |
| `--dir` | `-d` | Directory where properties files will be written | current directory |
| `--strategy` | `-s` | Property file strategy: 'application' or 'forage' (case-insensitive) | application |
| `--delete` | | Delete configuration for a specific instance | false |
| `--name` | `-n` | Instance name to delete (used with --delete) | |

## Property Key Transformation

The command transforms input keys to prefixed output keys:

| Input Key | Bean Name | Output Key |
|-----------|-----------|------------|
| `forage.jdbc.url` | `myPG` | `forage.myPG.jdbc.url` |
| `jdbc.url` | `myPG` | `forage.myPG.jdbc.url` |
| `forage.jms.broker.url` | `cf1` | `forage.cf1.jms.broker.url` |
| `ollama.model.name` | `test` | `forage.test.ollama.model.name` |

## File Update Behavior

When updating existing properties files:
1. Preserves comments and blank lines
2. Updates matching properties in-place
3. Appends new properties at the end
4. Maintains original file order

## Dependency Management

The command automatically manages `camel.jbang.dependencies*` properties in `application.properties` based on the factory type and kind being configured.

### How It Works

When writing configuration, the command:
1. Looks up the bean GAV from the catalog (e.g., `forage-jdbc-postgresql` for kind `postgresql`)
2. Looks up factory variant GAVs for each runtime (base, springboot, quarkus)
3. Merges these dependencies with any existing dependencies in `application.properties`

### Dependency Properties

| Property | Description | Example |
|----------|-------------|---------|
| `camel.jbang.dependencies` | Bean-specific dependencies (database driver, broker client) | `io.kaoto.forage:forage-jdbc-postgresql:1.0-SNAPSHOT` |
| `camel.jbang.dependencies.main` | Base/main runtime factory dependencies | `io.kaoto.forage:forage-jdbc:1.0-SNAPSHOT` |
| `camel.jbang.dependencies.spring-boot` | Spring Boot runtime dependencies | `io.kaoto.forage:forage-jdbc-starter:1.0-SNAPSHOT` |
| `camel.jbang.dependencies.quarkus` | Quarkus runtime dependencies | `io.kaoto.forage:forage-quarkus-jdbc-deployment:1.0-SNAPSHOT` |

### Example

**Input JSON:**
```json
{
  "forage.jdbc.db.kind": "postgresql",
  "forage.jdbc.url": "jdbc:postgresql://localhost:5432/",
  "forage.jdbc.username": "test",
  "forage.jdbc.password": "test",
  "kind": "postgresql",
  "forage.bean.name": "myPG"
}
```

**Generated application.properties:**
```properties
# Forage configuration properties
forage.myPG.jdbc.db.kind=postgresql
forage.myPG.jdbc.url=jdbc:postgresql://localhost:5432/
forage.myPG.jdbc.username=test
forage.myPG.jdbc.password=test

# Automatically generated dependencies
camel.jbang.dependencies=io.kaoto.forage:forage-jdbc-postgresql:1.0-SNAPSHOT
camel.jbang.dependencies.main=io.kaoto.forage:forage-jdbc:1.0-SNAPSHOT
camel.jbang.dependencies.spring-boot=io.kaoto.forage:forage-jdbc-starter:1.0-SNAPSHOT
camel.jbang.dependencies.quarkus=io.kaoto.forage:forage-quarkus-jdbc-deployment:1.0-SNAPSHOT
```

### Preserving Existing Dependencies

When updating dependencies, the command:
- **Never removes** existing dependencies from any `camel.jbang.dependencies*` property
- **Only adds** new forage dependencies to the list
- **Preserves order** of existing dependencies

**Example with existing dependencies:**

Before:
```properties
camel.jbang.dependencies=com.example:my-custom-lib:1.0
```

After adding PostgreSQL JDBC configuration:
```properties
camel.jbang.dependencies=com.example:my-custom-lib:1.0,io.kaoto.forage:forage-jdbc-postgresql:1.0-SNAPSHOT
```

### Dependency Cleanup During Deletion

When deleting a configuration with `--delete --name <instanceName>`, the command intelligently cleans up unused dependencies:

#### Cleanup Logic

1. **Delete Properties**: Remove the properties of the instance from the strategy's files (for the application
   strategy, `application.properties` and every `application-<profile>.properties` override)
2. **Deleted Dependencies**: Compute the dependencies the deleted keys need: their factory variant GAVs, the bean
   each key configures (e.g. `forage.myOllama.ollama.model.name` → `forage-model-ollama`) and the beans their values
   select (e.g. `jdbc.db.kind=postgresql`, `agent.model.kind=ollama`, `agent.memory.kind=redis`)
3. **Remaining Dependencies**: Compute the dependencies every remaining key needs, in `application.properties`,
   every `application-<profile>.properties` (whatever the active profile, since the dependency lists are shared by
   all profiles) and the `forage-*.properties` files
4. **Remove**: Remove the deleted dependencies that no remaining key needs

#### What Gets Removed

| Condition | Dependencies Removed |
|-----------|---------------------|
| Bean type no longer configured (e.g., no more `postgresql` instances) | Bean GAV (`forage-jdbc-postgresql`) |
| Factory type no longer used (e.g., no more `jdbc.*` properties) | Factory variant GAVs (`forage-jdbc`, `forage-jdbc-starter`, `forage-quarkus-jdbc-deployment`) |

#### What Is Preserved

- Dependencies for other bean types still configured
- Factory dependencies when other beans of the same factory type exist
- Non-forage dependencies (custom user dependencies)
- Comments and file structure

#### Delete Output Format

```json
{
  "success": true,
  "operation": "delete",
  "instanceName": "myPG",
  "results": {
    "application.properties": {
      "success": true,
      "propertiesFile": "/path/to/application.properties",
      "deletedProperties": 5,
      "message": "Deleted configuration for instance 'myPG'"
    },
    "dependencyCleanup": {
      "removedDependencies": [
        "io.kaoto.forage:forage-jdbc-postgresql:1.0-SNAPSHOT"
      ],
      "count": 1
    }
  }
}
```

## Error Handling

The command returns exit code 1 and JSON error response for:
- Unknown `--strategy` value
- A factory without a dedicated properties file under `--strategy forage` (nothing is written)
- Empty or invalid JSON input
- Missing required configuration
- File system errors
- Invalid directory paths

**Error Response Format:**
```json
{
  "success": false,
  "error": "Error message description"
}
```
