package io.kaoto.forage.plugin.config;

import java.lang.reflect.Field;

/**
 * Reflection helpers shared by the {@code config} command tests.
 */
final class ConfigCommandTestSupport {

    private ConfigCommandTestSupport() {}

    /**
     * Sets the {@code --strategy} value of a {@code config} command.
     */
    static void setStrategy(Object command, PropertiesFileStrategyType type) throws Exception {
        setStrategy(command, type.id());
    }

    /**
     * Sets the raw {@code --strategy} value of a {@code config} command, as picocli would.
     */
    static void setStrategy(Object command, String strategy) throws Exception {
        strategyOption(command).strategy = strategy;
    }

    /**
     * Returns the raw {@code --strategy} value of a {@code config} command.
     */
    static String getStrategy(Object command) throws Exception {
        return strategyOption(command).strategy;
    }

    /**
     * Sets a private field on the command object.
     */
    static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static PropertiesFileStrategyOption strategyOption(Object command) throws Exception {
        Field field = AbstractConfigCommand.class.getDeclaredField("strategyOption");
        field.setAccessible(true);
        return (PropertiesFileStrategyOption) field.get(command);
    }
}
