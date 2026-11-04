package io.kaoto.forage.quarkus.security.tls;

import java.util.Set;
import io.kaoto.forage.core.common.RuntimeManagedBeanFactories;
import io.kaoto.forage.security.tls.TlsBeanFactory;

/**
 * The Quarkus extension registers the TLS profiles at runtime initialization, so the plain {@link TlsBeanFactory}
 * must not create them during static initialization (image build time in native mode).
 */
public class ForageTlsManagedBeanFactories implements RuntimeManagedBeanFactories {

    @Override
    public Set<String> beanFactoryClassNames() {
        return Set.of(TlsBeanFactory.class.getName());
    }
}
