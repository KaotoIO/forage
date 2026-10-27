package io.kaoto.forage.core.util.config;

import java.util.Collections;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default configuration resolver that reads from runtime-specific configuration sources
 * (Spring Boot, Quarkus, Camel Main).
 *
 * <p>Environment variables and system properties are handled by {@link ConfigStore} itself,
 * before the resolver chain, so the documented precedence contract (env vars &gt; system
 * properties &gt; configuration files) holds on every runtime. This resolver is always
 * registered at priority 0 as the baseline for the configuration-file tier.
 *
 * @see ConfigResolver
 * @since 1.1
 */
public class DefaultConfigResolver implements ConfigResolver {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultConfigResolver.class);

    @Override
    public Optional<String> resolve(String propertyName) {
        Optional<String> result =
                switch (ConfigHelper.getRuntime()) {
                    case SPRING_BOOT -> ConfigHelper.getSpringBootProperty(propertyName);
                    case QUARKUS -> ConfigHelper.getQuarkusProperty(propertyName);
                    case MAIN -> ConfigHelper.getCamelMainProperty(propertyName);
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
