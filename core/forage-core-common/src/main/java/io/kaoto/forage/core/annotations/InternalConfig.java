package io.kaoto.forage.core.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code ConfigModule} field of a {@code ConfigEntries} class as internal: an infrastructure
 * or advanced override (for example a fully qualified class name used to replace a default
 * implementation) that a configuration UI should not render as a form field.
 *
 * <p>The property keeps working at runtime and stays in the catalog {@code configEntries}, so the
 * property validator and the documentation still know it. The catalog flags it with
 * {@code "hidden": true} and leaves it out of the generated {@code propertiesSchema}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface InternalConfig {}
