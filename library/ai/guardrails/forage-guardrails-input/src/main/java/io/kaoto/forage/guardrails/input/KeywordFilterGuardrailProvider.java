package io.kaoto.forage.guardrails.input;

import dev.langchain4j.guardrail.InputGuardrail;
import io.kaoto.forage.core.annotations.ForageBean;
import io.kaoto.forage.core.guardrails.InputGuardrailProvider;
import java.util.Set;
import org.apache.camel.component.langchain4j.agent.api.guardrails.KeywordFilterGuardrail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provider for creating KeywordFilterGuardrail instances.
 *
 * <p>This guardrail blocks messages containing specific keywords or phrases.
 * Useful for filtering profanity, inappropriate content, or banned terms.
 *
 * <p>Configuration:
 * <ul>
 *   <li>forage.guardrail.keyword.filter.blocked.words - Comma-separated list of blocked words</li>
 *   <li>forage.guardrail.keyword.filter.case.sensitive - Case-sensitive matching (default: false)</li>
 *   <li>forage.guardrail.keyword.filter.whole.word.match - Match whole words only (default: true)</li>
 * </ul>
 */
@ForageBean(
        value = "keyword-filter",
        components = {"camel-langchain4j-agent"},
        feature = "Input Guardrail",
        description = "Blocks messages containing specific keywords or phrases")
public class KeywordFilterGuardrailProvider implements InputGuardrailProvider {

    private static final Logger LOG = LoggerFactory.getLogger(KeywordFilterGuardrailProvider.class);

    @Override
    public InputGuardrail create(String id) {
        final KeywordFilterGuardrailConfig config = new KeywordFilterGuardrailConfig(id);

        Set<String> blockedWords = config.blockedWords();
        boolean caseSensitive = config.caseSensitive();
        boolean wholeWordMatch = config.wholeWordMatch();

        LOG.trace(
                "Creating KeywordFilterGuardrail with id={}, blockedWords={}, caseSensitive={}, wholeWordMatch={}",
                id,
                blockedWords,
                caseSensitive,
                wholeWordMatch);

        if (blockedWords.isEmpty()) {
            LOG.warn("KeywordFilterGuardrail has no blocked words configured for id={}, guardrail will have no effect", id);
        }

        return KeywordFilterGuardrail.builder()
                .blockedWords(blockedWords)
                .caseSensitive(caseSensitive)
                .wholeWordMatch(wholeWordMatch)
                .build();
    }
}
