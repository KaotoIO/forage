package io.kaoto.forage.core.common;

import java.util.Locale;

/**
 * Enum with supported runtimes. Duplicates on org.apache.camel.dsl.jbang.core.common.RuntimeType, but allows us to not
 * depend on cameljbang here, in commons module.
 */
public enum RuntimeType {
    MAIN,
    QUARKUS,
    SPRING_BOOT;

    public static RuntimeType fromValue(String value) {
        value = value.toLowerCase(Locale.ROOT);
        return switch (value) {
            case "springboot", "spring-boot", "camel-spring-boot" -> SPRING_BOOT;
            case "quarkus", "camel-quarkus" -> QUARKUS;
            // Camel 4.23+ "camel run" defaults to the in-process jbang runtime, which uses main's dependencies
            case "main", "camel-main", "jbang", "camel-jbang" -> MAIN;
            default -> throw new IllegalArgumentException("Unsupported runtime " + value);
        };
    }

    public String runtime() {
        return switch (this) {
            case SPRING_BOOT -> "spring-boot";
            case QUARKUS -> "quarkus";
            case MAIN -> "main";
        };
    }

    public String displayName() {
        return switch (this) {
            case SPRING_BOOT -> "Spring Boot";
            case QUARKUS -> "Quarkus";
            case MAIN -> "Camel Main";
        };
    }

    @Override
    public String toString() {
        return runtime();
    }
}
