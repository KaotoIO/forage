package io.kaoto.forage.core.util.config;

import java.util.Collections;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default configuration resolver that reads from environment variables, system properties,
 * and runtime-specific configuration sources (Spring Boot, Quarkus, Camel Main).
 *
 * <p>This resolver encapsulates the original {@link ConfigStore} resolution logic and is
 * always registered at priority 0 as the baseline resolver.
 *
 * <p><strong>Resolution order:</strong>
 * <ol>
 *   <li>Environment variables (via {@link System#getenv(String)})</li>
 *   <li>System properties (via {@link System#getProperty(String)})</li>
 *   <li>Runtime-specific config (Spring Boot application.properties, Quarkus SmallRyeConfig,
 *       or Camel Main application.properties)</li>
 * </ol>
 *
 * @see ConfigResolver
 * @since 1.1
 */
public class DefaultConfigResolver implements ConfigResolver {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultConfigResolver.class);

    @Override
    public Optional<String> resolve(String propertyName) {
        // 1. Environment variables: convert dot notation to UPPER_SNAKE_CASE
        String envName = propertyName.replace(".", "_").toUpperCase();
        String environmentValue = System.getenv(envName);
        if (environmentValue != null) {
            return Optional.of(environmentValue);
        }

        // 2. System properties
        String propertyValue = System.getProperty(propertyName);
        if (propertyValue != null) {
            return Optional.of(propertyValue);
        }

        // 3. Runtime-specific fallback
        Optional<String> result =
                switch (ConfigHelper.getRuntime()) {
                    case springBoot -> ConfigHelper.getSpringBootProperty(propertyName);
                    case quarkus -> ConfigHelper.getQuarkusProperty(propertyName);
                    case main -> ConfigHelper.getCamelMainProperty(propertyName);
                };
        if (result.isPresent()) {
            LOG.debug("Resolved '{}' from runtime config", propertyName);
        }
        return result;
    }

    @Override
    public Set<String> discoverPrefixes(String regexp) {
        Properties appProps = ConfigHelper.getApplicationProperties();
        if (appProps == null) {
            return Collections.emptySet();
        }
        return PropertyFileLocator.readPrefixes(appProps, regexp);
    }

    @Override
    public int priority() {
        return 0;
    }
}
