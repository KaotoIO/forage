package io.kaoto.forage.plugin;

import io.kaoto.forage.plugin.CamelCompatibility.VersionRange;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CamelCompatibilityTest {

    @Test
    void camelVersionIsInsideSupportedRange() {
        // fails when camel.version is bumped without updating camel.jbang.plugin.camel-versions in the plugin POM
        String range = ExportHelper.getSupportedCamelVersions();
        String camelVersion = ExportHelper.getCamelVersion();

        assertThat(range).isNotNull().doesNotContain("@");
        assertThat(VersionRange.parse(range).contains(camelVersion))
                .as("camel.version %s must be inside camel.jbang.plugin.camel-versions %s", camelVersion, range)
                .isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "'[4.23.0,4.24.0)', 4.23.0, true",
        "'[4.23.0,4.24.0)', 4.23.1, true",
        "'[4.23.0,4.24.0)', 4.23.0-SNAPSHOT, true",
        "'[4.23.0,4.24.0)', 4.22.2, false",
        "'[4.23.0,4.24.0)', 4.24.0, false",
        "'[4.23.0,4.24.0]', 4.24.0, true",
        "'(4.23.0,4.24.0)', 4.23.0, false",
        "'[4.18.0,)', 5.0.0, true",
        "'(,4.20.0)', 4.18.5, true",
        "'[4.22.2]', 4.22.2, true",
        "'[4.22.2]', 4.22.1, false"
    })
    void rangeContains(String range, String version, boolean expected) {
        assertThat(VersionRange.parse(range).contains(version)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"4.23.0", "4.23.0,4.24.0", "[4.23.0,4.24.0", "(4.22.2)"})
    void rejectsInvalidRange(String range) {
        assertThatThrownBy(() -> VersionRange.parse(range)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void warnsWhenCliIsOutsideRange() {
        assertThat(CamelCompatibility.warning("1.7.0", "[4.23.0,4.24.0)", "4.22.2"))
                .hasValueSatisfying(message -> assertThat(message)
                        .contains("Forage 1.7.0 supports Camel [4.23.0,4.24.0)")
                        .contains("this Camel CLI is 4.22.2")
                        .contains("camel plugin add forage --version")
                        .contains(CamelCompatibility.DOCS_URL));
    }

    @Test
    void noWarningInsideRangeOrWhenUnknown() {
        assertThat(CamelCompatibility.warning("1.7.0", "[4.23.0,4.24.0)", "4.23.0"))
                .isEmpty();
        assertThat(CamelCompatibility.warning("1.7.0", null, "4.22.2")).isEmpty();
        assertThat(CamelCompatibility.warning("1.7.0", "[4.23.0,4.24.0)", null)).isEmpty();
    }
}
