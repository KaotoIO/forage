package io.kaoto.forage.maven.catalog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.maven.plugin.logging.Log;
import io.kaoto.forage.catalog.model.BeanReference;
import io.kaoto.forage.catalog.model.ConfigEntry;
import io.kaoto.forage.catalog.model.FactoryModel;
import io.kaoto.forage.catalog.model.FeatureBeans;
import io.kaoto.forage.catalog.model.ForageBean;
import io.kaoto.forage.catalog.model.ForageFactory;
import io.kaoto.forage.core.annotations.FactoryType;

/**
 * Adds the UI metadata that lets a JSON Schema form renderer such as Kaoto's {@code KaotoForm}
 * work on the Forage catalog without Forage specific code (issue #233):
 *
 * <ul>
 *   <li>{@code model}: display metadata shaped like a camel-catalog {@code model} block</li>
 *   <li>{@code prefixPattern}: how the property keys of a named instance are built</li>
 *   <li>{@code beanReferences}: where a Camel route references the factory beans, derived from the
 *       Camel catalog or declared with {@code @ForageFactory(beanReferences = ...)}</li>
 *   <li>{@code propertiesSchema} on factories and beans: see {@link PropertiesSchemaBuilder}</li>
 * </ul>
 *
 * <p>It only adds fields: the existing catalog structure is left untouched.
 */
class FactoryUiMetadata {

    /**
     * Property keys of a named instance: the instance name is inserted after {@code forage.}, the
     * same rule as {@code ConfigModule#asNamed}. {@code {suffix}} is a {@code propertiesSchema} key
     * without its leading {@code forage.}.
     */
    static final String PREFIX_PATTERN = "forage.{name}.{suffix}";

    private static final String VALUE_TEMPLATE_REFERENCE = "#" + BeanReference.BEAN_REF_PLACEHOLDER;

    private final Log log;
    private final CamelCatalogLookup camelCatalog;

    FactoryUiMetadata(Log log) {
        this(log, new CamelCatalogLookup());
    }

    FactoryUiMetadata(Log log, CamelCatalogLookup camelCatalog) {
        this.log = log;
        this.camelCatalog = camelCatalog;
    }

    /**
     * Builds a declared bean reference, applying the {@code @BeanReference} defaults.
     *
     * @return the reference, or null when the name or the option is missing
     */
    static BeanReference declaredReference(
            String catalogKind, String name, String option, String optionKind, String valueTemplate) {
        if (isBlank(name) || isBlank(option)) {
            return null;
        }
        String kind = isBlank(catalogKind) ? CamelCatalogLookup.CATALOG_KIND_COMPONENT : catalogKind;
        String resolvedOptionKind = isBlank(optionKind) ? CamelCatalogLookup.OPTION_KIND_PARAMETER : optionKind;
        String template = isBlank(valueTemplate) ? defaultValueTemplate(resolvedOptionKind) : valueTemplate;
        return new BeanReference(kind, name, option, resolvedOptionKind, template);
    }

    /**
     * Endpoint parameters reference a bean as {@code #name}; path options and EIP attributes take
     * the plain bean name.
     */
    static String defaultValueTemplate(String optionKind) {
        return CamelCatalogLookup.OPTION_KIND_PARAMETER.equals(optionKind)
                ? VALUE_TEMPLATE_REFERENCE
                : BeanReference.BEAN_REF_PLACEHOLDER;
    }

    /**
     * Adds the UI metadata to the catalog factories.
     *
     * @param components the scanned components, which carry the {@code @ForageFactory} data of every variant
     * @param factories the catalog factories, with their config entries and beans already associated
     */
    void enrich(List<ForageComponent> components, Collection<ForageFactory> factories) {
        Map<String, List<ScannedFactory>> variantsByType = scannedFactoriesByType(components);
        for (ForageFactory factory : factories) {
            List<ScannedFactory> variants = variantsByType.getOrDefault(factory.getFactoryType(), List.of());

            factory.setModel(model(factory));
            if (variants.stream().allMatch(ScannedFactory::isNamedInstances)) {
                factory.setPrefixPattern(PREFIX_PATTERN);
            }
            List<BeanReference> references = beanReferences(factory, variants);
            if (!references.isEmpty()) {
                factory.setBeanReferences(references);
            }
            addPropertiesSchemas(factory);
        }
    }

    private static Map<String, List<ScannedFactory>> scannedFactoriesByType(List<ForageComponent> components) {
        Map<String, List<ScannedFactory>> result = new HashMap<>();
        for (ForageComponent component : components) {
            if (component.getFactories() == null) {
                continue;
            }
            for (ScannedFactory scanned : component.getFactories()) {
                result.computeIfAbsent(scanned.getFactoryType(), k -> new ArrayList<>())
                        .add(scanned);
            }
        }
        return result;
    }

    private FactoryModel model(ForageFactory factory) {
        FactoryModel model = new FactoryModel();
        model.setName(modelName(factory));
        model.setTitle(factory.getName());
        model.setDescription(factory.getDescription());

        Set<String> labels = new LinkedHashSet<>();
        for (String component : components(factory)) {
            camelCatalog
                    .componentScheme(component)
                    .ifPresent(scheme -> labels.addAll(camelCatalog.componentLabels(scheme)));
        }
        if (!labels.isEmpty()) {
            model.setLabel(String.join(",", labels));
        }
        return model;
    }

