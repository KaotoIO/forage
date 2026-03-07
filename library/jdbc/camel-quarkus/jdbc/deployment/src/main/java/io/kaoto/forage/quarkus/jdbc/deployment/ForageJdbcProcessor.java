package io.kaoto.forage.quarkus.jdbc.deployment;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.camel.processor.aggregate.jdbc.JdbcAggregationRepository;
import org.apache.camel.processor.idempotent.jdbc.JdbcMessageIdRepository;
import org.apache.camel.quarkus.core.deployment.spi.CamelContextBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelRuntimeBeanBuildItem;
import org.jboss.logging.Logger;
import io.kaoto.forage.core.annotations.FactoryType;
import io.kaoto.forage.core.annotations.FactoryVariant;
import io.kaoto.forage.core.annotations.ForageFactory;
import io.kaoto.forage.core.util.config.ConfigHelper;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.jdbc.common.DataSourceFactoryConfig;
import io.kaoto.forage.jdbc.common.JdbcModuleDescriptor;
import io.kaoto.forage.quarkus.jdbc.ForageJdbcRecorder;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.runtime.RuntimeValue;

import org.junit.platform.commons.util.StringUtils;

@ForageFactory(
        value = "DataSource (Quarkus)",
        components = {"camel-sql", "camel-jdbc"},
        description = "Native JDBC DataSource for Quarkus with compile-time optimization and repository support",
        type = FactoryType.DATA_SOURCE,
        autowired = true,
        configClass = DataSourceFactoryConfig.class,
        variant = FactoryVariant.QUARKUS,
        runtimeDependencies = {"mvn:org.apache.camel.quarkus:camel-quarkus-sql"})
public class ForageJdbcProcessor {

    private static final Logger LOG = Logger.getLogger(ForageJdbcProcessor.class);
    private static final String FEATURE = "forage-jdbc";
    private static final JdbcModuleDescriptor DESCRIPTOR = new JdbcModuleDescriptor();

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * Discovers Forage JDBC prefixes using the {@link JdbcModuleDescriptor} and produces
     * {@link ForageDataSourceBuildItem}s for consumption by subsequent build steps.
     */
    @BuildStep
    void discoverDataSources(BuildProducer<ForageDataSourceBuildItem> dataSources) {
        DataSourceFactoryConfig defaultConfig = DESCRIPTOR.createConfig(null);
        Set<String> prefixes = ConfigStore.getInstance()
                .readPrefixes(defaultConfig, ConfigHelper.getNamedPropertyRegexp(DESCRIPTOR.modulePrefix()));

        Map<String, DataSourceFactoryConfig> configs = prefixes.isEmpty()
                ? Collections.singletonMap(DESCRIPTOR.defaultBeanName(), DESCRIPTOR.createConfig(null))
                : prefixes.stream().collect(Collectors.toMap(n -> n, DESCRIPTOR::createConfig));

        for (Map.Entry<String, DataSourceFactoryConfig> entry : configs.entrySet()) {
            dataSources.produce(new ForageDataSourceBuildItem(entry.getKey(), entry.getValue()));
        }
    }

    /**
     * Registers aggregation and idempotent repository beans using discovered
     * {@link ForageDataSourceBuildItem}s instead of reading ConfigStore directly.
     */
    @BuildStep
    @Record(value = ExecutionTime.STATIC_INIT)
    void registerRepositories(
            CamelContextBuildItem context,
            ForageJdbcRecorder recorder,
            List<ForageDataSourceBuildItem> dataSources,
            BuildProducer<CamelRuntimeBeanBuildItem> beans) {

        for (ForageDataSourceBuildItem ds : dataSources) {
            String name = ds.getName();
            DataSourceFactoryConfig dsConfig = ds.getConfig();

            if (StringUtils.isNotBlank(dsConfig.aggregationRepositoryName())) {
                RuntimeValue<JdbcAggregationRepository> aggRepo =
                        recorder.createAggregationRepository(name, context.getCamelContext(), dsConfig);
                if (aggRepo != null) {
                    beans.produce(new CamelRuntimeBeanBuildItem(
                            dsConfig.aggregationRepositoryName(), JdbcAggregationRepository.class.getName(), aggRepo));
                }
            } else {
                logMissingMandatoryProperty(
                        "aggregation",
                        dsConfig,
                        "Aggregation name has to be provided in order to create aggregation repositories (`%s` is already provided)");
            }

            if (dsConfig.enableIdempotentRepository()) {
                if (StringUtils.isNotBlank(dsConfig.idempotentRepositoryTableName())) {
                    RuntimeValue<JdbcMessageIdRepository> idRepo =
                            recorder.createIdempotentRepository(name, context.getCamelContext(), dsConfig);
                    if (idRepo != null) {
                        beans.produce(new CamelRuntimeBeanBuildItem(
                                dsConfig.idempotentRepositoryTableName(),
                                JdbcMessageIdRepository.class.getName(),
                                idRepo));
                    }
                } else {
                    logMissingMandatoryProperty(
                            "idempotent",
                            dsConfig,
                            "Idempotent repository table name has to be provided in order to create idempotent repository (`%s` is already provided)");
                }
            }
        }
    }

    private static void logMissingMandatoryProperty(String keyword, DataSourceFactoryConfig config, String warnMsg) {
        ConfigHelper.getGetterMethods(DataSourceFactoryConfig.class).stream()
                .filter(m -> m.getName().toLowerCase().contains(keyword))
                .forEach(m -> {
                    try {
                        Object value = m.invoke(config);
                        if (value != null && StringUtils.isNotBlank(value.toString())) {
                            LOG.warn(warnMsg.formatted(m.getName()));
                        }
                    } catch (Exception e) {
                        LOG.trace("Error invoking config method", e);
                    }
                });
    }
}
