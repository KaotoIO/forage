package io.kaoto.forage.core.util.config;

/**
 * Thrown when a placeholder in a Forage property value cannot be resolved while Forage creates a bean.
 *
 * <p>The message names the property and the placeholder function, never the property value: the
 * value may contain secrets, and so may the text around the placeholder.
 *
 * @since 1.6
 */
public class UnresolvedPlaceholderException extends MissingConfigException {

    private final String propertyName;
    private final String function;

    public UnresolvedPlaceholderException(String propertyName, String function, String reason) {
        this(propertyName, function, reason, null);
    }

    public UnresolvedPlaceholderException(String propertyName, String function, String reason, Throwable cause) {
        super(
                "Cannot resolve the {{" + function + ":...}} placeholder in property '" + propertyName + "': " + reason,
                cause);
        this.propertyName = propertyName;
        this.function = function;
    }

    /**
     * The Forage property holding the placeholder.
     */
    public String getPropertyName() {
        return propertyName;
    }

    /**
     * The placeholder function, e.g. {@code secret} or {@code env}.
     */
    public String getFunction() {
        return function;
    }
}
