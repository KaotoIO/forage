package io.kaoto.forage.core.util.config;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaceholderResolverFunctionsTest {

    private static final String KEY = "forage.myDb.jdbc.password";
    private static final String UNSET_ENV = "{{env:FORAGE_TEST_NONEXISTENT_12345}}";

    /** Resolves {{vault:x}} to "resolved-x". */
    private static final PlaceholderFunctionResolver VAULT = (propertyName, function, remainder) ->
            "vault".equals(function) ? Optional.of("resolved-" + remainder) : Optional.empty();

    @Test
    void withoutResolverOnlyEnvAndSysAreResolved() {
        assertThat(PlaceholderResolver.currentFunctionResolver()).isNull();

        assertThat(PlaceholderResolver.resolveFunctions(KEY, "{{secret:db/password}}"))
                .isEqualTo("{{secret:db/password}}");
        assertThat(PlaceholderResolver.resolveFunctions(KEY, UNSET_ENV)).isEqualTo(UNSET_ENV);
        assertThat(PlaceholderResolver.resolveFunctions(KEY, "{{env:FORAGE_TEST_NONEXISTENT_12345:fallback}}"))
                .isEqualTo("fallback");
        assertThat(PlaceholderResolver.resolveFunctions(KEY, "{{sys:user.name}}"))
                .isEqualTo(System.getProperty("user.name"));
    }

    @Test
    void resolverResolvesFunctionsAndLeavesPlainPlaceholders() {
        String value = "a={{vault:x}} b={{name}} c={{db.password:changeme}} d={{unknown:y}} e={{sys:user.name}}";

        String resolved =
                PlaceholderResolver.withFunctionResolver(VAULT, () -> PlaceholderResolver.resolveFunctions(KEY, value));

        assertThat(resolved)
                .isEqualTo("a=resolved-x b={{name}} c={{db.password:changeme}} d={{unknown:y}} e="
                        + System.getProperty("user.name"));
    }

    @Test
    void resolvedValuesAreNotResolvedAgain() {
        PlaceholderFunctionResolver echo =
                (propertyName, function, remainder) -> Optional.of("{{vault:" + remainder + "}}");

        String resolved = PlaceholderResolver.withFunctionResolver(
                echo, () -> PlaceholderResolver.resolveFunctions(KEY, "{{vault:x}}"));

        assertThat(resolved).isEqualTo("{{vault:x}}");
    }

    @Test
    void unsetEnvironmentVariableIsLeftUnchangedAsToday() {
        // A getter may be read for a value the bean does not use (e.g. an API key with Ollama)
        String value = "a" + UNSET_ENV + "{{vault:x}}";

        String resolved =
                PlaceholderResolver.withFunctionResolver(VAULT, () -> PlaceholderResolver.resolveFunctions(KEY, value));

        assertThat(resolved).isEqualTo("a" + UNSET_ENV + "resolved-x");
    }

    @Test
    void scopedResolverTakesPrecedenceOverInstalledOne() {
        PlaceholderFunctionResolver installed = (propertyName, function, remainder) -> Optional.of("installed");
        PlaceholderResolver.install(installed);
        try {
            assertThat(PlaceholderResolver.resolveFunctions(KEY, "{{vault:x}}")).isEqualTo("installed");
            assertThat(PlaceholderResolver.withFunctionResolver(
                            VAULT, () -> PlaceholderResolver.resolveFunctions(KEY, "{{vault:x}}")))
                    .isEqualTo("resolved-x");
        } finally {
            assertThat(PlaceholderResolver.uninstall(installed)).isTrue();
        }
        assertThat(PlaceholderResolver.uninstall(installed)).isFalse();
    }

    @Test
    void quarkusResolverRejectsSecretStoreFunctionsOnly() {
        PlaceholderFunctionResolver quarkus = UnsupportedPlaceholderFunctionResolver.QUARKUS;

        assertThatThrownBy(() -> PlaceholderResolver.withFunctionResolver(
                        quarkus, () -> PlaceholderResolver.resolveFunctions(KEY, "{{secret:db/password}}")))
                .isInstanceOf(UnresolvedPlaceholderException.class)
                .hasMessageContaining(KEY)
                .hasMessageContaining("Quarkus")
                .hasMessageNotContaining("db/password");

        // Unset variables may only be set at runtime (translation also runs at build time)
        assertThat(PlaceholderResolver.withFunctionResolver(
                        quarkus, () -> PlaceholderResolver.resolveFunctions(KEY, UNSET_ENV + "{{name}}")))
                .isEqualTo(UNSET_ENV + "{{name}}");
    }
}
