# Forage Config Editor

Reads and edits Forage configuration properties files. It has no CLI dependency: the
`camel forage config read/write` commands of the Camel JBang plugin are thin wrappers around it, and
Kaoto Kompanion can use it directly.

## Main classes

| Class | Purpose |
|-------|---------|
| `ForageConfigurationEditor` | Reads the configuration of a directory, writes and deletes instances, validates new instance names, removes the Forage dependencies older versions stored in `camel.jbang.dependencies*` |
| `ForageConfiguration` | Every `forage.*` entry of a set of files, the instance it belongs to, and the instances they add up to |
| `ForagePropertyParser` | Which factory, instance and bean a key belongs to, read against the catalog config entries |
| `PropertiesDocument` | A properties file kept as its lines: entries found like `Properties.load` does, edited in place as text edits |
| `PropertiesFileStrategy` | Which files a configuration is read from, written to and deleted from (`application` or `forage`) |
| `ForageConfigurationFiles` | The properties files of a directory that can hold Forage configuration |

## Usage

```java
ForageCatalogReader catalog = ForageCatalogReader.getInstance(); // or the catalog of the project's Forage version
ForageConfigurationEditor editor =
        new ForageConfigurationEditor(catalog, projectDir, PropertiesFileStrategyType.APPLICATION.create(catalog));

// List the instances
for (ForageConfiguration.Instance instance : editor.read().instances()) {
    System.out.println(instance.factoryType() + " " + instance.name() + " " + instance.beans());
}

// Create or update an instance; keys are those of the default instance
EditResult result = editor.write("jdbc", "ordersDb", Map.of("jdbc.db.kind", "postgresql", "jdbc.url", "jdbc:..."));

// Nothing is written yet: write the files...
result.write();
// ...or apply the text edits of each file change in an editor
for (FileChange change : result.changes()) {
    change.file();    // the file
    change.created(); // a new file
    change.edits();   // line range + new lines, for the current content of the file
}

// Delete an instance, and its name from forage.multi.agent.names
editor.delete("ordersDb").write();
```

Edit operations throw `ConfigurationEditException`, with the message to show, when they cannot be made: a new
instance with an ambiguous name, a factory without a properties file under the `forage` strategy, an instance to
delete that does not exist.

## How keys are resolved

A key reads as `forage.<instance>.<catalog config entry>`, where the instance name is optional and may contain dots.
Some keys read in several ways: `forage.azure.openai.api.key` is both the default Azure OpenAI `api.key` and the
OpenAI `api.key` of an instance named `azure`. `ForageConfiguration` resolves the keys of all the files together, in
two passes:

1. Keys with a single reading, keys the segment heuristics recognise (typos such as `forage.ds1.jdbc.urll`) and the
   names listed in `forage.multi.agent.names` make the known instance names.
2. A key with several readings goes to the known instance it can belong to, or else to the default instance. A key no
   catalog entry matches belongs to the known instance it starts with.

`ForageConfigurationEditor.validateNewInstanceName` rejects the names that make keys ambiguous, so instances created
through the editor never need the second pass.
