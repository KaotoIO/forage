package io.kaoto.forage.cxf.common;

import java.util.HashMap;
import java.util.Map;

public class CxfCommonExportHelper {
    private static final String SOAP_ENDPOINT_PROVIDER = "io.kaoto.forage.cxf.soap.SoapEndpointProvider";
    private static final Map<String, String> CXF_KIND_TO_PROVIDER_CLASS = new HashMap<>();

    static {
        CXF_KIND_TO_PROVIDER_CLASS.put("soap", SOAP_ENDPOINT_PROVIDER);
    }

    public static String transformCxfKindIntoProviderClass(String cxfKind) {
        if (cxfKind == null) {
            return SOAP_ENDPOINT_PROVIDER;
        }
        return CXF_KIND_TO_PROVIDER_CLASS.getOrDefault(cxfKind.toLowerCase(), SOAP_ENDPOINT_PROVIDER);
    }
}
