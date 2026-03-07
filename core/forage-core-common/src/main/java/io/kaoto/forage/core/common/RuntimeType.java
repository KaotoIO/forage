package io.kaoto.forage.core.common;

import java.util.Locale;

/**
 * Enum with supported runtimes. Duplicates on org.apache.camel.dsl.jbang.core.common.RuntimeType, but allows us to not
 * depend on cameljbang here, in commons module.
 */
public enum RuntimeType {
    main,
    quarkus,
    springBoot;

    public static RuntimeType fromValue(String value) {
        value = value.toLowerCase(Locale.ROOT);
        return switch (value) {
            case "springboot", "spring-boot", "camel-spring-boot" -> springBoot;
            case "quarkus", "camel-quarkus" -> quarkus;
            case "main", "camel-main" -> main;
            default -> throw new IllegalArgumentException("Unsupported runtime " + value);
        };
    }

    public String runtime() {
        return switch (this) {
            case springBoot -> "spring-boot";
            case quarkus -> "quarkus";
            case main -> "main";
        };
    }

    @Override
    public String toString() {
        return runtime();
    }
}
