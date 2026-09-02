package io.kaoto.forage.core.util.config;

import java.io.InputStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized configuration store for the Forage framework that manages configuration values
 * from multiple sources with a defined precedence hierarchy.
 *
 * <p>The ConfigStore implements a singleton pattern and serves as the central repository for all
 * configuration values in the application. It supports loading configuration from multiple sources
 * and provides a consistent API for accessing configuration values.
 *
 * <p><strong>Configuration Source Precedence (highest to lowest):</strong>
 * <ol>
 *   <li>Environment variables</li>
 *   <li>System properties</li>
 *   <li>Configuration files (loaded via URL or classpath)</li>
 * </ol>
 *
 * <p>The store automatically resolves configuration values by checking sources in the above order,
 * returning the first non-null value found. This allows for flexible configuration management where
 * environment-specific values can override defaults without code changes.
 *
 * <p><strong>Usage Example:</strong>
 * <pre>{@code
 * // Register configuration entries
 * ConfigModule apiKey = ConfigModule.of(MyConfig.class, "api-key");
 * ConfigEntry entry = ConfigEntry.fromEnv("MY_API_KEY");
 * ConfigStore.getInstance().add(apiKey, entry);
 *
 * // Retrieve configuration values
 * String value = ConfigStore.getInstance().get(apiKey)
 *         .orElseThrow(() -> new MissingConfigException("API key not configured"));
 * }</pre>
 *
 * <p><strong>Thread Safety:</strong>
 * This class is thread-safe for concurrent reads after initialization. However, configuration
 * registration (add methods) should typically be performed during application startup before
 * concurrent access begins.
 *
 * @see Config
 * @see ConfigModule
 * @see ConfigEntry
 * @since 1.0
 */
public final class ConfigStore {
    private static final Logger LOG = LoggerFactory.getLogger(ConfigStore.class);
    private static final String PROPERTIES_SUFFIX = ".properties";
    private static final String CLASSPATH_ROOT = "/";

    private static final ConfigStore INSTANCE = new ConfigStore();
    private final Properties properties = new Properties();
    private final Map<String, String> propertyNameIndex = new ConcurrentHashMap<>();
    private final List<ConfigResolver> resolvers = new CopyOnWriteArrayList<>();
    private volatile ClassLoader classLoader;

    /**
     * Private constructor to enforce singleton pattern.
     * Registers the {@link DefaultConfigResolver} as the baseline resolver.
     */
    private ConfigStore() {
        resolvers.add(new DefaultConfigResolver());
    }

    /**
     * Returns the singleton instance of the ConfigStore.
     *
     * <p>The instance is created eagerly at class initialization, so this method is
     * lock-free. The same instance is returned for all calls within the same JVM.
     *
     * @return the singleton ConfigStore instance
     */
    public static ConfigStore getInstance() {
        return INSTANCE;
    }

    /**
     * Registers a custom {@link ConfigResolver} into the resolver chain.
     *
     * <p>Resolvers are consulted in priority order (highest first) when resolving configuration values.
     * The {@link DefaultConfigResolver} is always present at priority 0.
     *
     * <p>If a resolver of the same class is already registered, it is replaced. This prevents
     * stale resolvers (e.g., wrapping a closed Spring context) from accumulating and shadowing
     * the freshly registered one when an application context is refreshed.
     *
     * @param resolver the resolver to register
     */
    public void registerResolver(ConfigResolver resolver) {
        resolvers.removeIf(existing -> existing.getClass().equals(resolver.getClass()));
        resolvers.add(resolver);
        resolvers.sort(Comparator.comparingInt(ConfigResolver::priority).reversed());
    }

    /**
     * Removes all registered resolvers of the given class from the resolver chain.
     *
     * @param resolverClass the resolver class to unregister
     * @return true if at least one resolver was removed
     */
    public boolean unregisterResolver(Class<? extends ConfigResolver> resolverClass) {
        return resolvers.removeIf(existing -> existing.getClass().equals(resolverClass));
    }

    /**
     * Returns an unmodifiable view of the registered resolvers.
     *
     * @return the list of resolvers ordered by priority (highest first)
     */
    public List<ConfigResolver> getResolvers() {
        return Collections.unmodifiableList(resolvers);
    }

