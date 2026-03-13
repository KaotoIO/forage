# Forage Property Validation

The Forage property validator helps catch configuration errors before runtime by validating all Forage properties against the catalog metadata.

## Features

### 1. **Typo Detection**
Catches typos in property names and suggests corrections:
```properties
# ❌ Typo in property name
forage.jdbc.usernam=admin

# ✅ Validation warning:
[UNKNOWN_PROPERTY] in application.properties
  Property: forage.jdbc.usernam
  Unknown property 'usernam' for factory 'jdbc'. Did you mean 'username'?
```

### 2. **Invalid Bean Values**
Validates bean-name type properties and suggests alternatives:
```properties
# ❌ Invalid database kind
forage.jdbc.db.kind=postgresqll

# ✅ Validation warning:
[INVALID_BEAN_VALUE] in application.properties
  Property: forage.jdbc.db.kind
  Unknown database 'postgresqll'. Did you mean 'postgresql'?
  Valid options: postgresql, mysql, mariadb, db2, h2, oracle
```

### 3. **Unknown Properties**
Warns about properties that don't exist in the catalog:
```properties
# ❌ Unknown property
forage.jdbc.invalid.property=value

# ✅ Validation warning:
[UNKNOWN_PROPERTY] in application.properties
  Property: forage.jdbc.invalid.property
  Unknown property 'invalid.property' for factory 'jdbc'
```

### 4. **Unknown Factory Types**
Detects when a factory type doesn't exist:
```properties
# ❌ Unknown factory
forage.invalidfactory.property=value

# ✅ Validation warning:
[UNKNOWN_FACTORY] in application.properties
  Property: forage.invalidfactory.property
  Unknown factory type 'invalidfactory' in property 'forage.invalidfactory.property'
```

## Usage

### Command Line

Validation is available for both `run` and `export` commands.

#### Default Mode (Warn Only)
By default, validation runs automatically and prints warnings but doesn't fail:

```bash
camel forage run *
# or
camel forage export *

⚠️  Forage Property Validation Warnings:
══════════════════════════════════════════════════════════════════════
  [UNKNOWN_PROPERTY] in application.properties
    Property: forage.jdbc.usernam
    Unknown property 'usernam' for factory 'jdbc'. Did you mean 'username'?

══════════════════════════════════════════════════════════════════════
Total warnings: 1

Starting Camel JBang...
```

#### Strict Mode
Fail on validation warnings:

```bash
camel forage run * --strict
# or
camel forage export * --strict

⚠️  Forage Property Validation Warnings:
══════════════════════════════════════════════════════════════════════
  [UNKNOWN_PROPERTY] in application.properties
    Property: forage.jdbc.usernam
    Unknown property 'usernam' for factory 'jdbc'. Did you mean 'username'?

══════════════════════════════════════════════════════════════════════
Total warnings: 1

⛔ Validation failed in strict mode. Fix warnings and try again.
```

#### Skip Validation
Disable validation entirely:

```bash
camel forage run * --skip-validation
# or
camel forage export * --skip-validation
```

### Programmatic Usage

```java
import io.kaoto.forage.plugin.ForagePropertyValidator;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;

File workingDir = new File(".");
ForageCatalogReader catalog = ForageCatalogReader.getInstance();

ValidationResult result = ForagePropertyValidator.validate(workingDir, catalog);

if (result.hasWarnings()) {
    result.printWarnings(System.err);

    // Or process warnings individually
    for (ValidationWarning warning : result.getWarnings()) {
        System.err.println(warning.format());
    }
}
```

## How It Works

### 1. Property Scanning
The validator scans all properties files in the working directory:
- `application.properties`
- `forage-*.properties`
- Factory-specific properties files (e.g., `forage-jdbc.properties`)

### 2. Catalog Lookup
For each Forage property found, the validator:
1. Parses the property key to extract the factory type and property name
2. Looks up valid properties in the catalog metadata
3. Validates the property name and value

### 3. Suggestion Generation
When an invalid property is found, the validator uses **Levenshtein distance** to find the closest match:
- Maximum edit distance: 3 characters
- Suggests properties that differ by insertions, deletions, or substitutions

### 4. Bean Value Validation
For `bean-name` type properties (like `db.kind`, `model.kind`), the validator:
1. Extracts the property value
2. Checks if the bean exists in the catalog
3. Lists all valid alternatives if the bean is unknown

## Configuration

### Validation Behavior

The validator can be configured via command-line options:

| Option | Description | Default |
|--------|-------------|---------|
| `--strict` | Fail build on validation warnings | `false` |
| `--skip-validation` | Skip validation entirely | `false` |

### Environment Variables

No environment variables are currently supported.

## Examples

### Example 1: JDBC Configuration

```properties
# application.properties

# ✅ Valid
forage.jdbc.db.kind=postgresql
forage.jdbc.username=admin
forage.jdbc.password=secret

# ❌ Typo in property
forage.jdbc.usernam=admin

# ❌ Invalid database
forage.jdbc.db.kind=postgresqll
```

**Validation Output:**
```
⚠️  Forage Property Validation Warnings:
══════════════════════════════════════════════════════════════════════

  [UNKNOWN_PROPERTY] in application.properties
    Property: forage.jdbc.usernam
    Unknown property 'usernam' for factory 'jdbc'. Did you mean 'username'?

  [INVALID_BEAN_VALUE] in application.properties
    Property: forage.jdbc.db.kind
    Unknown database 'postgresqll'. Did you mean 'postgresql'?
    Valid options: postgresql, mysql, mariadb, db2, h2, oracle

══════════════════════════════════════════════════════════════════════
Total warnings: 2
```

### Example 2: Agent Configuration

```properties
# application.properties

# ✅ Valid
forage.agent.model.kind=openai
forage.agent.model.name=gpt-4
forage.agent.temperature=0.7

# ❌ Typo in model kind
forage.agent.model.kind=opena

# ❌ Unknown property
forage.agent.invalid.property=value
```

**Validation Output:**
```
⚠️  Forage Property Validation Warnings:
══════════════════════════════════════════════════════════════════════

  [INVALID_BEAN_VALUE] in application.properties
    Property: forage.agent.model.kind
    Unknown chat model 'opena'. Did you mean 'openai'?
    Valid options: openai, ollama, anthropic, google-gemini, azure-openai, ...

  [UNKNOWN_PROPERTY] in application.properties
    Property: forage.agent.invalid.property
    Unknown property 'invalid.property' for factory 'agent'

══════════════════════════════════════════════════════════════════════
Total warnings: 2
```

### Example 3: Named Instances

```properties
# application.properties

# ✅ Valid named instances
forage.ds1.jdbc.db.kind=postgresql
forage.ds1.jdbc.username=postgres_user

forage.ds2.jdbc.db.kind=mysql
forage.ds2.jdbc.username=mysql_user

# ❌ Typo in named instance property
forage.ds1.jdbc.usernam=admin
```