package io.kaoto.forage.plugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;
import io.kaoto.forage.plugin.ForagePropertyValidator.ValidationResult;
import io.kaoto.forage.plugin.ForagePropertyValidator.ValidationWarning;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class ForagePropertyValidatorTest {

    @TempDir
    Path tempDir;

    private ForageCatalogReader catalog;

    @BeforeEach
    void setUp() {
        catalog = ForageCatalogReader.getInstance();
    }

    @Test
    void testValidProperties() throws IOException {
        // Given: valid JDBC properties
        File propsFile = createPropertiesFile(
                "application.properties", "forage.jdbc.db.kind=postgresql", "forage.jdbc.username=admin");

        // When: validating
        ValidationResult result = ForagePropertyValidator.validate(tempDir.toFile(), catalog);

        // Then: no warnings
        assertThat(result.hasWarnings()).isFalse();
        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    void testTypoInPropertyName() throws IOException {
        // Given: typo in property name (usernam instead of username)
        File propsFile = createPropertiesFile(
                "application.properties", "forage.jdbc.db.kind=postgresql", "forage.jdbc.usernam=admin" // typo here
                );

        // When: validating
        ValidationResult result = ForagePropertyValidator.validate(tempDir.toFile(), catalog);

        // Then: warning with suggestion
        assertThat(result.hasWarnings()).isTrue();
        assertThat(result.getWarnings()).hasSize(1);

        ValidationWarning warning = result.getWarnings().get(0);
        assertThat(warning.getType()).isEqualTo(ValidationWarning.Type.UNKNOWN_PROPERTY);
        assertThat(warning.getMessage()).contains("usernam");
        assertThat(warning.getMessage()).contains("Did you mean 'username'?");
    }

    @Test
    void testInvalidBeanValue() throws IOException {
        // Given: invalid database kind
        File propsFile = createPropertiesFile(
                "application.properties", "forage.jdbc.db.kind=postgresqll" // typo in bean name
                );

        // When: validating
        ValidationResult result = ForagePropertyValidator.validate(tempDir.toFile(), catalog);

        // Then: warning with suggestions
        assertThat(result.hasWarnings()).isTrue();

        List<ValidationWarning> warnings = result.getWarnings();
        boolean hasInvalidBeanWarning = warnings.stream()
                .anyMatch(w -> w.getType() == ValidationWarning.Type.INVALID_BEAN_VALUE
                        && w.getMessage().contains("postgresqll"));

        assertThat(hasInvalidBeanWarning).isTrue();
    }

    @Test
    void testUnknownFactoryType() throws IOException {
        // Given: unknown factory type
        File propsFile = createPropertiesFile(
                "application.properties", "forage.unknown.property=value" // unknown factory
                );

        // When: validating
        ValidationResult result = ForagePropertyValidator.validate(tempDir.toFile(), catalog);

        // Then: warning about unknown factory
        assertThat(result.hasWarnings()).isTrue();
        assertThat(result.getWarnings()).anyMatch(w -> w.getType() == ValidationWarning.Type.UNKNOWN_FACTORY);
    }

    @Test
    void testMultipleWarnings() throws IOException {
        // Given: multiple issues
        File propsFile = createPropertiesFile(
                "application.properties",
                "forage.jdbc.db.kind=postgresqll", // invalid bean value
                "forage.jdbc.usernam=admin", // typo in property
                "forage.unknown.property=value" // unknown factory
                );

        // When: validating
        ValidationResult result = ForagePropertyValidator.validate(tempDir.toFile(), catalog);

        // Then: multiple warnings
        assertThat(result.hasWarnings()).isTrue();
        assertThat(result.getWarnings().size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void testValidAgentProperties() throws IOException {
        // Given: valid agent properties
        File propsFile = createPropertiesFile(
                "application.properties",
                "forage.agent.model.kind=openai",
                "forage.agent.model.name=gpt-4",
                "forage.agent.temperature=0.7");

        // When: validating
        ValidationResult result = ForagePropertyValidator.validate(tempDir.toFile(), catalog);

        // Then: no warnings
        assertThat(result.hasWarnings()).isFalse();
    }

    @Test
    void testTypoInAgentModelKind() throws IOException {
        // Given: typo in agent model kind
        File propsFile = createPropertiesFile(
                "application.properties", "forage.agent.model.kind=opena" // typo in 'openai'
                );

        // When: validating
        ValidationResult result = ForagePropertyValidator.validate(tempDir.toFile(), catalog);

        // Then: warning with suggestion
        assertThat(result.hasWarnings()).isTrue();

        boolean hasOpenAiSuggestion = result.getWarnings().stream()
                .anyMatch(w -> w.getType() == ValidationWarning.Type.INVALID_BEAN_VALUE
                        && w.getMessage().contains("opena"));

        assertThat(hasOpenAiSuggestion).isTrue();
    }

    @Test
    void testNamedInstanceProperties() throws IOException {
        // Given: named instance properties
        File propsFile = createPropertiesFile(
                "application.properties",
                "forage.ds1.jdbc.db.kind=postgresql",
                "forage.ds1.jdbc.username=admin",
                "forage.ds2.jdbc.db.kind=mysql");

        // When: validating
        ValidationResult result = ForagePropertyValidator.validate(tempDir.toFile(), catalog);

        // Then: no warnings for valid named instances
        assertThat(result.hasWarnings()).isFalse();
    }

    @Test
    void testDriverPropertiesAreAccepted() throws IOException {
        // Given: JDBC driver properties, which take any key under forage.[<name>.]jdbc.properties
        createPropertiesFile(
                "application.properties",
                "forage.jdbc.db.kind=db2",
                "forage.jdbc.properties.sslConnection=true",
                "forage.ds1.jdbc.db.kind=oracle",
                "forage.ds1.jdbc.properties.oracle.jdbc.ReadTimeout=5000",
                "forage.ds1.jdbc.propertiez.sslConnection=true"); // typo: still reported

        // When: validating
        ValidationResult result = ForagePropertyValidator.validate(tempDir.toFile(), catalog);

        // Then: only the misspelled key is reported
        assertThat(result.getWarnings()).singleElement().satisfies(warning -> assertThat(warning.getPropertyName())
                .isEqualTo("forage.ds1.jdbc.propertiez.sslConnection"));
    }

    @Test
    void testEmptyDirectory() throws IOException {
        // Given: empty directory with no properties files
        // When: validating
        ValidationResult result = ForagePropertyValidator.validate(tempDir.toFile(), catalog);

        // Then: no warnings
        assertThat(result.hasWarnings()).isFalse();
    }

    @Test
    void testNonForageProperties() throws IOException {
        // Given: properties file with non-Forage properties
        File propsFile = createPropertiesFile(
                "application.properties",
                "server.port=8080",
                "spring.application.name=myapp",
                "forage.jdbc.db.kind=postgresql" // only one Forage property
                );

        // When: validating
        ValidationResult result = ForagePropertyValidator.validate(tempDir.toFile(), catalog);

        // Then: no warnings (non-Forage properties are ignored)
        assertThat(result.hasWarnings()).isFalse();
    }

    @Test
    void testWarningFormat() throws IOException {
        // Given: invalid property
        File propsFile = createPropertiesFile("application.properties", "forage.jdbc.usernam=admin");

        // When: validating
        ValidationResult result = ForagePropertyValidator.validate(tempDir.toFile(), catalog);

        // Then: warning has proper format
        assertThat(result.hasWarnings()).isTrue();
        ValidationWarning warning = result.getWarnings().get(0);

        String formatted = warning.format();
        assertThat(formatted).contains("application.properties");
        assertThat(formatted).contains("forage.jdbc.usernam");
        assertThat(formatted).contains("UNKNOWN_PROPERTY");
    }

    @Test
    void testLevenshteinDistance() throws IOException {
        // Given: property with 1-letter typo
        File propsFile = createPropertiesFile(
                "application.properties",
                "forage.jdbc.db.kind=postgresql",
                "forage.jdbc.passwod=secret" // 'passwod' instead of 'password'
                );

        // When: validating
        ValidationResult result = ForagePropertyValidator.validate(tempDir.toFile(), catalog);

        // Then: warnings should exist with suggestions (distance = 1)
        assertThat(result.hasWarnings()).isTrue();
        boolean hasSuggestion =
                result.getWarnings().stream().anyMatch(w -> w.getMessage().contains("Did you mean"));
        assertThat(hasSuggestion).isTrue();
    }

    @Test
    void testSuggestionWithAndWithout() throws IOException {
        // Given: properties with close typo and far typo
        File propsFile = createPropertiesFile(
                "application.properties",
                "forage.jdbc.db.kind=postgresql",
                "forage.jdbc.usernam=admin", // Close typo - distance 1 from 'username'
                "forage.jdbc.completelyinvalidpropertyname=value" // Far typo - no suggestion
                );

        // When: validating
        ValidationResult result = ForagePropertyValidator.validate(tempDir.toFile(), catalog);

        // Then: should have 2 warnings
        assertThat(result.hasWarnings()).isTrue();
        assertThat(result.getWarnings()).hasSize(2);

        // Then: one warning should have suggestion, one should not
        long withSuggestion = result.getWarnings().stream()
                .filter(w -> w.getMessage().contains("Did you mean"))
                .count();
        long withoutSuggestion = result.getWarnings().stream()
                .filter(w -> !w.getMessage().contains("Did you mean"))
                .count();

        assertThat(withSuggestion).isEqualTo(1); // 'usernam' gets suggestion
        assertThat(withoutSuggestion).isEqualTo(1); // 'completelyinvalidpropertyname' does not

        // Verify the close typo warning
        ValidationWarning closeTypoWarning = result.getWarnings().stream()
                .filter(w -> w.getPropertyName().contains("usernam"))
                .findFirst()
                .orElseThrow();
        assertThat(closeTypoWarning.getMessage()).contains("Did you mean 'username'?");

        // Verify the far typo warning
        ValidationWarning farTypoWarning = result.getWarnings().stream()
                .filter(w -> w.getPropertyName().contains("completelyinvalidpropertyname"))
                .findFirst()
                .orElseThrow();
        assertThat(farTypoWarning.getMessage()).doesNotContain("Did you mean");
    }

    @Test
    void testValidationHandledGuardIsConsumedAndReset() {
        // Initially not handled
        assertThat(ForagePropertyValidator.consumeValidationHandled()).isFalse();

        // Marking makes the next consume return true...
        ForagePropertyValidator.markValidationHandled();
        assertThat(ForagePropertyValidator.consumeValidationHandled()).isTrue();

        // ...and consuming resets the flag so a reused plugin instance starts clean
        assertThat(ForagePropertyValidator.consumeValidationHandled()).isFalse();
    }

    @Test
    void testRoutePolicyPropertiesAreValid() throws IOException {
        // Per-route properties match the {routeId} entries of the Route Policy factory
        createPropertiesFile(
                "forage-policy.properties",
                "forage.route.policy.enabled=true",
                "forage.route.policy.route-primary.name=flip",
                "forage.route.policy.route-primary.flip.paired-route=route-secondary",
                "forage.route.policy.route-primary.flip.initially-active=true",
                "forage.route.policy.orders.v2.name=schedule,flip",
                "forage.route.policy.orders.v2.schedule.start-time=09:00",
                "forage.route.policy.orders.v2.schedule.stop-time=17:00",
                "forage.route.policy.orders.v2.schedule.timezone=Europe/Rome",
                "forage.route.policy.orders.v2.schedule.days-of-week=MON,TUE");

        ValidationResult result = ForagePropertyValidator.validate(tempDir.toFile(), catalog);

        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    void testUnknownRoutePolicyOption() throws IOException {
        createPropertiesFile(
                "forage-policy.properties",
                "forage.route.policy.route-a.name=flip",
                "forage.route.policy.route-a.flip.enabled=true");

        ValidationResult result = ForagePropertyValidator.validate(tempDir.toFile(), catalog);

        assertThat(result.getWarnings()).singleElement().satisfies(warning -> {
            assertThat(warning.getType()).isEqualTo(ValidationWarning.Type.UNKNOWN_PROPERTY);
            assertThat(warning.getPropertyName()).isEqualTo("forage.route.policy.route-a.flip.enabled");
        });
    }

    @Test
    void testUnknownRoutePolicyName() throws IOException {
        // Each policy of a comma-separated list is checked against the Route Policy beans
        createPropertiesFile("forage-policy.properties", "forage.route.policy.route-a.name=schedule, flp");

        ValidationResult result = ForagePropertyValidator.validate(tempDir.toFile(), catalog);

        assertThat(result.getWarnings()).singleElement().satisfies(warning -> {
            assertThat(warning.getType()).isEqualTo(ValidationWarning.Type.INVALID_BEAN_VALUE);
            assertThat(warning.getMessage()).contains("'flp'").contains("Did you mean 'flip'?");
            assertThat(warning.getMessage()).doesNotContain("'schedule'");
        });
    }

    @Test
    void testInMemoryEmbeddingStoreKindIsValid() throws IOException {
        createPropertiesFile(
                "application.properties",
                "forage.myAgent.agent.model.kind=ollama",
                "forage.myAgent.agent.embedding.model.name=granite-embedding:latest",
                "forage.myAgent.agent.embedding.store.kind=in-memory-store",
                "forage.myAgent.agent.in.memory.store.file.source=company-knowledge-base.txt",
                "forage.myAgent.agent.rag.max.results=3");

        ValidationResult result = ForagePropertyValidator.validate(tempDir.toFile(), catalog);

        assertThat(result.getWarnings()).isEmpty();
    }

    // Helper method to create properties files
    private File createPropertiesFile(String filename, String... lines) throws IOException {
        File file = tempDir.resolve(filename).toFile();
        try (FileWriter writer = new FileWriter(file)) {
            for (String line : lines) {
                writer.write(line + "\n");
            }
        }
        return file;
    }
}
