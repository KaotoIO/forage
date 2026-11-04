package io.kaoto.forage.core.util.config;

import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves Camel-style property placeholders in configuration values.
 *
 * <p>Forage resolves {@code {{env:KEY}}} and {@code {{sys:KEY}}} by itself, on every runtime, when
 * properties are loaded ({@link #resolve(String)}):
 * <ul>
 *   <li>{@code {{env:KEY}}} — resolves to the environment variable {@code KEY}</li>
 *   <li>{@code {{env:KEY:default}}} — resolves to {@code KEY}, or {@code default} if not set</li>
 *   <li>{@code {{sys:KEY}}} — resolves to the system property {@code KEY}</li>
 *   <li>{@code {{sys:KEY:default}}} — resolves to {@code KEY}, or {@code default} if not set</li>
 * </ul>
 *
 * <p>Other placeholder functions ({@code {{secret:name/key}}}, {@code {{aws:name}}}, ...) are resolved
 * when a value is read ({@link #resolveFunctions(String, String)}) by the {@link PlaceholderFunctionResolver}
 * that the runtime adapter installed, typically backed by the Camel {@code PropertiesComponent}. Without a
 * resolver they are left unchanged. Plain property placeholders without a function ({@code {{name}}},
 * {@code {{name:default}}}) are never touched: values such as prompt templates legitimately contain them.
 *
 * <p>Resolution is single-pass and non-recursive. Nested placeholders like
 * {@code {{env:KEY:{{sys:FALLBACK}}}}} are not supported.
 *
 * <p>Resolved values are never logged.
 *
 * @since 1.4
 */
public final class PlaceholderResolver {

    private static final Logger LOG = LoggerFactory.getLogger(PlaceholderResolver.class);
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{(env|sys):([^}:]+)(?::([^}]*))?}}");
    private static final Pattern FUNCTION_PATTERN = Pattern.compile("\\{\\{([A-Za-z][\\w.-]*):([^{}]*)}}");
    private static final String PLACEHOLDER_TOKEN = "{{";
    private static final String ENV = "env";
    private static final String SYS = "sys";

    /**
     * Camel placeholder functions backed by a secret store, with the artifact that provides each one.
     * A placeholder using one of them fails when the function is not available, instead of being read
     * by Camel as a property key with a default value.
     */
    private static final Map<String, String> SECRET_FUNCTIONS = Map.ofEntries(
            Map.entry("aws", "camel-aws-secrets-manager"),
            Map.entry("aws-parameterstore", "camel-aws-parameter-store"),
            Map.entry("azure", "camel-azure-key-vault"),
            Map.entry("cyberark", "camel-cyberark-vault"),
            Map.entry("gcp", "camel-google-secret-manager"),
            Map.entry("hashicorp", "camel-hashicorp-vault"),
            Map.entry("ibm", "camel-ibm-secrets-manager"),
            Map.entry("configmap", "camel-kubernetes"),
            Map.entry("configmap-binary", "camel-kubernetes"),
            Map.entry("secret", "camel-kubernetes"),
            Map.entry("secret-binary", "camel-kubernetes"),
            Map.entry("spring-config", "camel-spring-cloud-config"));

    private static final AtomicReference<PlaceholderFunctionResolver> INSTALLED = new AtomicReference<>();
    private static final ThreadLocal<PlaceholderFunctionResolver> SCOPED = new ThreadLocal<>();

    private PlaceholderResolver() {}

    /**
     * Returns {@code true} if the value contains at least one {@code {{env:...}}} or
     * {@code {{sys:...}}} placeholder.
     */
    public static boolean containsPlaceholders(String value) {
        if (value == null || !value.contains(PLACEHOLDER_TOKEN)) {
            return false;
        }
        return PLACEHOLDER_PATTERN.matcher(value).find();
    }

    /**
     * Resolves all {@code {{env:...}}} and {@code {{sys:...}}} placeholders in the given value.
     *
     * @param value the property value to resolve (may be {@code null})
     * @return the resolved value, or the original value if no placeholders are found
     */
    public static String resolve(String value) {
        if (value == null || !value.contains(PLACEHOLDER_TOKEN)) {
            return value;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(value);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String function = matcher.group(1);
            String key = matcher.group(2);
            String defaultValue = matcher.group(3);

            String resolved = lookup(function, key);

            if (resolved != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(resolved));
            } else if (defaultValue != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(defaultValue));
            } else {
                LOG.debug("Placeholder {{{}:{}}} could not be resolved and has no default value", function, key);
            }
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * Resolves all {@code {{env:...}}} and {@code {{sys:...}}} placeholders in the given
     * {@link Properties} object, modifying values in place.
     *
     * @param props the properties to process (may be {@code null})
     */
    public static void resolveAll(Properties props) {
        if (props == null || props.isEmpty()) {
            return;
        }

        for (String key : props.stringPropertyNames()) {
            String value = props.getProperty(key);
            if (value != null && value.contains(PLACEHOLDER_TOKEN)) {
                String resolved = resolve(value);
                if (!value.equals(resolved)) {
                    props.setProperty(key, resolved);
                }
            }
        }
    }

    /**
     * Resolves the placeholders of a value that is about to be used, in a single pass:
     * {@code {{env:...}}} and {@code {{sys:...}}} by Forage, every other {@code {{function:...}}}
     * by the {@linkplain #currentFunctionResolver() current function resolver}.
     *
     * <p>As with {@link #resolve(String)}, an {@code env}/{@code sys} placeholder whose variable is not set
     * and that has no default is left unchanged. Without a function resolver, other functions are left
     * unchanged too; with one, the resolver decides. Placeholders without a function are left unchanged.
     *
     * @param propertyName the property holding the value, used in error messages instead of the value
     * @param value the value (may be {@code null})
     * @return the resolved value
     * @throws UnresolvedPlaceholderException if the function resolver cannot resolve a placeholder
     */
    public static String resolveFunctions(String propertyName, String value) {
        if (value == null || !value.contains(PLACEHOLDER_TOKEN)) {
            return value;
        }
        PlaceholderFunctionResolver resolver = currentFunctionResolver();

        Matcher matcher = FUNCTION_PATTERN.matcher(value);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String function = matcher.group(1);
            String remainder = matcher.group(2);
            String resolved;
            if (ENV.equals(function) || SYS.equals(function)) {
                resolved = resolveEnvironment(function, remainder);
            } else if (resolver != null) {
                resolved = resolver.resolve(propertyName, function, remainder).orElse(null);
            } else {
                resolved = null;
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(resolved != null ? resolved : matcher.group()));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String resolveEnvironment(String function, String remainder) {
        int colon = remainder.indexOf(':');
        String key = colon < 0 ? remainder : remainder.substring(0, colon);
        String resolved = lookup(function, key);
        if (resolved == null && colon >= 0) {
            resolved = remainder.substring(colon + 1);
        }
        return resolved;
    }

    /**
     * Installs the function resolver used by {@link #resolveFunctions(String, String)}, replacing the
     * previous one. Runtime adapters call this where Forage creates beans.
     */
    public static void install(PlaceholderFunctionResolver resolver) {
        INSTALLED.set(resolver);
    }

    /**
     * Removes the given function resolver, if it is still the installed one.
     *
     * @return {@code true} if it was removed
     */
    public static boolean uninstall(PlaceholderFunctionResolver resolver) {
        return INSTALLED.compareAndSet(resolver, null);
    }

    /**
     * Returns the function resolver installed with {@link #install(PlaceholderFunctionResolver)}, ignoring
     * any resolver set for the current thread by {@link #withFunctionResolver(PlaceholderFunctionResolver, Supplier)}.
     */
    public static PlaceholderFunctionResolver installedFunctionResolver() {
        return INSTALLED.get();
    }

    /**
     * Returns the function resolver for the current thread: the one set by
     * {@link #withFunctionResolver(PlaceholderFunctionResolver, Supplier)}, else the installed one, else
     * {@code null}.
     */
    public static PlaceholderFunctionResolver currentFunctionResolver() {
        PlaceholderFunctionResolver scoped = SCOPED.get();
        return scoped != null ? scoped : INSTALLED.get();
    }

    /**
     * Runs {@code action} with {@code resolver} as the function resolver of the current thread.
     */
    public static <T> T withFunctionResolver(PlaceholderFunctionResolver resolver, Supplier<T> action) {
        PlaceholderFunctionResolver previous = SCOPED.get();
        SCOPED.set(resolver);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                SCOPED.remove();
            } else {
                SCOPED.set(previous);
            }
        }
    }

    /**
     * Returns the artifact providing the given secret-store placeholder function (e.g.
     * {@code camel-kubernetes} for {@code secret}), or empty if it is not a known secret-store function.
     */
    public static Optional<String> secretFunctionArtifact(String function) {
        return Optional.ofNullable(SECRET_FUNCTIONS.get(function));
    }

    private static String lookup(String function, String key) {
        if (ENV.equals(function)) {
            return IOHelper.lookupEnvironmentVariable(key);
        }
        return System.getProperty(key);
    }
}
