package io.kaoto.forage.core.util.config;

/**
 * Marker interface for user-provided {@link PropertyFileSource} implementations
 * that are discovered at runtime via Java's {@link java.util.ServiceLoader} mechanism.
 *
 * <p>To register a custom property file source:
 * <ol>
 *   <li>Implement this interface</li>
 *   <li>Create a file {@code META-INF/services/io.kaoto.forage.core.util.config.PluggablePropertyFileSource}
 *       containing the fully-qualified class name of your implementation</li>
 * </ol>
 *
 * <p>Custom sources are automatically discovered by {@link PluggablePropertyFileSourceLoader}
 * and integrated into the {@link PropertyFileLocator} resolution chain.
 *
 * @since 1.2
 * @see PropertyFileSource
 * @see PluggablePropertyFileSourceLoader
 */
public interface PluggablePropertyFileSource extends PropertyFileSource {}
