package io.kaoto.forage.maven.catalog;

import java.util.ArrayList;
import java.util.List;
import io.kaoto.forage.catalog.model.BeanReference;
import io.kaoto.forage.catalog.model.ConditionalBeanGroup;

public class FactoryAnnotationData {

    private String name = "";
    private List<String> components = new ArrayList<>();
    private String description = "";
    private String factoryType = "";
    private boolean autowired = false;
    private String defaultBeanName = null;
    private List<ConditionalBeanGroup> conditionalBeans = new ArrayList<>();
    private String variant = "BASE";
    private String configClassName = null;
    private List<String> runtimeDependencies = new ArrayList<>();
    private boolean namedInstances = true;
    private List<BeanReference> beanReferences = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getComponents() {
        return components;
    }

    public void setComponents(List<String> components) {
        this.components = components;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getFactoryType() {
        return factoryType;
    }

    public void setFactoryType(String factoryType) {
        this.factoryType = factoryType;
    }

    public boolean isAutowired() {
        return autowired;
    }

    public void setAutowired(boolean autowired) {
        this.autowired = autowired;
    }

    public String getDefaultBeanName() {
        return defaultBeanName;
    }

    public void setDefaultBeanName(String defaultBeanName) {
        this.defaultBeanName = defaultBeanName;
    }

    public List<ConditionalBeanGroup> getConditionalBeans() {
        return conditionalBeans;
    }

    public void setConditionalBeans(List<ConditionalBeanGroup> conditionalBeans) {
        this.conditionalBeans = conditionalBeans;
    }

    public String getVariant() {
        return variant;
    }

    public void setVariant(String variant) {
        this.variant = variant;
    }

    public String getConfigClassName() {
        return configClassName;
    }

    public void setConfigClassName(String configClassName) {
        this.configClassName = configClassName;
    }

    public List<String> getRuntimeDependencies() {
        return runtimeDependencies;
    }

    public void setRuntimeDependencies(List<String> runtimeDependencies) {
        this.runtimeDependencies = runtimeDependencies;
    }

    public boolean isNamedInstances() {
        return namedInstances;
    }

    public void setNamedInstances(boolean namedInstances) {
        this.namedInstances = namedInstances;
    }

    public List<BeanReference> getBeanReferences() {
        return beanReferences;
    }

    public void setBeanReferences(List<BeanReference> beanReferences) {
        this.beanReferences = beanReferences;
    }
}
