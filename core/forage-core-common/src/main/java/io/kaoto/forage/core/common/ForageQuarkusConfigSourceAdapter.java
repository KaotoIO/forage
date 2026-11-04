package io.kaoto.forage.core.common;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.core.util.config.Config;
import io.kaoto.forage.core.util.config.ConfigHelper;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.core.util.config.PlaceholderResolver;
import io.kaoto.forage.core.util.config.PropertyFileLocator;
import io.kaoto.forage.core.util.config.UnsupportedPlaceholderFunctionResolver;
import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import io.smallrye.config.ConfigValue;

/**
 * Generic SmallRye {@link ConfigSourceFactory} adapter that translates Forage properties
 * into Quarkus-native properties using a {@link ForageModuleDescriptor}.
 *
 * <p>Subclasses need only provide the descriptor — all prefix discovery, config creation,
 * and property translation logic is handled by this base class.
 *
 * <p>Prefix discovery consults both Forage's own configuration sources and the SmallRye
 * {@link ConfigSourceContext}, so keys contributed by profile-scoped properties
 * ({@code %prod.forage.*}), YAML sources, and other Quarkus config sources are visible.
 *
 * @param <C> the module's configuration type
 * @since 1.1
 */
public abstract class ForageQuarkusConfigSourceAdapter<C extends Config> implements ConfigSourceFactory {

    private static final Logger LOG = LoggerFactory.getLogger(ForageQuarkusConfigSourceAdapter.class);

    private static final Map<String, Set<String>> DISCOVERED_PREFIXES = new ConcurrentHashMap<>();

    /**
     * Returns the module descriptor that provides module-specific knowledge.
     */
    protected abstract ForageModuleDescriptor<C, ?> descriptor();

