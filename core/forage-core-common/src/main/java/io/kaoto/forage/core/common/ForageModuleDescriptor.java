package io.kaoto.forage.core.common;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import io.kaoto.forage.core.util.config.Config;

/**
 * Runtime-agnostic descriptor that captures all module-specific knowledge for a Forage module
 * (e.g., JDBC, JMS, AI). Each runtime adapter (Spring Boot, Quarkus) consumes descriptors
 * to register beans and translate configuration, eliminating per-module/per-runtime boilerplate.
 *
 * @param <C> the module's configuration type (e.g., {@code DataSourceFactoryConfig})
 * @param <P> the module's provider type (e.g., {@code DataSourceProvider})
 * @since 1.1
 */
public interface ForageModuleDescriptor<C extends Config, P extends BeanProvider<?>> {

    /**
     * The module prefix used in property names, e.g., {@code "jdbc"} or {@code "jms"}.
     */
    String modulePrefix();

    /**
     * Creates a Config instance for a given prefix ({@code null} for default/non-prefixed configuration).
     */
    C createConfig(String prefix);

    /**
     * The ServiceLoader provider interface class (e.g., {@code DataSourceProvider.class}).
     */
    Class<P> providerClass();

    /**
     * Resolves which provider implementation to use based on the configuration
     * (e.g., dbKind "postgresql" maps to {@code io.kaoto.forage.jdbc.postgresql.PostgresqlJdbc}).
     *
     * @return the fully qualified class name of the provider to use
     */
    String resolveProviderClassName(C config);

    /**
     * The default bean name when registering the first/unnamed bean
     * (e.g., {@code "dataSource"}, {@code "connectionFactory"}).
     */
    String defaultBeanName();

    /**
     * The class type to use for primary bean registration
     * (e.g., {@code AgroalDataSource.class}, {@code ConnectionFactory.class}).
     */
    Class<?> primaryBeanClass();

    /**
     * Additional alias names for the default bean (e.g., {@code "jmsConnectionFactory"}).
     * These are registered alongside the default bean to prevent framework auto-configs
     * from creating redundant beans.
     *
     * @return list of alias names; empty list by default
     */
    default List<String> defaultBeanAliases() {
        return Collections.emptyList();
    }

    /**
     * Translates forage properties into runtime-native properties (for Quarkus).
     *
     * <p>When {@code prefix} is {@code null}, the descriptor should use its own default
     * Quarkus prefix (e.g., {@code "dataSource"} for JDBC, {@code "<default>"} for JMS).
     *
     * @param prefix the configuration prefix, or {@code null} for default configuration
     * @param config the loaded configuration
     * @return a map of Quarkus property name to value; empty map if no translation needed
     */
    default Map<String, String> translateProperties(String prefix, C config) {
        return Collections.emptyMap();
    }

    /**
     * Translates forage properties for export-time use. By default delegates to
     * {@link #translateProperties(String, Config)}, but subclasses can override to skip
     * runtime-only checks (e.g., classpath verification) that don't apply at export time.
     */
    default Map<String, String> translatePropertiesForExport(String prefix, C config) {
        return translateProperties(prefix, config);
    }

    /**
     * Returns auxiliary bean descriptors (e.g., aggregation repos, idempotent repos)
     * that should be created alongside the primary bean for the given prefix.
     *
     * @param prefix the configuration prefix
     * @return list of auxiliary bean descriptors; empty list if none
     */
    default List<AuxiliaryBeanDescriptor> auxiliaryBeans(String prefix) {
        return Collections.emptyList();
    }

    /**
     * Whether transactions are enabled for the given configuration.
     */
    boolean transactionEnabled(C config);
}
