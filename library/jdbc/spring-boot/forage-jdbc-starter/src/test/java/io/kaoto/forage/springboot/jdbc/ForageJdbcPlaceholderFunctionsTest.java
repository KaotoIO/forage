package io.kaoto.forage.springboot.jdbc;

import java.sql.Connection;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.apache.camel.spring.boot.vault.AwsVaultAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import io.agroal.api.AgroalDataSource;
import io.agroal.api.security.SimplePassword;
import io.kaoto.forage.core.util.config.ConfigStore;
import io.kaoto.forage.core.util.config.PlaceholderResolver;
import io.kaoto.forage.core.util.config.UnresolvedPlaceholderException;
import io.kaoto.forage.springboot.common.ForagePlaceholderFunctionsInitializer;

import static io.kaoto.forage.springboot.jdbc.TestSecretPropertiesFunction.SECRET;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Boot: placeholder functions in Forage JDBC properties are resolved through the CamelContext
 * bean when the DataSource bean is created, with the {@code camel.vault.*} configuration of Camel Spring
 * Boot applied.
 */
class ForageJdbcPlaceholderFunctionsTest {

    // ApplicationContextRunner does not apply spring.factories initializers, SpringApplication does
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ForagePlaceholderFunctionsInitializer())
            .withConfiguration(AutoConfigurations.of(
                    CamelAutoConfiguration.class,
                    AwsVaultAutoConfiguration.class,
                    ForageDataSourceAutoConfiguration.class));

    @AfterEach
    void reset() {
        ConfigStore.getInstance().reload();
    }

    @Test
    void dataSourceCredentialsAreResolvedThroughTheCamelContext() {
        contextRunner
                .withPropertyValues("camel.vault.aws.region=sb-vault-user")
                .withSystemProperties(
                        "forage.sbph.jdbc.db.kind=h2",
                        "forage.sbph.jdbc.url=jdbc:h2:mem:sbph;DB_CLOSE_DELAY=-1",
                        "forage.sbph.jdbc.username={{forage-sb-test:region}}",
                        "forage.sbph.jdbc.password={{forage-sb-test:db-password}}")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();

                    AgroalDataSource dataSource = ctx.getBean("sbph", AgroalDataSource.class);
                    assertThat(dataSource
                                    .getConfiguration()
                                    .connectionPoolConfiguration()
                                    .connectionFactoryConfiguration()
                                    .credentials())
                            .contains(new SimplePassword(SECRET));
                    try (Connection connection = dataSource.getConnection()) {
                        assertThat(connection.getMetaData().getUserName()).isEqualToIgnoringCase("sb-vault-user");
                    }
                });
        assertThat(PlaceholderResolver.installedFunctionResolver())
                .as("the resolver is removed when the application context closes")
                .isNull();
    }

    @Test
    void unresolvablePlaceholderFailsTheStartupWithThePropertyName() {
        contextRunner
                .withSystemProperties(
                        "forage.sbbad.jdbc.db.kind=h2",
                        "forage.sbbad.jdbc.url=jdbc:h2:mem:sbbad;DB_CLOSE_DELAY=-1",
                        "forage.sbbad.jdbc.username=sa",
                        "forage.sbbad.jdbc.password=prefix-{{forage-sb-test:missing}}")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(UnresolvedPlaceholderException.class)
                            .hasMessageContaining("forage.sbbad.jdbc.password")
                            .hasMessageNotContaining("prefix-");
                });
    }
}
