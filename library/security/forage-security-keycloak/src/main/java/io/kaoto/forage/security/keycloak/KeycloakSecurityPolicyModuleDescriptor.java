package io.kaoto.forage.security.keycloak;

import io.kaoto.forage.core.security.SecurityPolicyModuleDescriptor;

/**
 * Describes the Keycloak security policy provider to the Spring Boot and Quarkus adapters.
 *
 * @since 1.6.3
 */
public class KeycloakSecurityPolicyModuleDescriptor
        extends SecurityPolicyModuleDescriptor<KeycloakSecurityPolicyConfig> {

    public static final String DEFAULT_BEAN_NAME = "keycloakPolicy";

    @Override
    public String modulePrefix() {
        return "keycloak";
    }

    @Override
    public KeycloakSecurityPolicyConfig createConfig(String prefix) {
        return new KeycloakSecurityPolicyConfig(prefix);
    }

    @Override
    public String resolveProviderClassName(KeycloakSecurityPolicyConfig config) {
        return KeycloakSecurityPolicyProvider.class.getName();
    }

    @Override
    public String defaultBeanName() {
        return DEFAULT_BEAN_NAME;
    }

    @Override
    public Class<?> primaryBeanClass() {
        return ForageKeycloakSecurityPolicy.class;
    }
}
