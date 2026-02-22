package sk.concentra.jcml

import groovy.util.logging.Slf4j
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import sk.concentra.jcml.soap.ReportWebService
import sk.concentra.jcml.soap.dto.GetReportResponse
import spock.lang.Ignore
import spock.lang.Specification

@Slf4j
@MicronautTest
class ReportEndpointSpec extends Specification {

    static final String DATE_FROM = "2026-01-01T00:00:00"
    static final String DATE_TO   = "2026-02-01T00:00:00"

    // ── SOAP ─────────────────────────────────────────────────────────────────

    @Inject
    ReportWebService reportWebService


    @Ignore
    void 'SOAP getReport returns results for date range'() {
        when:
        GetReportResponse response = reportWebService.getReport(DATE_FROM, DATE_TO)

        then:
        response != null
        response.reportRows != null
        response.reportRows.size() > 0

        and: 'each row has a recoveryKey'
        response.reportRows.every { it.recoveryKey != null }

        and: 'each row has a logOperation'
        response.reportRows.every { it.logOperation != null }

        and: 'log summary'
        log.info("SOAP: received {} report rows", response.reportRows.size())
        response.reportRows.each { row ->
            log.info("  recoveryKey={} dateTime={} logOperation={} tableName={} userName={} fullDescription={}",
                    row.recoveryKey, row.dateTime, row.logOperation,
                    row.tableName, row.userName, row.fullDescription)
        }
    }

    // ── REST ──────────────────────────────────────────────────────────────────

    @Inject
    @Client("/")
    HttpClient httpClient

    @Ignore
    void 'REST GET /report returns results for date range'() {
        when:
        def request = HttpRequest.GET("/report?dateFrom=${DATE_FROM}&dateTo=${DATE_TO}")
        GetReportResponse response = httpClient.toBlocking()
                .retrieve(request, GetReportResponse)

        then:
        response != null
        response.reportRows != null
        response.reportRows.size() > 0

        and: 'each row has a recoveryKey'
        response.reportRows.every { it.recoveryKey != null }

        and: 'each row has a logOperation'
        response.reportRows.every { it.logOperation != null }

        and: 'log summary'
        log.info("REST: received {} report rows", response.reportRows.size())
        response.reportRows.each { row ->
            log.info("  recoveryKey={} dateTime={} logOperation={} tableName={} userName={} fullDescription={}",
                    row.recoveryKey, row.dateTime, row.logOperation,
                    row.tableName, row.userName, row.fullDescription)
        }
    }
}