package io.kaoto.forage.cxf;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.citrusframework.annotations.CitrusTest;
import org.citrusframework.junit.jupiter.CitrusSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.integration.tests.ForageIntegrationTest;
import io.kaoto.forage.integration.tests.ForageTestCaseRunner;
import io.kaoto.forage.integration.tests.IntegrationTestSetupExtension;
import com.github.tomakehurst.wiremock.WireMockServer;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@CitrusSupport
@ExtendWith(IntegrationTestSetupExtension.class)
public class CxfSoapTest implements ForageIntegrationTest {
    private static final Logger LOG = LoggerFactory.getLogger(CxfSoapTest.class);

    public static final String INTEGRATION_NAME = "cxf-soap-client";

    static WireMockServer wireMock;

    private static final String SOAP_RESPONSE =
            """
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
              <soap:Body>
                <sayHelloResponse xmlns="http://example.com/hello">
                  <greeting>Hello Forage from CXF</greeting>
                </sayHelloResponse>
              </soap:Body>
            </soap:Envelope>""";

    private static final String WSDL =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://schemas.xmlsoap.org/wsdl/"
                         xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/"
                         xmlns:tns="http://example.com/hello"
                         xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                         name="HelloService"
                         targetNamespace="http://example.com/hello">
              <types>
                <xsd:schema targetNamespace="http://example.com/hello">
                  <xsd:element name="sayHello">
                    <xsd:complexType>
                      <xsd:sequence>
                        <xsd:element name="name" type="xsd:string"/>
                      </xsd:sequence>
                    </xsd:complexType>
                  </xsd:element>
                  <xsd:element name="sayHelloResponse">
                    <xsd:complexType>
                      <xsd:sequence>
                        <xsd:element name="greeting" type="xsd:string"/>
                      </xsd:sequence>
                    </xsd:complexType>
                  </xsd:element>
                </xsd:schema>
              </types>
              <message name="SayHelloRequest">
                <part name="parameters" element="tns:sayHello"/>
              </message>
              <message name="SayHelloResponse">
                <part name="parameters" element="tns:sayHelloResponse"/>
              </message>
              <portType name="HelloPortType">
                <operation name="sayHello">
                  <input message="tns:SayHelloRequest"/>
                  <output message="tns:SayHelloResponse"/>
                </operation>
              </portType>
              <binding name="HelloBinding" type="tns:HelloPortType">
                <soap:binding style="document" transport="http://schemas.xmlsoap.org/soap/http"/>
                <operation name="sayHello">
                  <soap:operation soapAction="sayHello"/>
                  <input><soap:body use="literal"/></input>
                  <output><soap:body use="literal"/></output>
                </operation>
              </binding>
              <service name="HelloService">
                <port name="HelloPort" binding="tns:HelloBinding">
                  <soap:address location="http://localhost:8080/ws/hello"/>
                </port>
              </service>
            </definitions>""";

    @Override
    public String runBeforeAll(ForageTestCaseRunner runner, Consumer<AutoCloseable> afterAll) {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        afterAll.accept(() -> wireMock.stop());

        wireMock.stubFor(get(urlMatching("/ws/hello\\?wsdl"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(WSDL)));

        wireMock.stubFor(post(urlEqualTo("/ws/hello"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(SOAP_RESPONSE)));

        String wireMockUrl = "http://localhost:" + wireMock.port();

        Map<String, String> envs = new HashMap<>();
        envs.put("FORAGE_CXF_ADDRESS", wireMockUrl + "/ws/hello");
        envs.put("FORAGE_CXF_WSDL_URL", wireMockUrl + "/ws/hello?wsdl");

        runner.when(forageRun(INTEGRATION_NAME, "forage-cxf.properties", "cxf-soap-client.camel.yaml")
                .dumpIntegrationOutput(true)
                .withEnvs(envs));

        return INTEGRATION_NAME;
    }

    @Test
    @CitrusTest()
    public void soapClientCall(ForageTestCaseRunner runner) {
        runner.then(camel().jbang().verify().integration(INTEGRATION_NAME).waitForLogMessage("Hello Forage from CXF"));
    }
}
