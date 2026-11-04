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
     * The name under which this bean is registered when it is configured without an instance name, when it
     * differs from the factory's {@link ForageFactory#defaultBeanName()} (e.g. one bean per security provider).
     * Used by tooling such as {@code camel forage config read}.
     *
     * @return the default bean name
     */
    String defaultBeanName() default "";

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

    /**
     * Config entries of the factory this bean belongs to whose presence means the factory uses this bean,
     * even when none of the bean's own config entries is set. For example, the agent builds its retrieval
     * augmentor whenever {@code forage.agent.embedding.model.name} is set. Tooling such as
     * {@code camel forage run} and {@code camel forage export} adds the bean's artifact when one of them is
     * configured, for any instance name.
     *
     * @return full config entry names (e.g., "forage.agent.embedding.model.name")
     */
    String[] enabledBy() default {};

    /**
     * Additional Maven repository URLs required to resolve this bean's transitive dependencies.
     * Only applied during {@code camel run} to configure KameletMain repositories.
     *
     * @return array of Maven repository URLs
     */
    String[] repositories() default {};
}
