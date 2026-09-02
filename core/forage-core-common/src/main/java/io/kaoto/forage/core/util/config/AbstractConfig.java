package io.kaoto.forage.core.util.config;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractConfig implements Config {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractConfig.class);
    private static final String FORAGE_PREFIX = "forage.";

    private final String prefix;
    private final Class<? extends ConfigEntries> entriesClass;

    protected AbstractConfig(String prefix, Class<? extends ConfigEntries> entriesClass) {
        this.prefix = prefix;
        this.entriesClass = entriesClass;
        ConfigEntries.registerPrefix(entriesClass, prefix);
        loadFromProperties();
        ConfigEntries.loadOverridesFor(entriesClass, prefix);
    }

    @SuppressWarnings("unchecked")
    private <T extends Config> void loadFromProperties() {
        ConfigStore.getInstance().load((Class<T>) this.getClass(), (T) this, this::register);
    }

    protected String prefix() {
        return prefix;
    }

    @Override
    public void register(String name, String value) {
        Map<ConfigModule, ConfigEntry> modules = ConfigEntries.getModules(entriesClass);
        Optional<ConfigModule> module = ConfigEntries.find(modules, prefix, name);
        if (module.isPresent()) {
            ConfigStore.getInstance().set(module.get(), PlaceholderResolver.resolve(value));
        } else {
            warnUnknownKey(modules, name);
        }
    }

    /**
     * Logs a warning (key name only, never the value) when a {@code forage.*} key from a
     * properties file matches no known configuration module of this config, suggesting the
     * nearest known key. Keys that match a base module once their name segment is stripped
     * (i.e., named variants belonging to another prefixed instance) are skipped, as are
     * keys outside the {@code forage.} namespace.
     */
    private void warnUnknownKey(Map<ConfigModule, ConfigEntry> modules, String name) {
        // Subclasses with dynamic modules (e.g., route policies) keep their own registry,
        // so an empty map here means we cannot tell known keys from typos
        if (modules.isEmpty() || !name.startsWith(FORAGE_PREFIX)) {
            return;
        }

        // A key like forage.ds1.jdbc.url is a named variant of forage.jdbc.url and may
        // belong to a prefixed instance that has not been constructed yet — not a typo.
        int dot = name.indexOf('.', FORAGE_PREFIX.length());
        if (dot > 0) {
            String stripped = FORAGE_PREFIX + name.substring(dot + 1);
            if (modules.keySet().stream().anyMatch(m -> m.match(stripped))) {
                return;
            }
        }

        String suggestion = nearestKnownKey(modules, name);
        if (suggestion != null) {
            LOG.warn("Unknown configuration key '{}' in {} — did you mean '{}'?", name, name(), suggestion);
        } else {
            LOG.warn("Unknown configuration key '{}' in {}", name, name());
        }
    }

    private static String nearestKnownKey(Map<ConfigModule, ConfigEntry> modules, String name) {
        return modules.keySet().stream()
                .map(ConfigModule::propertyName)
                .min(Comparator.comparingInt(candidate -> levenshtein(candidate, name)))
                .filter(candidate -> levenshtein(candidate, name) <= Math.max(3, name.length() / 4))
                .orElse(null);
    }

    static int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    protected Optional<String> get(ConfigModule module) {
        return ConfigStore.getInstance().get(module.asNamed(prefix));
    }

    protected String getRequired(ConfigModule module, String errorMessage) {
        return get(module).orElseThrow(() -> new MissingConfigException(errorMessage));
    }
}
