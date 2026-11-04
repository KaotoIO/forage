package io.kaoto.forage.springboot.common;

import java.util.Optional;
import org.apache.camel.CamelContext;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import io.kaoto.forage.core.util.config.CamelPlaceholderFunctionResolver;
import io.kaoto.forage.core.util.config.PlaceholderFunctionResolver;
import io.kaoto.forage.core.util.config.PlaceholderResolutionDeferredException;
import io.kaoto.forage.core.util.config.UnsupportedPlaceholderFunctionResolver;

/**
 * Resolves placeholder functions in Forage properties ({@code {{secret:...}}}, {@code {{aws:...}}}, ...)
 * through the {@link CamelContext} bean, when Spring creates a Forage bean.
 *
 * <p>Forage translates its properties into the Spring Environment before any bean exists, but reads a
 * value only when it creates the bean using it. At that point the CamelContext bean can be obtained: Camel
 * Spring Boot has then bridged the Spring Environment into its PropertiesComponent and applied the
 * {@code camel.vault.*} configuration, so functions behave as in routes. The CamelContext is only requested
 * for values that contain a placeholder function.
 *
 * @since 1.6
 */
final class SpringCamelPlaceholderFunctionResolver implements PlaceholderFunctionResolver {

    private static final PlaceholderFunctionResolver NO_CAMEL_CONTEXT = new UnsupportedPlaceholderFunctionResolver(
            "no CamelContext bean is defined in the Spring application context (add camel-spring-boot-starter)");

    private final ConfigurableApplicationContext applicationContext;
    private volatile CamelPlaceholderFunctionResolver delegate;

    SpringCamelPlaceholderFunctionResolver(ConfigurableApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public Optional<String> resolve(String propertyName, String function, String remainder) {
        return delegate(propertyName, function).resolve(propertyName, function, remainder);
    }

    private PlaceholderFunctionResolver delegate(String propertyName, String function) {
        CamelPlaceholderFunctionResolver current = delegate;
        if (current != null) {
            return current;
        }
        ConfigurableListableBeanFactory beanFactory = applicationContext.getBeanFactory();
        String[] names = beanFactory.getBeanNamesForType(CamelContext.class, true, false);
        if (names.length == 0) {
            return NO_CAMEL_CONTEXT;
        }
        for (String name : names) {
            if (beanFactory.isCurrentlyInCreation(name)) {
                // Forage's bean factories look beans up while the CamelContext is built: Spring creates
                // the bean again when a route needs it, with the CamelContext ready
                throw new PlaceholderResolutionDeferredException(
                        propertyName, function, "the CamelContext bean is being created");
            }
        }
        CamelContext camelContext = beanFactory.getBean(CamelContext.class);
        synchronized (this) {
            if (delegate == null) {
                delegate = new CamelPlaceholderFunctionResolver(camelContext);
            }
            return delegate;
        }
    }
}
