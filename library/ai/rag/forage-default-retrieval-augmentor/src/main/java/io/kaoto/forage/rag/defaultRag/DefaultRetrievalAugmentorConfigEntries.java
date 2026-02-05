package io.kaoto.forage.rag.defaultRag;

import io.kaoto.forage.core.util.config.ConfigEntries;
import io.kaoto.forage.core.util.config.ConfigEntry;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigTag;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultRetrievalAugmentorConfigEntries extends ConfigEntries {
    public static final ConfigModule MAX_RESULTS = ConfigModule.of(
            DefaultRetrievalAugmentorConfig.class,
            "forage.rag.max.results",
            "The maximum number of Contents to retrieve.",
            "Max results",
            null,
            "int",
            false,
            ConfigTag.COMMON);
    public static final ConfigModule MIN_SCORE = ConfigModule.of(
            DefaultRetrievalAugmentorConfig.class,
            "forage.rag.min.score",
            "The minimum relevance score for the returned Contents.",
            "Min score",
            null,
            "double",
            false,
            ConfigTag.COMMON);
    private static final Map<ConfigModule, ConfigEntry> CONFIG_MODULES = new ConcurrentHashMap<>();

    static {
        init();
    }

    static void init() {
        CONFIG_MODULES.put(MAX_RESULTS, ConfigEntry.fromModule());
        CONFIG_MODULES.put(MIN_SCORE, ConfigEntry.fromModule());
    }

    public static Map<ConfigModule, ConfigEntry> entries() {
        return Collections.unmodifiableMap(CONFIG_MODULES);
    }

    public static Optional<ConfigModule> find(String prefix, String name) {
        return find(CONFIG_MODULES, prefix, name);
    }

    /**
     * Registers new known configuration if a prefix is provided (otherwise is ignored)
     * @param prefix the prefix to register
     */
    public static void register(String prefix) {
        if (prefix != null) {
            for (Map.Entry<ConfigModule, ConfigEntry> entry : entries().entrySet()) {
                ConfigModule configModule = entry.getKey().asNamed(prefix);
                CONFIG_MODULES.put(configModule, ConfigEntry.fromModule());
            }
        }
    }

    /**
     * Load override configurations (which are defined via environment variables and/or system properties)
     * @param prefix and optional prefix to use
     */
    public static void loadOverrides(String prefix) {
        load(CONFIG_MODULES, prefix);
    }
}
