package io.kaoto.forage.plugin;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;
import io.kaoto.forage.plugin.ForagePropertyParser.ParsedProperty;

/**
 * Computes the dependencies {@code forage.*} properties need: the variant GAVs of each property's factory
 * (base, Spring Boot, Quarkus) and the GAVs of the beans they configure, taken from the bean the key belongs to
 * (e.g. {@code ollama} for {@code forage.ollama.model.name}) and from the beans a value selects (e.g.
 * {@code db.kind=postgresql}, {@code agent.model.kind=ollama}; see
 * {@link ForagePropertyParser#selectedBeans(ParsedProperty, String)}).
 *
 * <p>This class has no CLI dependencies, so it can be reused outside the {@code config} commands.
 */
public final class ForageDependencyResolver {

    private final ForageCatalogReader catalog;
    private final ForagePropertyParser parser;

    public ForageDependencyResolver(ForageCatalogReader catalog) {
        this(catalog, new ForagePropertyParser(catalog));
    }

    public ForageDependencyResolver(ForageCatalogReader catalog, ForagePropertyParser parser) {
        this.catalog = catalog;
        this.parser = parser;
    }

    /**
     * Dependencies grouped by the {@code camel.jbang.dependencies*} property they belong to.
     *
     * @param base bean GAVs ({@code camel.jbang.dependencies})
     * @param main plain Camel factory GAVs ({@code camel.jbang.dependencies.main})
     * @param springBoot Spring Boot factory GAVs ({@code camel.jbang.dependencies.spring-boot})
     * @param quarkus Quarkus factory GAVs ({@code camel.jbang.dependencies.quarkus})
     */
    public record Dependencies(Set<String> base, Set<String> main, Set<String> springBoot, Set<String> quarkus) {

        public Dependencies() {
            this(new LinkedHashSet<>(), new LinkedHashSet<>(), new LinkedHashSet<>(), new LinkedHashSet<>());
        }

        /**
         * @return every GAV, whatever the runtime
         */
        public Set<String> all() {
            Set<String> all = new LinkedHashSet<>(base);
            all.addAll(main);
            all.addAll(springBoot);
            all.addAll(quarkus);
            return all;
        }

        public boolean isEmpty() {
            return base.isEmpty() && main.isEmpty() && springBoot.isEmpty() && quarkus.isEmpty();
        }

        /**
         * Adds every GAV of the given dependencies to this one.
         */
        public void addAll(Dependencies other) {
            base.addAll(other.base);
            main.addAll(other.main);
            springBoot.addAll(other.springBoot);
            quarkus.addAll(other.quarkus);
        }
    }

    /**
     * Computes the dependencies the given properties need. Keys that are not Forage properties are ignored.
     *
     * @param properties property keys and values (e.g. the content of a properties file)
     * @return the required dependencies
     */
    public Dependencies resolve(Map<String, String> properties) {
        Dependencies dependencies = new Dependencies();
        properties.forEach((key, value) -> parser.parse(key).ifPresent(parsed -> add(dependencies, parsed, value)));
        return dependencies;
    }

    /**
     * Adds the dependencies of one property: its factory, the bean it configures and the beans its value selects.
     *
     * @param dependencies the dependencies to add to
     * @param parsed the parsed property key
     * @param value the property value
     */
    public void add(Dependencies dependencies, ParsedProperty parsed, String value) {
        addFactory(dependencies, parsed.factoryType());
        if (parsed.beanType() != null) {
            addBean(dependencies, parsed.beanType());
        }
        parser.selectedBeans(parsed, value).forEach(bean -> addBean(dependencies, bean));
    }

    /**
     * Adds the variant GAVs of a factory.
     */
    public void addFactory(Dependencies dependencies, String factoryTypeKey) {
        catalog.getFactoryVariantGav(factoryTypeKey, "base").ifPresent(dependencies.main()::add);
        catalog.getFactoryVariantGav(factoryTypeKey, "springboot").ifPresent(dependencies.springBoot()::add);
        catalog.getFactoryVariantGav(factoryTypeKey, "quarkus").ifPresent(dependencies.quarkus()::add);
    }

    /**
     * Adds the GAVs of a bean, given its name or kind (e.g. {@code postgresql}, {@code ollama}).
     */
    public void addBean(Dependencies dependencies, String beanName) {
        if (beanName != null && !beanName.isBlank()) {
            dependencies.base().addAll(catalog.getBeanGavs(beanName.trim().toLowerCase(Locale.ROOT)));
        }
    }
}
