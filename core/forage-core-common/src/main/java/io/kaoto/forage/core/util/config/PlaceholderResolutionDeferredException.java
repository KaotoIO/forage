package io.kaoto.forage.core.util.config;

/**
 * Thrown by a {@link PlaceholderFunctionResolver} that cannot resolve a placeholder yet, for example on
 * Spring Boot while the CamelContext bean is still being created. Unlike
 * {@link UnresolvedPlaceholderException} it is not a configuration error: the bean creation that needed
 * the value can be retried once the CamelContext is ready.
 *
 * @since 1.6
 */
public class PlaceholderResolutionDeferredException extends RuntimeException {

    public PlaceholderResolutionDeferredException(String propertyName, String function, String reason) {
        super("Cannot resolve the {{" + function + ":...}} placeholder in property '" + propertyName + "' yet: "
                + reason);
    }
}
