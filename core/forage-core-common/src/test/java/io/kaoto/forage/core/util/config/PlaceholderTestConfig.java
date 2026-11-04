package io.kaoto.forage.core.util.config;

import java.util.Optional;

/**
 * Test config with values that may contain placeholders.
 */
public class PlaceholderTestConfig extends AbstractConfig {

    public PlaceholderTestConfig() {
        this(null);
    }

    public PlaceholderTestConfig(String prefix) {
        super(prefix, PlaceholderTestConfigEntries.class);
    }

    @Override
    public String name() {
        return "forage-placeholder-test";
    }

    public Optional<String> password() {
        return get(PlaceholderTestConfigEntries.PASSWORD);
    }

    public Optional<String> prompt() {
        return get(PlaceholderTestConfigEntries.PROMPT);
    }

    public static final class PlaceholderTestConfigEntries extends ConfigEntries {
        public static final ConfigModule PASSWORD =
                ConfigModule.of(PlaceholderTestConfig.class, "forage.placeholdertest.password");
        public static final ConfigModule PROMPT =
                ConfigModule.of(PlaceholderTestConfig.class, "forage.placeholdertest.prompt");

        static {
            initModules(PlaceholderTestConfigEntries.class, PASSWORD, PROMPT);
        }
    }
}
