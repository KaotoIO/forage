package io.kaoto.forage.vectordb.inmemory;

import io.kaoto.forage.core.util.config.ConfigEntries;
import io.kaoto.forage.core.util.config.ConfigEntry;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigTag;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryStoreConfigEntries extends ConfigEntries {
    public static final ConfigModule FILE_SOURCE = ConfigModule.of(
            InMemoryStoreConfig.class,
            "forage.in.memory.store.file.source",
            "Path to a file to be loaded into store.",
            "File source",
            null,
            "string",
            true,
            ConfigTag.COMMON);

    public static final ConfigModule MAX_SIZE = ConfigModule.of(
            InMemoryStoreConfig.class,
            "forage.in.memory.store.max.size",
            "The maximum size of the segment, defined in characters.",
            "Max size",
            null,
            "int",
            false,
            ConfigTag.COMMON);

    public static final ConfigModule OVERLAP_SIZE = ConfigModule.of(
            InMemoryStoreConfig.class,
            "forage.in.memory.store.overlap.size",
            "The maximum size of the overlap, defined in characters.",
            "Overlap size",
            null,
            "int",
            false,
            ConfigTag.COMMON);

    private static final Map<ConfigModule, ConfigEntry> CONFIG_MODULES = new ConcurrentHashMap<>();

    static {
        init();
    }

    static void init() {
        CONFIG_MODULES.put(FILE_SOURCE, ConfigEntry.fromModule());
        CONFIG_MODULES.put(MAX_SIZE, ConfigEntry.fromModule());
        CONFIG_MODULES.put(OVERLAP_SIZE, ConfigEntry.fromModule());
    }

    public static Map<ConfigModule, ConfigEntry> entries() {
        return Collections.unmodifiableMap(CONFIG_MODULES);
    }

    public static Optional<ConfigModule> find(String prefix, String name) {
        return find(CONFIG_MODULES, prefix, name);
    }

    public static void register(String prefix) {
        if (prefix != null) {
            for (Map.Entry<ConfigModule, ConfigEntry> entry : entries().entrySet()) {
                ConfigModule configModule = entry.getKey().asNamed(prefix);
                CONFIG_MODULES.put(configModule, ConfigEntry.fromModule());
            }
        }
    }

    public static void loadOverrides(String prefix) {
        load(CONFIG_MODULES, prefix);
    }
}
