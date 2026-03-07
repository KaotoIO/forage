package io.kaoto.forage.maven.catalog;

import java.util.List;
import io.kaoto.forage.catalog.model.ConditionalBeanGroup;
import io.kaoto.forage.catalog.model.ForageFactory;

/**
 * Internal wrapper class for factories discovered during source code scanning.
 * Contains both the serializable ForageFactory model and scanning-specific metadata.
 */
public class ScannedFactory {

    private final ForageFactory factory;

    // Scanning-specific fields (not serialized to catalog)
    private String className;
    private String variant;
    private String configClassName;
    private List<String> runtimeDependencies;

    public ScannedFactory() {
        this.factory = new ForageFactory();
    }

    public ScannedFactory(
            String name,
            List<String> components,
            String description,
            String factoryType,
            String className,
            boolean autowired) {
        this.factory = new ForageFactory();
        this.factory.setName(name);
        this.factory.setComponents(components);
        this.factory.setDescription(description);
        this.factory.setFactoryType(factoryType);
        this.factory.setAutowired(autowired);
        this.className = className;
    }

    public ScannedFactory(String name, String component, String description, String factoryType, String className) {
        this(
                name,
                component != null && !component.isEmpty() ? List.of(component) : List.of(),
                description,
                factoryType,
                className,
                false);
    }

    /**
     * Returns the underlying ForageFactory model for serialization.
     */
    public ForageFactory getFactory() {
        return factory;
    }

    public String getName() {
        return factory.getName();
    }

    public void setName(String name) {
        factory.setName(name);
    }

    public String getFactoryType() {
        return factory.getFactoryType();
    }

    public void setFactoryType(String factoryType) {
        factory.setFactoryType(factoryType);
    }

    public String getDescription() {
        return factory.getDescription();
    }

    public void setDescription(String description) {
        factory.setDescription(description);
    }

    public List<String> getComponents() {
        return factory.getComponents();
    }

    public void setComponents(List<String> components) {
        factory.setComponents(components);
    }

    public boolean isAutowired() {
        return factory.isAutowired();
    }

    public void setAutowired(boolean autowired) {
        factory.setAutowired(autowired);
    }

    public List<ConditionalBeanGroup> getConditionalBeans() {
        return factory.getConditionalBeans();
    }

    public void setConditionalBeans(List<ConditionalBeanGroup> conditionalBeans) {
        factory.setConditionalBeans(conditionalBeans);
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
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

    @Override
    public String toString() {
        return "ScannedFactory{" + "name='"
                + getName() + '\'' + ", components="
                + getComponents() + ", description='"
                + getDescription() + '\'' + ", factoryType='"
                + getFactoryType() + '\'' + ", className='"
                + className + '\'' + ", autowired="
                + isAutowired() + ", variant='"
                + variant + '\'' + '}';
    }
}