    /**
     * Translates the module's Forage properties. The translated values are consumed by Quarkus extensions,
     * not by Camel, so a Camel secret-store placeholder ({@code {{secret:...}}}, {@code {{aws:...}}}, ...)
     * in a translated value fails here with an explanation instead of reaching the extension as a literal.
     * {@code {{env:...}}}/{@code {{sys:...}}} placeholders keep working, and stay unresolved when the
     * variable is only set at runtime (this also runs at build time).
     */
    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context) {
        return PlaceholderResolver.withFunctionResolver(
                UnsupportedPlaceholderFunctionResolver.QUARKUS, () -> translate(context));
    }

    private Iterable<ConfigSource> translate(ConfigSourceContext context) {
        ForageModuleDescriptor<C, ?> desc = descriptor();
        ConfigStore.getInstance().setClassLoader(Thread.currentThread().getContextClassLoader());

        C defaultConfig = desc.createConfig(null);
        String namedRegexp = ConfigHelper.getNamedPropertyRegexp(desc.modulePrefix());
        Set<String> prefixes = new HashSet<>(ConfigStore.getInstance().readPrefixes(defaultConfig, namedRegexp));
        prefixes.addAll(discoverPrefixesFromContext(context, namedRegexp));

        DISCOVERED_PREFIXES.put(desc.modulePrefix(), Set.copyOf(prefixes));

        Map<String, String> configuration = new HashMap<>();

        String defaultRegexp = ConfigHelper.getDefaultPropertyRegexp(desc.modulePrefix());
        if (!prefixes.isEmpty()) {
            for (String name : prefixes) {
                C config = desc.createConfig(name);
                configuration.putAll(desc.translateProperties(name, config));
                registerContextProperties(context, config, desc.modulePrefix(), name);
            }
        } else if (!ConfigStore.getInstance()
                        .readPrefixes(defaultConfig, defaultRegexp)
                        .isEmpty()
                || !discoverPrefixesFromContext(context, defaultRegexp).isEmpty()) {
            configuration.putAll(desc.translateProperties(null, defaultConfig));
            registerContextProperties(context, defaultConfig, desc.modulePrefix(), null);
        } else {
            LOG.trace("No {} config found.", desc.modulePrefix());
        }

        if (configuration.isEmpty()) {
            return Collections.emptyList();
        }

        String sourceName = "Forage" + capitalize(desc.modulePrefix()) + "TranslatedConfigSource";
        return Collections.singletonList(new ForageTranslatedConfigSource(sourceName, configuration));
    }

    /**
     * Discovers module prefixes from the SmallRye {@link ConfigSourceContext}, which exposes
     * keys from all other config sources (application.properties, YAML, env, etc.).
     * Profile-scoped keys ({@code %prod.forage.*}) are matched with their profile marker stripped.
     */
    private static Set<String> discoverPrefixesFromContext(ConfigSourceContext context, String regexp) {
        Set<String> prefixes = new HashSet<>();
        Pattern pattern = PropertyFileLocator.pattern(regexp);
        Iterator<String> names = context.iterateNames();
        while (names.hasNext()) {
            String name = names.next();
            if (name.startsWith("%")) {
                int dot = name.indexOf('.');
                if (dot < 0) {
                    continue;
                }
                name = name.substring(dot + 1);
            }
            Matcher matcher = pattern.matcher(name);
            if (matcher.matches()) {
                prefixes.add(matcher.group(1));
            }
        }
        return prefixes;
    }

    /**
     * Registers property values from the SmallRye {@link ConfigSourceContext} into the
     * {@link ConfigStore} so that deployment processors can read them during augmentation.
     *
     * <p>Newer Quarkus versions no longer expose {@code application.properties} through
     * {@code ConfigProvider.getConfig()} at augmentation time, but the {@link ConfigSourceContext}
     * (available during config bootstrap) still sees all config sources. This method bridges
     * that gap by eagerly storing the values into the singleton {@link ConfigStore}.
     */
    private void registerContextProperties(
            ConfigSourceContext context, C config, String modulePrefix, String namedPrefix) {

        String foragePrefixDot;
        if (namedPrefix != null) {
            foragePrefixDot = "forage." + namedPrefix + "." + modulePrefix + ".";
        } else {
            foragePrefixDot = "forage." + modulePrefix + ".";
        }

        Iterator<String> names = context.iterateNames();
        while (names.hasNext()) {
            String rawName = names.next();
            String name = rawName;
            if (rawName.startsWith("%")) {
                int dot = rawName.indexOf('.');
                if (dot < 0) {
                    continue;
                }
                name = rawName.substring(dot + 1);
            }
            if (name.startsWith(foragePrefixDot)) {
                ConfigValue cv = context.getValue(rawName);
                if (cv != null && cv.getValue() != null) {
                    config.register(name, cv.getValue());
                }
            }
        }
    }

    /**
     * Returns prefixes discovered during config source assembly for the given module.
     * Deployment processors call this to find prefixes that were discovered from
     * the SmallRye {@link ConfigSourceContext} during the config bootstrap phase,
     * which runs before augmentation build steps.
     *
     * @param modulePrefix the module prefix (e.g., "cxf", "agent")
     * @return discovered prefixes, or empty set if none found
     */
    public static Set<String> getDiscoveredPrefixes(String modulePrefix) {
        return DISCOVERED_PREFIXES.getOrDefault(modulePrefix, Collections.emptySet());
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    /**
     * Internal ConfigSource with ordinal 240: below user configuration
     * (application.properties at 250/260, env at 300, system properties at 400)
     * so explicit user config always overrides Forage-translated values, and above
     * microprofile-config.properties at 100.
     */
    private static class ForageTranslatedConfigSource implements ConfigSource {

        private final String name;
        private final Map<String, String> configuration;

        ForageTranslatedConfigSource(String name, Map<String, String> configuration) {
            this.name = name;
            this.configuration = Collections.unmodifiableMap(configuration);
        }

        @Override
        public int getOrdinal() {
            return 240;
        }

        @Override
        public Set<String> getPropertyNames() {
            return configuration.keySet();
        }

        @Override
        public String getValue(String propertyName) {
            return configuration.get(propertyName);
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
