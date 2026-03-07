package io.kaoto.forage.core.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import io.kaoto.forage.core.util.config.Config;

/**
 * Annotation to mark classes as Forage factories.
 *
 * This annotation can be used to identify and configure Forage factory components
 * for automatic discovery and registration. Factories are responsible for creating
 * and configuring other Forage components.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ForageFactory {

    /**
     * The name of the factory. If not specified, the class name will be used.
     *
     * @return the factory name
     */
    String value() default "";

    /**
     * The component that this Forage factory supports (e.g., "camel-langchain4j-agent", "camel-langchain4j-embeddings").
     *
     * @return the supported component
     */
    String[] components() default {};

    /**
     * Description of what this factory provides.
     *
     * @return the factory description
     */
    String description() default "";

    /**
     * The type of factory that determines which variants belong together.
     * Factories with the same type but different variants will be grouped in the catalog.
     *
     * @return the factory type enum
     */
    FactoryType type();

    /**
     * Mark if the Factory configure itself, without having to configure the Factory name on the component.
     *
     * @return
     */
    boolean autowired() default false;

    /**
     * Conditional beans that are automatically registered when certain config entries are enabled.
     * Used by the catalog generator to inform UI wizards about beans created based on configuration.
     *
     * @return array of conditional bean groups
     */
    ConditionalBeanGroup[] conditionalBeans() default {};

    /**
     * The runtime variant for which this factory is designed (Base, Spring Boot, or Quarkus).
     * This is used during catalog generation to group factories with the same name under different variants.
     *
     * @return the factory variant
     */
    FactoryVariant variant() default FactoryVariant.BASE;

    /**
     * The Config class that defines the properties file name for this factory.
     * The Config class's name() method returns the base name used for the properties file.
     * If not specified (defaults to Config.class), no properties file will be associated.
     *
     * @return the Config class
     */
    Class<? extends Config> configClass() default Config.class;

    /**
     * Additional runtime dependencies needed for this factory variant.
     * GAVs in mvn: format, resolved at catalog generation time.
     * BOM-managed dependencies omit the version (e.g., "mvn:org.apache.camel.quarkus:camel-quarkus-sql").
     *
     * @return array of GAV strings
     */
    String[] runtimeDependencies() default {};
}
