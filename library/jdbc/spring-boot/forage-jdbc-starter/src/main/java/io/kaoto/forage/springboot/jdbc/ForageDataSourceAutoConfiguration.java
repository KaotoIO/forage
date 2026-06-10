package io.kaoto.forage.springboot.jdbc;

import javax.sql.DataSource;

import java.util.List;
import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import io.agroal.api.AgroalDataSource;
import io.agroal.springframework.boot.AgroalDataSourceAutoConfiguration;
import io.kaoto.forage.core.annotations.FactoryType;
import io.kaoto.forage.core.annotations.FactoryVariant;
import io.kaoto.forage.core.annotations.ForageFactory;
import io.kaoto.forage.core.jdbc.DataSourceProvider;
import io.kaoto.forage.jdbc.common.DataSourceFactoryConfig;
import io.kaoto.forage.jdbc.common.ForageDataSource;
import io.kaoto.forage.jdbc.common.JdbcModuleDescriptor;
import io.kaoto.forage.jdbc.common.idempotent.ForageIdRepository;
import io.kaoto.forage.springboot.common.ForageSpringBootModuleAdapter;

/**
 * Auto-configuration for Forage DataSource creation using ServiceLoader discovery.
 * Automatically creates DataSource beans from JDBC configuration properties,
 * supporting both single and multi-instance (prefixed) configurations.
 *
 * <p>Named/prefixed datasources (e.g., {@code forage.ds1.jdbc.url}) are registered dynamically
 * by {@link ForageSpringBootModuleAdapter} using the {@link JdbcModuleDescriptor}.
 *
 * <p>This configuration class handles:
 * <ul>
 *   <li>The {@link ForageSpringBootModuleAdapter} bean for dynamic registration</li>
 *   <li>Fallback single-provider DataSource when no prefixed configurations are found</li>
 * </ul>
 *
 * <p>Transaction management (JTA transaction manager, Camel transaction policies, and
 * {@code @EnableTransactionManagement}) is provided by
 * {@code io.kaoto.forage.springboot.jdbc.jta.ForageJdbcTransactionManagementAutoConfiguration},
 * which activates when any default or prefixed {@code forage[.<name>].jdbc.transaction.enabled=true}
 * property is present.
 */
@ForageFactory(
        value = "DataSource (Spring Boot)",
        components = {"camel-sql", "camel-jdbc", "camel-spring-jdbc"},
        description =
                "Auto-configured JDBC DataSource for Spring Boot with transaction management and repository support",
        type = FactoryType.DATA_SOURCE,
        autowired = true,
        configClass = DataSourceFactoryConfig.class,
        variant = FactoryVariant.SPRING_BOOT)
@AutoConfiguration(
        before = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            AgroalDataSourceAutoConfiguration.class
        })
@Import(ForageJdbcBeanRegistrar.class)
public class ForageDataSourceAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ForageDataSourceAutoConfiguration.class);

    /**
     * Registers the generic module adapter that discovers prefixed DataSource
     * configurations and registers them as proper bean definitions using the
     * {@link JdbcModuleDescriptor}.
     */
    @Bean
    static ForageSpringBootModuleAdapter<DataSourceFactoryConfig, DataSourceProvider> forageJdbcModuleAdapter(
            Environment environment) {
        return new ForageSpringBootModuleAdapter<>(new JdbcModuleDescriptor(), environment);
    }

    /**
     * Fallback DataSource bean created when exactly one DataSourceProvider is on the classpath
     * and no named/prefixed configurations are found.
     *
     * <p>This bean is only registered when:
     * <ul>
     *   <li>No "dataSource" bean already exists (e.g., from the module adapter's prefix discovery)</li>
     *   <li>Exactly one {@link DataSourceProvider} is on the classpath (via {@link SingleDataSourceProviderCondition})</li>
     * </ul>
     *
     * <p>When multiple providers are present (e.g., postgresql + mysql), named/prefixed configurations
     * must be used instead, and this fallback is skipped to avoid registering a null bean that would
     * break Spring Boot Actuator's health checks.
     */
    @Bean("dataSource")
    @ConditionalOnMissingBean(name = "dataSource")
    @Conditional(SingleDataSourceProviderCondition.class)
    @ConditionalOnProperty(prefix = "forage.jdbc", name = "db.kind")
    public DataSource forageDefaultDataSource() {
        List<ServiceLoader.Provider<DataSourceProvider>> providers =
                ServiceLoader.load(DataSourceProvider.class).stream().toList();

        log.info(
                "Creating default DataSource using single provider: {}",
                providers.get(0).type().getName());
        DataSourceProvider dsProvider = providers.get(0).get();
        AgroalDataSource dataSource = (AgroalDataSource) dsProvider.create(null);

        ForageIdRepository forageIdRepository = null;
        if (dsProvider instanceof ForageIdRepository forageIdRepo) {
            forageIdRepository = forageIdRepo;
        }

        ForageDataSource forageDataSource = new ForageDataSource(dataSource, forageIdRepository);

        log.info("Registered default DataSource bean");
        return forageDataSource.dataSource();
    }
}
