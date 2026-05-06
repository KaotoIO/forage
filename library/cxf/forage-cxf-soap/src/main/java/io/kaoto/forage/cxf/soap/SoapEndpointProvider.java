package io.kaoto.forage.cxf.soap;

import org.apache.camel.component.cxf.common.DataFormat;
import org.apache.camel.component.cxf.jaxws.CxfEndpoint;
import io.kaoto.forage.core.annotations.ForageBean;
import io.kaoto.forage.core.cxf.CxfEndpointProvider;
import io.kaoto.forage.cxf.common.CxfConfig;

@ForageBean(
        value = "soap",
        components = {"camel-cxf"},
        description = "Apache CXF SOAP (JAX-WS) endpoint",
        feature = "org.apache.camel.component.cxf.jaxws.CxfEndpoint")
public class SoapEndpointProvider implements CxfEndpointProvider {

    @Override
    public Object create(String id) {
        CxfConfig config = new CxfConfig(id);
        CxfEndpoint endpoint = new CxfEndpoint();

        endpoint.setAddress(config.address());
        endpoint.setDataFormat(DataFormat.valueOf(config.dataFormat()));

        if (config.wsdlUrl() != null) {
            endpoint.setWsdlURL(config.wsdlUrl());
        }

        Class<?> serviceClass = config.loadServiceClass();
        if (serviceClass != null) {
            endpoint.setServiceClass(serviceClass);
        }

        if (config.serviceName() != null) {
            endpoint.setServiceName(config.serviceName());
        }
        if (config.portName() != null) {
            endpoint.setPortName(config.portName());
        }

        endpoint.setLoggingFeatureEnabled(config.loggingEnabled());
        endpoint.setLoggingSizeLimit(config.loggingSizeLimit());
        endpoint.setSkipFaultLogging(config.skipFaultLogging());

        if (config.username() != null) {
            endpoint.setUsername(config.username());
        }
        if (config.password() != null) {
            endpoint.setPassword(config.password());
        }

        endpoint.setMtomEnabled(config.mtomEnabled());

        if (config.wrappedStyle() != null) {
            endpoint.setWrappedStyle(config.wrappedStyle());
        }

        endpoint.setSchemaValidationEnabled(config.schemaValidationEnabled());
        endpoint.setContinuationTimeout(config.continuationTimeout());
        endpoint.setSynchronous(config.synchronous());

        if (config.defaultOperationName() != null) {
            endpoint.setDefaultOperationName(config.defaultOperationName());
        }
        if (config.defaultOperationNamespace() != null) {
            endpoint.setDefaultOperationNamespace(config.defaultOperationNamespace());
        }

        return endpoint;
    }
}
