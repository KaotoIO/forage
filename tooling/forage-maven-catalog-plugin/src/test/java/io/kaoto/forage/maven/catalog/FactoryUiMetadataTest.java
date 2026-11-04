package io.kaoto.forage.maven.catalog;

import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugin.logging.SystemStreamLog;
import io.kaoto.forage.catalog.model.BeanReference;
import io.kaoto.forage.catalog.model.ConfigEntry;
import io.kaoto.forage.catalog.model.FeatureBeans;
import io.kaoto.forage.catalog.model.ForageBean;
import io.kaoto.forage.catalog.model.ForageFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the UI metadata added to catalog factories, using the real Camel catalog.
 */
class FactoryUiMetadataTest {

    private final List<String> warnings = new ArrayList<>();
    private FactoryUiMetadata uiMetadata;

    @BeforeEach
    void setUp() {
        uiMetadata = new FactoryUiMetadata(new SystemStreamLog() {
            @Override
            public void warn(CharSequence content) {
                warnings.add(content.toString());
            }
        });
    }

    @Test
    void addsModelPrefixPatternReferencesAndSchemaToADataSourceFactory() {
        ScannedFactory scanned = scannedFactory("javax.sql.DataSource", true);
        scanned.setBeanReferences(
                List.of(FactoryUiMetadata.declaredReference(null, "jdbc", "dataSourceName", "path", null)));
        ForageFactory factory = catalogFactory("DataSource", "javax.sql.DataSource", "camel-sql", "camel-jdbc");
        ConfigEntry kind = entry("forage.jdbc.db.kind", "bean-name", true);
        kind.setSelectsFrom("javax.sql.DataSource");
        factory.setConfigEntries(List.of(kind, entry("forage.jdbc.url", "string", true)));
        factory.getBeansByFeature().add(feature("javax.sql.DataSource", bean("postgresql"), bean("mysql")));

        uiMetadata.enrich(List.of(component(scanned)), List.of(factory));

        assertThat(factory.getModel().getKind()).isEqualTo("forage-factory");
        assertThat(factory.getModel().getName()).isEqualTo("data-source");
        assertThat(factory.getModel().getTitle()).isEqualTo("DataSource");
        assertThat(factory.getModel().getDescription()).isEqualTo("DataSource description");
        assertThat(factory.getModel().getLabel()).isEqualTo("database");
        assertThat(factory.getPrefixPattern()).isEqualTo("forage.{name}.{suffix}");
        assertThat(factory.getBeanReferences())
                .containsExactly(
                        new BeanReference("component", "sql", "dataSource", "parameter", "#{beanRef}"),
                        new BeanReference("component", "jdbc", "dataSourceName", "path", "{beanRef}"));
        assertThat(factory.getPropertiesSchema().getRequired())
                .containsExactly("forage.jdbc.db.kind", "forage.jdbc.url");
        assertThat(factory.getPropertiesSchema()
                        .getProperties()
                        .get("forage.jdbc.db.kind")
                        .getEnumValues())
                .containsExactly("postgresql", "mysql");
        assertThat(factory.getBeansByFeature()
                        .get(0)
                        .getBeans()
                        .get(0)
                        .getPropertiesSchema()
                        .getProperties())
                .isEmpty();
        assertThat(warnings).isEmpty();
    }

    @Test
    void putsBeanContributedEntriesInTheBeanSchemaOnly() {
        ScannedFactory scanned = scannedFactory("dev.langchain4j.web.search.WebSearchEngine", false);
        ForageFactory factory = catalogFactory(
                "WebSearch", "dev.langchain4j.web.search.WebSearchEngine", "camel-langchain4j-web-search");
        ConfigEntry apiKey = entry("forage.tavily.api.key", "password", true);
        ForageBean tavily = bean("tavily");
        tavily.setConfigEntries(List.of(apiKey));
        factory.setConfigEntries(List.of(apiKey));
        factory.getBeansByFeature().add(feature("Web Search Engine", tavily));

        uiMetadata.enrich(List.of(component(scanned)), List.of(factory));

        assertThat(factory.getPrefixPattern())
                .as("factory without named instances")
                .isNull();
        assertThat(factory.getBeanReferences())
                .containsExactly(new BeanReference(
                        "component", "langchain4j-web-search", "webSearchEngine", "parameter", "#{beanRef}"));
        assertThat(factory.getPropertiesSchema().getProperties()).isEmpty();
        assertThat(tavily.getPropertiesSchema().getProperties()).containsOnlyKeys("forage.tavily.api.key");
        assertThat(tavily.getPropertiesSchema().getRequired()).containsExactly("forage.tavily.api.key");
        assertThat(tavily.getPropertiesSchema()
                        .getProperties()
                        .get("forage.tavily.api.key")
                        .getFormat())
                .isEqualTo("password");
    }

