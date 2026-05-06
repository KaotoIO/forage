package io.kaoto.forage.cxf.common;

import io.kaoto.forage.core.util.config.AbstractConfig;
import io.kaoto.forage.core.util.config.MissingConfigException;

import static io.kaoto.forage.cxf.common.CxfConfigEntries.ADDRESS;
import static io.kaoto.forage.cxf.common.CxfConfigEntries.CONTINUATION_TIMEOUT;
import static io.kaoto.forage.cxf.common.CxfConfigEntries.CXF_KIND;
import static io.kaoto.forage.cxf.common.CxfConfigEntries.DATA_FORMAT;
import static io.kaoto.forage.cxf.common.CxfConfigEntries.DEFAULT_OPERATION_NAME;
import static io.kaoto.forage.cxf.common.CxfConfigEntries.DEFAULT_OPERATION_NAMESPACE;
import static io.kaoto.forage.cxf.common.CxfConfigEntries.LOGGING_ENABLED;
import static io.kaoto.forage.cxf.common.CxfConfigEntries.LOGGING_SIZE_LIMIT;
import static io.kaoto.forage.cxf.common.CxfConfigEntries.MTOM_ENABLED;
import static io.kaoto.forage.cxf.common.CxfConfigEntries.PASSWORD;
import static io.kaoto.forage.cxf.common.CxfConfigEntries.PORT_NAME;
import static io.kaoto.forage.cxf.common.CxfConfigEntries.SCHEMA_VALIDATION_ENABLED;
import static io.kaoto.forage.cxf.common.CxfConfigEntries.SERVICE_CLASS;
import static io.kaoto.forage.cxf.common.CxfConfigEntries.SERVICE_NAME;
import static io.kaoto.forage.cxf.common.CxfConfigEntries.SKIP_FAULT_LOGGING;
import static io.kaoto.forage.cxf.common.CxfConfigEntries.SSL_CONTEXT_PARAMETERS;
import static io.kaoto.forage.cxf.common.CxfConfigEntries.SYNCHRONOUS;
import static io.kaoto.forage.cxf.common.CxfConfigEntries.USERNAME;
import static io.kaoto.forage.cxf.common.CxfConfigEntries.WRAPPED_STYLE;
import static io.kaoto.forage.cxf.common.CxfConfigEntries.WSDL_URL;

public class CxfConfig extends AbstractConfig {

    public CxfConfig() {
        this(null);
    }

    public CxfConfig(String prefix) {
        super(prefix, CxfConfigEntries.class);
    }

    @Override
    public String name() {
        return "forage-cxf";
    }

    public String cxfKind() {
        return getRequired(CXF_KIND, "CXF kind is required but not configured");
    }

    public String address() {
        return getRequired(ADDRESS, "CXF endpoint address is required but not configured");
    }

    public String wsdlUrl() {
        return get(WSDL_URL).orElse(null);
    }

    public String serviceClassName() {
        return get(SERVICE_CLASS).orElse(null);
    }

    public Class<?> loadServiceClass() {
        String className = serviceClassName();
        if (className == null) {
            return null;
        }
        try {
            return Thread.currentThread().getContextClassLoader().loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new MissingConfigException("Service class not found: " + className);
        }
    }

    public String serviceName() {
        return get(SERVICE_NAME).orElse(null);
    }

    public String portName() {
        return get(PORT_NAME).orElse(null);
    }

    public String dataFormat() {
        return get(DATA_FORMAT).orElse(DATA_FORMAT.defaultValue());
    }

    public boolean loggingEnabled() {
        return get(LOGGING_ENABLED)
                .map(Boolean::parseBoolean)
                .orElse(Boolean.parseBoolean(LOGGING_ENABLED.defaultValue()));
    }

    public int loggingSizeLimit() {
        return get(LOGGING_SIZE_LIMIT)
                .map(Integer::parseInt)
                .orElse(Integer.parseInt(LOGGING_SIZE_LIMIT.defaultValue()));
    }

    public boolean skipFaultLogging() {
        return get(SKIP_FAULT_LOGGING)
                .map(Boolean::parseBoolean)
                .orElse(Boolean.parseBoolean(SKIP_FAULT_LOGGING.defaultValue()));
    }

    public String username() {
        return get(USERNAME).orElse(null);
    }

    public String password() {
        return get(PASSWORD).orElse(null);
    }

    public boolean mtomEnabled() {
        return get(MTOM_ENABLED).map(Boolean::parseBoolean).orElse(Boolean.parseBoolean(MTOM_ENABLED.defaultValue()));
    }

    public Boolean wrappedStyle() {
        return get(WRAPPED_STYLE).map(Boolean::parseBoolean).orElse(null);
    }

    public boolean schemaValidationEnabled() {
        return get(SCHEMA_VALIDATION_ENABLED)
                .map(Boolean::parseBoolean)
                .orElse(Boolean.parseBoolean(SCHEMA_VALIDATION_ENABLED.defaultValue()));
    }

    public int continuationTimeout() {
        return get(CONTINUATION_TIMEOUT)
                .map(Integer::parseInt)
                .orElse(Integer.parseInt(CONTINUATION_TIMEOUT.defaultValue()));
    }

    public String defaultOperationName() {
        return get(DEFAULT_OPERATION_NAME).orElse(null);
    }

    public String defaultOperationNamespace() {
        return get(DEFAULT_OPERATION_NAMESPACE).orElse(null);
    }

    public boolean synchronous() {
        return get(SYNCHRONOUS).map(Boolean::parseBoolean).orElse(Boolean.parseBoolean(SYNCHRONOUS.defaultValue()));
    }

    public String sslContextParameters() {
        return get(SSL_CONTEXT_PARAMETERS).orElse(null);
    }
}
