package io.kaoto.forage.core.util.config;

/**
 * Runtime exception thrown when a required configuration value is missing or cannot be resolved.
 *
 * <p>This exception is typically thrown by configuration classes when they attempt to access
 * a required configuration parameter that has not been provided through any of the supported
 * configuration sources (environment variables, system properties, or configuration files).
 *
 * <p>The exception indicates a configuration error that prevents the application from starting
 * or functioning correctly. It should be treated as a fatal error during application initialization.
 *
 * <p><strong>Common scenarios where this exception is thrown:</strong>
 * <ul>
 *   <li>Required API keys or credentials are not provided</li>
 *   <li>Database connection strings are missing</li>
 *   <li>Service endpoints are not configured</li>
 *   <li>Required file paths are not specified</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong>
 * <pre>{@code
 * public String apiKey() {
 *     return ConfigStore.getInstance()
 *             .get(API_KEY_MODULE)
 *             .orElseThrow(() -> new MissingConfigException("API key is required but not configured"));
 * }
 * }</pre>
 *
 * <p>When this exception is thrown, it typically indicates that the application deployment
 * or environment setup is incomplete. The message should provide clear guidance on what
 * configuration is missing and how to provide it.
 *
 * @see ConfigStore
 * @see Config
 * @see ConfigModule
 * @since 1.0
 */
public class MissingConfigException extends RuntimeException {

    /**
     * Constructs a new MissingConfigException with the specified detail message.
     *
     * <p>The message should clearly describe which configuration parameter is missing
     * and provide guidance on how to provide it. For example:
     * <ul>
     *   <li>"Google API key is required. Set the GOOGLE_API_KEY environment variable."</li>
     *   <li>"Database URL not configured. Provide database.url system property."</li>
     *   <li>"Missing required configuration: my-service.endpoint"</li>
     * </ul>
     *
     * @param message the detail message explaining which configuration is missing
     */
    public MissingConfigException(String message) {
        super(message);
    }

    /**
     * Constructs a new MissingConfigException with the specified detail message and cause.
     *
     * @param message the detail message explaining which configuration is missing
     * @param cause the cause, or {@code null}
     * @since 1.6
     */
    public MissingConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
