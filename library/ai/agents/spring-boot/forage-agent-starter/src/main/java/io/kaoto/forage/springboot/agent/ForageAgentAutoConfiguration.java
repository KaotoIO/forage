package io.kaoto.forage.springboot.agent;

import java.util.Set;
import org.apache.camel.component.langchain4j.agent.api.Agent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import io.kaoto.forage.agent.AgentConfig;
import io.kaoto.forage.agent.AgentCreator;
import io.kaoto.forage.core.annotations.FactoryType;
import io.kaoto.forage.core.annotations.FactoryVariant;
import io.kaoto.forage.core.annotations.ForageFactory;
import io.kaoto.forage.core.util.config.ConfigHelper;
import io.kaoto.forage.springboot.common.SpringPropertyHelper;

/**
 * Auto-configuration for Forage Agent creation in Spring Boot.
 * Creates Agent beans directly using Forage ServiceLoader providers,
 * supporting both single and multi-instance (prefixed) configurations.
 *
 * @since 1.1
 */
@ForageFactory(
        value = "Agent (Spring Boot)",
        components = {"camel-langchain4j-agent"},
        description = "Auto-configured AI Agent for Spring Boot with ServiceLoader-based model discovery",
        type = FactoryType.AGENT,
        autowired = true,
        configClass = AgentConfig.class,
        variant = FactoryVariant.SPRING_BOOT)
@AutoConfiguration
public class ForageAgentAutoConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(ForageAgentAutoConfiguration.class);

    @Bean
    static ForageAgentBeanRegistryPostProcessor forageAgentBeanRegistryPostProcessor(Environment environment) {
        return new ForageAgentBeanRegistryPostProcessor(environment);
    }

    static class ForageAgentBeanRegistryPostProcessor implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {

        private Environment environment;

        ForageAgentBeanRegistryPostProcessor(Environment environment) {
            this.environment = environment;
        }

        @Override
        public void setEnvironment(Environment environment) {
            this.environment = environment;
        }

        @Override
        public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
            Set<String> prefixes =
                    SpringPropertyHelper.discoverPrefixes(environment, ConfigHelper.getNamedPropertyRegexp("agent"));

            if (!prefixes.isEmpty()) {
                LOG.info("Discovered Forage agent configuration prefixes: {}", prefixes);
                for (String name : prefixes) {
                    registerAgentBean(registry, name, name);
                }
            } else if (SpringPropertyHelper.hasProperties(
                    environment, ConfigHelper.getDefaultPropertyRegexp("agent"))) {
                LOG.info("Discovered default Forage agent configuration");
                registerAgentBean(registry, AgentCreator.DEFAULT_AGENT, null);
            } else {
                LOG.debug("No Forage agent configuration found");
            }
        }

        private void registerAgentBean(BeanDefinitionRegistry registry, String beanName, String prefix) {
            if (registry.containsBeanDefinition(beanName)) {
                LOG.debug("Bean '{}' already defined, skipping agent registration", beanName);
                return;
            }

            GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
            beanDefinition.setBeanClass(Agent.class);
            beanDefinition.setInstanceSupplier(() -> {
                AgentConfig config = prefix != null ? new AgentConfig(prefix) : new AgentConfig();
                ClassLoader cl = Thread.currentThread().getContextClassLoader();
                Agent agent = AgentCreator.createAgent(config, beanName, cl);
                if (agent != null) {
                    LOG.info("Created Agent bean: {}", beanName);
                }
                return agent;
            });
            registry.registerBeanDefinition(beanName, beanDefinition);
            LOG.info("Registered Agent bean definition: {}", beanName);
        }

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
            // No-op
        }
    }
}
