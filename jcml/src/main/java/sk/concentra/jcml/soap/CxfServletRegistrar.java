package sk.concentra.jcml.soap;

import io.micronaut.context.annotation.Value;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import jakarta.inject.Singleton;
import jakarta.xml.ws.Endpoint;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.endpoint.ServerRegistry;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.jaxws.JaxWsServerFactoryBean;
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.common.gzip.GZIPOutInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Publishes the CXF SOAP endpoint at application startup.
 *
 * <p>Runs on a separate port from Micronaut's main HTTP server to avoid
 * servlet infrastructure conflicts.</p>
 *
 * <p>Configuration in {@code application.yml}:
 * <pre>
 * soap:
 *   port: 8081
 *   endpoint: /ws/report
 * </pre>
 * </p>
 *
 * <p>WSDL will be available at:
 * {@code http://localhost:8081/ws/report?wsdl}</p>
 */
@Singleton
public class CxfServletRegistrar implements ApplicationEventListener<ServerStartupEvent> {

    private static final Logger log = LoggerFactory.getLogger(CxfServletRegistrar.class);

    private final ReportWebService reportWebService;
    private final String soapScheme;
    private final String soapHost;
    private final int soapPort;
    private final String soapEndpointPath;

    public CxfServletRegistrar(
            ReportWebService reportWebService,
            @Value("${soap.scheme:http}")        String soapScheme,
            @Value("${soap.host:localhost}")      String soapHost,
            @Value("${soap.port:8081}") int soapPort,
            @Value("${soap.endpoint:/ws/report}") String soapEndpointPath
    ) {
        this.reportWebService = reportWebService;
        this.soapScheme       = soapScheme;
        this.soapHost         = soapHost;
        this.soapPort = soapPort;
        this.soapEndpointPath = soapEndpointPath;
    }

    @Override
    public void onApplicationEvent(ServerStartupEvent event) {
        final String address = soapScheme + "://" + soapHost + ":" + soapPort + soapEndpointPath;
        try {
            final Bus bus = BusFactory.getDefaultBus();

            // Check if already registered on this address.
            // ServerRegistry may be null if CXF bus-extensions.txt files were not merged
            // correctly in the fat JAR (defensive guard; root fix is in shadowJar task).
            final ServerRegistry serverRegistry = bus.getExtension(ServerRegistry.class);
            final boolean alreadyRegistered = serverRegistry != null && serverRegistry.getServers().stream()
                    .anyMatch(s -> address.equals(s.getEndpoint().getEndpointInfo().getAddress()));

            if (alreadyRegistered) {
                log.info("SOAP endpoint already registered at {} — skipping.", address);
                return;
            }

            final JaxWsServerFactoryBean factory = new JaxWsServerFactoryBean();
            factory.setServiceClass(ReportWebService.class);
            factory.setServiceBean(reportWebService);
            factory.setAddress(address);
            // Enable indentation at the XMLStreamWriter level
//            Map<String, Object> properties = new HashMap<>();
//            properties.put("io.netty.maxChunkSize", 10 * 1024 * 1024);
//            properties.put("org.apache.cxf.stax.force-prop-increment", true);
//            properties.put("javax.xml.stream.XMLStreamWriter.indent", "yes");
            // Set the max content/ length (default is often only 1MB or 10MB)
//            properties.put("io.netty.maxContentLength", 300 * 1024 * 1024); // 300MB
// Increase chunk size for the response stream
//            properties.put("io.netty.maxChunkSize", 10 * 1024 * 1024); // 10MB
            // Note: This often requires the Woodstox property for indentation:
//            properties.put("com.ctc.wstx.outputIndentation", true);
//            properties.put("com.ctc.wstx.indentChar", " ");
//            properties.put("com.ctc.wstx.indentStep", "2");
//            factory.setProperties(properties);

            factory.getOutInterceptors().add((Interceptor<? extends Message>) new Utf8EncodingInterceptor());
            factory.getOutInterceptors().add(new GZIPOutInterceptor());
//            factory.getOutInterceptors().add(new PrettyPrintInterceptor());
//            factory.getOutInterceptors().add(new PrettyPrintingOutInterceptor(3));
            factory.create();
            log.info("SOAP endpoint published at:  {}", address);
            log.info("WSDL available at:           {}?wsdl", address);
        } catch (Exception e) {
            Throwable cause = e;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            log.error("Root cause message: {}", cause.getMessage());
            if (cause instanceof org.glassfish.jaxb.runtime.v2.runtime.IllegalAnnotationsException iae) {
                iae.getErrors().forEach(err -> log.error("JAXB error: {}", err.getMessage()));
            }
            throw new RuntimeException("SOAP endpoint startup failed", e);
        }
    }
}