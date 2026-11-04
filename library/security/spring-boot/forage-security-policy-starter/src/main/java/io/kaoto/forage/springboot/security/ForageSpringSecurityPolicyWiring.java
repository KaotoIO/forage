package io.kaoto.forage.springboot.security;

import org.apache.camel.Exchange;
import org.apache.camel.component.spring.security.SpringSecurityAuthorizationPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.ResolvableType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Sets the {@link AuthenticationManager} and {@link AuthorizationManager} of the Spring Security policy registered
 * by Forage, before Spring initializes it (its {@code afterPropertiesSet()} requires both).
 *
 * <p>Managers already set on the policy are kept. Otherwise the authentication manager is, in order: the application's
 * unique {@code AuthenticationManager} bean, the one built by Spring Security's {@code AuthenticationConfiguration}
 * (present with {@code @EnableWebSecurity}, which Spring Boot applies to servlet applications), or one authenticating
 * against the application's unique {@code UserDetailsService}. The authorization manager is the application's unique
 * {@code AuthorizationManager<Exchange>} bean, or one granting access to any authenticated caller.
 */
class ForageSpringSecurityPolicyWiring implements BeanPostProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(ForageSpringSecurityPolicyWiring.class);

    private final ConfigurableListableBeanFactory beanFactory;

    ForageSpringSecurityPolicyWiring(ConfigurableListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof SpringSecurityAuthorizationPolicy policy && isRegisteredByForage(beanName)) {
            if (policy.getAuthenticationManager() == null) {
                policy.setAuthenticationManager(authenticationManager(beanName));
            }
            if (policy.getAuthorizationManager() == null) {
                policy.setAuthorizationManager(authorizationManager(beanName));
            }
        }
        return bean;
    }

    private boolean isRegisteredByForage(String beanName) {
        return beanFactory.containsBeanDefinition(beanName)
                && beanFactory
                        .getBeanDefinition(beanName)
                        .hasAttribute(ForageSecurityPolicyBeanRegistrar.PROVIDER_ATTRIBUTE);
    }

    private AuthenticationManager authenticationManager(String beanName) {
        AuthenticationManager manager =
                beanFactory.getBeanProvider(AuthenticationManager.class).getIfUnique();
        if (manager != null) {
            LOG.debug("Security policy '{}' uses the AuthenticationManager bean", beanName);
            return manager;
        }
        AuthenticationConfiguration configuration =
                beanFactory.getBeanProvider(AuthenticationConfiguration.class).getIfUnique();
        if (configuration != null) {
            try {
                manager = configuration.getAuthenticationManager();
            } catch (Exception e) {
                throw new BeanCreationException(beanName, "Cannot obtain Spring Security's AuthenticationManager", e);
            }
            if (manager != null) {
                LOG.debug("Security policy '{}' uses Spring Security's AuthenticationManager", beanName);
                return manager;
            }
        }
        UserDetailsService userDetailsService =
                beanFactory.getBeanProvider(UserDetailsService.class).getIfUnique();
        if (userDetailsService != null) {
            DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
            beanFactory.getBeanProvider(PasswordEncoder.class).ifUnique(provider::setPasswordEncoder);
            LOG.debug("Security policy '{}' authenticates against the UserDetailsService bean", beanName);
            return new ProviderManager(provider);
        }
        throw new BeanCreationException(
                beanName,
                "The Spring Security policy needs an AuthenticationManager: define an AuthenticationManager or"
                        + " UserDetailsService bean, or enable Spring Security (for example with"
                        + " spring-boot-starter-security in a servlet application)");
    }

    @SuppressWarnings("unchecked")
    private AuthorizationManager<Exchange> authorizationManager(String beanName) {
        ResolvableType type = ResolvableType.forClassWithGenerics(AuthorizationManager.class, Exchange.class);
        AuthorizationManager<Exchange> manager = (AuthorizationManager<Exchange>)
                beanFactory.getBeanProvider(type).getIfUnique();
        if (manager != null) {
            LOG.debug("Security policy '{}' uses the AuthorizationManager<Exchange> bean", beanName);
            return manager;
        }
        LOG.info(
                "Security policy '{}' grants access to any authenticated caller; define an"
                        + " AuthorizationManager<Exchange> bean to restrict it",
                beanName);
        return AuthenticatedAuthorizationManager.authenticated();
    }
}
