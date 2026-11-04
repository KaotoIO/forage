package io.kaoto.forage.policy.factory;

import org.apache.camel.CamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.core.annotations.FactoryType;
import io.kaoto.forage.core.annotations.ForageFactory;
import io.kaoto.forage.core.common.BeanFactory;

/**
 * Registers {@link DefaultCamelForageRoutePolicyFactory} on the CamelContext, so that each route gets the
 * policies listed in {@code forage.route.policy.<routeId>.name}.
 *
 * <p>The policy implementations (flip, schedule) are {@code RoutePolicyProvider} beans in the
 * "Route Policy" feature of this factory. The factory works the same on every runtime, so it has no
 * Spring Boot or Quarkus variant.
 */
@ForageFactory(
        value = "Route Policy",
        components = {"camel-core"},
        description =
                "Applies route policies, such as flip or schedule, to the routes configured in forage.route.policy properties",
        type = FactoryType.ROUTE_POLICY,
        autowired = true,
        namedInstances = false,
        configClass = RoutePolicyFactoryConfig.class)
public class DefaultCamelForageRoutePolicyFactoryBean implements BeanFactory {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultCamelForageRoutePolicyFactoryBean.class);

    private CamelContext camelContext;

    @Override
    public void configure() {
        LOG.warn("Creating route policy factory");
        RoutePolicyFactoryConfig config = new RoutePolicyFactoryConfig();

        if (!config.isEnabled()) {
            LOG.info("Route policy factory is disabled via configuration");
            return;
        }

        DefaultCamelForageRoutePolicyFactory routePolicyFactory = new DefaultCamelForageRoutePolicyFactory(config);
        camelContext.addRoutePolicyFactory(routePolicyFactory);
        LOG.debug("Route policy factory registered with CamelContext");
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }
}
