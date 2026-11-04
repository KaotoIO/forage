package io.kaoto.forage.maven.catalog;

import java.util.List;

/**
 * Thrown when the scanned beans and factories cannot be turned into a complete catalog, for example because a
 * {@code @ForageBean} is not attached to any {@code @ForageFactory}. Kaoto and {@code camel forage run/export} only
 * see what is in the catalog, so these problems fail the build instead of silently dropping entries.
 */
public class CatalogValidationException extends Exception {

    private final List<String> problems;

    public CatalogValidationException(List<String> problems) {
        super("The Forage catalog is incomplete:\n\n" + String.join("\n\n", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> getProblems() {
        return problems;
    }
}
