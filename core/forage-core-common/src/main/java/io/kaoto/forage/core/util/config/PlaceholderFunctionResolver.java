package io.kaoto.forage.core.util.config;

import java.util.Optional;

/**
 * Resolves Camel-style placeholder functions ({@code {{function:remainder}}}) other than
 * {@code env} and {@code sys}, which {@link PlaceholderResolver} always resolves by itself.
 *
 * <p>A runtime adapter installs a resolver with {@link PlaceholderResolver#install(PlaceholderFunctionResolver)}
 * where Forage creates beans: on plain Camel the resolver delegates to the {@code CamelContext}
 * {@code PropertiesComponent}, on Spring Boot to the {@code CamelContext} bean. Without a resolver,
 * function placeholders are left unchanged.
 *
 * <p>Implementations must never log resolved values.
 *
 * @since 1.6
 */
public interface PlaceholderFunctionResolver {

    /**
     * Resolves the placeholder {@code {{function:remainder}}} found in the value of {@code propertyName}.
     *
     * @param propertyName the Forage property holding the placeholder, for error messages only
     * @param function the function name, e.g. {@code secret} or {@code aws}
     * @param remainder the text after the first colon, passed to the function as is
     * @return the resolved value, or empty when {@code function} is not a placeholder function known to
     *         this resolver: the placeholder is then left unchanged, as it may be a plain property
     *         placeholder with a default value ({@code {{key:default}}}) resolved later by Camel
     * @throws UnresolvedPlaceholderException when the function is known but cannot resolve the placeholder
     */
    Optional<String> resolve(String propertyName, String function, String remainder);
}
