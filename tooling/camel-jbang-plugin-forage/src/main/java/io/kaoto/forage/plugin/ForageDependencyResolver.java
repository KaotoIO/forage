package io.kaoto.forage.plugin;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;

/**
 * Computes the dependencies {@code forage.*} properties need: the variant GAVs of each property's factory
 * (base, Spring Boot, Quarkus) and the GAVs of the beans they configure, taken from the bean the key belongs to
 * (e.g. {@code ollama} for {@code forage.ollama.model.name}) and from kind values ({@code db.kind}, {@code kind}).
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
    }

    /**
     * Computes the dependencies the given properties need. Keys that are not Forage properties are ignored.
     *
     * @param properties property keys and values (e.g. the content of a properties file)
     * @return the required dependencies
     */
    public Dependencies resolve(Map<String, String> properties) {
        Dependencies dependencies = new Dependencies();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            parser.parse(entry.getKey()).ifPresent(parsed -> {
                addFactory(dependencies, parsed.factoryType());
                if (parsed.beanType() != null) {
                    addBean(dependencies, parsed.beanType());
                }
                if (parsed.isKindProperty()) {
                    addBean(dependencies, entry.getValue());
                }
            });
        }
        return dependencies;
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
