package io.kaoto.forage.core.common;

import java.util.Set;

/**
 * Declares {@link BeanFactory} implementations whose beans a runtime adapter registers itself, for
 * example a Forage Quarkus extension that creates them at runtime initialization.
 *
 * <p>Implementations are discovered via {@link java.util.ServiceLoader}, so shipping one in a runtime
 * adapter module is enough to take ownership of the listed factories.
 * {@link io.kaoto.forage.core.ForageContextServicePlugin} does not configure them, which prevents beans
 * from being created twice. On Quarkus this also keeps them out of the native image build: Camel Quarkus
 * starts context service plugins during static initialization, which runs at image build time.
 *
 * @since 1.6.3
 */
public interface RuntimeManagedBeanFactories {

    /**
     * Returns the fully qualified class names of the bean factories whose beans the runtime adapter registers.
     *
     * @return the managed bean factory class names
     */
    Set<String> beanFactoryClassNames();
}
