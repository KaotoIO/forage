package io.kaoto.forage.springboot.common;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import io.kaoto.forage.core.util.config.PlaceholderFunctionResolver;
import io.kaoto.forage.core.util.config.PlaceholderResolver;

/**
 * Makes Forage resolve placeholder functions ({@code {{secret:name/key}}}, {@code {{aws:name}}}, ...)
 * in its properties through the {@code CamelContext} bean of this application context.
 *
 * <p>Installed before the context is refreshed, so it is in place before any Forage bean is created, and
 * removed when the context closes. Registered via {@code META-INF/spring.factories}.
 *
 * @since 1.6
 */
public class ForagePlaceholderFunctionsInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        PlaceholderFunctionResolver resolver = new SpringCamelPlaceholderFunctionResolver(applicationContext);
        PlaceholderResolver.install(resolver);
        applicationContext.addApplicationListener(new UninstallOnClose(applicationContext, resolver));
    }

    private record UninstallOnClose(
            ConfigurableApplicationContext applicationContext, PlaceholderFunctionResolver resolver)
            implements ApplicationListener<ApplicationEvent> {

        @Override
        public void onApplicationEvent(ApplicationEvent event) {
            if (event instanceof ContextClosedEvent closed && closed.getApplicationContext() == applicationContext) {
                PlaceholderResolver.uninstall(resolver);
            }
        }
    }
}
