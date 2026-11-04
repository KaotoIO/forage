package io.kaoto.forage.maven.catalog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import io.kaoto.forage.catalog.model.BeanReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reads component and EIP metadata from the Camel catalog of the Camel version Forage is built
 * with, so that the Forage catalog can be derived from Camel's own data instead of being declared.
 */
class CamelCatalogLookup {

    static final String CATALOG_KIND_COMPONENT = "component";
    static final String CATALOG_KIND_PROCESSOR = "processor";
    static final String OPTION_KIND_PARAMETER = "parameter";

    private static final String CAMEL_ARTIFACT_PREFIX = "camel-";

    private final CamelCatalog camelCatalog;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Optional<JsonNode>> cache = new HashMap<>();

    CamelCatalogLookup() {
        this(new DefaultCamelCatalog(true));
    }

    CamelCatalogLookup(CamelCatalog camelCatalog) {
        this.camelCatalog = camelCatalog;
    }

    /**
     * Resolves a Forage {@code components} entry such as {@code camel-sql} to the Camel component
     * scheme ({@code sql}), if the Camel catalog knows it. Entries that are not Camel components
     * (for example {@code camel-shiro}) resolve to empty.
     */
    Optional<String> componentScheme(String forageComponent) {
        if (forageComponent == null || !forageComponent.startsWith(CAMEL_ARTIFACT_PREFIX)) {
            return Optional.empty();
        }
        String scheme = forageComponent.substring(CAMEL_ARTIFACT_PREFIX.length());
        return component(scheme).map(json -> scheme);
    }

    /**
     * Returns the comma-separated labels of a component (for example {@code database}).
     */
    List<String> componentLabels(String scheme) {
        List<String> labels = new ArrayList<>();
        component(scheme)
                .map(json -> json.path("component").path("label").asText(""))
                .ifPresent(label -> {
                    for (String value : label.split(",")) {
                        if (!value.isBlank()) {
                            labels.add(value.trim());
                        }
                    }
                });
        return labels;
    }

    /**
     * Returns the endpoint parameters of a component whose Java type is the given bean type,
     * as bean references written {@code #beanName}.
     */
    List<BeanReference> endpointParametersOfType(String scheme, String javaType) {
        List<BeanReference> references = new ArrayList<>();
        if (javaType == null) {
            return references;
        }
        component(scheme).ifPresent(json -> json.path("properties").properties().forEach(option -> {
            JsonNode metadata = option.getValue();
            if (OPTION_KIND_PARAMETER.equals(metadata.path("kind").asText())
                    && javaType.equals(metadata.path("javaType").asText())) {
                references.add(new BeanReference(
                        CATALOG_KIND_COMPONENT,
                        scheme,
                        option.getKey(),
                        OPTION_KIND_PARAMETER,
                        FactoryUiMetadata.defaultValueTemplate(OPTION_KIND_PARAMETER)));
            }
        }));
        return references;
    }

    /**
     * Returns whether the Camel catalog entry of a bean reference has the referenced option,
     * with the declared option kind.
     */
    boolean hasOption(BeanReference reference) {
        Optional<JsonNode> entry =
                switch (reference.getCatalogKind()) {
                    case CATALOG_KIND_COMPONENT -> component(reference.getName());
                    case CATALOG_KIND_PROCESSOR -> model(reference.getName());
                    default -> Optional.empty();
                };
        return entry.map(json -> json.path("properties").path(reference.getOption()))
                .filter(option -> !option.isMissingNode())
                .map(option ->
                        reference.getOptionKind().equals(option.path("kind").asText()))
                .orElse(false);
    }

    private Optional<JsonNode> component(String scheme) {
        return cache.computeIfAbsent("component:" + scheme, k -> parse(camelCatalog.componentJSonSchema(scheme)));
    }

    private Optional<JsonNode> model(String name) {
        return cache.computeIfAbsent("model:" + name, k -> parse(camelCatalog.modelJSonSchema(name)));
    }

    private Optional<JsonNode> parse(String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readTree(json));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }
}
