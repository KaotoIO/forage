package io.kaoto.forage.cxf.soap;

import javax.net.ssl.SSLContext;

import org.apache.camel.component.cxf.jaxws.CxfEndpoint;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.cxf.Bus;
import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.transport.http.HTTPConduitConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ForageCxfEndpoint extends CxfEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(ForageCxfEndpoint.class);

    private String sslContextParametersBeanName;
    private boolean sslConfigured;

    public void setSslContextParametersBeanName(String beanName) {
        this.sslContextParametersBeanName = beanName;
    }

    public String getSslContextParametersBeanName() {
        return sslContextParametersBeanName;
    }

    @Override
    public Bus getBus() {
        Bus bus = super.getBus();

        if (sslContextParametersBeanName != null && !sslConfigured) {
            sslConfigured = true;
            applySsl(bus);
        }

        return bus;
    }

    private void applySsl(Bus bus) {
        SSLContextParameters sslCtx = getCamelContext()
                .getRegistry()
                .lookupByNameAndType(sslContextParametersBeanName, SSLContextParameters.class);
        if (sslCtx == null) {
            LOG.warn("SSL context parameters bean '{}' not found in registry", sslContextParametersBeanName);
            return;
        }

        setSslContextParameters(sslCtx);

        try {
            SSLContext sslContext = sslCtx.createSSLContext(getCamelContext());
            TLSClientParameters tlsParams = new TLSClientParameters();
            tlsParams.setSSLSocketFactory(sslContext.getSocketFactory());

            bus.setExtension(
                    (HTTPConduitConfigurer) (name, address, conduit) -> {
                        conduit.setTlsClientParameters(tlsParams);
                    },
                    HTTPConduitConfigurer.class);
        } catch (Exception e) {
            LOG.warn("Could not configure CXF Bus with SSL context for WSDL fetching: {}", e.getMessage(), e);
        }
    }
}
