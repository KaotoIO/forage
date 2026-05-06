package io.kaoto.forage.cxf.common;

import java.util.HashMap;
import java.util.Map;

public class CxfCommonExportHelper {
    private static final Map<String, String> CXF_KIND_TO_PROVIDER_CLASS = new HashMap<>();

    static {
        CXF_KIND_TO_PROVIDER_CLASS.put("soap", "io.kaoto.forage.cxf.soap.SoapEndpointProvider");
    }

    public static String transformCxfKindIntoProviderClass(String cxfKind) {
        return CXF_KIND_TO_PROVIDER_CLASS.getOrDefault(
                cxfKind.toLowerCase(), "io.kaoto.forage.cxf.soap.SoapEndpointProvider");
    }
}
