# Property Validation Demo

This example demonstrates how the Forage property validator catches common configuration errors.

## Setup

Create an example properties in a test directory:

```properties
# application.properties

# Valid properties
forage.jdbc.db.kind=postgresql
forage.jdbc.jdbc.url=jdbc:postgresql://localhost:5432/mydb

# ❌ Typo: 'usernam' instead of 'username'
forage.jdbc.usernam=admin

# ❌ Typo: 'passwod' instead of 'password'
forage.jdbc.passwod=secret
```

Run validation:

```bash
camel forage run *
# or for export
camel forage export *
```

**Output:**
```text
⚠️  Forage Property Validation Warnings:
══════════════════════════════════════════════════════════════════════

  [UNKNOWN_PROPERTY] in application.properties
    Property: forage.jdbc.usernam
    Unknown property 'usernam' for factory 'jdbc'. Did you mean 'username'?

  [UNKNOWN_PROPERTY] in application.properties
    Property: forage.jdbc.passwod
    Unknown property 'passwod' for factory 'jdbc'. Did you mean 'password'?

══════════════════════════════════════════════════════════════════════
Total warnings: 2

Starting Camel JBang...
```

**Fix:** Change `usernam` → `username` and `passwod` → `password`

## Example 2: Invalid Bean Value

Create `application.properties` with invalid bean values:

```properties
# application.properties

# ❌ Invalid database kind (typo in 'postgresql')
forage.jdbc.db.kind=postgresqll

# ❌ Invalid JMS kind
forage.jms.kind=activemqs
```

Run validation:

```bash
camel forage run *
# or for export
camel forage export *
```

**Output:**
```text
⚠️  Forage Property Validation Warnings:
══════════════════════════════════════════════════════════════════════

  [INVALID_BEAN_VALUE] in application.properties
    Property: forage.jdbc.db.kind
    Unknown database 'postgresqll'. Did you mean 'postgresql'?
    Valid options: postgresql, mysql, mariadb, db2, h2, oracle

  [INVALID_BEAN_VALUE] in application.properties
    Property: forage.jms.kind
    Unknown jms connection 'activemqs'. Did you mean 'artemis'?
    Valid options: artemis, ibmmq

══════════════════════════════════════════════════════════════════════
Total warnings: 2

Starting Camel JBang...
```

**Fix:** Change `postgresqll` → `postgresql` and `activemqs` → `artemis`

## Example 3: AI Agent Configuration

Create `application.properties` with AI agent typos:

```properties
# application.properties

# ❌ Typo in model kind
forage.agent.model.kind=opena

# ❌ Unknown property
forage.agent.model.nam=gpt-4

# ✅ Valid property
forage.agent.temperature=0.7
```

Run validation:

```bash
camel forage run *
# or for export
camel forage export *
```

**Output:**
```text
⚠️  Forage Property Validation Warnings:
══════════════════════════════════════════════════════════════════════

  [INVALID_BEAN_VALUE] in application.properties
    Property: forage.agent.model.kind
    Unknown chat model 'opena'. Did you mean 'openai'?
    Valid options: openai, ollama, anthropic, google-gemini, azure-openai,
                   mistral-ai, hugging-face, watsonx-ai, bedrock, dashscope, local-ai

  [UNKNOWN_PROPERTY] in application.properties
    Property: forage.agent.model.nam
    Unknown property 'model.nam' for factory 'agent'. Did you mean 'model.name'?

══════════════════════════════════════════════════════════════════════
Total warnings: 2

Starting Camel JBang...
```

**Fix:** Change `opena` → `openai` and `model.nam` → `model.name`

## Example 4: Strict Mode

Run with strict mode to fail on warnings:

```bash
camel forage run * --strict
# or for export
camel forage export * --strict
```

**Output:**
```text
⚠️  Forage Property Validation Warnings:
══════════════════════════════════════════════════════════════════════

  [UNKNOWN_PROPERTY] in application.properties
    Property: forage.jdbc.usernam
    Unknown property 'usernam' for factory 'jdbc'. Did you mean 'username'?

══════════════════════════════════════════════════════════════════════
Total warnings: 1

⛔ Validation failed in strict mode. Fix warnings and try again.
```

Exit code: `1` (failed)

## Example 5: Named Instances

Create `application.properties` with multiple named instances:

```properties
# application.properties

# First PostgreSQL instance
forage.ds1.jdbc.db.kind=postgresql
forage.ds1.jdbc.username=pg_user

# Second MySQL instance
forage.ds2.jdbc.db.kind=mysql
forage.ds2.jdbc.username=mysql_user

# ❌ Typo in third instance
forage.ds3.jdbc.db.kind=mariadbb
```

Run validation:

```bash
camel forage run *
# or for export
camel forage export *
```

**Output:**
```text
⚠️  Forage Property Validation Warnings:
══════════════════════════════════════════════════════════════════════

  [INVALID_BEAN_VALUE] in application.properties
    Property: forage.ds3.jdbc.db.kind
    Unknown database 'mariadbb'. Did you mean 'mariadb'?
    Valid options: postgresql, mysql, mariadb, db2, h2, oracle

══════════════════════════════════════════════════════════════════════
Total warnings: 1

Starting Camel JBang...
```

**Fix:** Change `mariadbb` → `mariadb`

## Example 6: Skip Validation

Skip validation entirely:

```bash
camel forage run * --skip-validation
# or for export
camel forage export * --skip-validation
```

**Output:**
```text
Starting Camel JBang...
(no validation warnings)
```

## Best Practices

### 1. Run Validation During Development

Always validate during development to catch errors early:

```bash
# Development workflow
vi application.properties
camel forage run * --strict  # Catches errors immediately

# Before exporting
camel forage export * --strict  # Validate before export
```

### 2. Use in CI/CD Pipelines

Add validation to your CI/CD pipeline:

```yaml
# .github/workflows/validate.yml
name: Validate Forage Config

on: [push, pull_request]

jobs:
  validate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Validate Forage properties
        run: |
          camel forage run --strict *
```

### 3. Fix Warnings Immediately

Don't ignore validation warnings - fix them immediately to prevent runtime errors.

### 4. Review Suggestions Carefully

The validator provides suggestions based on edit distance. Review them carefully:

```text
Unknown property 'password'. Did you mean 'passwd'?
```

In this case, the suggestion is wrong - you want `password`, not the suggested `passwd`.
