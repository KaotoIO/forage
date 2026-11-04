package io.kaoto.forage.maven.catalog;

import java.io.File;
import java.util.List;
import java.util.Properties;
import org.apache.maven.plugin.logging.SystemStreamLog;
import io.kaoto.forage.catalog.model.FeatureBeans;
import io.kaoto.forage.catalog.model.ForageBean;
import io.kaoto.forage.catalog.model.ForageCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests that {@link CatalogGenerator} fails instead of silently dropping beans and factories from the catalog.
 */
class CatalogCompletenessValidationTest {

    private static final String FACTORY_TYPE = "com.example.Client";

    @TempDir
    File tempDir;

    private CatalogGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new CatalogGenerator(new SystemStreamLog());
        generator.setFormat("json");
    }

    @Test
    void beanWithoutFactoryFailsAndNothingIsWritten() {
        List<ForageComponent> components = List.of(
                component(
                        "forage-example",
                        List.of(bean("example", "camel-example", "com.example.ExampleProvider")),
                        List.of(factory("Example", "camel-example", "com.example.ExampleBeanFactory"))),
                component(
                        "forage-orphan",
                        List.of(bean("orphan", "camel-other", "com.example.OrphanProvider")),
                        List.of()));

        assertThatThrownBy(() -> generate(components))
                .isInstanceOf(CatalogValidationException.class)
                .hasMessageContaining("not attached to any @ForageFactory")
                .hasMessageContaining(
                        "orphan: com.example.OrphanProvider (module forage-orphan, components [camel-other])")
                .hasMessageNotContaining("com.example.ExampleProvider")
                .hasMessageContaining("<excludedBeans>");
        assertThat(tempDir.listFiles()).isEmpty();
    }

    @Test
    void excludedBeanWithoutFactoryIsAllowedAndLeftOutOfTheCatalog() throws Exception {
        generator.setExcludedBeans(List.of(new ExcludedBean("com.example.OrphanProvider", "Pending #1")));
        List<ForageComponent> components = List.of(
                component(
                        "forage-example",
                        List.of(bean("example", "camel-example", "com.example.ExampleProvider")),
                        List.of(factory("Example", "camel-example", "com.example.ExampleBeanFactory"))),
                component(
                        "forage-orphan",
                        List.of(bean("orphan", "camel-other", "com.example.OrphanProvider")),
                        List.of()));

        generate(components);

        ForageCatalog catalog =
                new ObjectMapper().readValue(new File(tempDir, "forage-catalog.json"), ForageCatalog.class);
        assertThat(catalog.getFactories()).singleElement().satisfies(factory -> assertThat(
                        factory.getBeansByFeature().stream()
                                .map(FeatureBeans::getBeans)
                                .flatMap(List::stream)
                                .map(ForageBean::getClassName))
                .containsExactly("com.example.ExampleProvider"));
    }

    @Test
    void exclusionOfABeanListedUnderAFactoryFails() {
        generator.setExcludedBeans(List.of(new ExcludedBean("com.example.ExampleProvider", "Pending #1")));
        List<ForageComponent> components = List.of(component(
                "forage-example",
                List.of(bean("example", "camel-example", "com.example.ExampleProvider")),
                List.of(factory("Example", "camel-example", "com.example.ExampleBeanFactory"))));

        assertThatThrownBy(() -> generate(components))
                .isInstanceOf(CatalogValidationException.class)
                .hasMessageContaining("Stale <excludedBean> com.example.ExampleProvider")
                .hasMessageContaining("factory 'Example'");
    }

    @Test
    void exclusionOfAnUnknownClassFails() {
        generator.setExcludedBeans(List.of(new ExcludedBean("com.example.Renamed", "Pending #1")));
        List<ForageComponent> components = List.of(component(
                "forage-example",
                List.of(bean("example", "camel-example", "com.example.ExampleProvider")),
                List.of(factory("Example", "camel-example", "com.example.ExampleBeanFactory"))));

        assertThatThrownBy(() -> generate(components))
                .isInstanceOf(CatalogValidationException.class)
                .hasMessageContaining("<excludedBean> com.example.Renamed does not match any scanned @ForageBean");
    }

    @Test
    void exclusionWithoutReasonFails() {
        generator.setExcludedBeans(List.of(new ExcludedBean("com.example.OrphanProvider", " ")));
        List<ForageComponent> components = List.of(
                component(
                        "forage-example",
                        List.of(bean("example", "camel-example", "com.example.ExampleProvider")),
                        List.of(factory("Example", "camel-example", "com.example.ExampleBeanFactory"))),
                component(
                        "forage-orphan",
                        List.of(bean("orphan", "camel-other", "com.example.OrphanProvider")),
                        List.of()));

        assertThatThrownBy(() -> generate(components))
                .isInstanceOf(CatalogValidationException.class)
                .hasMessageContaining("<excludedBean> com.example.OrphanProvider has no <reason>");
    }

    @Test
    void twoFactoryClassesForTheSameVariantFail() {
        List<ForageComponent> components = List.of(component(
                "forage-example",
                List.of(bean("example", "camel-example", "com.example.ExampleProvider")),
                List.of(
                        factory("Example", "camel-example", "com.example.ExampleBeanFactory"),
                        factory("Example Copy", "camel-example", "com.example.OtherBeanFactory"))));

        assertThatThrownBy(() -> generate(components))
                .isInstanceOf(CatalogValidationException.class)
                .hasMessageContaining("com.example.ExampleBeanFactory and com.example.OtherBeanFactory")
                .hasMessageContaining("base variant");
    }

    @Test
    void twoBeanClassesWithTheSameNameInAFeatureFail() {
        List<ForageComponent> components = List.of(component(
                "forage-example",
                List.of(
                        bean("example", "camel-example", "com.example.ExampleProvider"),
                        bean("example", "camel-example", "com.example.OtherProvider")),
                List.of(factory("Example", "camel-example", "com.example.ExampleBeanFactory"))));

        assertThatThrownBy(() -> generate(components))
                .isInstanceOf(CatalogValidationException.class)
                .hasMessageContaining(
                        "com.example.ExampleProvider and com.example.OtherProvider are both named " + "'example'");
    }

    @Test
    void beanMatchingSeveralComponentsOfAFactoryIsListedOnce() throws Exception {
        ScannedBean bean =
                new ScannedBean("example", List.of("camel-a", "camel-b"), "Example", "com.example.ExampleProvider", "");
        ScannedFactory factory = new ScannedFactory(
                "Example", List.of("camel-a", "camel-b"), "Example", FACTORY_TYPE, "com.example.Factory", false);

        generate(List.of(component("forage-example", List.of(bean), List.of(factory))));

        ForageCatalog catalog =
                new ObjectMapper().readValue(new File(tempDir, "forage-catalog.json"), ForageCatalog.class);
        assertThat(catalog.getFactories().get(0).getBeansByFeature())
                .singleElement()
                .satisfies(feature -> assertThat(feature.getBeans()).hasSize(1));
    }

    private void generate(List<ForageComponent> components) throws Exception {
        generator.generateCatalog(components, new Properties(), tempDir);
    }

    private static ScannedBean bean(String name, String component, String className) {
        return new ScannedBean(name, component, "Bean " + name, className, "");
    }

    private static ScannedFactory factory(String name, String component, String className) {
        return new ScannedFactory(name, List.of(component), "Factory " + name, FACTORY_TYPE, className, false);
    }

    private static ForageComponent component(
            String artifactId, List<ScannedBean> beans, List<ScannedFactory> factories) {
        ForageComponent component = new ForageComponent();
        component.setGroupId("io.kaoto.forage");
        component.setArtifactId(artifactId);
        component.setVersion("1.0");
        component.setBeans(beans);
        component.setFactories(factories);
        return component;
    }
}
