package io.kaoto.forage.integration.tests;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to disable a test when running with plain Camel Main runtime.
 *
 * <p>This annotation can be applied to test methods or test classes to skip execution
 * when the {@link IntegrationTestSetupExtension#RUNTIME_PROPERTY} system property
 * is null or empty (indicating plain Camel Main runtime without Spring Boot or Quarkus).
 *
 * <p><strong>Important:</strong> This annotation requires {@link RuntimeConditionExtension}
 * to be registered on the test class via {@code @ExtendWith(RuntimeConditionExtension.class)}.
 * Without this extension, the annotation will be silently ignored.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface DisableOnCamelMain {
    /**
     * Optional reason for disabling the test on plain Camel Main runtime.
     */
    String reason() default "";
}
