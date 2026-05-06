package io.kaoto.forage.quarkus.cxf.deployment;

import io.kaoto.forage.cxf.common.CxfConfig;
import io.quarkus.builder.item.MultiBuildItem;

public final class ForageCxfBuildItem extends MultiBuildItem {

    private final String name;
    private final String prefix;
    private final CxfConfig config;

    public ForageCxfBuildItem(String name, String prefix, CxfConfig config) {
        this.name = name;
        this.prefix = prefix;
        this.config = config;
    }

    public String getName() {
        return name;
    }

    public String getPrefix() {
        return prefix;
    }

    public CxfConfig getConfig() {
        return config;
    }
}
