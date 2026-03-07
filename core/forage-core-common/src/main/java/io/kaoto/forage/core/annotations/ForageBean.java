package io.kaoto.forage.core.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import io.kaoto.forage.core.util.config.Config;

/**
 * Annotation to mark classes as Forage beans.
 *
 * This annotation can be used to identify and configure Forage components
 * for automatic discovery and registration.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ForageBean {

    /**
     * The name of the bean. If not specified, the class name will be used.
     *
     * @return the bean name
     */
    String value() default "";

    /**
     * The component that this Forage bean supports (e.g., "camel-langchain4j-agent", "camel-langchain4j-embeddings").
     *
     * @return the supported component
     */
    String[] components() default {};

    /**
     * Description of what this bean provides.
     *
     * @return the bean description
     */
    String description() default "";

    /**
     * Group different features for the same components
     * for example, camel-langchain4j-agent "Chat Model" and "Memory"
     *
     * @return
     */
    String feature() default "";

    /**
     * The Config class that defines the properties file name for this bean.
     * The Config class's name() method returns the base name used for the properties file.
     * If not specified (defaults to Config.class), no properties file will be associated.
     * Beans without individual configs (like JDBC beans) should leave this as default.
     *
     * @return the Config class
     */
    Class<? extends Config> configClass() default Config.class;

    /**
     * Additional runtime dependencies needed for this bean, per variant.
     * Format: "variant:gavString" (e.g., "quarkus:mvn:io.quarkus:quarkus-jdbc-postgresql").
     * BOM-managed deps omit version; non-BOM deps include version.
     *
     * @return array of variant-prefixed GAV strings
     */
    String[] runtimeDependencies() default {};
}
