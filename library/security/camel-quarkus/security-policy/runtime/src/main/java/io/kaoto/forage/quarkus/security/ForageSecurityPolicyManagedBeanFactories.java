package io.kaoto.forage.quarkus.security;

import java.util.Set;
import io.kaoto.forage.core.common.RuntimeManagedBeanFactories;
import io.kaoto.forage.core.security.SecurityPolicyBeanFactory;

/**
 * The Quarkus extension registers the security policies at runtime initialization, so the plain
 * {@link SecurityPolicyBeanFactory} must not create them during static initialization (image build time in native
 * mode).
 */
public class ForageSecurityPolicyManagedBeanFactories implements RuntimeManagedBeanFactories {

    @Override
    public Set<String> beanFactoryClassNames() {
        return Set.of(SecurityPolicyBeanFactory.class.getName());
    }
}
