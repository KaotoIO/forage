package io.kaoto.forage.core.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines a group of beans that are conditionally registered when a boolean config entry is enabled.
 *
 * This annotation is used within {@link ForageFactory#conditionalBeans()} to declare beans
 * that are automatically created when certain configuration options are enabled.
 * The catalog generator uses this to inform UI wizards (read-only, informational).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface ConditionalBeanGroup {

    /**
     * Unique identifier for this conditional bean group.
     *
     * @return the group identifier
     */
    String id();

    /**
     * Human-readable description of what these beans provide.
     *
     * @return the description
     */
    String description() default "";

    /**
     * The boolean config entry that triggers bean creation when set to true.
     * For example: "jdbc.transaction.enabled"
     *
     * @return the config entry name
     */
    String configEntry();

    /**
     * The beans that are created when the condition is met.
     *
     * @return array of conditional bean definitions
     */
    ConditionalBean[] beans();

    /**
     * Runtime dependencies triggered when this condition is met.
     * Format: "variant:gavString" (e.g., "quarkus:mvn:io.quarkus:quarkus-narayana-jta").
     *
     * @return array of variant-prefixed GAV strings
     */
    String[] runtimeDependencies() default {};
}
