package io.kaoto.forage.cxf.soap;

import javax.net.ssl.SSLContext;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.component.cxf.jaxws.CxfEndpoint;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.cxf.Bus;
import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.transport.http.HTTPConduitConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ForageCxfEndpoint extends CxfEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(ForageCxfEndpoint.class);
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "0.0.0.0");
    private static final String DEFAULT_CXF_SERVLET_PATH = "/services";

    private String sslContextParametersBeanName;
    private boolean sslConfigured;
    private String quarkusCxfServletPath;

    public void setQuarkusCxfServletPath(String path) {
        this.quarkusCxfServletPath = path;
    }

    public void setSslContextParametersBeanName(String beanName) {
        this.sslContextParametersBeanName = beanName;
    }

    public String getSslContextParametersBeanName() {
        return sslContextParametersBeanName;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        adaptAddressForQuarkusServer();
        return super.createConsumer(processor);
    }

    private void adaptAddressForQuarkusServer() {
        if (quarkusCxfServletPath == null) {
            return;
        }

        String address = getAddress();
        if (address == null || address.startsWith("/")) {
            return;
        }

        URI uri;
        try {
            uri = new URI(address);
        } catch (URISyntaxException e) {
            return;
        }

        String host = uri.getHost();
        if (host == null || !LOCAL_HOSTS.contains(host.toLowerCase())) {
            return;
        }

        String servletPath = quarkusCxfServletPath;
        if (!servletPath.startsWith("/")) {
            servletPath = "/" + servletPath;
        }
        if (servletPath.endsWith("/")) {
            servletPath = servletPath.substring(0, servletPath.length() - 1);
        }

        String path = uri.getPath();
        String relativePath;
        if (path != null && path.startsWith(servletPath)) {
            relativePath = path.substring(servletPath.length());
            if (relativePath.isEmpty()) {
                relativePath = "/";
            }
        } else {
            relativePath = path != null ? path : "/";
        }

        LOG.warn(
                "Absolute CXF address '{}' detected on Quarkus server endpoint; "
                        + "adapting to relative path '{}' (CXF servlet root: '{}')",
                address,
                relativePath,
                servletPath);

        setAddress(relativePath);
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
