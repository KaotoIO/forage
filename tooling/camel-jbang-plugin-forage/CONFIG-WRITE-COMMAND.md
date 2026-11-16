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
│ JSON Response (stdout)                                  │
│ {                                                       │
│   "success": true,                                      │
│   "factories": {                                        │
│     "jdbc": {                                           │
│       "propertiesFile": "/path/to/file.properties",     │
│       "operation": "create" | "update",                 │
│       "message": "Successfully created/updated...",     │
│       "beanName": "myPG"                                │
│     }                                                   │
│   },                                                    │
│   "dependencyCleanup": {...}   (only if any was removed)│
│ }                                                       │
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
│ handleDelete()                                           │
│                                                          │
│ 1. Requires --name parameter                             │
│ 2. Scans the same files `config read` lists for the      │
│    strategy (top level of --dir only); the application   │
│    strategy also scans every application-*.properties,   │
│    so profile overrides of the instance are deleted too  │
│ 3. Reads every file before modifying any                 │
│ 4. Removes the keys that belong to the instance, parsed  │
│    like `config read` does (ForagePropertyParser):       │
│    forage.my.ds.* is instance "my.ds", not "my"; a key   │
│    under forage.<name>. whose rest reads as a default    │
│    key belongs to <name> (forage.azure.openai.api.key    │
│    for an OpenAI instance named "azure")                 │
│ 5. Finds entries like Properties.load does               │
│    (PropertiesDocument): '=', ':' or whitespace          │
│    separators, continuation lines                        │
│ 6. Removes the Forage dependencies older versions wrote  │
│    (see Dependencies)                                    │
│ 7. Preserves comments and other properties               │
│                                                          │
│ Returns JSON with the deleted properties per file        │
└──────────────────────────────────────────────────────────┘
```

## Library

`config write` is a thin wrapper around `ForageConfigurationEditor` from the `forage-config-editor` library
(`tooling/forage-config-editor`), which has no CLI dependency so that Kaoto Kompanion can reuse it. The command only
turns the Kaoto JSON input into the factory, instance name and properties of each instance, and formats the result.

- **New instance names** must start with a letter or a digit, contain only letters, digits, `-` and `_`, and must not
  be the start of Forage property keys (a factory key such as `jdbc`, a bean prefix such as `ollama` or `azure`):
  the keys of such an instance would also read as keys of the default instance. Existing instances keep working
  whatever their name.
- **Delete** also removes the instance from the lists of instance names that refer to it
  (`forage.multi.agent.names`), and removes such a list once empty (`updatedProperties` in the output).
- **`--strategy forage`** writes a bean property to the bean's own file when the catalog records one (e.g.
  `forage-model-bedrock.properties`), and to the factory's file otherwise. A bean property outside the factory's
  namespace (e.g. `forage.myOllama.ollama.model.name` for the agent factory) in the factory's file is only read on
  Spring Boot, so the output then has a `warnings` entry. A factory written to several files lists them under
  `propertiesFiles`.

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

### PropertiesDocument
- A properties file kept as its lines, so entries can be updated or removed in place
- Finds entries with the rules of `Properties.load`, so anything `config read` lists can be edited
- Escapes the keys and values it writes, so they read back unchanged

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
```

**Command:**
```bash
camel forage config write --delete --name myPG
```

**After (application.properties):**
```properties
forage.myMariaDB.jdbc.url=jdbc:mariadb://localhost:3306/
forage.myMariaDB.jdbc.username=test
```

**Effect:**
- Removes all `forage.myPG.*` properties
- Preserves MariaDB configuration

### Example 5: Delete Operation (Last Bean of Factory Type)

**Scenario:** Only Ollama is configured. Delete it entirely.

**Before (application.properties):**
```properties
forage.myOllama.ollama.model.name=llama3
forage.myOllama.ollama.base.url=http://localhost:11434
camel.jbang.dependencies=com.example:my-lib:1.0,io.kaoto.forage:forage-model-ollama:1.6.2
camel.jbang.dependencies.main=io.kaoto.forage:forage-agent:1.6.2
```

**Command:**
```bash
camel forage config write --delete --name myOllama
```

**After (application.properties):**
```properties
camel.jbang.dependencies=com.example:my-lib:1.0
```

**Effect:**
- Removes all `forage.myOllama.*` properties
- Removes the Forage dependencies an older version of the command wrote, keeps `com.example:my-lib:1.0`

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

## Dependencies

The command does not write any `camel.jbang.dependencies*` property. Forage projects run with the Forage plugin
installed, and the plugin resolves the dependencies from the configuration whenever `camel run` or
`camel export` runs (`CatalogDrivenExportCustomizer`), for the runtime in use.

Older versions of the command wrote the Forage dependencies to `application.properties`, with the Forage version
of the time. Such a stored copy is redundant and can pin an outdated version next to the one the plugin resolves,
so every `config write` and `config write --delete` removes them:

- Removes, from every `camel.jbang.dependencies*` property of `application.properties`, the GAVs of the factories
  and beans the catalog knows, whatever their version
- Keeps every other dependency, and removes a property only once it is empty
- Reports what it removed under `dependencyCleanup`

**Before:**
```properties
camel.jbang.dependencies=com.example:my-custom-lib:1.0,io.kaoto.forage:forage-jdbc-postgresql:1.6.2
camel.jbang.dependencies.main=io.kaoto.forage:forage-jdbc:1.6.2
```

**After:**
```properties
camel.jbang.dependencies=com.example:my-custom-lib:1.0
```

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
        "io.kaoto.forage:forage-jdbc-postgresql:1.6.2"
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
