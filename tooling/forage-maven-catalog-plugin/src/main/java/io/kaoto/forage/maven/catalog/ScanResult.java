package io.kaoto.forage.maven.catalog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.kaoto.forage.catalog.model.ConfigEntry;

/**
 * Holds all scan results in one structure.
 */
public class ScanResult {
    private final List<ScannedBean> beans = new ArrayList<>();
    private final List<ScannedFactory> factories = new ArrayList<>();
    private final List<ConfigEntry> configProperties = new ArrayList<>();
    private final Map<String, String> configClasses = new HashMap<>();
    private final List<String> moduleDescriptors = new ArrayList<>();

    public List<ScannedBean> getBeans() {
        return beans;
    }

    public List<ScannedFactory> getFactories() {
        return factories;
    }

    public List<ConfigEntry> getConfigProperties() {
        return configProperties;
    }

    public Map<String, String> getConfigClasses() {
        return configClasses;
    }

    public List<String> getModuleDescriptors() {
        return moduleDescriptors;
    }
}
