package io.kaoto.forage.maven.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import io.kaoto.forage.catalog.model.ConfigEntry;

/**
 * Holds all scan results in one structure.
 * Collections are thread-safe to support parallel file scanning.
 */
public class ScanResult {
    private final List<ScannedBean> beans = Collections.synchronizedList(new ArrayList<>());
    private final List<ScannedFactory> factories = Collections.synchronizedList(new ArrayList<>());
    private final List<ConfigEntry> configProperties = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, String> configClasses = new ConcurrentHashMap<>();

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
}
