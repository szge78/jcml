package sk.concentra.jcml.soap;

import jakarta.inject.Singleton;
import jakarta.jws.WebService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.concentra.jcml.service.ReportService;
import sk.concentra.jcml.soap.dto.GetReportResponse;

/**
 * JAX-WS implementation of {@link ReportWebService}.
 * Delegates to {@link ReportService} — same logic as the REST controller.
 */
@Singleton
@WebService(
        serviceName       = "ReportService",
        portName          = "ReportServicePort",
        targetNamespace   = "http://util.gcml.concentra.sk/",
        endpointInterface = "sk.concentra.jcml.soap.ReportWebService"
)
public class ReportWebServiceImpl implements ReportWebService {

    private static final Logger log = LoggerFactory.getLogger(ReportWebServiceImpl.class);

    private final ReportService reportService;

    public ReportWebServiceImpl(ReportService reportService) {
        this.reportService = reportService;
    }

    @Override
    public GetReportResponse getReport(final String dateFrom, final String dateTo) {
        log.info("SOAP getReport: dateFrom={}, dateTo={}", dateFrom, dateTo);
        return reportService.getReport(dateFrom, dateTo);
    }
}