    @Test
    void appendsDeclaredEipReferencesAndWarnsOnUnknownOptions() {
        ScannedFactory scanned = scannedFactory("org.apache.camel.spi.AuthorizationPolicy", false);
        scanned.setBeanReferences(List.of(
                FactoryUiMetadata.declaredReference("processor", "policy", "ref", "attribute", null),
                FactoryUiMetadata.declaredReference(null, "keycloak", "noSuchOption", "parameter", null)));
        ForageFactory factory = catalogFactory(
                "Security Policy", "org.apache.camel.spi.AuthorizationPolicy", "camel-keycloak", "camel-shiro");

        uiMetadata.enrich(List.of(component(scanned)), List.of(factory));

        assertThat(factory.getModel().getName()).isEqualTo("security-policy");
        assertThat(factory.getModel().getLabel()).isEqualTo("security,management");
        assertThat(factory.getBeanReferences())
                .containsExactly(
                        new BeanReference("component", "keycloak", "noSuchOption", "parameter", "#{beanRef}"),
                        new BeanReference("processor", "policy", "ref", "attribute", "{beanRef}"));
        assertThat(warnings).singleElement().asString().contains("noSuchOption");
    }

    @Test
    void requiresEveryVariantToSupportNamedInstances() {
        ScannedFactory base = scannedFactory("javax.sql.DataSource", true);
        ScannedFactory quarkus = scannedFactory("javax.sql.DataSource", false);
        ForageFactory factory = catalogFactory("DataSource", "javax.sql.DataSource");

        uiMetadata.enrich(List.of(component(base), component(quarkus)), List.of(factory));

        assertThat(factory.getPrefixPattern()).isNull();
        assertThat(factory.getBeanReferences()).isNull();
    }

    @Test
    void appliesBeanReferenceDefaults() {
        assertThat(FactoryUiMetadata.declaredReference(null, "sql", "dataSource", null, null))
                .isEqualTo(new BeanReference("component", "sql", "dataSource", "parameter", "#{beanRef}"));
        assertThat(FactoryUiMetadata.declaredReference(null, "cxf", "beanId", "path", "bean:{beanRef}"))
                .isEqualTo(new BeanReference("component", "cxf", "beanId", "path", "bean:{beanRef}"));
        assertThat(FactoryUiMetadata.declaredReference(null, "", "option", null, null))
                .isNull();
        assertThat(FactoryUiMetadata.declaredReference(null, "sql", null, null, null))
                .isNull();
    }

    private static ScannedFactory scannedFactory(String factoryType, boolean namedInstances) {
        ScannedFactory scanned = new ScannedFactory("factory", List.of(), "", factoryType, "x.Factory", true);
        scanned.setNamedInstances(namedInstances);
        return scanned;
    }

    private static ForageComponent component(ScannedFactory factory) {
        ForageComponent component = new ForageComponent();
        component.setArtifactId("forage-test");
        component.setFactories(List.of(factory));
        return component;
    }

    private static ForageFactory catalogFactory(String name, String factoryType, String... components) {
        ForageFactory factory = new ForageFactory(name, factoryType, name + " description");
        factory.setComponents(List.of(components));
        factory.setBeansByFeature(new ArrayList<>());
        return factory;
    }

    private static FeatureBeans feature(String name, ForageBean... beans) {
        return new FeatureBeans(name, new ArrayList<>(List.of(beans)));
    }

    private static ForageBean bean(String name) {
        ForageBean bean = new ForageBean();
        bean.setName(name);
        return bean;
    }

    private static ConfigEntry entry(String name, String type, boolean required) {
        return new ConfigEntry(name, type, name, required);
    }
}