    /**
     * Loads the configuration from the given module
     *
     * <p>This method attempts to resolve a value for the given ConfigEntry by checking
     * environment variables and system properties in order of precedence. If a value
     * is found, it is stored in the internal properties using the ConfigModule as the key.
     *
     * <p>If no value is found from any source, nothing is stored, and subsequent calls
     * to {@link #get(ConfigModule)} will return an empty Optional.
     *
     * @param module the configuration module that serves as the key
     */
    public void load(ConfigModule module) {
        final Optional<String> read = tryRead(module);

        read.ifPresent(s -> {
            String resolved = PlaceholderResolver.resolve(s);
            properties.put(module, resolved);
            propertyNameIndex.put(module.propertyName(), resolved);
        });
    }

    /**
     * Loads the configuration from the class' associated properties file.
     *
     * <p>This method looks for a properties file named after the configuration instance's
     * {@link Config#name()} method in the same package as the configuration class. If found,
     * the properties are loaded and added to the store.
     *
     * <p>For example, if the config name is "my-module", it will look for "my-module.properties"
     * in the classpath relative to the configuration class.
     *
     * @param clazz the configuration class
     * @param instance the configuration instance
     * @param <T> the type of the configuration class
     */
    public <T extends Config> void load(Class<T> clazz, T instance, BiConsumer<String, String> registerFunction) {
        final String fileName = asProperties(instance);
        LOG.debug("Adding {} to {}", clazz, fileName);

        loadProperties(registerFunction, loadPropertiesWithPriority(instance, fileName));
    }

    private static void loadProperties(BiConsumer<String, String> registerFunction, Properties props) {
        props.forEach((k, v) -> registerFunction.accept((String) k, (String) v));
    }

    /**
     * Utility method to read common prefixes from the {@link Config}, defined by the regexp.
     *
     * <p>Regexp has to contain one group, which is extracted.
     * For the regexp <pre>"(.+).jdbc\\..*"</pre> from the properties:
     * <pre>
     *     ds1.jdbc.url=jdbc:postgresql://localhost:5432/postgres
     *     ds2.jdbc.url=jdbc:mysql://localhost:3306/test
     * </pre>
     * both <strong>ds1, ds2</strong> prefixes are extracted.
     *
     * @return If there is no group extracted in the whole properties file, null is return. Else prefixes defined by
     * the regexp in a set.
     */
    public <T extends Config> Set<String> readPrefixes(T instance, String regexp) {
        final String fileName = asProperties(instance);
        Properties merged = loadPropertiesWithPriority(instance, fileName);

        // Also include properties from application.properties so that prefixes
        // defined there (e.g., forage.ollama.agent.*) are detected
        Properties appProps = ConfigHelper.getApplicationProperties();
        if (appProps != null) {
            merged.putAll(appProps);
        }

        // Also include properties already registered in ConfigStore by the Config
        // constructor, which may have loaded them from a properties file that cannot
        // be re-read in certain runtime contexts (e.g., JBang classloader)
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            if (entry.getKey() instanceof ConfigModule cm) {
                merged.putIfAbsent(cm.propertyName(), entry.getValue());
            }
        }

        Set<String> prefixes = PropertyFileLocator.readPrefixes(merged, regexp);

        // Consult registered resolvers for additional prefix discovery
        for (ConfigResolver resolver : resolvers) {
            prefixes.addAll(resolver.discoverPrefixes(regexp));
        }

