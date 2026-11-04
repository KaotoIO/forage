package io.kaoto.forage.maven.catalog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.catalog.model.ForageBean;

/**
 * Internal wrapper class for beans discovered during source code scanning.
 * Contains both the serializable ForageBean model and scanning-specific metadata.
 */
public class ScannedBean {

    private static final Logger LOG = LoggerFactory.getLogger(ScannedBean.class);

    private final ForageBean bean;

    // Scanning-specific fields (not serialized to catalog)
    private List<String> components;
    private String feature;
    private String configClassName;
    private Map<String, List<String>> runtimeDependencies;
    private List<String> repositories;
    private List<String> enabledBy;

    public ScannedBean() {
        this.bean = new ForageBean();
    }

    public ScannedBean(String name, List<String> components, String description, String className, String feature) {
        this.bean = new ForageBean();
        this.bean.setName(name);
        this.bean.setDescription(description);
        this.bean.setClassName(className);
        this.components = components;
        this.feature = feature;
    }

    public ScannedBean(String name, String component, String description, String className, String feature) {
        this(
                name,
                component != null && !component.isEmpty() ? List.of(component) : List.of(),
                description,
                className,
                feature);
    }

    /**
     * Returns the underlying ForageBean model for serialization.
     */
    public ForageBean getBean() {
        return bean;
    }

    public String getName() {
        return bean.getName();
    }

    public String getDefaultBeanName() {
        return bean.getDefaultBeanName();
    }

    public void setDefaultBeanName(String defaultBeanName) {
        bean.setDefaultBeanName(defaultBeanName);
    }

    public void setName(String name) {
        bean.setName(name);
    }

    public String getDescription() {
        return bean.getDescription();
    }

    public void setDescription(String description) {
        bean.setDescription(description);
    }

    public String getClassName() {
        return bean.getClassName();
    }

    public void setClassName(String className) {
        bean.setClassName(className);
    }

    public String getGav() {
        return bean.getGav();
    }

    public void setGav(String gav) {
        bean.setGav(gav);
    }

    public List<String> getComponents() {
        return components;
    }

    public void setComponents(List<String> components) {
        this.components = components;
    }

    public String getFeature() {
        return feature;
    }

    public void setFeature(String feature) {
        this.feature = feature;
    }

    public String getConfigClassName() {
        return configClassName;
    }

    public void setConfigClassName(String configClassName) {
        this.configClassName = configClassName;
    }

    public Map<String, List<String>> getRuntimeDependencies() {
        return runtimeDependencies;
    }

    public void setRuntimeDependencies(Map<String, List<String>> runtimeDependencies) {
        this.runtimeDependencies = runtimeDependencies;
    }

    public List<String> getRepositories() {
        return repositories;
    }

    public void setRepositories(List<String> repositories) {
        this.repositories = repositories;
    }

    public List<String> getEnabledBy() {
        return enabledBy;
    }

    public void setEnabledBy(List<String> enabledBy) {
        this.enabledBy = enabledBy;
    }

    /**
     * Parses "variant:gav" format strings into a variant->gavs map.
     */
    public static Map<String, List<String>> parseVariantDependencies(List<String> rawDeps) {
        if (rawDeps == null || rawDeps.isEmpty()) {
            return null;
        }
        Map<String, List<String>> result = new HashMap<>();
        for (String dep : rawDeps) {
            int colonIndex = dep.indexOf(':');
            if (colonIndex > 0) {
                String variant = dep.substring(0, colonIndex);
                String gav = dep.substring(colonIndex + 1);
                result.computeIfAbsent(variant, k -> new ArrayList<>()).add(gav);
            } else {
                LOG.warn("Malformed variant:gav entry, expected 'variant:gav' format but got: {}", dep);
            }
        }
        return result.isEmpty() ? null : result;
    }

    /**
     * Creates a ForageBean for catalog output with GAV set.
     */
    public ForageBean toCatalogBean(String gav) {
        ForageBean catalogBean = new ForageBean();
        catalogBean.setName(bean.getName());
        catalogBean.setDescription(bean.getDescription());
        catalogBean.setDefaultBeanName(bean.getDefaultBeanName());
        catalogBean.setClassName(bean.getClassName());
        catalogBean.setGav(gav);
        catalogBean.setRuntimeDependencies(runtimeDependencies);
        catalogBean.setRepositories(repositories);
        catalogBean.setEnabledBy(enabledBy);
        return catalogBean;
    }

    @Override
    public String toString() {
        return "ScannedBean{" + "name='"
                + getName() + '\'' + ", components="
                + components + ", description='"
                + getDescription() + '\'' + ", className='"
                + getClassName() + '\'' + ", feature='"
                + feature + '\'' + '}';
    }
}
