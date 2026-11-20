package io.kaoto.forage.core.common;

import java.util.Map;
import java.util.Set;
import io.kaoto.forage.core.util.config.Config;

/**
 * Opts a Forage module in to {@code camel export --runtime=quarkus} as {@code quarkus.*} properties. Register
 * implementations in {@code META-INF/services}; modules without one stay {@code forage.*}.
 */
public interface QuarkusConfigHook<C extends Config> {

    ForageModuleDescriptor<C, ?> descriptor();

    /**
     * The keys, after {@code forage.[<name>.]<module>.}, that {@code translateProperties} fully covers ({@code x.}
     * covers all {@code x.*}). Any other key present keeps the module on forage.*, so no configuration is dropped.
     */
    Set<String> covered();

    /** Why the configuration ({@code name} is {@code null} for the default one) stays forage.*, or {@code null}. */
    default String unsupported(String name, C config) {
        return null;
    }

    /**
     * The translated keys ({@code x} matches keys ending with {@code .x}) the module's dependencies are chosen from.
     * When {@code application.properties} sets one to another value, the configuration stays forage.*.
     */
    default Set<String> dependencyKeys() {
        return Set.of();
    }

    /** Adjusts the translated properties and returns the catalog groups they enable. */
    default Set<String> adjust(C config, Map<String, String> translated) {
        return Set.of();
    }
}
