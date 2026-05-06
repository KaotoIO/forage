package io.kaoto.forage.cxf.common;

import io.kaoto.forage.core.util.config.ConfigEntries;
import io.kaoto.forage.core.util.config.ConfigModule;
import io.kaoto.forage.core.util.config.ConfigTag;

public final class CxfConfigEntries extends ConfigEntries {

    public static final ConfigModule CXF_KIND = ConfigModule.ofBeanName(
            CxfConfig.class,
            "forage.cxf.kind",
            "The CXF endpoint kind/type",
            "CXF Kind",
            true,
            ConfigTag.COMMON,
            "java.lang.Object");

    public static final ConfigModule ADDRESS = ConfigModule.of(
            CxfConfig.class,
            "forage.cxf.address",
            "The CXF endpoint address URL",
            "Address",
            null,
            "string",
            true,
            ConfigTag.COMMON);

    public static final ConfigModule WSDL_URL = ConfigModule.of(
            CxfConfig.class,
            "forage.cxf.wsdl.url",
            "The WSDL document URL",
            "WSDL URL",
            null,
            "string",
            false,
            ConfigTag.COMMON);

    public static final ConfigModule SERVICE_CLASS = ConfigModule.of(
            CxfConfig.class,
            "forage.cxf.service.class",
            "The fully qualified service endpoint interface class name",
            "Service Class",
            null,
            "string",
            false,
            ConfigTag.COMMON);

    public static final ConfigModule SERVICE_NAME = ConfigModule.of(
            CxfConfig.class,
            "forage.cxf.service.name",
            "The service name in the WSDL",
            "Service Name",
            null,
            "string",
            false,
            ConfigTag.COMMON);

    public static final ConfigModule PORT_NAME = ConfigModule.of(
            CxfConfig.class,
            "forage.cxf.port.name",
            "The port name in the WSDL",
            "Port Name",
            null,
            "string",
            false,
            ConfigTag.COMMON);

    public static final ConfigModule DATA_FORMAT = ConfigModule.of(
            CxfConfig.class,
            "forage.cxf.data.format",
            "The CXF data format (POJO, PAYLOAD, RAW, CXF_MESSAGE)",
            "Data Format",
            "POJO",
            "string",
            false,
            ConfigTag.COMMON);

    public static final ConfigModule LOGGING_ENABLED = ConfigModule.of(
            CxfConfig.class,
            "forage.cxf.logging.enabled",
            "Enable CXF message logging",
            "Logging Enabled",
            "false",
            "boolean",
            false,
            ConfigTag.COMMON);

    public static final ConfigModule LOGGING_SIZE_LIMIT = ConfigModule.of(
            CxfConfig.class,
            "forage.cxf.logging.size.limit",
            "Maximum size of logged messages in bytes",
            "Logging Size Limit",
            "49152",
            "integer",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule SKIP_FAULT_LOGGING = ConfigModule.of(
            CxfConfig.class,
            "forage.cxf.skip.fault.logging",
            "Skip logging of SOAP fault messages",
            "Skip Fault Logging",
            "false",
            "boolean",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule USERNAME = ConfigModule.of(
            CxfConfig.class,
            "forage.cxf.username",
            "The username for CXF endpoint authentication",
            "Username",
            null,
            "string",
            false,
            ConfigTag.SECURITY);

    public static final ConfigModule PASSWORD = ConfigModule.of(
            CxfConfig.class,
            "forage.cxf.password",
            "The password for CXF endpoint authentication",
            "Password",
            null,
            "password",
            false,
            ConfigTag.SECURITY);

    public static final ConfigModule MTOM_ENABLED = ConfigModule.of(
            CxfConfig.class,
            "forage.cxf.mtom.enabled",
            "Enable MTOM (Message Transmission Optimization Mechanism)",
            "MTOM Enabled",
            "false",
            "boolean",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule WRAPPED_STYLE = ConfigModule.of(
            CxfConfig.class,
            "forage.cxf.wrapped.style",
            "Enable wrapped document/literal style",
            "Wrapped Style",
            null,
            "boolean",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule SCHEMA_VALIDATION_ENABLED = ConfigModule.of(
            CxfConfig.class,
            "forage.cxf.schema.validation.enabled",
            "Enable schema validation of SOAP messages",
            "Schema Validation",
            "false",
            "boolean",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule CONTINUATION_TIMEOUT = ConfigModule.of(
            CxfConfig.class,
            "forage.cxf.continuation.timeout",
            "Continuation timeout in milliseconds for asynchronous server operations",
            "Continuation Timeout",
            "30000",
            "integer",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule DEFAULT_OPERATION_NAME = ConfigModule.of(
            CxfConfig.class,
            "forage.cxf.default.operation.name",
            "The default operation name for dispatching",
            "Default Operation Name",
            null,
            "string",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule DEFAULT_OPERATION_NAMESPACE = ConfigModule.of(
            CxfConfig.class,
            "forage.cxf.default.operation.namespace",
            "The default operation namespace for dispatching",
            "Default Operation Namespace",
            null,
            "string",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule SYNCHRONOUS = ConfigModule.of(
            CxfConfig.class,
            "forage.cxf.synchronous",
            "Force synchronous invocation of the producer",
            "Synchronous",
            "false",
            "boolean",
            false,
            ConfigTag.ADVANCED);

    public static final ConfigModule SSL_CONTEXT_PARAMETERS = ConfigModule.of(
            CxfConfig.class,
            "forage.cxf.ssl.context.parameters",
            "Reference to a Camel SSLContextParameters bean by name",
            "SSL Context Parameters",
            null,
            "string",
            false,
            ConfigTag.SECURITY);

    static {
        initModules(
                CxfConfigEntries.class,
                CXF_KIND,
                ADDRESS,
                WSDL_URL,
                SERVICE_CLASS,
                SERVICE_NAME,
                PORT_NAME,
                DATA_FORMAT,
                LOGGING_ENABLED,
                LOGGING_SIZE_LIMIT,
                SKIP_FAULT_LOGGING,
                USERNAME,
                PASSWORD,
                MTOM_ENABLED,
                WRAPPED_STYLE,
                SCHEMA_VALIDATION_ENABLED,
                CONTINUATION_TIMEOUT,
                DEFAULT_OPERATION_NAME,
                DEFAULT_OPERATION_NAMESPACE,
                SYNCHRONOUS,
                SSL_CONTEXT_PARAMETERS);
    }
}