    /**
     * A stable identifier: the {@link FactoryType} constant in kebab case (for example
     * {@code data-source}), which does not depend on the display name nor on which variant was
     * scanned first.
     */
    static String modelName(ForageFactory factory) {
        return FactoryType.fromDisplayName(factory.getFactoryType())
                .map(type -> type.name().toLowerCase(Locale.ROOT).replace('_', '-'))
                .orElseGet(() -> slug(factory.getName()));
    }

    private static String slug(String value) {
        if (value == null) {
            return null;
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    /**
     * Declared references win over the ones derived from the Camel catalog for the same component.
     * References follow the order of the factory components; references to EIPs come last.
     */
    private List<BeanReference> beanReferences(ForageFactory factory, List<ScannedFactory> variants) {
        Map<String, List<BeanReference>> declaredByEntry = new LinkedHashMap<>();
        for (ScannedFactory variant : variants) {
            for (BeanReference reference : variant.getBeanReferences()) {
                if (!camelCatalog.hasOption(reference)) {
                    log.warn("Factory '" + factory.getName() + "' declares bean reference " + reference
                            + " but the Camel catalog has no such option");
                }
                List<BeanReference> declared =
                        declaredByEntry.computeIfAbsent(entryKey(reference), k -> new ArrayList<>());
                if (!declared.contains(reference)) {
                    declared.add(reference);
                }
            }
        }

        List<BeanReference> result = new ArrayList<>();
        Set<String> added = new HashSet<>();
        for (String component : components(factory)) {
            Optional<String> scheme = camelCatalog.componentScheme(component);
            if (scheme.isEmpty()) {
                continue;
            }
            String key = CamelCatalogLookup.CATALOG_KIND_COMPONENT + ":" + scheme.get();
            if (!added.add(key)) {
                continue;
            }
            List<BeanReference> declared = declaredByEntry.get(key);
            result.addAll(
                    declared != null
                            ? declared
                            : camelCatalog.endpointParametersOfType(scheme.get(), factory.getFactoryType()));
        }
        declaredByEntry.forEach((key, declared) -> {
            if (added.add(key)) {
                result.addAll(declared);
            }
        });
        return result;
    }

    private static String entryKey(BeanReference reference) {
        return reference.getCatalogKind() + ":" + reference.getName();
    }

    /**
     * The factory schema holds the factory's own entries; entries a bean contributes are in that
     * bean's schema only, so that a UI renders each property once: the factory form plus the form
     * of each selected bean.
     */
    private void addPropertiesSchemas(ForageFactory factory) {
        Set<String> beanEntryNames = new HashSet<>();
        for (ForageBean bean : beans(factory)) {
            if (bean.getConfigEntries() != null) {
                bean.getConfigEntries().forEach(entry -> beanEntryNames.add(entry.getName()));
            }
        }

        List<ConfigEntry> factoryEntries = new ArrayList<>();
        if (factory.getConfigEntries() != null) {
            for (ConfigEntry entry : factory.getConfigEntries()) {
                warnOnUnknownType(factory.getName(), entry);
                if (!beanEntryNames.contains(entry.getName())) {
                    factoryEntries.add(entry);
                }
            }
        }

        factory.setPropertiesSchema(
                PropertiesSchemaBuilder.build(factoryEntries, feature -> beanNames(factory, feature)));
        for (ForageBean bean : beans(factory)) {
            bean.setPropertiesSchema(
                    PropertiesSchemaBuilder.build(bean.getConfigEntries(), feature -> beanNames(factory, feature)));
        }
    }

    private void warnOnUnknownType(String factoryName, ConfigEntry entry) {
        if (!PropertiesSchemaBuilder.isKnownType(entry.getType())) {
            log.warn("Config entry '" + entry.getName() + "' of factory '" + factoryName + "' has type '"
                    + entry.getType() + "' with no JSON Schema mapping; it is rendered as a string");
        }
    }

    private static List<String> beanNames(ForageFactory factory, String feature) {
        List<String> names = new ArrayList<>();
        if (factory.getBeansByFeature() != null) {
            for (FeatureBeans featureBeans : factory.getBeansByFeature()) {
                if (feature.equals(featureBeans.getFeature()) && featureBeans.getBeans() != null) {
                    featureBeans.getBeans().forEach(bean -> names.add(bean.getName()));
                }
            }
        }
        return names;
    }

    private static List<ForageBean> beans(ForageFactory factory) {
        List<ForageBean> beans = new ArrayList<>();
        if (factory.getBeansByFeature() != null) {
            for (FeatureBeans featureBeans : factory.getBeansByFeature()) {
                if (featureBeans.getBeans() != null) {
                    beans.addAll(featureBeans.getBeans());
                }
            }
        }
        return beans;
    }

    private static List<String> components(ForageFactory factory) {
        return factory.getComponents() != null ? factory.getComponents() : List.of();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
