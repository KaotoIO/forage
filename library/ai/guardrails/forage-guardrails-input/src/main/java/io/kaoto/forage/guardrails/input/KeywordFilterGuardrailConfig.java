package io.kaoto.forage.guardrails.input;

import static io.kaoto.forage.guardrails.input.KeywordFilterGuardrailConfigEntries.*;

import io.kaoto.forage.core.util.config.Config;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigStore;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Configuration class for keyword filter guardrail.
 */
public class KeywordFilterGuardrailConfig implements Config {

    private final String prefix;

    public KeywordFilterGuardrailConfig() {
        this(null);
    }

    public KeywordFilterGuardrailConfig(String prefix) {
        this.prefix = prefix;

        KeywordFilterGuardrailConfigEntries.register(prefix);
        ConfigStore.getInstance().load(KeywordFilterGuardrailConfig.class, this, this::register);
        KeywordFilterGuardrailConfigEntries.loadOverrides(prefix);
    }

    @Override
    public void register(String name, String value) {
        Optional<ConfigModule> config = KeywordFilterGuardrailConfigEntries.find(prefix, name);
        config.ifPresent(module -> ConfigStore.getInstance().set(module, value));
    }

    @Override
    public String name() {
        return "forage-guardrail-keyword-filter";
    }

    public Set<String> blockedWords() {
        ConfigModule module = BLOCKED_WORDS.asNamed(prefix);
        Optional<String> value = ConfigStore.getInstance().get(module);
        return value.map(s -> {
                    Set<String> words = new HashSet<>();
                    Arrays.stream(s.split(","))
                            .map(String::trim)
                            .filter(w -> !w.isEmpty())
                            .forEach(words::add);
                    return words;
                })
                .orElse(new HashSet<>());
    }

    public boolean caseSensitive() {
        return ConfigStore.getInstance()
                .get(CASE_SENSITIVE.asNamed(prefix))
                .map(Boolean::parseBoolean)
                .orElse(false);
    }

    public boolean wholeWordMatch() {
        return ConfigStore.getInstance()
                .get(WHOLE_WORD_MATCH.asNamed(prefix))
                .map(Boolean::parseBoolean)
                .orElse(true);
    }
}
