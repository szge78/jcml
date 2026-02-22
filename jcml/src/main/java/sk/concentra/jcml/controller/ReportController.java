package sk.concentra.jcml.controller;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.concentra.jcml.service.ReportService;
import sk.concentra.jcml.soap.dto.GetReportResponse;

/**
 * REST endpoint replicating the legacy SOAP getReport method.
 *
 * <p>Example:
 * {@code GET /report?dateFrom=2026-02-19T11:00:00Z&dateTo=2026-02-19T12:00:00Z}</p>
 *
 * <p>Returns JSON by default. Add {@code Accept: application/xml} for XML.</p>
 */
@Controller("/report")
public class ReportController {

    private static final Logger log = LoggerFactory.getLogger(ReportController.class);

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @Get(produces = {MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @ExecuteOn(TaskExecutors.BLOCKING)
    public GetReportResponse getReport(
            @QueryValue String dateFrom,
            @QueryValue String dateTo
    ) {
        log.info("REST getReport: dateFrom={}, dateTo={}", dateFrom, dateTo);
        return reportService.getReport(dateFrom, dateTo);
    }
}