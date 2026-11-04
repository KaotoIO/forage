package io.kaoto.forage.core.util.config;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.camel.CamelContext;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.vault.VaultConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link PlaceholderFunctionResolver} that resolves placeholder functions through the
 * {@link PropertiesComponent} of a {@link CamelContext}, so a Forage property accepts the same
 * {@code {{secret:name/key}}}, {@code {{aws:name}}}, {@code {{hashicorp:...}}}, ... placeholders as a route.
 *
 * <p>Functions are applied directly, not through {@link PropertiesComponent#parseUri(String)}: Camel would
 * read an unknown function as a property key with a default value, and logs resolved values at DEBUG.
 *
 * <p>Forage may create beans while the CamelContext is being built (plain Camel creates them from a
 * {@code ContextServicePlugin}), before Camel Main initializes the PropertiesComponent and applies the
 * {@code camel.vault.*} properties. The first function placeholder therefore initializes the
 * PropertiesComponent and binds the {@code camel.vault.*} properties to the context
 * {@link VaultConfiguration}, as Camel Main does later with the same values, so functions resolved by
 * Forage are configured as they are for routes.
 *
 * <p>When the PropertiesComponent ignores missing properties ({@code camel.component.properties.ignore-missing-property},
 * which Camel JBang sets when exporting), unresolvable placeholders are left unchanged instead of failing.
 *
 * @since 1.6
 */
public class CamelPlaceholderFunctionResolver implements PlaceholderFunctionResolver {

    private static final Logger LOG = LoggerFactory.getLogger(CamelPlaceholderFunctionResolver.class);
    private static final String VAULT_PREFIX = "camel.vault.";
    private static final String IGNORE_MISSING_PROPERTY = "camel.component.properties.ignore-missing-property";
    private static final String IGNORE_MISSING_PROPERTY_CAMEL_CASE = "camel.component.properties.ignoreMissingProperty";

    private final CamelContext camelContext;
    private final Set<String> notFunctions = ConcurrentHashMap.newKeySet();
    private volatile PropertiesComponent prepared;
    private volatile Boolean lenient;

    public CamelPlaceholderFunctionResolver(CamelContext camelContext) {
        this.camelContext = Objects.requireNonNull(camelContext, "camelContext");
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public Optional<String> resolve(String propertyName, String function, String remainder) {
        if (notFunctions.contains(function)) {
            return Optional.empty();
        }
        PropertiesComponent pc;
        PropertiesFunction propertiesFunction;
        try {
            pc = prepare();
            propertiesFunction = pc.getPropertiesFunction(function);
        } catch (RuntimeException e) {
            return unresolved(propertyName, function, "the placeholder function could not be started", e);
        }
        if (propertiesFunction == null) {
            Optional<String> artifact = PlaceholderResolver.secretFunctionArtifact(function);
            if (artifact.isEmpty()) {
                // Not a function: a plain placeholder with a default value, e.g. {{name:default}}
                notFunctions.add(function);
                return Optional.empty();
            }
            return unresolved(
                    propertyName,
                    function,
                    "the placeholder function is not available, add the " + artifact.get() + " dependency",
                    null);
        }

        String input = remainder;
        String value = null;
        try {
            if (propertiesFunction.lookupFirst(remainder)) {
                input = pc.resolveProperty(remainder).orElse(null);
            }
            if (input != null) {
                value = propertiesFunction.apply(input);
            }
        } catch (RuntimeException e) {
            return unresolved(propertyName, function, "the placeholder function failed", e);
        }
        if (input == null) {
            return unresolved(propertyName, function, "the property it refers to is not defined", null);
        }
        if (value != null) {
            return Optional.of(value);
        }
        if (propertiesFunction.optional(remainder)) {
            return Optional.empty();
        }
        return unresolved(propertyName, function, "the placeholder function returned no value", null);
    }

    private Optional<String> unresolved(String propertyName, String function, String reason, Throwable cause) {
        if (isLenient()) {
            LOG.debug(
                    "Leaving the {{{}:...}} placeholder of property '{}' unresolved, as the PropertiesComponent"
                            + " ignores missing properties: {}",
                    function,
                    propertyName,
                    reason);
            return Optional.empty();
        }
        // The cause is chained but its message is not copied: Camel binding errors include property values
        throw new UnresolvedPlaceholderException(
                propertyName, function, cause != null ? reason + " (see the cause)" : reason, cause);
    }

    private PropertiesComponent prepare() {
        // Camel Spring Boot replaces the PropertiesComponent after the CamelContext build: prepare the current one
        PropertiesComponent pc = camelContext.getPropertiesComponent();
        if (prepared != pc) {
            synchronized (this) {
                if (prepared != pc) {
                    // Idempotent: a no-op once the CamelContext has initialized the PropertiesComponent.
                    // Before that (beans created during the CamelContext build), it gives the placeholder
                    // functions their CamelContext and loads the properties files they read their options from.
                    ServiceHelper.initService(pc);
                    if (camelContext.getTypeConverter() == null) {
                        // Property binding (vault options, Kubernetes client options) needs the type converter,
                        // which the CamelContext build creates right after its plugins: create it now
                        camelContext.getTypeConverterRegistry();
                    }
                    applyVaultConfiguration(pc);
                    lenient = null;
                    prepared = pc;
                }
            }
        }
        return pc;
    }

    /**
     * Binds the {@code camel.vault.<vault>.*} properties to the matching {@link VaultConfiguration} of the
     * CamelContext, the same way Camel Main does (BaseMainSupport#setVaultProperties), which happens after
     * the CamelContext is built. Binding the same values twice is harmless. Never logs values.
     */
    private void applyVaultConfiguration(PropertiesComponent pc) {
        Properties properties;
        try {
            properties =
                    pc.loadProperties(key -> key.startsWith(VAULT_PREFIX), key -> key.substring(VAULT_PREFIX.length()));
        } catch (RuntimeException e) {
            LOG.debug(
                    "Cannot read the camel.vault.* properties: {}", e.getClass().getName());
            return;
        }
        if (properties.isEmpty()) {
            return;
        }

        Map<String, Map<String, Object>> options = new HashMap<>();
        for (String name : properties.stringPropertyNames()) {
            String vault = StringHelper.before(name, ".");
            String option = StringHelper.after(name, ".");
            if (vault != null && option != null && !option.isEmpty()) {
                options.computeIfAbsent(vault.toLowerCase(Locale.ROOT), k -> new HashMap<>())
                        .put(StringHelper.dashToCamelCase(option), properties.getProperty(name));
            }
        }

        VaultConfiguration vaultConfiguration = camelContext.getVaultConfiguration();
        options.forEach((vault, values) -> {
            Object target = vaultTarget(vaultConfiguration, vault);
            if (target == null) {
                return;
            }
            // One option at a time, so that an option that cannot be bound yet does not stop the others
            values.forEach((option, value) -> {
                try {
                    PropertyBindingSupport.build()
                            .withCamelContext(camelContext)
                            .withTarget(target)
                            .withProperty(option, value)
                            .withIgnoreCase(true)
                            .bind();
                } catch (RuntimeException e) {
                    // The exception message contains the value: log the option name only
                    LOG.debug(
                            "Cannot apply camel.vault.{}.{} yet: {}",
                            vault,
                            option,
                            e.getClass().getName());
                }
            });
        });
    }

    private static Object vaultTarget(VaultConfiguration configuration, String vault) {
        return switch (vault) {
            case "aws" -> configuration.aws();
            case "azure" -> configuration.azure();
            case "cyberark" -> configuration.cyberark();
            case "gcp" -> configuration.gcp();
            case "hashicorp" -> configuration.hashicorp();
            case "ibm" -> configuration.ibmSecretsManager();
            case "kubernetes" -> configuration.kubernetes();
            case "kubernetescm" -> configuration.kubernetesConfigmaps();
            case "springconfig" -> configuration.springConfig();
            default -> null;
        };
    }

    private boolean isLenient() {
        Boolean answer = lenient;
        if (answer == null) {
            answer = computeLenient();
            lenient = answer;
        }
        return answer;
    }

    private boolean computeLenient() {
        PropertiesComponent pc = camelContext.getPropertiesComponent();
        if (pc instanceof org.apache.camel.component.properties.PropertiesComponent impl
                && impl.isIgnoreMissingProperty()) {
            return true;
        }
        // Camel Main applies the camel.component.properties.* options after the CamelContext is built:
        // read the option itself (Camel JBang sets it as an initial property when exporting)
        String value = System.getProperty(IGNORE_MISSING_PROPERTY);
        if (value == null) {
            try {
                Properties properties = pc.loadProperties(
                        key -> IGNORE_MISSING_PROPERTY.equals(key) || IGNORE_MISSING_PROPERTY_CAMEL_CASE.equals(key));
                value = properties.getProperty(
                        IGNORE_MISSING_PROPERTY, properties.getProperty(IGNORE_MISSING_PROPERTY_CAMEL_CASE));
            } catch (RuntimeException e) {
                LOG.debug(
                        "Cannot read {}: {}",
                        IGNORE_MISSING_PROPERTY,
                        e.getClass().getName());
            }
        }
        return "true".equalsIgnoreCase(value);
    }
}