        return prefixes;
    }

    /**
     * Method for loading properties from different sources in proper order, defaulting to 'default' properties.
     *
     * <ul>
     *     <li>File from a directory defined via properties `forage.config.dir` or `FORAGE_CONFIG_DIR`</li>
     *     <li>File in the working directory</li>
     *     <li>Properties read via specific classloader</li>
     *     <li>Properties loaded by a default classloader</li>
     * </ul>
     *
     * <p>Be aware, that <pre>Thread.currentThread().getContextClassLoader()</pre> has to be used as default classloader
     * (to work as expected in Quarkus runtime)</p>
     */
    private <T extends Config> Properties loadPropertiesWithPriority(T instance, String fileName) {
        // 1. Filesystem: working directory → config directory
        InputStream is = PropertyFileLocator.locateFromFilesystem(fileName);

        // 2. Custom classloader: package-relative path
        if (is == null && classLoader != null) {
            LOG.debug("Trying to use the classloader to read {}", fileName);
            is = PropertyFileLocator.locateFromClasspath(asClasspathPath(instance), classLoader);
        }

        // 3. Default classloader: root classpath path
        if (is == null) {
            LOG.debug("Loading defaults from the forage component");
            String rootPath = CLASSPATH_ROOT + instance.name() + PROPERTIES_SUFFIX;
            ClassLoader cl = classLoader != null ? classLoader : ConfigStore.class.getClassLoader();
            is = cl.getResourceAsStream(rootPath);
        }

        return PropertyFileLocator.readProperties(is);
    }

    private static <T extends Config> String asClasspathPath(T instance) {
        return instance.getClass().getPackageName().replace(".", "/") + "/" + instance.name() + PROPERTIES_SUFFIX;
    }

    private static <T extends Config> String asProperties(T instance) {
        return "./" + instance.name() + PROPERTIES_SUFFIX;
    }

    /**
     * Reads a configuration value following the documented precedence contract:
     * environment variables, then system properties, then the resolver chain.
     *
     * <p>Environment variables and system properties are checked here, before any resolver,
     * so the contract holds identically on every runtime regardless of which resolvers are
     * registered. Resolvers only supply runtime-specific configuration sources (Spring
     * Environment, Quarkus SmallRyeConfig, application.properties files) and are tried in
     * order of descending priority; the first non-empty value wins.
     *
     * @return an Optional containing the configuration value, or empty if not found
     */
    private Optional<String> tryRead(ConfigModule module) {
        // 1. Environment variables
        String envName = module.envName();
        String environmentValue = envName != null ? System.getenv(envName) : null;
        if (environmentValue != null) {
            return Optional.of(environmentValue);
        }

        // 2. System properties
        String propertyName = module.propertyName();
        String propertyValue = System.getProperty(propertyName);
        if (propertyValue != null) {
            return Optional.of(propertyValue);
        }

        // 3. Resolver chain (runtime-specific configuration sources)
        for (ConfigResolver resolver : resolvers) {
            Optional<String> value = resolver.resolve(propertyName);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    /**
     * Retrieves a configuration value for the specified ConfigModule.
     *
     * <p>This method returns the configuration value that was previously stored for the
     * given ConfigModule, either through direct registration via {@link #load(ConfigModule)}
     * or through properties loaded from files.
     *
     * <p>If no value was found during registration or if the ConfigModule was never registered,
     * an empty Optional is returned.
     *
     * @param entry the configuration module to look up
     * @return an Optional containing the configuration value, or empty if not found
     */
    public Optional<String> get(ConfigModule entry) {
        return Optional.ofNullable((String) properties.get(entry));
    }

    /**
     * Sets a configuration value directly for the specified ConfigModule.
     *
     * <p>This method allows direct assignment of configuration values, bypassing the normal
     * configuration source resolution process. It immediately stores the provided value in
     * the internal properties store, overriding any previously stored value for the same
     * ConfigModule.
     *
     * <p>This method is primarily used by:
     * <ul>
     *   <li>Dynamic configuration registration through {@link Config#register(String, String)}</li>
     *   <li>Configuration loading from property files during startup</li>
     *   <li>Runtime configuration updates in specific scenarios</li>
     *   <li>Testing scenarios where configuration values need to be controlled directly</li>
     * </ul>
     *
     * <p><strong>Usage Context:</strong>
     * Unlike the {@link #load(ConfigModule)} method which resolves values from
     * environment variables and system properties, this method directly sets the value without
     * any source resolution. This makes it suitable for scenarios where the value has already
     * been resolved or comes from a different source (like configuration files).
     *
     * <p><strong>Example Usage:</strong>
     * <pre>{@code
     * // Direct value assignment (typically from Config.register implementations)
     * ConfigModule apiKey = ConfigModule.of(MyConfig.class, "api.key");
     * ConfigStore.getInstance().set(apiKey, "resolved-api-key-value");
     *
     * // The value is immediately available for retrieval
     * String value = ConfigStore.getInstance().get(apiKey).orElse("default");
     * }</pre>
     *
     * <p><strong>Precedence Override:</strong>
     * Values set through this method will override any values that might have been previously
     * registered through environment variables or system properties for the same ConfigModule.
     * Subsequent calls to {@link #get(ConfigModule)} will return the value set by this method.
     *
     * <p><strong>Thread Safety:</strong>
     * This method is not thread-safe. If concurrent access is required, external synchronization
     * should be used. In typical usage, configuration values are set during application startup
     * before concurrent access begins.
     *
     * @param module the configuration module that serves as the key for storing the value
     * @param value the configuration value to store; may be {@code null} to remove the configuration
     * @see #load(ConfigModule)
     * @see #get(ConfigModule)
     * @see Config#register(String, String)
     * @since 1.0
     */
    public void set(ConfigModule module, String value) {
        if (value == null) {
            properties.remove(module);
            propertyNameIndex.remove(module.propertyName());
        } else {
            properties.put(module, value);
            propertyNameIndex.put(module.propertyName(), value);
        }
    }

    /**
     * Sets a configuration value directly by string key.
     *
     * <p>This method bypasses the ConfigModule lookup and stores the value directly
     * in the internal properties using the provided key. This is useful when mapping
     * configuration values between different namespaces.
     *
     * @param key the configuration key (e.g., "google.api.key")
     * @param value the configuration value to store
     * @since 1.0
     */
    public void setDirect(String key, String value) {
        if (value == null) {
            properties.remove(key);
            propertyNameIndex.remove(key);
        } else {
            properties.put(key, value);
            propertyNameIndex.put(key, value);
        }
    }

    /**
     * Gets a configuration value directly by string key.
     *
     * <p>This method bypasses the ConfigModule lookup and retrieves the value directly
     * from the internal properties using the provided key.
     *
     * @param key the configuration key (e.g., "google.api.key")
     * @return an Optional containing the value if present, or empty if not found
     * @since 1.0
     */
    public Optional<String> getDirect(String key) {
        return Optional.ofNullable((String) properties.get(key));
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * Gets all the configuration entries stored/set.
     *
     * <p>Returns a defensive snapshot: mutations of the store after this call are not
     * reflected in the returned set, and iterating it can never fail with
     * {@link java.util.ConcurrentModificationException}.
     *
     * @return A Set of all the entries
     */
    public Set<Map.Entry<Object, Object>> entries() {
        return Collections.unmodifiableSet(((Properties) properties.clone()).entrySet());
    }

    /**
     * Looks up a stored configuration value by its dot-notation property name
     * (e.g., {@code "forage.jdbc.url"}).
     *
     * <p>This is an indexed lookup covering both {@link ConfigModule}-keyed values
     * (indexed under {@link ConfigModule#propertyName()}) and values stored via
     * {@link #setDirect(String, String)}.
     *
     * @param propertyName the dot-notation property name
     * @return an Optional containing the value if present, or empty if not found
     * @since 1.2
     */
    public Optional<String> getByPropertyName(String propertyName) {
        return Optional.ofNullable(propertyNameIndex.get(propertyName));
    }

    /**
     * Returns a snapshot of all dot-notation property names with a stored value.
     *
     * @return the set of property names
     * @since 1.2
     */
    public Set<String> propertyNames() {
        return Set.copyOf(propertyNameIndex.keySet());
    }

    /**
     * Clears all cached configuration values so they will be re-read from their
     * sources (property files, environment variables, system properties) on next access.
     *
     * <p>This method is used during hot-reload to force a fresh read of configuration
     * values from disk. Resolvers are not cleared: hot-reload does not re-run the
     * runtime bootstrap that registers them, and {@link #registerResolver(ConfigResolver)}
     * replaces same-class resolvers so refreshed contexts cannot leave stale ones behind.
     *
     * @since 1.1
     */
    public void reload() {
        LOG.debug("ConfigStore.reload() - clearing {} cached properties", properties.size());
        properties.clear();
        propertyNameIndex.clear();
        ConfigHelper.clearCache();
        LOG.debug("ConfigStore.reload() - caches cleared");
    }
}
