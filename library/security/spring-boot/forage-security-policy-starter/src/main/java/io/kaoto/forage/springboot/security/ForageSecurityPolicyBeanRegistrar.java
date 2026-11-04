package io.kaoto.forage.springboot.security;

import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import io.kaoto.forage.core.common.RuntimeType;
import io.kaoto.forage.core.security.SecurityPolicyModuleDescriptor;
import io.kaoto.forage.core.security.SecurityPolicyProvider;
import io.kaoto.forage.core.util.config.ConfigHelper;
import io.kaoto.forage.springboot.common.SpringPropertyHelper;

/**
 * Registers a Spring bean for the default policy of every configured {@link SecurityPolicyProvider}.
 *
 * <p>Registration happens while configuration classes are processed, so a bean the application defines with the
 * same name takes precedence. The Spring context owns the policies: those implementing {@link AutoCloseable} (for
 * example the Keycloak policy and its HTTP clients) are closed when the context closes. Providers without a
 * {@link SecurityPolicyModuleDescriptor} are left to the plain {@code SecurityPolicyBeanFactory}, which binds them
 * in the Camel registry.
 */
class ForageSecurityPolicyBeanRegistrar
        implements ImportBeanDefinitionRegistrar, EnvironmentAware, BeanClassLoaderAware {

    /**
     * Bean definition attribute holding the name of the provider that created the policy.
     */
    static final String PROVIDER_ATTRIBUTE = "io.kaoto.forage.security.policy.provider";

    private static final Logger LOG = LoggerFactory.getLogger(ForageSecurityPolicyBeanRegistrar.class);

    private Environment environment;
    private ClassLoader classLoader;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        for (SecurityPolicyProvider provider : ServiceLoader.load(SecurityPolicyProvider.class, classLoader)) {
            SecurityPolicyModuleDescriptor<?> descriptor = provider.descriptor();
            if (descriptor == null || !isConfigured(descriptor)) {
                continue;
            }
            if (!descriptor.supportedRuntimes().contains(RuntimeType.SPRING_BOOT)) {
                LOG.warn(
                        "The {} security policy is not supported on Spring Boot, it is not registered",
                        provider.name());
                continue;
            }
            String beanName = descriptor.defaultBeanName();
            if (registry.containsBeanDefinition(beanName)) {
                LOG.debug("Bean '{}' is already defined, skipping the {} security policy", beanName, provider.name());
                continue;
            }
            GenericBeanDefinition definition = new GenericBeanDefinition();
            definition.setBeanClass(descriptor.primaryBeanClass());
            definition.setInstanceSupplier(() -> provider.create(null));
            // Closes AutoCloseable policies when the context closes
            definition.setDestroyMethodName(AbstractBeanDefinition.INFER_METHOD);
            definition.setAttribute(PROVIDER_ATTRIBUTE, provider.name());
            registry.registerBeanDefinition(beanName, definition);
            LOG.info("Registered {} security policy bean definition '{}'", provider.name(), beanName);
        }
    }

    private boolean isConfigured(SecurityPolicyModuleDescriptor<?> descriptor) {
        return SpringPropertyHelper.hasProperties(
                        environment, ConfigHelper.getDefaultPropertyRegexp(descriptor.modulePrefix()))
                || descriptor.isConfigured();
    }
}
