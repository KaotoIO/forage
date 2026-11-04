package io.kaoto.forage.core.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares how a Camel route references a bean created by a {@link ForageFactory}, so that tooling
 * can generate an example route without knowing the factory.
 *
 * <p>The catalog generator already derives the reference for every component listed in
 * {@link ForageFactory#components()} whose Camel catalog entry has an endpoint parameter of the
 * factory bean type (for example {@code dataSource} on {@code sql}). Declare a reference only where
 * that cannot work: the bean name goes into the endpoint path (for example {@code jdbc:myDataSource}),
 * or the bean is used by an EIP rather than an endpoint (for example {@code policy}). A declared
 * reference replaces the derived one for the same component.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface BeanReference {

    /**
     * The Camel catalog kind of {@link #name()}: {@code component} or {@code processor} (an EIP).
     * Use a string literal: the catalog generator reads annotation values from source.
     *
     * @return the Camel catalog kind
     */
    String catalogKind() default "component";

    /**
     * The Camel component scheme (for example {@code jdbc}) or EIP name (for example {@code policy}).
     *
     * @return the Camel catalog entry name
     */
    String name();

    /**
     * The option of the Camel catalog entry that holds the bean reference
     * (for example {@code dataSourceName}).
     *
     * @return the option name
     */
    String option();

    /**
     * The kind of the option, as named by the Camel catalog: {@code path} or {@code parameter} for
     * endpoint options, {@code attribute} for EIP options.
     *
     * @return the option kind
     */
    String optionKind() default "parameter";

    /**
     * How the option value is written, with {@code {beanRef}} standing for the bean name.
     * When empty, the catalog uses {@code #{beanRef}} for {@code parameter} options and
     * {@code {beanRef}} otherwise.
     *
     * @return the value template
     */
    String valueTemplate() default "";
}
