package io.kaoto.forage.springboot.common;

import java.lang.reflect.Constructor;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;
import io.kaoto.forage.core.util.config.Config;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigStore;

/**
 * Condition that matches when a specific ConfigModule property matches the expected criteria.
 *
 * @deprecated Use standard {@code @ConditionalOnProperty} instead. See {@link ConditionalOnForageProperty}.
 * This condition leverages the existing ConfigModule and ConfigStore infrastructure to provide
 * type-safe property access without reflection.
 *
 * <p>The condition logic:
 * <ul>
 * <li>If a specific prefix is provided, the ConfigModule is converted to use that prefix</li>
 * <li>If no prefix is provided and checkAllConfigurations=true, checks all available configurations</li>
 * <li>If no prefix is provided and checkAllConfigurations=false, checks only default configuration</li>
 * <li>Uses ConfigStore.get() to retrieve values directly without reflection</li>
 * <li>Compares the result with the expected value or checks for existence if no value specified</li>
 * </ul>
 *
 * @see ConditionalOnForageProperty
 * @see io.kaoto.forage.core.util.config.ConfigModule
 * @see io.kaoto.forage.core.util.config.ConfigStore
 */
@Deprecated(since = "1.1", forRemoval = true)
public class ForagePropertyCondition extends SpringBootCondition {

    private static final Logger log = LoggerFactory.getLogger(ForagePropertyCondition.class);
    private static final String PROPERTY_PREFIX = PROPERTY_PREFIX;

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(
                metadata.getAnnotationAttributes(ConditionalOnForageProperty.class.getName()));

        if (attributes == null) {
            return ConditionOutcome.noMatch(ConditionMessage.forCondition(ConditionalOnForageProperty.class)
                    .because("annotation not found"));
        }

        Class<? extends Config> configClass = attributes.getClass("configClass");
        String property = attributes.getString("property");
        String havingValue = attributes.getString("havingValue");
        boolean matchIfMissing = attributes.getBoolean("matchIfMissing");

        if (configClass == null || !StringUtils.hasText(property)) {
            return ConditionOutcome.noMatch(ConditionMessage.forCondition(ConditionalOnForageProperty.class)
                    .because("configClass and property must be specified"));
        }

        boolean hasExpectedValue = StringUtils.hasText(havingValue);

        try {
            // This is pretty early in the life cycle, the Config class may not be initialized
            configClass.getDeclaredConstructor().newInstance();
            // Create the ConfigModule from class and property name
            ConfigModule configModule = ConfigModule.of(configClass, property);

            return checkAllAvailableConfigurations(configModule, havingValue, hasExpectedValue, matchIfMissing);
        } catch (Exception e) {
            log.warn("Error evaluating ForagePropertyCondition for {}.{}", configClass.getSimpleName(), property, e);
            return ConditionOutcome.noMatch(ConditionMessage.forCondition(ConditionalOnForageProperty.class)
                    .because("error occurred while checking configuration: " + e.getMessage()));
        }
    }

    private ConditionOutcome checkAllAvailableConfigurations(
            ConfigModule configModule, String havingValue, boolean hasExpectedValue, boolean matchIfMissing) {

        log.debug("Checking ConfigModule {} for any available configuration", configModule.name());

        try {
            // Check default configuration first
            String defaultPropertyValue =
                    ConfigStore.getInstance().get(configModule).orElse(null);
            boolean defaultMatches =
                    evaluatePropertyValue(defaultPropertyValue, havingValue, hasExpectedValue, matchIfMissing);

            log.debug(
                    "Default configuration ConfigModule {} = {}, matches = {}",
                    configModule.name(),
                    defaultPropertyValue,
                    defaultMatches);

            if (defaultMatches) {
                return ConditionOutcome.match(ConditionMessage.forCondition(ConditionalOnForageProperty.class)
                        .foundExactly(PROPERTY_PREFIX + configModule.name() + "' matches criteria"));
            }

            // Check prefixed configurations
            Config tempConfig = createConfigInstance(configModule.config(), null);
            Set<String> prefixes = ConfigStore.getInstance().readPrefixes(tempConfig, "(.+)\\..*");

            log.debug("Found {} prefixes for configuration checking: {}", prefixes.size(), prefixes);

            for (String prefix : prefixes) {
                try {
                    ConfigModule prefixedModule = configModule.asNamed(prefix);
                    String propertyValue =
                            ConfigStore.getInstance().get(prefixedModule).orElse(null);
                    boolean matches =
                            evaluatePropertyValue(propertyValue, havingValue, hasExpectedValue, matchIfMissing);

                    log.debug(
                            "ConfigModule {} for prefix '{}' = {}, matches = {}",
                            configModule.name(),
                            prefix,
                            propertyValue,
                            matches);

                    if (matches) {
                        return ConditionOutcome.match(ConditionMessage.forCondition(ConditionalOnForageProperty.class)
                                .foundExactly(PROPERTY_PREFIX + configModule.name() + "' matches criteria for prefix '"
                                        + prefix + "'"));
                    }
                } catch (Exception e) {
                    log.debug(
                            "Failed to check ConfigModule {} for prefix '{}': {}",
                            configModule.name(),
                            prefix,
                            e.getMessage());
                }
            }

            // No match found in any configuration
            if (matchIfMissing) {
                return ConditionOutcome.match(ConditionMessage.forCondition(ConditionalOnForageProperty.class)
                        .foundExactly(PROPERTY_PREFIX + configModule.name() + "' not found and matchIfMissing=true"));
            } else {
                return ConditionOutcome.noMatch(ConditionMessage.forCondition(ConditionalOnForageProperty.class)
                        .because(PROPERTY_PREFIX + configModule.name()
                                + "' does not match criteria in any configuration"));
            }

        } catch (Exception e) {
            log.debug("Failed to check ConfigModule {}: {}", configModule.name(), e.getMessage());

            if (matchIfMissing) {
                return ConditionOutcome.match(ConditionMessage.forCondition(ConditionalOnForageProperty.class)
                        .foundExactly("error checking configurations and matchIfMissing=true"));
            } else {
                return ConditionOutcome.noMatch(ConditionMessage.forCondition(ConditionalOnForageProperty.class)
                        .because("error checking configurations: " + e.getMessage()));
            }
        }
    }

    private Config createConfigInstance(Class<? extends Config> configClass, String prefix) throws Exception {
        if (prefix != null) {
            Constructor<? extends Config> constructor = configClass.getConstructor(String.class);
            return constructor.newInstance(prefix);
        } else {
            Constructor<? extends Config> constructor = configClass.getConstructor();
            return constructor.newInstance();
        }
    }

    private boolean evaluatePropertyValue(
            String propertyValue, String havingValue, boolean hasExpectedValue, boolean matchIfMissing) {
        if (propertyValue == null) {
            return matchIfMissing;
        }

        if (!hasExpectedValue) {
            return StringUtils.hasText(propertyValue);
        }

        boolean matches = havingValue.equals(propertyValue);
        return matchIfMissing ? !matches : matches;
    }
}
