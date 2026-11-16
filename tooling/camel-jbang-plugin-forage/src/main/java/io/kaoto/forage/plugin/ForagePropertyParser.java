package io.kaoto.forage.plugin;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;

/**
 * Resolves which factory, instance and bean a {@code forage.*} property belongs to.
 *
 * <p>Keys are matched against the catalog's config entries the way the runtime resolves them:
 * {@code forage.<instance>.<config entry>}, where the instance name is optional and may contain dots. This covers
 * default instances ({@code forage.jdbc.url}), named instances ({@code forage.ds1.jdbc.url}), dotted names
 * ({@code forage.my.ds.jdbc.url}) and bean prefixes that differ from the bean name ({@code forage.google.api.key}).
 * Keys that match no config entry (e.g. typos or dynamic route policy keys) fall back to segment heuristics.
 *
 * <p>This class has no CLI dependencies, so it can be reused outside the {@code config} commands.
 */
public final class ForagePropertyParser {

    /** Properties whose value names the bean kind (e.g. {@code db.kind=postgresql}, {@code kind=artemis}). */
    public static final Set<String> KIND_PROPERTIES = Set.of("db.kind", "kind");

    private static final String FORAGE_PREFIX = "forage.";
    private static final Pattern FORAGE_PROPERTY_PATTERN = Pattern.compile("^forage\\.(.+)$");

    private final ForageCatalogReader catalog;

    public ForagePropertyParser(ForageCatalogReader catalog) {
        this.catalog = catalog;
    }

    /**
     * A parsed {@code forage.*} property.
     *
     * @param factoryType the factory type key (e.g. {@code jdbc}, {@code agent})
     * @param instanceName the instance name, or {@code null} for the default instance
     * @param beanType the bean the property configures (e.g. {@code ollama}), or {@code null}
     * @param propertyName the property name relative to the factory or bean (e.g. {@code url}, {@code model.name})
     * @param entryKey the key without {@code forage.} and the instance name (e.g. {@code ollama.model.name})
     */
    public record ParsedProperty(
            String factoryType, String instanceName, String beanType, String propertyName, String entryKey) {

        /**
         * @return {@code true} if this property's value names the bean kind
         */
        public boolean isKindProperty() {
            return KIND_PROPERTIES.contains(propertyName);
        }
    }

    /**
     * Parses a full property key.
     *
     * @param key the property key (e.g. {@code forage.ds1.jdbc.url})
     * @return the parsed property, or empty if the key is not a recognised Forage property
     */
    public Optional<ParsedProperty> parse(String key) {
        Matcher matcher = FORAGE_PROPERTY_PATTERN.matcher(key);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String remainder = matcher.group(1);
        ParsedProperty parsed = parseUsingConfigEntries(remainder);
        return Optional.ofNullable(parsed != null ? parsed : parseUsingSegments(remainder));
    }

    /**
     * Tries the shortest instance name first, so a key that is itself a config entry belongs to the default
     * instance.
     */
    private ParsedProperty parseUsingConfigEntries(String key) {
        String instanceName = null;
        String entryKey = key;
        while (true) {
            Optional<ForageCatalogReader.ConfigEntryOwner> owner =
                    catalog.findConfigEntryOwner(FORAGE_PREFIX + entryKey);
            if (owner.isPresent()) {
                return toParsedProperty(instanceName, entryKey, owner.get());
            }
            int dot = entryKey.indexOf('.');
            if (dot < 0) {
                return null;
            }
            String segment = entryKey.substring(0, dot);
            instanceName = instanceName == null ? segment : instanceName + "." + segment;
            entryKey = entryKey.substring(dot + 1);
        }
    }

    private ParsedProperty toParsedProperty(
            String instanceName, String entryKey, ForageCatalogReader.ConfigEntryOwner owner) {
        String factoryTypeKey = owner.factoryTypeKey();
        String factoryPrefix = factoryTypeKey + ".";
        // Factory-level property (e.g., "jdbc.url", "jms.artemis.reconnect.attempts"), unless the
        // factory key is really a bean prefix (e.g., the security policy factory keyed as "keycloak")
        if (entryKey.startsWith(factoryPrefix)
                && catalog.findBeanNameForPropertyPrefix(factoryTypeKey).isEmpty()) {
            return new ParsedProperty(
                    factoryTypeKey, instanceName, null, entryKey.substring(factoryPrefix.length()), entryKey);
        }
        // Bean property (e.g., "ollama.model.name", "azure.openai.api.key")
        if (owner.beanName() != null) {
            Optional<String> beanPrefix = catalog.findBeanPropertyPrefix(owner.beanName(), entryKey);
            if (beanPrefix.isPresent()) {
                return new ParsedProperty(
                        factoryTypeKey,
                        instanceName,
                        owner.beanName(),
                        entryKey.substring(beanPrefix.get().length() + 1),
                        entryKey);
            }
        }
        // Factory property outside the factory namespace (e.g., "multi.agent.names", "guardrail.*")
        return new ParsedProperty(factoryTypeKey, instanceName, null, entryKey, entryKey);
    }

    /**
     * Segment heuristics for keys that match no catalog config entry.
     */
    private ParsedProperty parseUsingSegments(String key) {
        String[] parts = key.split("\\.", 2);
        if (parts.length < 2) {
            return null;
        }
        String firstPart = parts[0];
        String restOfKey = parts[1];

        // Default instance of a factory (e.g., "jdbc.url")
        if (catalog.getFactoryMetadata(firstPart).isPresent()) {
            return new ParsedProperty(firstPart, null, null, restOfKey, key);
        }
        // Default instance of a bean (e.g., "ollama.model.name")
        Optional<String> beanFactory = catalog.findFactoryTypeKeyForBeanName(firstPart);
        if (beanFactory.isPresent()) {
            return new ParsedProperty(beanFactory.get(), null, firstPart, restOfKey, key);
        }

        // Named instance: check the second segment for a factory or bean
        String[] restParts = restOfKey.split("\\.", 2);
        String secondPart = restParts[0];
        String propertyName = restParts.length > 1 ? restParts[1] : "";
        if (catalog.getFactoryMetadata(secondPart).isPresent()) {
            return new ParsedProperty(secondPart, firstPart, null, propertyName, restOfKey);
        }
        Optional<String> beanFactory2 = catalog.findFactoryTypeKeyForBeanName(secondPart);
        if (beanFactory2.isPresent()) {
            return new ParsedProperty(beanFactory2.get(), firstPart, secondPart, propertyName, restOfKey);
        }

        // Bean-specific prefix mapped to a factory (e.g., "google.api.key")
        return catalog.findFactoryTypeKeyForPropertyPrefix(firstPart)
                .map(factory -> new ParsedProperty(factory, null, null, key, key))
                .orElse(null);
    }
}
