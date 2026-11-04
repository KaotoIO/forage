package io.kaoto.forage.core.util.config;

import java.util.Optional;

/**
 * {@link PlaceholderFunctionResolver} for places where Forage cannot resolve secret-store placeholder
 * functions: it fails for them with an explanation, instead of letting {@code {{secret:...}}} or
 * {@code {{aws:...}}} reach a driver as a literal value. Other placeholders are left unchanged.
 *
 * @since 1.6
 */
public final class UnsupportedPlaceholderFunctionResolver implements PlaceholderFunctionResolver {

    /**
     * Quarkus: Forage translates its properties into Quarkus configuration (consumed by Quarkus
     * extensions such as Agroal or Artemis) before any CamelContext exists, and the beans it creates
     * itself are created while the CamelContext is built at static init.
     */
    public static final UnsupportedPlaceholderFunctionResolver QUARKUS =
            new UnsupportedPlaceholderFunctionResolver("Camel placeholder functions other than env and sys are not"
                    + " supported in Forage properties on Quarkus, because Forage turns them into Quarkus"
                    + " configuration before any CamelContext exists. Expose the secret as an environment"
                    + " variable and use {{env:NAME}}, or use a Quarkus configuration expression (${...})");

    private final String reason;

    public UnsupportedPlaceholderFunctionResolver(String reason) {
        this.reason = reason;
    }

    @Override
    public Optional<String> resolve(String propertyName, String function, String remainder) {
        if (PlaceholderResolver.secretFunctionArtifact(function).isPresent()) {
            throw new UnresolvedPlaceholderException(propertyName, function, reason);
        }
        return Optional.empty();
    }
}
